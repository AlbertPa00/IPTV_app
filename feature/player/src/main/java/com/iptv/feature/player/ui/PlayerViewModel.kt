package com.iptv.feature.player.ui

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.iptv.core.common.cast.CastController
import com.iptv.core.common.download.DownloadController
import com.iptv.core.common.pip.PipController
import com.iptv.core.common.playback.PlaybackErrorKind
import com.iptv.core.common.playback.PlaybackSession
import com.iptv.core.common.prefs.AppPreferences
import com.iptv.core.common.tvlink.TvLinkClient
import com.iptv.core.common.tvlink.TvLinkConnection
import com.iptv.core.storage.dao.CategoryDao
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.DownloadDao
import com.iptv.core.storage.dao.GuideRow
import com.iptv.core.storage.dao.ProgrammeDao
import com.iptv.core.storage.entity.CategoryEntity
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.DownloadEntity
import com.iptv.core.storage.entity.DownloadSections
import com.iptv.core.storage.entity.DownloadStatus
import com.iptv.core.storage.entity.Kinds
import com.iptv.core.storage.entity.ProgrammeEntity
import com.iptv.feature.player.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Adaptador de UI sobre [PlaybackSession] (singleton de proceso en
 * `:feature:player`): pliega el estado de la sesión, del [CastController] y
 * de los DAOs en el [UiState] que consumen las pantallas táctil y de TV.
 *
 * La sesión es quien posee el player, la reconexión, el zapping y la
 * persistencia de progreso; este ViewModel mantiene sólo lo que es estado
 * de pantalla (rail, EPG de overlays, descarga del item y diálogos).
 */
@ExperimentalCoroutinesApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val programmeDao: ProgrammeDao,
    private val downloadDao: DownloadDao,
    private val downloadController: DownloadController,
    private val pipController: PipController,
    private val prefs: AppPreferences,
    private val castController: CastController,
    private val playbackSession: PlaybackSession,
    private val tvLinkClient: TvLinkClient,
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

    private val channelId: Long = savedStateHandle.get<Long>("channelId")
        ?: error("channelId es obligatorio en la ruta del reproductor")

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Player visible (local o remoto), propiedad de la sesión. */
    val player: StateFlow<Player?> = playbackSession.player

    /** Orden de zapping derivado de la sesión: habilita zap y dígitos. */
    val hasChannelList: StateFlow<Boolean> = playbackSession.channelOrder
        .map { it.size > 1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // -- Rail de canales (estado de pantalla, no de sesión) ---------------------

    private val _channelList = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val channelList: StateFlow<List<ChannelEntity>> = _channelList.asStateFlow()

    /** La lista del rail ya respondió una vez: sin ella un filtro vacío
     *  mostraría un spinner eterno en lugar de "sin canales". */
    private val _channelListLoaded = MutableStateFlow(false)
    val channelListLoaded: StateFlow<Boolean> = _channelListLoaded.asStateFlow()
    private var channelListRequested = false

    /** Filtro activo del rail de canales de TV (no afecta al orden de zap). */
    sealed interface RailFilter {
        data object All : RailFilter
        data object Favorites : RailFilter
        data class Category(val id: Long) : RailFilter
    }

    private val _railCategories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val railCategories: StateFlow<List<CategoryEntity>> = _railCategories.asStateFlow()

    private val _railFilter = MutableStateFlow<RailFilter>(RailFilter.All)
    val railFilter: StateFlow<RailFilter> = _railFilter.asStateFlow()

    /** Carga perezosa de la lista de canales: solo cuando el panel se abre. */
    fun requestChannelList() {
        if (channelListRequested) return
        // El flag sólo se sella con fuente resuelta: abrir el rail antes de
        // que el canal cargue no debe impedir una carga posterior.
        val sourceId = _uiState.value.channel?.sourceId ?: return
        channelListRequested = true
        viewModelScope.launch {
            _railCategories.value = categoryDao.visibleBySource(sourceId, Kinds.LIVE)
            reloadChannelList(sourceId)
        }
    }

    /** Cambia el filtro del rail y recarga la lista mostrada. */
    fun selectRailFilter(filter: RailFilter) {
        _railFilter.value = filter
        val sourceId = _uiState.value.channel?.sourceId ?: return
        viewModelScope.launch { reloadChannelList(sourceId) }
    }

    private suspend fun reloadChannelList(sourceId: Long) {
        _channelList.value = when (val filter = _railFilter.value) {
            RailFilter.All -> channelDao.liveBySource(sourceId)
            RailFilter.Favorites -> channelDao.liveFavoritesBySource(sourceId)
            is RailFilter.Category -> channelDao.liveByCategory(sourceId, filter.id)
        }
        _channelListLoaded.value = true
    }

    // -- EPG (shell de TV) ------------------------------------------------------

    /** Programa en curso y siguiente del canal en reproducción. */
    data class NowNext(
        val current: ProgrammeEntity? = null,
        val next: ProgrammeEntity? = null,
    )

    /**
     * Now/next del canal activo. El ticker reevalúa la ventana cada 30 s:
     * Room sólo emite al cambiar la tabla, así que sin él un programa que
     * termina quedaría "en curso" hasta la próxima sincronización de la EPG.
     */
    val nowNext: StateFlow<NowNext> =
        combine(
            _uiState.map { it.channel }.distinctUntilChanged(),
            flow {
                while (true) {
                    emit(System.currentTimeMillis())
                    delay(EPG_REFRESH_MS)
                }
            },
        ) { channel, atUtc -> channel to atUtc }
            .flatMapLatest { (channel, atUtc) ->
                if (channel == null) {
                    flowOf(NowNext())
                } else {
                    val key = channel.tvgId.orEmpty()
                    combine(
                        programmeDao.observeCurrentForChannel(
                            channel.sourceId, key, channel.providerNameNorm, atUtc,
                        ),
                        programmeDao.observeNextForChannel(
                            channel.sourceId, key, channel.providerNameNorm, atUtc,
                        ),
                    ) { current, next -> NowNext(current, next) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowNext())

    private val _guideRows = MutableStateFlow<List<GuideRow>>(emptyList())
    val guideRows: StateFlow<List<GuideRow>> = _guideRows.asStateFlow()

    /** Recarga la guía "ahora y a continuación" del overlay de TV. */
    fun refreshGuide() {
        val sourceId = _uiState.value.channel?.sourceId ?: return
        viewModelScope.launch {
            _guideRows.value = programmeDao.guideSnapshot(
                sourceId,
                System.currentTimeMillis(),
            )
        }
    }

    // -- Cast: el volumen y los permisos siguen siendo cosa de pantalla ---------

    val castVolume = castController.volume
    val castMuted = castController.muted

    val isInPipMode: StateFlow<Boolean> = pipController.isInPipMode

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

    // -- Delegación a la sesión -------------------------------------------------

    /** Cambia a un canal concreto desde la lista del reproductor. */
    fun playChannel(id: Long) = playbackSession.play(id)

    /** Zapping: canal anterior/siguiente de la fuente, retorno circular. */
    fun playAdjacent(delta: Int) = playbackSession.playAdjacent(delta)

    /** "Recall" del mando: alterna con el canal anterior, si lo hay. */
    fun zapToPrevious() = playbackSession.zapToPrevious()

    /** Seek relativo (sólo VOD). */
    fun seekBy(deltaMs: Long) = playbackSession.seekBy(deltaMs)

    /** Programa la pausa en [minutes] minutos; null la cancela. */
    fun setSleepTimer(minutes: Int?) = playbackSession.setSleepTimer(minutes)

    /** Reintenta tras un error. */
    fun retry() = playbackSession.retry()

    /** Termina la sesión Cast y limpia el aviso. */
    fun returnToMobile() = playbackSession.returnToMobile()

    // -- TV vinculada (enlace de la app, distinto de Google Cast) ------------

    /** Nombre de la TV vinculada con sesión activa; null sin enlace. */
    val linkedTvName: StateFlow<String?> = tvLinkClient.connection
        .map { if (it.state == TvLinkConnection.CONNECTED) it.deviceName else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "Transmitir" hacia la TV vinculada: usa el enlace de la app, no el
     *  protocolo Google Cast — la app de la TV reproduce el mismo canal. */
    fun sendToLinkedTv() {
        val channel = _uiState.value.channel ?: return
        tvLinkClient.remote.value?.play(
            channel.id,
            player.value?.currentPosition ?: 0,
        )
    }

    // -- Plegado del estado de la sesión y del Cast en UiState -------------------

    init {
        // Canal en reproducción → entidad completa (la EPG y las descargas
        // necesitan campos que el contrato neutro NowPlaying no lleva).
        viewModelScope.launch {
            playbackSession.nowPlaying.collect { np ->
                val entity = np?.let { channelDao.findById(it.channelId) }
                if (np != null) downloadChannelId.value = np.channelId
                _uiState.update {
                    it.copy(channel = entity, isLive = np?.isLive ?: true)
                }
            }
        }
        viewModelScope.launch {
            playbackSession.info.collect { info ->
                _uiState.update {
                    it.copy(
                        errorRes = info.error?.let(::errorResFor),
                        reconnectAttempt = info.reconnectAttempt,
                        networkLost = info.networkLost,
                        showReturnToMobile = info.showReturnToMobile,
                        sleepTimerEndAtMs = info.sleepTimerEndAtMs,
                    )
                }
            }
        }
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
        playbackSession.play(channelId)
    }

    override fun onCleared() {
        pipController.autoEnterOnUserLeave = false
        // La sesión es de proceso pero hoy el ViewModel es su único
        // consumidor: morir equivale a liberar (ciclo anterior intacto).
        playbackSession.release()
        super.onCleared()
    }

    @StringRes
    private fun errorResFor(kind: PlaybackErrorKind): Int = when (kind) {
        PlaybackErrorKind.NETWORK -> R.string.player_error_network
        PlaybackErrorKind.HTTP -> R.string.player_error_http
        PlaybackErrorKind.FORMAT -> R.string.player_error_format
        PlaybackErrorKind.CODEC -> R.string.player_error_codec
        PlaybackErrorKind.TIMEOUT -> R.string.player_error_timeout
        PlaybackErrorKind.CHANNEL_MISSING -> R.string.player_error_channel_missing
        PlaybackErrorKind.LOCKED -> R.string.player_error_locked
        PlaybackErrorKind.CAST_FORMAT -> R.string.player_error_cast_format
        PlaybackErrorKind.GENERIC -> R.string.player_error_generic
    }

    private companion object {
        const val EPG_REFRESH_MS = 30_000L
    }
}
