package com.iptv.feature.player.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.iptv.core.common.download.DownloadController
import com.iptv.core.common.pip.PipController
import com.iptv.core.network.DEFAULT_USER_AGENT
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.DownloadDao
import com.iptv.core.storage.dao.PlaybackHistoryDao
import com.iptv.core.common.prefs.AppPreferences
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.DownloadEntity
import com.iptv.core.storage.entity.DownloadSections
import com.iptv.core.storage.entity.DownloadStatus
import com.iptv.core.storage.entity.Kinds
import com.iptv.core.storage.entity.PlaybackHistoryEntity
import com.iptv.core.common.cast.CastController
import com.iptv.core.common.cast.CastEvent
import com.iptv.core.common.cast.CastStreamSpec
import com.iptv.feature.player.R
import com.iptv.core.network.StreamProber
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Reproducción local/Cast. Ambos players pertenecen al ViewModel y se liberan en onCleared. */
@kotlinx.coroutines.ExperimentalCoroutinesApi
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val channelDao: ChannelDao,
    private val sourceDao: SourceDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val downloadDao: DownloadDao,
    private val downloadController: DownloadController,
    private val pipController: PipController,
    private val prefs: AppPreferences,
    private val okHttpClient: OkHttpClient,
    private val castController: CastController,
) : ViewModel() {

    data class UiState(
        val channel: ChannelEntity? = null,
        val isLive: Boolean = true,
        @StringRes val errorRes: Int? = null,
        val castButtonAvailable: Boolean = false,
        val castAvailable: Boolean = false,
        val isCasting: Boolean = false,
        /** Sesión Cast negociándose o carga en vuelo hacia el receptor. */
        val isCastConnecting: Boolean = false,
        val castDeviceName: String? = null,
        val showReturnToMobile: Boolean = false,
        val castErrorDetail: String? = null,
        /** Epoch ms en que el temporizador de apagado pausará; null = inactivo. */
        val sleepTimerEndAtMs: Long? = null,
        /** Intento de reconexión automática en curso; null = no reconectando. */
        val reconnectAttempt: Int? = null,
        /** True mientras el sistema reporta pérdida total de conectividad. */
        val networkLost: Boolean = false,
    )

    private var channelId: Long = savedStateHandle.get<Long>("channelId")
        ?: error("channelId es obligatorio en la ruta del reproductor")

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private var localPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var mediaItem: MediaItem? = null
    private var castUserAgent: String = DEFAULT_USER_AGENT
    private var castReferrer: String? = null

    /**
     * URL remota del item en reproducción. Cuando el media local es un
     * fichero descargado (file://) el receptor Cast sigue necesitando la
     * URL http original.
     */
    private var remoteUrl: String? = null

    /** One-shot guard: format errors retry once with a sniffed MIME. */
    private var mimeRetried = false

    /** Orden de canales en directo de la fuente activa para el zapping. */
    private var channelOrder: List<Long> = emptyList()

    private val _channelList = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val channelList: StateFlow<List<ChannelEntity>> = _channelList.asStateFlow()

    private val _hasChannelList = MutableStateFlow(false)
    val hasChannelList: StateFlow<Boolean> = _hasChannelList.asStateFlow()
    private var channelListRequested = false

    /** Carga perezosa de la lista de canales: solo cuando el panel se abre. */
    fun requestChannelList() {
        if (channelListRequested) return
        channelListRequested = true
        val sourceId = _uiState.value.channel?.sourceId ?: return
        viewModelScope.launch {
            _channelList.value = channelDao.liveBySource(sourceId)
        }
    }

    val castVolume = castController.volume
    val castMuted = castController.muted

    val isInPipMode: StateFlow<Boolean> = pipController.isInPipMode

    /** Volumen actual del receptor Cast (0..1) y silencio. */
    fun refreshCastVolume() = castController.refreshCastVolume()

    fun setCastVolume(volume: Float) = castController.setVolume(volume)

    fun setCastMuted(muted: Boolean) = castController.setMuted(muted)

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

    // -- Descargas (sólo VOD/episodios) ---------------------------------------

    private val downloadChannelId = MutableStateFlow(channelId)

    /** Descarga asociada al contenido actual; null = no descargado. */
    val download: StateFlow<DownloadEntity?> = downloadChannelId
        .flatMapLatest { downloadDao.observeByChannel(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Botón de descarga del reproductor: sin descarga la encola, en curso la
     * cancela y fallida reintenta. Con la descarga completada la pantalla
     * pide confirmación y llama a [deleteDownload].
     */
    fun toggleDownload() {
        val channel = _uiState.value.channel ?: return
        if (channel.kind == Kinds.LIVE) return
        val section = if (channel.externalId.startsWith("episode:")) {
            DownloadSections.EPISODE
        } else {
            DownloadSections.MOVIE
        }
        viewModelScope.launch {
            when (download.value?.status) {
                null, DownloadStatus.FAILED ->
                    downloadController.enqueue(channel.id, section)
                DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING ->
                    downloadController.cancel(channel.id)
                else -> Unit
            }
        }
    }

    /** Borra la descarga actual y su fichero tras confirmar en la UI. */
    fun deleteDownload() {
        val id = download.value?.id ?: return
        viewModelScope.launch { downloadController.delete(id) }
    }

    // -- Permisos de Cast (notificación + descubrimiento) ---------------------

    /** Se piden una sola vez por instalación; sin flag, cada arranque re-pedía. */
    fun wasMediaPermsAsked(): Boolean = prefs.wasCastPermissionsAsked()

    fun markMediaPermsAsked() = prefs.setCastPermissionsAsked()

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

    /** Vigía de buffering: streams caídos pueden dejar al player en
     *  BUFFERING indefinidamente; pasado el umbral entra en reconexión. */
    private var bufferingWatchdog: kotlinx.coroutines.Job? = null

    /** Reconexión automática ante señal inestable (errores retriables). */
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private var reconnectAttempts = 0

    /** Parámetros de búfer del perfil elegido en Ajustes (leído al abrir). */
    private var bufferParams = PlaybackResilience.bufferParams(PlaybackResilience.PROFILE_AUTO)

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val localListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            bufferingWatchdog?.cancel()
            bufferingWatchdog = null
            // Emitiendo, el player local está pausado y el mando lo lleva el
            // receptor: un error suyo no debe tapar la pantalla de Cast.
            if (_uiState.value.isCasting) {
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
            _uiState.update { it.copy(errorRes = mapPlaybackError(error)) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    if (bufferingWatchdog?.isActive != true) {
                        // Mientras siga bufferizando, cada caducidad del vigía
                        // cuenta como un intento de reconexión: un stream
                        // colgado que no llega a errorar también se recupera.
                        bufferingWatchdog = viewModelScope.launch {
                            while (true) {
                                delay(BUFFERING_TIMEOUT_MS)
                                if (_uiState.value.isCasting) return@launch
                                if (!scheduleReconnect()) {
                                    _uiState.update {
                                        it.copy(errorRes = R.string.player_error_timeout)
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
                    // El stream se recuperó: se limpia el aviso y el contador
                    // de reconexión para que el próximo corte arranque de 0.
                    reconnectJob?.cancel()
                    reconnectAttempts = 0
                    if (_uiState.value.errorRes == R.string.player_error_timeout ||
                        _uiState.value.reconnectAttempt != null
                    ) {
                        _uiState.update {
                            it.copy(errorRes = null, reconnectAttempt = null)
                        }
                    }
                }
                else -> {
                    bufferingWatchdog?.cancel()
                    bufferingWatchdog = null
                }
            }
        }
    }

    /**
     * Encola un reintento automático con backoff exponencial. Devuelve false
     * cuando se agotan los intentos (salvo reconexión continua en directo):
     * el llamador muestra entonces el error.
     */
    private fun scheduleReconnect(): Boolean {
        if (_uiState.value.isCasting || castController.uiState.value.transferPending) return false
        val attempt = reconnectAttempts + 1
        val unlimited = _uiState.value.isLive && prefs.isLiveReconnectContinuous()
        if (!unlimited && attempt > PlaybackResilience.MAX_RECONNECT_ATTEMPTS) return false
        reconnectAttempts = attempt
        _uiState.update { it.copy(errorRes = null, reconnectAttempt = attempt) }
        if (reconnectJob?.isActive != true) {
            reconnectJob = viewModelScope.launch {
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
        if (_uiState.value.reconnectAttempt != null) {
            _uiState.update { it.copy(reconnectAttempt = null) }
        }
    }

    /**
     * Los paneles que sirven TS en URLs .m3u8 (o mienten el formato) rompen el
     * parser del manifiesto/contenedor: se reintenta una vez con el MIME que
     * revela el propio payload. Sin coste cuando el servidor es honesto.
     */
    private fun sniffRetry(error: PlaybackException): Boolean {
        if (mimeRetried ||
            mediaItem?.localConfiguration?.uri?.scheme == "file" ||
            (error.errorCode != PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED &&
                error.errorCode != PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)
        ) {
            return false
        }
        mimeRetried = true
        viewModelScope.launch {
            val url = remoteUrl ?: return@launch
            val mime = withContext(Dispatchers.IO) {
                StreamProber.probe(url, castUserAgent, castReferrer).mimeType
            } ?: return@launch
            Log.w(TAG, "Retrying playback with sniffed mime $mime")
            mediaItem = mediaItem?.buildUpon()?.setMimeType(mime)?.build()
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
     * Vigila la conectividad real: sin red se marca de inmediato (no hace
     * falta esperar al vigía) y al volver se reintenta sin esperar al backoff.
     */
    private fun registerNetworkCallback() {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                _uiState.update { it.copy(networkLost = true) }
            }

            override fun onAvailable(network: Network) {
                val wasLost = _uiState.value.networkLost
                _uiState.update { it.copy(networkLost = false) }
                if (!wasLost) return
                viewModelScope.launch {
                    val local = localPlayer ?: return@launch
                    val s = _uiState.value
                    // Errores fatales (códec, formato, 404…) no se benefician
                    // de la vuelta de la red: sólo se reintenta lo retriable.
                    val retriableError = s.errorRes == null ||
                        s.errorRes == R.string.player_error_network ||
                        s.errorRes == R.string.player_error_timeout
                    val needsKick = retriableError && (
                        local.playbackState == Player.STATE_BUFFERING ||
                            local.playbackState == Player.STATE_IDLE ||
                            s.reconnectAttempt != null ||
                            s.errorRes != null
                        )
                    if (needsKick && !s.isCasting) {
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

    init {
        // El controller publica el estado de la sesión Cast; el VM lo pliega
        // en su UiState para que la pantalla no conozca la frontera.
        viewModelScope.launch {
            castController.uiState.collect { s ->
                _uiState.update {
                    it.copy(
                        castButtonAvailable = s.available,
                        castAvailable = s.available,
                        isCastConnecting = s.connecting || s.transferPending,
                        isCasting = s.casting,
                        castDeviceName = s.deviceName,
                        castErrorDetail = s.receiverError,
                    )
                }
            }
        }
        viewModelScope.launch {
            castController.events.collect { event ->
                when (event) {
                    CastEvent.RemoteActive -> {
                        localPlayer?.pause()
                        castController.remotePlayer?.let { remote ->
                            _player.value = remote
                            mediaSession?.player = remote
                        }
                        _uiState.update {
                            it.copy(errorRes = null, showReturnToMobile = false)
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
                        )
                        if (event.showError) {
                            _uiState.update {
                                it.copy(
                                    errorRes = R.string.player_error_cast_format,
                                    showReturnToMobile = true,
                                )
                            }
                        }
                    }
                }
            }
        }
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
            // Solo ids para el zapping: cargar ~50k entidades completas por cada
            // apertura del reproductor era el mayor coste del arranque.
            channelOrder = channelDao.channelIds(channel.sourceId, Kinds.LIVE)
            _hasChannelList.value = channelOrder.size > 1
        }
        val isLive = channel.kind == Kinds.LIVE
        _uiState.update { it.copy(channel = channel, isLive = isLive) }
        val source = sourceDao.findById(channel.sourceId)

        // Prioridad: cabeceras propias del canal (#EXTVLCOPT) > las de la fuente.
        val userAgent = channel.userAgent?.takeIf { it.isNotBlank() }
            ?: source?.userAgent ?: DEFAULT_USER_AGENT
        val referrer = channel.referrer?.takeIf { it.isNotBlank() }
            ?: source?.referrer?.takeIf { it.isNotBlank() }
        castUserAgent = userAgent
        castReferrer = referrer

        // OkHttpDataSource comparte el pool del cliente inyectado: el zapping
        // reutiliza la conexión al mismo host en vez de rehacer TCP+TLS.
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(buildMap {
                put("User-Agent", userAgent)
                referrer?.let { put("Referer", it) }
            })

        bufferParams = PlaybackResilience.bufferParams(prefs.bufferProfile())
        val item = buildMediaItem(channel, isLive)
        mediaItem = item
        remoteUrl = channel.streamUrl
        // EXTENSION_RENDERER_MODE_ON: FFmpeg sólo cuando no hay códec nativo
        // (AC3/EAC3/DTS, habitual en IPTV); con PREFER también decodificaría
        // AAC por software y quemaría batería. DefaultDataSource delega
        // file:// a FileDataSource (descargas) y http(s):// a la fábrica
        // HTTP configurada arriba.
        val renderersFactory = DefaultRenderersFactory(appContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        // ForStreaming: las descargas (file://) son "local playback" y
        // mantienen sus defaults rápidos; sólo se toca el stream por red.
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
                // del Wi-Fi corte el stream cuando la pantalla duerme o la
                // señal va justa (el permiso ya está en el manifest).
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

        // El player remoto vive en el controller (ámbito de proceso); si ya
        // hay sesión activa se transfiere la reproducción en curso.
        castController.attach(newLocalPlayer)
        mediaSession?.let { castController.setMediaSessionToken(it.platformToken) }
        castController.setZapHandler { delta -> playAdjacent(delta) }
        if (castController.isSessionAvailable) {
            viewModelScope.launch {
                castController.transferToCast(
                    item,
                    newLocalPlayer.currentPosition.coerceAtLeast(0L),
                    newLocalPlayer.playWhenReady,
                    castSpec(channel),
                )
            }
        }
    }

    /** Spec del stream remoto (URL + cabeceras) que el receptor debe cargar. */
    private fun castSpec(channel: ChannelEntity) = CastStreamSpec(
        remoteUrl = remoteUrl ?: channel.streamUrl,
        userAgent = castUserAgent,
        referrer = castReferrer,
        channelTitle = channel.name,
    )

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
        downloadChannelId.value = id
        mimeRetried = false
        // El canal nuevo arranca su propio vigía y su propia reconexión,
        // sin heredar ni el tiempo ni los intentos del anterior.
        bufferingWatchdog?.cancel()
        bufferingWatchdog = null
        stopReconnect()
        reconnectAttempts = 0
        val isLive = channel.kind == Kinds.LIVE
        _uiState.update {
            it.copy(
                channel = channel,
                isLive = isLive,
                errorRes = null,
                castErrorDetail = null,
                reconnectAttempt = null,
            )
        }
        val item = buildMediaItem(channel, isLive)
        mediaItem = item
        remoteUrl = channel.streamUrl
        val resumeAt = if (isLive) 0L else resumePosition(channel.id)
        localPlayer?.let { local ->
            local.setMediaItem(item, resumeAt)
            local.prepare()
            if (!_uiState.value.isCasting) local.playWhenReady = true
        }
        if (_uiState.value.isCasting) {
            castController.loadOnRemote(item, castSpec(channel))
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





    private fun restoreLocalPlayback(playWhenReady: Boolean = localPlayer?.playWhenReady == true) {
        val local = localPlayer ?: return
        _player.value = local
        mediaSession?.player = local
        if (local.playbackState == Player.STATE_IDLE) local.prepare()
        local.playWhenReady = playWhenReady
        _uiState.update {
            it.copy(
                isCasting = false,
                isCastConnecting = false,
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



    /** Recarga el MediaItem local: el player puede quedar en BUFFERING o IDLE
     *  tras un fallo y prepare() solo no reintenta una carga en curso. En
     *  directo se vuelve al borde vivo; en VOD se conserva la posición. */
    private fun retryLocalPlayback() {
        if (_uiState.value.isCasting || castController.uiState.value.transferPending) return
        val player = localPlayer ?: return
        val item = mediaItem ?: return
        val startAt = if (_uiState.value.isLive) {
            0L
        } else {
            player.currentPosition.coerceAtLeast(0L)
        }
        player.setMediaItem(item, startAt)
        player.prepare()
        player.playWhenReady = true
    }

    fun retry() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        castController.clearReceiverError()
        _uiState.update {
            it.copy(
                errorRes = null,
                reconnectAttempt = null,
                showReturnToMobile = false,
                castErrorDetail = null,
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

    fun returnToMobile() {
        castController.endSession()
        _uiState.update { it.copy(showReturnToMobile = false) }
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
        reconnectJob?.cancel()
        bufferingWatchdog?.cancel()
        networkCallback?.let { cb ->
            runCatching {
                appContext.getSystemService(ConnectivityManager::class.java)
                    ?.unregisterNetworkCallback(cb)
            }
        }
        networkCallback = null
        pipController.autoEnterOnUserLeave = false
        // El CastPlayer y la sesión son de proceso: detach() sólo suelta los
        // listeners de esta pantalla; emitir sigue vivo al salir y el proxy
        // lo desmonta el servicio cuando la sesión termina.
        castController.setMediaSessionToken(null)
        mediaSession?.release()
        mediaSession = null
        castController.detach()
        localPlayer?.removeListener(localListener)
        localPlayer?.release()
        localPlayer = null
        _player.value = null
        super.onCleared()
    }

    private companion object {
        const val TAG = "IptvPlayer"
        const val HISTORY_SAVE_INTERVAL_MS = 10_000L
        const val BUFFERING_TIMEOUT_MS = 20_000L
        const val COMPLETE_THRESHOLD = 0.95
    }
}
