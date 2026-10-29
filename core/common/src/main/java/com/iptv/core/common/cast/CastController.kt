package com.iptv.core.common.cast

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Datos del stream que el receptor debe cargar (URL remota + cabeceras). */
data class CastStreamSpec(
    val remoteUrl: String,
    val userAgent: String,
    val referrer: String?,
    val channelTitle: String?,
)

data class CastUiState(
    val available: Boolean = false,
    val connecting: Boolean = false,
    val casting: Boolean = false,
    /** Carga en vuelo hacia el receptor (setMediaItem/prepare no confirmado). */
    val transferPending: Boolean = false,
    val deviceName: String? = null,
    val receiverError: String? = null,
)

sealed interface CastEvent {
    /** El remoto reproduce (transferencia nueva o sesión adoptada). */
    data object RemoteActive : CastEvent

    /** La reproducción debe volver al player local. */
    data class LocalResume(
        /** True si había remoto activo cuya posición conviene transferir. */
        val transferFromRemote: Boolean,
        /** Null = conservar el playWhenReady actual del local. */
        val playWhenReady: Boolean?,
        /** True = fallo del receptor: avisar y ofrecer "volver al móvil". */
        val showError: Boolean,
    ) : CastEvent
}

/**
 * Contrato de la orquestación Cast (patrón [com.iptv.core.common.downloads.DownloadController]):
 * la implementación vive en `:feature:cast`, quien la consume (player) no
 * conoce el Cast SDK. Instancia por consumidor, no singleton: refleja el
 * ciclo de vida de la pantalla que la usa (attach/detach), mientras la
 * sesión Cast y el proxy siguen siendo de proceso.
 */
interface CastController {
    val uiState: StateFlow<CastUiState>
    val events: Flow<CastEvent>
    val volume: StateFlow<Float>
    val muted: StateFlow<Boolean>

    /** Player remoto activo, o null si el Cast no está disponible. */
    val remotePlayer: Player?
    val isSessionAvailable: Boolean

    /** Engancha listeners del framework y publica el player remoto. */
    fun attach(local: Player)

    /** Desengancha listeners; la sesión Cast sobrevive a la pantalla. */
    fun detach()

    /** Token de sesión media para la notificación del proxy (framework). */
    fun setMediaSessionToken(token: android.media.session.MediaSession.Token?)

    /** Callback de zapping desde la notificación del proxy (delta ±1). */
    fun setZapHandler(handler: ((Int) -> Unit)?)

    /** Transfiere la reproducción local al receptor (o adopta la sesión). */
    suspend fun transferToCast(
        item: MediaItem,
        positionMs: Long,
        playWhenReady: Boolean,
        spec: CastStreamSpec,
    )

    /** Recarga el item en el receptor (zapping con sesión activa). */
    suspend fun loadOnRemote(item: MediaItem, spec: CastStreamSpec)

    /** Termina la sesión y devuelve la reproducción al local. */
    fun endSession()

    fun clearReceiverError()
    fun refreshCastVolume()
    fun setVolume(volume: Float)
    fun setMuted(muted: Boolean)
}
