package com.iptv.feature.player.cast

import android.content.Context
import android.view.KeyEvent
import com.google.android.gms.cast.framework.CastContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Desvía las teclas físicas de volumen a la sesión Cast activa: mientras se
 * emite, subir/bajar volumen en el teléfono controla el volumen de la TV.
 */
@Singleton
class CastVolumeKeyHandler @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    /** Devuelve true si el evento se aplicó a la sesión Cast activa. */
    fun handle(event: KeyEvent): Boolean {
        val session = runCatching {
            CastContext.getSharedInstance(appContext).sessionManager.currentCastSession
        }.getOrNull() ?: return false
        if (!session.isConnected) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val step = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        VOLUME_STEP
                    } else {
                        -VOLUME_STEP
                    }
                    runCatching {
                        session.setVolume((session.volume + step).coerceIn(0.0, 1.0))
                    }
                }
                true
            }

            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    runCatching { session.setMute(!session.isMute) }
                }
                true
            }

            else -> false
        }
    }

    private companion object {
        const val VOLUME_STEP = 0.05
    }
}
