package com.iptv.core.common.playback

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

/** Lo que suena ahora: identidad suficiente para overlays y zapping sin
 *  filtrar la entidad Room al contrato (pensado también para el port de
 *  escritorio, donde el canal vendrá de otro origen de datos). */
data class NowPlaying(
    val channelId: Long,
    val sourceId: Long,
    val name: String,
    val logoUrl: String?,
    val isLive: Boolean,
    val isFavorite: Boolean,
    /** Clave estable (externalId) cuando la sesión viene de un host remoto
     *  por tv-link: el mando marca "sonando" en su catálogo espejo. */
    val channelKey: String? = null,
)

/** Clasificación de fallo de reproducción: la capa de presentación la
 *  traduce a recursos de texto de su módulo. */
enum class PlaybackErrorKind {
    NETWORK, HTTP, FORMAT, CODEC, TIMEOUT,
    CHANNEL_MISSING, LOCKED, CAST_FORMAT, GENERIC,
}

/** Estado observable de la sesión que no es el canal en sí. */
data class PlaybackInfo(
    /** Fallo actual clasificado; null = sin error. */
    val error: PlaybackErrorKind? = null,
    /** Intento de reconexión automática en curso; null = no reconectando. */
    val reconnectAttempt: Int? = null,
    /** El sistema reporta pérdida total de conectividad. */
    val networkLost: Boolean = false,
    /** El receptor Cast falló: la UI ofrece "volver al móvil". */
    val showReturnToMobile: Boolean = false,
    /** Epoch ms en que el temporizador pausará; null = inactivo. */
    val sleepTimerEndAtMs: Long? = null,
)

/**
 * Sesión de reproducción a nivel de proceso (patrón [com.iptv.core.common.cast.CastController]):
 * la implementación vive en `:feature:player` y es un `@Singleton` — el
 * vídeo y el zapping sobreviven a cualquier pantalla u overlay. Quien la
 * consume recibe flujos neutros; nada de tipos de UI ni de Room.
 *
 * Política de liberación: [release] libera el player local y la sesión de
 * medios. Hoy lo invoca el ViewModel del reproductor al morir — equivale
 * al ciclo de vida anterior (salir de la pantalla libera); una versión
 * futura puede cambiarlo a "liberar tras N minutos sin consumidores" sin
 * tocar esta clase.
 */
interface PlaybackSession {
    /** Player visible: local o remoto (Cast). Null si no hay reproducción. */
    val player: StateFlow<Player?>

    /** Canal en reproducción; null si aún no cargó ninguno. */
    val nowPlaying: StateFlow<NowPlaying?>

    /** Errores, reconexión, red y temporizador. */
    val info: StateFlow<PlaybackInfo>

    /** Orden de canales en directo de la fuente activa (zapping). */
    val channelOrder: StateFlow<List<Long>>

    /** Reproduce un canal concreto. No-op si ya es el actual. */
    fun play(channelId: Long)

    /** Zapping relativo dentro de [channelOrder], con retorno circular. */
    fun playAdjacent(delta: Int)

    /** "Recall": alterna con el canal anterior, si lo hay. */
    fun zapToPrevious()

    /** Seek relativo en contenido no en directo. */
    fun seekBy(deltaMs: Long)

    /** Seek absoluto en contenido no en directo (línea temporal arrastrable). */
    fun seekTo(positionMs: Long)

    /** Programa la pausa en [minutes] minutos; null la cancela. */
    fun setSleepTimer(minutes: Int?)

    /** Reintenta la carga tras un error (limpia el estado de fallo). */
    fun retry()

    /** Termina la sesión Cast en curso y limpia el aviso "volver al móvil". */
    fun returnToMobile()

    /** Libera player local, sesión de medios y vigías. */
    fun release()
}
