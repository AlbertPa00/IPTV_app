package com.iptv.feature.player.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.iptv.core.common.cast.CastController
import com.iptv.core.common.cast.CastEvent
import com.iptv.core.common.cast.CastStreamSpec
import com.iptv.core.common.playback.NowPlaying
import com.iptv.core.common.playback.PlaybackErrorKind
import com.iptv.core.common.playback.PlaybackInfo
import com.iptv.core.common.playback.PlaybackSession
import com.iptv.core.common.prefs.AppPreferences
import com.iptv.core.network.DEFAULT_USER_AGENT
import com.iptv.core.network.StreamProber
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.DownloadDao
import com.iptv.core.storage.dao.PlaybackHistoryDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.Kinds
import com.iptv.core.storage.entity.PlaybackHistoryEntity
import com.iptv.core.storage.entity.SourceEntity
import com.iptv.feature.player.ui.PlaybackMediaItemFactory
import com.iptv.feature.player.ui.PlaybackResilience
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Sesión de reproducción a nivel de proceso: player local/remoto, MediaSession,
 * zapping, reconexión automática, sniff de MIME, vigía de red y puente con el
 * [CastController]. Implementa el contrato [PlaybackSession] de `:core:common`.
 *
 * Los errores se clasifican con [PlaybackErrorKind]; la traducción a recursos
 * de texto es responsabilidad de la capa de presentación (el ViewModel).
 * El ciclo de vida lo decide el consumidor vía [release] — hoy el ViewModel
 * del reproductor, igual que antes de la extracción.
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Singleton
class PlaybackSessionImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val channelDao: ChannelDao,
    private val sourceDao: SourceDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val downloadDao: DownloadDao,
    private val prefs: AppPreferences,
    private val okHttpClient: OkHttpClient,
    private val castController: CastController,
) : PlaybackSession {

    /** Scope de proceso: la sesión sobrevive a los ViewModels de pantalla. */
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _player = MutableStateFlow<Player?>(null)
    override val player: StateFlow<Player?> = _player.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    override val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _info = MutableStateFlow(PlaybackInfo())
    override val info: StateFlow<PlaybackInfo> = _info.asStateFlow()

    private val _channelOrder = MutableStateFlow<List<Long>>(emptyList())
    override val channelOrder: StateFlow<List<Long>> = _channelOrder.asStateFlow()

    private var localPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var mediaItem: MediaItem? = null

    /**
     * Cabeceras HTTP del canal en reproducción (UA/Referer). El
     * ResolvingDataSource las aplica a cada petición — también a los
     * segmentos HLS, cuya URI no es la del item — así un zap a un canal con
     * #EXTVLCOPT propio no hereda las del anterior.
     */
    @Volatile private var currentRequestHeaders: Map<String, String> = emptyMap()

    /** URL remota del item en reproducción: el receptor Cast no lee file://. */
    private var remoteUrl: String? = null

    private var channelId: Long = -1L
    private var previousChannelId: Long? = null

    /** One-shot guard: format errors retry once with a sniffed MIME. */
    private var mimeRetried = false

    /** Vigía de buffering: streams colgados sin error aparente. */
    private var bufferingWatchdog: Job? = null

    /** Reconexión automática ante señal inestable (errores retriables). */
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    private var sleepTimerJob: Job? = null

    /** Bucle de persistencia de progreso VOD. */
    private var historyJob: Job? = null

    private var bufferParams =
        PlaybackResilience.bufferParams(PlaybackResilience.PROFILE_AUTO)

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val isCasting get() = castController.uiState.value.casting

    // -- Ciclo de vida de la sesión -------------------------------------------

    init {
        // Los eventos del runtime Cast mueven la reproducción entre el
        // player local y el remoto; las banderas de UI viven en _info.
        sessionScope.launch {
            castController.events.collect { event ->
                when (event) {
                    CastEvent.RemoteActive -> {
                        localPlayer?.pause()
                        castController.remotePlayer?.let { remote ->
                            _player.value = remote
                            mediaSession?.player = remote
                        }
                        _info.update {
                            it.copy(error = null, showReturnToMobile = false)
                        }
                    }
                    is CastEvent.LocalResume -> {
                        val remote = castController.remotePlayer
                        val local = localPlayer
                        if (event.transferFromRemote && remote != null && local != null) {
                            transferPlayback(remote, local)
                        }
                        restoreLocalPlayback(
                            event.playWhenReady ?: localPlayer?.playWhenReady == true,
                            event.positionMs,
                        )
                        if (event.showError) {
                            _info.update {
                                it.copy(
                                    error = PlaybackErrorKind.CAST_FORMAT,
                                    showReturnToMobile = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** Listener del player local: errores, vigía y reconexión. */
    private val localListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            bufferingWatchdog?.cancel()
            bufferingWatchdog = null
            // Emitiendo, el local está pausado y el mando lo lleva el receptor.
            if (isCasting) {
                Log.w(TAG, "Local error while casting: ${error.errorCodeName}")
                return
            }
            if (sniffRetry(error)) return
            if (PlaybackResilience.isRetriableError(error.errorCode, httpStatusOf(error)) &&
                scheduleReconnect()
            ) {
                return
            }
            stopReconnect()
            _info.update {
                it.copy(error = PlaybackResilience.mapErrorKind(error.errorCode))
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    if (bufferingWatchdog?.isActive != true) {
                        // Cada caducidad del vigía cuenta como intento: un
                        // stream colgado que no llega a errorar se recupera.
                        bufferingWatchdog = sessionScope.launch {
                            while (true) {
                                delay(BUFFERING_TIMEOUT_MS)
                                if (isCasting) return@launch
                                if (!scheduleReconnect()) {
                                    _info.update {
                                        it.copy(error = PlaybackErrorKind.TIMEOUT)
                                    }
                                    return@launch
                                }
                            }
                        }
                    }
                }
                Player.STATE_READY -> {
                    bufferingWatchdog?.cancel()
                    bufferingWatchdog = null
                    // El stream se recuperó: aviso y contador a cero.
                    reconnectJob?.cancel()
                    reconnectAttempts = 0
                    if (_info.value.error == PlaybackErrorKind.TIMEOUT ||
                        _info.value.reconnectAttempt != null
                    ) {
                        _info.update { it.copy(error = null, reconnectAttempt = null) }
                    }
                }
                else -> {
                    bufferingWatchdog?.cancel()
                    bufferingWatchdog = null
                }
            }
        }
    }

    // -- API del contrato -------------------------------------------------------

    override fun play(channelId: Long) {
        if (channelId == this.channelId && localPlayer != null) return
        sessionScope.launch {
            if (localPlayer == null) loadChannel(channelId) else switchChannel(channelId)
        }
    }

    override fun playAdjacent(delta: Int) {
        val order = _channelOrder.value
        if (order.size < 2) return
        val index = order.indexOf(channelId)
        if (index < 0) return
        val nextId = order[Math.floorMod(index + delta, order.size)]
        if (nextId == channelId) return
        sessionScope.launch { switchChannel(nextId) }
    }

    override fun zapToPrevious() {
        previousChannelId?.let(::play)
    }

    override fun seekBy(deltaMs: Long) {
        val p = _player.value ?: return
        // TIME_UNSET en duration haría coerceIn lanzar (min > max).
        if (p.duration > 0) {
            p.seekTo((p.currentPosition + deltaMs).coerceIn(0L, p.duration))
        }
    }

    override fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes == null) {
            _info.update { it.copy(sleepTimerEndAtMs = null) }
            return
        }
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        _info.update { it.copy(sleepTimerEndAtMs = endAt) }
        sleepTimerJob = sessionScope.launch {
            delay(minutes * 60_000L)
            _player.value?.pause()
            _info.update { it.copy(sleepTimerEndAtMs = null) }
        }
    }

    override fun retry() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        castController.clearReceiverError()
        _info.update {
            it.copy(
                error = null,
                reconnectAttempt = null,
                showReturnToMobile = false,
            )
        }
        val player = _player.value ?: return
        if (player === localPlayer) {
            retryLocalPlayback()
        } else {
            player.prepare()
            player.play()
        }
    }

    override fun returnToMobile() {
        castController.endSession()
        _info.update { it.copy(showReturnToMobile = false) }
    }

    override fun release() {
        // Última escritura de progreso antes de liberar; la lectura de
        // posición ocurre en main y la escritura en IO.
        val np = _nowPlaying.value
        val player = _player.value
        if (np != null && !np.isLive && player != null) {
            val duration = player.duration
            val position = player.currentPosition
            if (duration > 0 && duration != C.TIME_UNSET && position > 0) {
                runBlocking {
                    withContext(Dispatchers.IO) { writeProgress(np, position, duration) }
                }
            }
        }
        historyJob?.cancel()
        historyJob = null
        reconnectJob?.cancel()
        bufferingWatchdog?.cancel()
        sleepTimerJob?.cancel()
        networkCallback?.let { cb ->
            runCatching {
                appContext.getSystemService(ConnectivityManager::class.java)
                    ?.unregisterNetworkCallback(cb)
            }
        }
        networkCallback = null
        // El CastPlayer y la sesión son de proceso: detach() sólo suelta los
        // listeners; emitir sigue vivo al salir y el proxy lo desmonta cuando
        // termina la sesión (ver CastPlaybackController).
        castController.setMediaSessionToken(null)
        mediaSession?.release()
        mediaSession = null
        castController.detach()
        localPlayer?.removeListener(localListener)
        localPlayer?.release()
        localPlayer = null
        mediaItem = null
        remoteUrl = null
        channelId = -1L
        previousChannelId = null
        _channelOrder.value = emptyList()
        _player.value = null
        _nowPlaying.value = null
        _info.value = PlaybackInfo()
    }

    // -- Carga y cambio de canal -------------------------------------------------

    private suspend fun loadChannel(id: Long) {
        val channel = channelDao.findById(id)
        if (channel == null) {
            _info.update { it.copy(error = PlaybackErrorKind.CHANNEL_MISSING) }
            return
        }
        if (channelDao.isInLockedCategory(channel.id)) {
            _info.update { it.copy(error = PlaybackErrorKind.LOCKED) }
            return
        }
        channelId = id
        if (channel.kind == Kinds.LIVE) {
            // Solo ids para el zapping: cargar ~50k entidades completas era
            // el mayor coste del arranque del reproductor.
            _channelOrder.value = channelDao.channelIds(channel.sourceId, Kinds.LIVE)
            // Arranque de TV "donde lo dejaste": el último canal visto.
            prefs.setLastLiveChannelId(channel.id)
        }
        publishChannel(channel)
        val source = sourceDao.findById(channel.sourceId)

        // Prioridad: cabeceras propias del canal (#EXTVLCOPT) > las de la fuente.
        val (userAgent, referrer) = headersFor(channel, source)
        currentRequestHeaders = requestHeaders(userAgent, referrer)

        // OkHttpDataSource comparte el pool del cliente inyectado: el zapping
        // reutiliza la conexión al mismo host en vez de rehacer TCP+TLS.
        val httpFactory = ResolvingDataSource.Factory(
            OkHttpDataSource.Factory(okHttpClient),
        ) { spec ->
            spec.buildUpon().setHttpRequestHeaders(currentRequestHeaders).build()
        }

        bufferParams = PlaybackResilience.bufferParams(prefs.bufferProfile())
        val isLive = channel.kind == Kinds.LIVE
        val item = buildMediaItem(channel, isLive)
        mediaItem = item
        remoteUrl = channel.streamUrl
        // EXTENSION_RENDERER_MODE_ON: FFmpeg sólo cuando no hay códec nativo
        // (AC3/EAC3/DTS, habitual en IPTV). DefaultDataSource delega file://
        // a FileDataSource (descargas) y http(s):// a la fábrica HTTP de arriba.
        val renderersFactory = DefaultRenderersFactory(appContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        // ForStreaming: las descargas (file://) mantienen sus defaults rápidos;
        // sólo se toca el stream por red.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMsForStreaming(
                bufferParams.minBufferMs,
                bufferParams.maxBufferMs,
                bufferParams.bufferForPlaybackMs,
                bufferParams.bufferForPlaybackAfterRebufferMs,
            )
            .build()
        val newLocalPlayer = ExoPlayer.Builder(appContext, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(appContext, httpFactory)),
            )
            .build()
            .also {
                // WAKE_MODE_NETWORK = WifiLock: evita que el ahorro de energía
                // del Wi-Fi corte el stream con pantalla dormida o señal justa.
                it.setWakeMode(C.WAKE_MODE_NETWORK)
                it.setHandleAudioBecomingNoisy(true)
                it.addListener(localListener)
            }
        localPlayer = newLocalPlayer
        registerNetworkCallback()

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
            _info.update { it.copy(error = PlaybackErrorKind.FORMAT) }
            return
        }

        startProgressLoop(isLive)

        // El player remoto vive en el controller (ámbito de proceso); si ya
        // hay sesión activa se transfiere la reproducción en curso.
        castController.attach(newLocalPlayer)
        mediaSession?.let { castController.setMediaSessionToken(it.platformToken) }
        castController.setZapHandler { delta -> playAdjacent(delta) }
        if (castController.isSessionAvailable) {
            sessionScope.launch {
                castController.transferToCast(
                    item,
                    newLocalPlayer.currentPosition.coerceAtLeast(0L),
                    newLocalPlayer.playWhenReady,
                    castSpec(channel),
                )
            }
        }
    }

    private suspend fun switchChannel(id: Long) {
        val channel = channelDao.findById(id)?.takeUnless { channelDao.isInLockedCategory(id) }
            ?: return
        previousChannelId = channelId
        channelId = id
        mimeRetried = false
        // El canal nuevo arranca su propio vigía y su propia reconexión.
        bufferingWatchdog?.cancel()
        bufferingWatchdog = null
        stopReconnect()
        reconnectAttempts = 0
        val isLive = channel.kind == Kinds.LIVE
        if (isLive) {
            // El zap también actualiza el "último canal" del arranque de TV.
            prefs.setLastLiveChannelId(channel.id)
        }
        _info.update {
            it.copy(error = null, reconnectAttempt = null)
        }
        publishChannel(channel)
        val (ua, ref) = headersFor(channel)
        currentRequestHeaders = requestHeaders(ua, ref)
        val item = buildMediaItem(channel, isLive)
        mediaItem = item
        remoteUrl = channel.streamUrl
        val resumeAt = if (isLive) 0L else resumePosition(channel.id)
        localPlayer?.let { local ->
            local.setMediaItem(item, resumeAt)
            local.prepare()
            if (!isCasting) local.playWhenReady = true
        }
        if (isCasting) {
            castController.loadOnRemote(item, castSpec(channel))
        }
        startProgressLoop(isLive)
    }

    /** Publica la identidad del canal en reproducción (sin entidad Room). */
    private fun publishChannel(channel: ChannelEntity) {
        _nowPlaying.value = NowPlaying(
            channelId = channel.id,
            sourceId = channel.sourceId,
            name = channel.name,
            logoUrl = channel.logoUrl,
            isLive = channel.kind == Kinds.LIVE,
            isFavorite = channel.isFavorite,
        )
    }

    /** Bucle de persistencia de progreso: sólo VOD/episodios. */
    private fun startProgressLoop(isLive: Boolean) {
        historyJob?.cancel()
        historyJob = null
        if (isLive) return
        historyJob = sessionScope.launch {
            while (isActive) {
                delay(HISTORY_SAVE_INTERVAL_MS)
                persistProgress()
            }
        }
    }

    private fun persistProgress() {
        val np = _nowPlaying.value ?: return
        if (np.isLive) return
        val player = _player.value ?: return
        val duration = player.duration
        val position = player.currentPosition
        if (duration <= 0 || duration == C.TIME_UNSET || position <= 0) return
        sessionScope.launch(Dispatchers.IO) { writeProgress(np, position, duration) }
    }

    private suspend fun writeProgress(np: NowPlaying, position: Long, duration: Long) {
        if (position >= duration * COMPLETE_THRESHOLD) {
            playbackHistoryDao.delete(np.channelId)
        } else {
            playbackHistoryDao.upsert(
                PlaybackHistoryEntity(
                    channelId = np.channelId,
                    sourceId = np.sourceId,
                    positionMs = position,
                    durationMs = duration,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun resumePosition(channelId: Long): Long =
        playbackHistoryDao.find(channelId)?.let { entry ->
            val completed = entry.durationMs > 0 &&
                entry.positionMs >= entry.durationMs * COMPLETE_THRESHOLD
            if (completed) 0L else entry.positionMs
        } ?: 0L

    private suspend fun buildMediaItem(channel: ChannelEntity, isLive: Boolean): MediaItem {
        // Si hay una descarga completa se reproduce el fichero local; la URL
        // remota se conserva aparte para Cast (el receptor no lee file://).
        val downloaded = downloadDao.findCompleted(channel.id)
        val uri = downloaded?.localPath
            ?.let { path -> java.io.File(path).takeIf { it.exists() } }
            ?.let { android.net.Uri.fromFile(it).toString() }
            ?: channel.streamUrl
        return PlaybackMediaItemFactory.create(
            PlaybackMediaItemFactory.spec(
                streamUrl = uri,
                title = channel.name,
                artworkUrl = channel.logoUrl,
                isLive = isLive,
                liveTargetOffsetMs = bufferParams.liveTargetOffsetMs,
            ),
        )
    }

    // -- Cast ----------------------------------------------------------------

    private fun restoreLocalPlayback(
        playWhenReady: Boolean = localPlayer?.playWhenReady == true,
        positionMs: Long? = null,
    ) {
        val local = localPlayer ?: return
        _player.value = local
        mediaSession?.player = local
        // El controlador detiene el local al transferir (libera la conexión
        // del panel): en IDLE se recarga el item en la posición guardada.
        if (local.playbackState == Player.STATE_IDLE) {
            val item = mediaItem
            if (item != null) {
                if (positionMs != null) local.setMediaItem(item, positionMs)
                else local.setMediaItem(item)
            }
            local.prepare()
        }
        local.playWhenReady = playWhenReady
        _info.update {
            it.copy(showReturnToMobile = false)
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

    /** Spec del stream remoto (URL + cabeceras) que el receptor debe cargar. */
    private suspend fun castSpec(channel: ChannelEntity): CastStreamSpec {
        val (userAgent, referrer) = headersFor(channel)
        return CastStreamSpec(
            remoteUrl = remoteUrl ?: channel.streamUrl,
            userAgent = userAgent,
            referrer = referrer,
            channelTitle = channel.name,
            containerExt = channel.containerExt,
        )
    }

    // -- Reconexión, vigía y red -------------------------------------------------

    /**
     * Encola un reintento con backoff exponencial. Devuelve false cuando se
     * agotan los intentos (salvo reconexión continua en directo): el llamador
     * muestra entonces el error.
     */
    private fun scheduleReconnect(): Boolean {
        if (isCasting || castController.uiState.value.transferPending) return false
        val attempt = reconnectAttempts + 1
        val unlimited = _nowPlaying.value?.isLive == true && prefs.isLiveReconnectContinuous()
        if (!unlimited && attempt > PlaybackResilience.MAX_RECONNECT_ATTEMPTS) return false
        reconnectAttempts = attempt
        _info.update { it.copy(error = null, reconnectAttempt = attempt) }
        if (reconnectJob?.isActive != true) {
            reconnectJob = sessionScope.launch {
                delay(PlaybackResilience.reconnectDelayMs(attempt))
                reconnectJob = null
                retryLocalPlayback()
            }
        }
        return true
    }

    private fun stopReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        if (_info.value.reconnectAttempt != null) {
            _info.update { it.copy(reconnectAttempt = null) }
        }
    }

    /** Recarga el MediaItem local: tras un fallo prepare() no reintenta una
     *  carga en curso. En directo vuelve al borde vivo; en VOD conserva. */
    private fun retryLocalPlayback() {
        if (isCasting || castController.uiState.value.transferPending) return
        val player = localPlayer ?: return
        val item = mediaItem ?: return
        val startAt = if (_nowPlaying.value?.isLive == true) {
            0L
        } else {
            player.currentPosition.coerceAtLeast(0L)
        }
        player.setMediaItem(item, startAt)
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * Paneles que sirven TS en URLs .m3u8 (o mienten el formato): se
     * reintenta una vez con el MIME que revela el propio payload.
     */
    private fun sniffRetry(error: PlaybackException): Boolean {
        val item = mediaItem
        val url = remoteUrl
        val channelIdAtError = channelId
        if (item == null || url == null || channelIdAtError < 0 || mimeRetried ||
            item.localConfiguration?.uri?.scheme == "file" ||
            (error.errorCode != PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED &&
                error.errorCode != PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)
        ) {
            return false
        }
        mimeRetried = true
        sessionScope.launch {
            val channel = channelDao.findById(channelIdAtError) ?: return@launch
            val (ua, ref) = headersFor(channel)
            val mime = withContext(Dispatchers.IO) {
                StreamProber.probe(okHttpClient, url, ua, ref).mimeType
            } ?: return@launch
            // El usuario pudo zapear durante el sondeo: sólo se aplica si el
            // item en reproducción sigue siendo el que falló.
            if (mediaItem !== item) return@launch
            Log.w(TAG, "Retrying playback with sniffed mime $mime")
            mediaItem = item.buildUpon().setMimeType(mime).build()
            retryLocalPlayback()
        }
        return true
    }

    /** Extrae el código HTTP si el fallo vino de una respuesta del servidor. */
    private fun httpStatusOf(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
        }
        return null
    }

    /**
     * Vigila la conectividad real: sin red se marca de inmediato y al volver
     * se reintenta sin esperar al backoff.
     */
    private fun registerNetworkCallback() {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                _info.update { it.copy(networkLost = true) }
            }

            override fun onAvailable(network: Network) {
                val wasLost = _info.value.networkLost
                _info.update { it.copy(networkLost = false) }
                if (!wasLost) return
                sessionScope.launch {
                    val local = localPlayer ?: return@launch
                    val s = _info.value
                    // Errores fatales no se benefician de la vuelta de la red:
                    // sólo se reintenta lo retriable.
                    val retriableError = s.error == null ||
                        s.error == PlaybackErrorKind.NETWORK ||
                        s.error == PlaybackErrorKind.TIMEOUT
                    val needsKick = retriableError && (
                        local.playbackState == Player.STATE_BUFFERING ||
                            local.playbackState == Player.STATE_IDLE ||
                            s.reconnectAttempt != null ||
                            s.error != null
                        )
                    if (needsKick && !isCasting) {
                        reconnectJob?.cancel()
                        reconnectJob = null
                        retryLocalPlayback()
                    }
                }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onSuccess { networkCallback = callback }
    }

    // -- Utilidades -------------------------------------------------------------

    private suspend fun headersFor(channel: ChannelEntity): Pair<String, String?> =
        headersFor(channel, sourceDao.findById(channel.sourceId))

    private fun headersFor(
        channel: ChannelEntity,
        source: SourceEntity?,
    ): Pair<String, String?> {
        val userAgent = channel.userAgent?.takeIf { it.isNotBlank() }
            ?: source?.userAgent ?: DEFAULT_USER_AGENT
        val referrer = channel.referrer?.takeIf { it.isNotBlank() }
            ?: source?.referrer?.takeIf { it.isNotBlank() }
        return userAgent to referrer
    }

    private fun requestHeaders(userAgent: String, referrer: String?) = buildMap {
        put("User-Agent", userAgent)
        referrer?.let { put("Referer", it) }
    }

    private companion object {
        const val TAG = "IptvPlayback"
        const val HISTORY_SAVE_INTERVAL_MS = 10_000L
        const val BUFFERING_TIMEOUT_MS = 20_000L
        const val COMPLETE_THRESHOLD = 0.95
    }
}
