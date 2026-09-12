package com.iptv.feature.player.ui

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.MediaError
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.iptv.core.common.pip.PipController
import com.iptv.core.network.DEFAULT_USER_AGENT
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.PlaybackHistoryDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.Kinds
import com.iptv.core.storage.entity.PlaybackHistoryEntity
import com.iptv.feature.player.R
import com.iptv.feature.player.cast.CastMediaDecisions
import com.iptv.feature.player.cast.CastProxyRuntime
import com.iptv.feature.player.cast.CastStreamProber
import com.iptv.feature.player.cast.LiveAwareMediaItemConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Reproducción local/Cast. Ambos players pertenecen al ViewModel y se liberan en onCleared. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val channelDao: ChannelDao,
    private val sourceDao: SourceDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val pipController: PipController,
) : ViewModel() {

    data class UiState(
        val channel: ChannelEntity? = null,
        val isLive: Boolean = true,
        @StringRes val errorRes: Int? = null,
        val castButtonAvailable: Boolean = false,
        val castAvailable: Boolean = false,
        val isCasting: Boolean = false,
        val castDeviceName: String? = null,
        val showReturnToMobile: Boolean = false,
        val castErrorDetail: String? = null,
        /** Epoch ms en que el temporizador de apagado pausará; null = inactivo. */
        val sleepTimerEndAtMs: Long? = null,
    )

    private var channelId: Long = savedStateHandle.get<Long>("channelId")
        ?: error("channelId es obligatorio en la ruta del reproductor")

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private var localPlayer: ExoPlayer? = null
    private var castPlayer: CastPlayer? = null
    private var mediaSession: MediaSession? = null
    private var castContext: CastContext? = null
    private var mediaItem: MediaItem? = null
    private var observedMediaClient: RemoteMediaClient? = null
    private var castUserAgent: String = DEFAULT_USER_AGENT
    private var castReferrer: String? = null
    private var castTransferPending = false
    private var castTransferPlayWhenReady = false

    /** HLS wrap mode of the in-flight/current cast item ("r", "t", "n"). */
    private var activeCastHlsMode: String? = null

    /** One-shot guard: remux failures retry once with audio transcode. */
    private var transcodeEscalated = false

    /** Orden de canales en directo de la fuente activa para el zapping. */
    private var channelOrder: List<Long> = emptyList()

    private val _channelList = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val channelList: StateFlow<List<ChannelEntity>> = _channelList.asStateFlow()

    private val _castVolume = MutableStateFlow(1f)
    val castVolume: StateFlow<Float> = _castVolume.asStateFlow()
    private val _castMuted = MutableStateFlow(false)
    val castMuted: StateFlow<Boolean> = _castMuted.asStateFlow()

    private var observedCastSession: CastSession? = null
    private val castDeviceListener = object : Cast.Listener() {
        override fun onVolumeChanged() = refreshCastVolume()
    }

    val isInPipMode: StateFlow<Boolean> = pipController.isInPipMode

    /** Volumen actual del receptor Cast (0..1) y silencio. */
    fun refreshCastVolume() {
        val session = runCatching {
            castContext?.sessionManager?.currentCastSession
        }.getOrNull() ?: return
        _castVolume.value = session.volume.toFloat().coerceIn(0f, 1f)
        _castMuted.value = session.isMute
    }

    fun setCastVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        _castVolume.value = v
        runCatching {
            castContext?.sessionManager?.currentCastSession?.setVolume(v.toDouble())
        }
    }

    fun setCastMuted(muted: Boolean) {
        _castMuted.value = muted
        runCatching {
            castContext?.sessionManager?.currentCastSession?.setMute(muted)
        }
    }

    /** Activa/desactiva la entrada automática a PiP al salir con Home. */
    fun setPipAutoEnter(enabled: Boolean) {
        pipController.autoEnterOnUserLeave = enabled
    }

    /** Marca/desmarca el canal actual como favorito. */
    fun toggleFavorite() {
        val channel = _uiState.value.channel ?: return
        viewModelScope.launch {
            channelDao.setFavorite(channel.id, !channel.isFavorite)
            _uiState.update {
                it.copy(channel = channel.copy(isFavorite = !channel.isFavorite))
            }
        }
    }

    // -- Temporizador de apagado -------------------------------------------

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    /** Programa la pausa en [minutes] minutos; null la cancela. */
    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes == null) {
            _uiState.update { it.copy(sleepTimerEndAtMs = null) }
            return
        }
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        _uiState.update { it.copy(sleepTimerEndAtMs = endAt) }
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            _player.value?.pause()
            _uiState.update { it.copy(sleepTimerEndAtMs = null) }
        }
    }

    // Fuera de viewModelScope para poder hacer la escritura final en onCleared.
    private val historyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val localListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            _uiState.update { it.copy(errorRes = mapPlaybackError(error)) }
        }
    }

    private val castListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "Cast playback state: $playbackState (pending=$castTransferPending)")
            if (castTransferPending && playbackState == Player.STATE_READY) {
                completeCastTransfer()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Cast player error: ${error.errorCode} ${error.message}", error)
            // A remuxed TS stream failing usually means the receiver can't
            // decode the audio (MP2/AC3) — retry once with audio→AAC
            // transcode before giving up.
            if (activeCastHlsMode == "r" && !transcodeEscalated) {
                transcodeEscalated = true
                castTransferPending = true
                Log.w(TAG, "Retrying cast with audio transcode")
                viewModelScope.launch {
                    mediaItem?.let { applyCastItem(it, 0L, playWhenReady = true, hlsModeOverride = "t") }
                }
                return
            }
            val wasCasting = _uiState.value.isCasting
            val resumePlayback = if (wasCasting) {
                castPlayer?.playWhenReady == true
            } else {
                castTransferPlayWhenReady
            }
            castTransferPending = false
            CastProxyRuntime.stop(appContext)
            if (wasCasting) {
                val remote = castPlayer
                val local = localPlayer
                if (remote != null && local != null) transferPlayback(remote, local)
            }
            restoreLocalPlayback(resumePlayback)
            _uiState.update {
                it.copy(
                    errorRes = R.string.player_error_cast_format,
                    showReturnToMobile = true,
                )
            }
        }
    }

    private val sessionAvailabilityListener = object : SessionAvailabilityListener {
        override fun onCastSessionAvailable() = startCasting()
        override fun onCastSessionUnavailable() = stopCasting()
    }

    private val receiverErrorCallback = object : RemoteMediaClient.Callback() {
        override fun onMediaError(mediaError: MediaError) {
            val detail = buildString {
                append(mediaError.type ?: "LOAD_FAILED")
                mediaError.detailedErrorCode?.let { append(" · code ").append(it) }
                mediaError.reason?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            }
            Log.e(TAG, "Receiver media error: $detail")
            _uiState.update { it.copy(castErrorDetail = detail) }
        }
    }

    init {
        viewModelScope.launch { loadChannel() }
    }

    private suspend fun loadChannel() {
        val channel = channelDao.findById(channelId)
        if (channel == null) {
            _uiState.update { it.copy(errorRes = R.string.player_error_channel_missing) }
            return
        }
        if (channelDao.isInLockedCategory(channel.id)) {
            _uiState.update { it.copy(errorRes = R.string.player_error_locked) }
            return
        }
        if (channel.kind == Kinds.LIVE) {
            val liveChannels = channelDao.liveBySource(channel.sourceId)
            _channelList.value = liveChannels
            channelOrder = liveChannels.map { it.id }
        }
        val isLive = channel.kind == Kinds.LIVE
        _uiState.update { it.copy(channel = channel, isLive = isLive) }
        val source = sourceDao.findById(channel.sourceId)

        castUserAgent = source?.userAgent ?: DEFAULT_USER_AGENT
        castReferrer = source?.referrer?.takeIf { it.isNotBlank() }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(source?.userAgent ?: DEFAULT_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        source?.referrer?.takeIf { it.isNotBlank() }?.let { referrer ->
            httpFactory.setDefaultRequestProperties(mapOf("Referer" to referrer))
        }

        val item = buildMediaItem(channel, isLive)
        mediaItem = item
        // EXTENSION_RENDERER_MODE_PREFER: usa el decodificador FFmpeg cuando el
        // dispositivo no tiene códec nativo (AC3/EAC3/DTS, habitual en IPTV).
        val renderersFactory = DefaultRenderersFactory(appContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val newLocalPlayer = ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .also { it.addListener(localListener) }
        localPlayer = newLocalPlayer

        val resumeAt = if (isLive) 0L else resumePosition(channel.id)
        try {
            newLocalPlayer.setMediaItem(item, resumeAt)
            newLocalPlayer.prepare()
            newLocalPlayer.playWhenReady = true
            _player.value = newLocalPlayer
            // Publica la sesión para controles del sistema (bloqueo, BT, Wear OS).
            mediaSession?.release()
            mediaSession = MediaSession.Builder(appContext, newLocalPlayer).build()
        } catch (_: IllegalStateException) {
            newLocalPlayer.release()
            localPlayer = null
            _uiState.update { it.copy(errorRes = R.string.player_error_format) }
            return
        }

        if (!isLive) {
            viewModelScope.launch {
                while (isActive) {
                    delay(HISTORY_SAVE_INTERVAL_MS)
                    persistProgress()
                }
            }
        }

        initializeCastSafely()
    }

    private suspend fun resumePosition(channelId: Long): Long =
        playbackHistoryDao.find(channelId)?.let { entry ->
            val completed = entry.durationMs > 0 &&
                entry.positionMs >= entry.durationMs * COMPLETE_THRESHOLD
            if (completed) 0L else entry.positionMs
        } ?: 0L

    /** Cambia a un canal concreto desde la lista del reproductor. */
    fun playChannel(id: Long) {
        if (id == channelId) return
        viewModelScope.launch { switchChannel(id) }
    }

    /** Zapping: cambia al canal anterior/siguiente de la fuente, con retorno circular. */
    fun playAdjacent(delta: Int) {
        if (channelOrder.size < 2) return
        val index = channelOrder.indexOf(channelId)
        if (index < 0) return
        val nextId = channelOrder[Math.floorMod(index + delta, channelOrder.size)]
        if (nextId == channelId) return
        viewModelScope.launch { switchChannel(nextId) }
    }

    private suspend fun switchChannel(id: Long) {
        val channel = channelDao.findById(id)?.takeUnless { channelDao.isInLockedCategory(id) }
            ?: return
        channelId = id
        transcodeEscalated = false
        activeCastHlsMode = null
        CastProxyRuntime.channelTitle = channel.name
        val isLive = channel.kind == Kinds.LIVE
        _uiState.update {
            it.copy(
                channel = channel,
                isLive = isLive,
                errorRes = null,
                castErrorDetail = null,
            )
        }
        val item = buildMediaItem(channel, isLive)
        mediaItem = item
        val resumeAt = if (isLive) 0L else resumePosition(channel.id)
        localPlayer?.let { local ->
            local.setMediaItem(item, resumeAt)
            local.prepare()
            if (!_uiState.value.isCasting) local.playWhenReady = true
        }
        if (_uiState.value.isCasting) {
            applyCastItem(item, positionMs = 0L, playWhenReady = true)
        }
    }

    /** Lee el estado del player (hilo principal) y encola la escritura en IO. */
    private fun persistProgress() {
        val channel = _uiState.value.channel ?: return
        if (channel.kind == Kinds.LIVE) return
        val player = _player.value ?: return
        val duration = player.duration
        val position = player.currentPosition
        if (duration <= 0 || duration == C.TIME_UNSET || position <= 0) return
        historyScope.launch { writeProgress(channel, position, duration) }
    }

    private suspend fun writeProgress(channel: ChannelEntity, position: Long, duration: Long) {
        if (position >= duration * COMPLETE_THRESHOLD) {
            playbackHistoryDao.delete(channel.id)
        } else {
            playbackHistoryDao.upsert(
                PlaybackHistoryEntity(
                    channelId = channel.id,
                    sourceId = channel.sourceId,
                    positionMs = position,
                    durationMs = duration,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun buildMediaItem(channel: ChannelEntity, isLive: Boolean): MediaItem =
        PlaybackMediaItemFactory.create(
            PlaybackMediaItemFactory.spec(
                streamUrl = channel.streamUrl,
                title = channel.name,
                artworkUrl = channel.logoUrl,
                isLive = isLive,
            ),
        )

    private fun initializeCastSafely() {
        var createdPlayer: CastPlayer? = null
        try {
            val context = CastContext.getSharedInstance(appContext)
            createdPlayer = CastPlayer(context, LiveAwareMediaItemConverter())
            castContext = context
            castPlayer = createdPlayer
            createdPlayer.addListener(castListener)
            createdPlayer.setSessionAvailabilityListener(sessionAvailabilityListener)
            _uiState.update { it.copy(castButtonAvailable = true, castAvailable = true) }
            if (createdPlayer.isCastSessionAvailable) startCasting()
        } catch (_: Exception) {
            createdPlayer?.setSessionAvailabilityListener(null)
            createdPlayer?.removeListener(castListener)
            createdPlayer?.release()
            castPlayer = null
            castContext = null
            _uiState.update { it.copy(castButtonAvailable = false, castAvailable = false) }
        }
    }

    private fun startCasting() {
        val local = localPlayer ?: return
        val remote = castPlayer ?: return
        val item = mediaItem ?: return
        if (_uiState.value.isCasting || castTransferPending) return
        castTransferPlayWhenReady = local.playWhenReady
        castTransferPending = true

        observeReceiverErrors()
        publishCastExtras()

        // Re-entering the player while a cast is already running: adopt the
        // session instead of reloading the stream on the TV.
        val remoteState = runCatching {
            castContext?.sessionManager?.currentCastSession
                ?.remoteMediaClient?.mediaStatus?.playerState
        }.getOrNull()
        if (remoteState == MediaStatus.PLAYER_STATE_PLAYING ||
            remoteState == MediaStatus.PLAYER_STATE_BUFFERING ||
            remoteState == MediaStatus.PLAYER_STATE_PAUSED
        ) {
            castTransferPending = false
            local.pause()
            _player.value = remote
            mediaSession?.player = remote
            _uiState.update {
                it.copy(
                    isCasting = true,
                    castDeviceName = castContext?.sessionManager
                        ?.currentCastSession?.castDevice?.friendlyName,
                    errorRes = null,
                    showReturnToMobile = false,
                )
            }
            return
        }

        viewModelScope.launch {
            applyCastItem(
                item,
                positionMs = local.currentPosition.coerceAtLeast(0L),
                playWhenReady = castTransferPlayWhenReady,
            )
        }
    }

    /**
     * Carga [item] en el receptor Cast: sondea el payload real para decidir la
     * URL y el MIME (los paneles IPTV mienten por extensión y Content-Type),
     * y lo sirve a través del proxy local de sesión.
     */
    private suspend fun applyCastItem(
        item: MediaItem,
        positionMs: Long,
        playWhenReady: Boolean,
        hlsModeOverride: String? = null,
    ) {
        val remote = castPlayer ?: return
        val originalUrl = item.localConfiguration?.uri.toString()
        val isLive = item.liveConfiguration != MediaItem.LiveConfiguration.UNSET
        val target = withContext(Dispatchers.IO) {
            resolveCastTarget(originalUrl, isLive, hlsModeOverride)
        }
        if (castPlayer?.isCastSessionAvailable != true) return

        val remoteAddress = runCatching {
            castContext?.sessionManager?.currentCastSession?.castDevice?.inetAddress
        }.getOrNull()
        val proxy = CastProxyRuntime.ensureStarted(
            appContext, castUserAgent, castReferrer, remoteAddress,
        )
        val proxiedUrl = when {
            proxy == null -> target.url
            target.hlsMode != null -> proxy.hlsUrl(target.url, target.hlsMode)
            else -> proxy.proxyUrl(target.url)
        }
        activeCastHlsMode = target.hlsMode
        Log.d(TAG, "Cast URL: $proxiedUrl (${target.mimeType}, mode=${target.hlsMode})")

        val castItem = item.buildUpon()
            .setUri(proxiedUrl)
            .setMimeType(target.mimeType)
            .build()

        runCatching {
            remote.playWhenReady = playWhenReady
            remote.setMediaItem(castItem, positionMs)
            remote.prepare()
        }.onFailure { e ->
            Log.e(TAG, "applyCastItem failed", e)
            castTransferPending = false
            CastProxyRuntime.stop(appContext)
            restoreLocalPlayback()
        }
    }

    /** [hlsMode]: null = direct proxy; "r" = ffmpeg remux to HLS;
     *  "t" = ffmpeg remux with audio→AAC transcode; "n" = in-memory cutter.
     *  The receiver can't play progressive video/mp2t — live TS needs HLS. */
    private data class CastTarget(val url: String, val mimeType: String, val hlsMode: String?)

    /**
     * Resolves the URL and MIME type to hand to the receiver. For Xtream live
     * streams the `.m3u8` variant is tried first (real HLS), falling back to
     * the original URL when the panel serves TS at that URL or doesn't
     * support HLS output at all. Raw TS streams are wrapped in synthetic HLS
     * because the Default Media Receiver rejects progressive video/mp2t.
     */
    private fun resolveCastTarget(
        originalUrl: String,
        isLive: Boolean,
        hlsModeOverride: String? = null,
    ): CastTarget {
        val hlsVariant = CastMediaDecisions.preferHlsForLive(originalUrl, isLive)
        val candidates = if (hlsVariant == originalUrl) {
            listOf(originalUrl)
        } else {
            listOf(hlsVariant, originalUrl)
        }
        for (url in candidates) {
            val kind = CastStreamProber.probe(url, castUserAgent, castReferrer)
            Log.d(TAG, "Probe $url -> $kind")
            val target = when (kind) {
                CastStreamProber.Kind.HLS -> CastTarget(url, MimeTypes.APPLICATION_M3U8, null)
                // Raw TS live → remux to HLS through ffmpeg (keyframe-aligned
                // segments, far more reliable than naive packet cuts).
                CastStreamProber.Kind.TS -> CastTarget(url, MimeTypes.APPLICATION_M3U8, hlsModeOverride ?: "r")
                CastStreamProber.Kind.MP4 -> CastTarget(url, MimeTypes.VIDEO_MP4, null)
                CastStreamProber.Kind.DASH -> CastTarget(url, MimeTypes.APPLICATION_MPD, null)
                CastStreamProber.Kind.UNKNOWN -> null
            }
            if (target != null) return target
        }
        val url = candidates.first()
        val mime = CastMediaDecisions.mimeTypeForCast(url, isLive)
        // A live TS fallback still needs the HLS wrapper to play at all.
        val needsWrap = mime == MimeTypes.VIDEO_MP2T && isLive
        return CastTarget(
            url,
            if (needsWrap) MimeTypes.APPLICATION_M3U8 else mime,
            if (needsWrap) hlsModeOverride ?: "r" else null,
        )
    }

    private fun completeCastTransfer() {
        if (!castTransferPending || castPlayer?.isCastSessionAvailable != true) return
        val local = localPlayer ?: return
        val remote = castPlayer ?: return
        castTransferPending = false
        local.pause()
        _player.value = remote
        mediaSession?.player = remote
        _uiState.update {
            it.copy(
                isCasting = true,
                castDeviceName = castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName,
                errorRes = null,
                showReturnToMobile = false,
            )
        }
    }

    private fun stopCasting() {
        castTransferPending = false
        if (_uiState.value.isCasting) {
            val remote = castPlayer
            val local = localPlayer
            if (remote != null && local != null) transferPlayback(remote, local)
        }
        CastProxyRuntime.stop(appContext)
        restoreLocalPlayback()
    }

    private fun restoreLocalPlayback(playWhenReady: Boolean = localPlayer?.playWhenReady == true) {
        val local = localPlayer ?: return
        _player.value = local
        mediaSession?.player = local
        if (local.playbackState == Player.STATE_IDLE) local.prepare()
        local.playWhenReady = playWhenReady
        _uiState.update {
            it.copy(
                isCasting = false,
                castDeviceName = null,
                showReturnToMobile = false,
            )
        }
    }

    private fun transferPlayback(from: Player, to: Player): Boolean = runCatching {
        val item = mediaItem ?: from.currentMediaItem ?: return@runCatching false
        val position = from.currentPosition.coerceAtLeast(0L)
        val shouldPlay = from.playWhenReady
        to.setMediaItem(item, position)
        to.prepare()
        to.playWhenReady = shouldPlay
        from.pause()
        true
    }.getOrDefault(false)

    /** Shares channel info and zap control with the proxy service's
     *  media notification so casting can be controlled in background. */
    private fun publishCastExtras() {
        CastProxyRuntime.channelTitle = _uiState.value.channel?.name
        CastProxyRuntime.mediaSession = mediaSession
        CastProxyRuntime.onZap = { delta -> playAdjacent(delta) }
    }

    private fun observeReceiverErrors() {
        val session = runCatching {
            castContext?.sessionManager?.currentCastSession
        }.getOrNull() ?: return
        if (observedCastSession !== session) {
            observedCastSession?.removeCastListener(castDeviceListener)
            session.addCastListener(castDeviceListener)
            observedCastSession = session
        }
        refreshCastVolume()
        val client = session.remoteMediaClient ?: return
        if (observedMediaClient !== client) {
            observedMediaClient?.unregisterCallback(receiverErrorCallback)
            client.registerCallback(receiverErrorCallback)
            observedMediaClient = client
        }
    }

    fun retry() {
        _uiState.update { it.copy(errorRes = null, showReturnToMobile = false, castErrorDetail = null) }
        _player.value?.run {
            prepare()
            play()
        }
    }

    fun returnToMobile() {
        stopCasting()
        runCatching { castContext?.sessionManager?.endCurrentSession(true) }
    }

    @StringRes
    private fun mapPlaybackError(error: PlaybackException): Int = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> R.string.player_error_network

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        -> R.string.player_error_http

        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        -> R.string.player_error_format

        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        -> R.string.player_error_codec

        else -> R.string.player_error_generic
    }

    override fun onCleared() {
        // Última escritura de progreso antes de liberar los players; la lectura
        // de posición ocurre en el hilo principal y la escritura en IO.
        val channel = _uiState.value.channel
        val player = _player.value
        if (channel != null && channel.kind != Kinds.LIVE && player != null) {
            val duration = player.duration
            val position = player.currentPosition
            if (duration > 0 && duration != C.TIME_UNSET && position > 0) {
                runBlocking {
                    withContext(Dispatchers.IO) { writeProgress(channel, position, duration) }
                }
            }
        }
        historyScope.cancel()
        pipController.autoEnterOnUserLeave = false
        // Zap buttons live with the ViewModel; title/session stay for the
        // notification until the cast session itself ends.
        CastProxyRuntime.onZap = null

        // The proxy is session-scoped (CastProxyRuntime + CastProxyService):
        // if a Cast session is still alive it must keep running, and the
        // service tears it down when the session ends.
        mediaSession?.release()
        mediaSession = null
        observedMediaClient?.unregisterCallback(receiverErrorCallback)
        observedMediaClient = null
        observedCastSession?.removeCastListener(castDeviceListener)
        observedCastSession = null
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.removeListener(castListener)
        castPlayer?.release()
        castPlayer = null
        localPlayer?.removeListener(localListener)
        localPlayer?.release()
        localPlayer = null
        _player.value = null
        super.onCleared()
    }

    private companion object {
        const val TAG = "IptvPlayer"
        const val HISTORY_SAVE_INTERVAL_MS = 10_000L
        const val COMPLETE_THRESHOLD = 0.95
    }
}
