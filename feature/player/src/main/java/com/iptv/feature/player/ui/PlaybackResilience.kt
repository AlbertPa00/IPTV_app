package com.iptv.feature.player.ui

import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.DefaultLoadControl
import com.iptv.core.common.prefs.AppPreferences

/**
 * Decisiones de resiliencia ante una señal inestable: clasificación de
 * errores, backoff de reconexión y perfiles de búfer. Funciones puras para
 * poder probarlas en unit tests (JUnit sin Robolectric).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PlaybackResilience {

    /** Parámetros de búfer que se pasan a [DefaultLoadControl.Builder]. */
    data class BufferParams(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val bufferForPlaybackMs: Int,
        val bufferForPlaybackAfterRebufferMs: Int,
        val liveTargetOffsetMs: Long,
    )

    // Los valores persistidos los define AppPreferences (los usa también la
    // pantalla de Ajustes, que no puede depender de este feature).
    const val PROFILE_AUTO = AppPreferences.BUFFER_AUTO
    const val PROFILE_LOW = AppPreferences.BUFFER_LOW
    const val PROFILE_HIGH = AppPreferences.BUFFER_HIGH

    const val MAX_RECONNECT_ATTEMPTS = 5

    /**
     * Auto = defaults de streaming de ExoPlayer (1.9: 50/50 s de búfer,
     * arranque 1 s, rebuffer 2 s). Low = arranque y zapping rápidos en redes
     * buenas. High = más colchón para señal inestable: se espera a acumular
     * más búfer tras cada corte (evita el bucle congela-reproduce-congela) y
     * se sube el objetivo de offset en directo.
     */
    fun bufferParams(profile: String): BufferParams = when (profile) {
        PROFILE_LOW -> BufferParams(
            minBufferMs = 10_000,
            maxBufferMs = 20_000,
            bufferForPlaybackMs = 800,
            bufferForPlaybackAfterRebufferMs = 1_500,
            liveTargetOffsetMs = 2_000,
        )
        PROFILE_HIGH -> BufferParams(
            minBufferMs = 50_000,
            maxBufferMs = 90_000,
            bufferForPlaybackMs = 4_000,
            bufferForPlaybackAfterRebufferMs = 10_000,
            liveTargetOffsetMs = 6_000,
        )
        else -> BufferParams(
            minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            maxBufferMs = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            bufferForPlaybackMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            bufferForPlaybackAfterRebufferMs =
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            liveTargetOffsetMs = 3_000,
        )
    }

    /**
     * Errores que merecen reintento automático: cortes de red, timeouts y
     * respuestas HTTP de servidor (5xx, 408, 429). Los 4xx restantes, los
     * fallos de formato/códec y el resto son fatales: reintentarlos no
     * cambia nada y castigar al servidor Xtream sí.
     */
    fun isRetriableError(errorCode: Int, httpStatus: Int?): Boolean = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_TIMEOUT,
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
        -> true

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            httpStatus == null || httpStatus >= 500 || httpStatus == 429 || httpStatus == 408

        else -> false
    }

    /** Backoff exponencial por intento de reconexión: 1 s, 2 s, 4 s… tope 10 s. */
    fun reconnectDelayMs(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 6)
        return (1_000L shl shift).coerceAtMost(10_000L)
    }
}
