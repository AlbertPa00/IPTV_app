package com.iptv.feature.tvlink.wire

import androidx.media3.common.Player
import com.iptv.core.common.playback.PlaybackSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Handoff: tras play() el player carga en segundo plano; hay que esperar a
 * STATE_READY para que el seek surta efecto. Sondeo corto en vez de otro
 * Player.Listener (los vigías de la sesión ya bastan). Falla en silencio:
 * si el stream no llega a READY el canal se queda desde su inicio/historial.
 * OJO: ExoPlayer sólo admite acceso desde el hilo principal.
 */
internal suspend fun seekWhenReady(
    session: PlaybackSession,
    targetMs: Long,
    tries: Int = 40,
) {
    repeat(tries) {
        val position = withContext(Dispatchers.Main) {
            session.player.value?.takeIf {
                it.playbackState == Player.STATE_READY
            }?.currentPosition
        }
        if (position != null) {
            val delta = targetMs - position
            if (kotlin.math.abs(delta) > 2_000) {
                withContext(Dispatchers.Main) { session.seekBy(delta) }
            }
            return
        }
        delay(250)
    }
}
