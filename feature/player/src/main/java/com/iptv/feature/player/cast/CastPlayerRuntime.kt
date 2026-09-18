package com.iptv.feature.player.cast

import android.content.Context
import android.util.Log
import androidx.media3.cast.RemoteCastPlayer
import com.google.android.gms.cast.framework.CastContext

/**
 * Process-scoped owner of the [RemoteCastPlayer] and its [CastContext].
 *
 * [RemoteCastPlayer.release] ends the whole Cast session
 * (SessionManager.endCurrentSession), so the player can't live in the
 * player-screen ViewModel: leaving the screen would kill TV playback.
 * Holding it here lets the user keep browsing while casting — the session
 * only ends on an explicit disconnect (route button, "Volver al móvil",
 * notification stop) or when the receiver drops it. [CastProxyService]
 * notices a dead session and tears the proxy down on its own.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object CastPlayerRuntime {

    private var castContext: CastContext? = null
    private var castPlayer: RemoteCastPlayer? = null
    private var initFailed = false

    /**
     * URI original (antes del proxy) del último item enviado al receptor.
     * Permite distinguir "vuelvo al mismo canal" (se adopta la sesión sin
     * recargar la TV) de "cambié de canal" (se carga el nuevo).
     */
    @Volatile
    var loadedStreamUri: String? = null
        private set

    fun markLoaded(streamUri: String) {
        loadedStreamUri = streamUri
    }

    fun clearLoaded() {
        loadedStreamUri = null
    }

    /** Shared CastContext; null hasta que [castPlayer] se crea o si Cast no está. */
    fun castContext(): CastContext? = castContext

    /**
     * Shared RemoteCastPlayer, created on first use. Never released:
     * release() would end the active Cast session. Must be called on the
     * main thread.
     */
    @Synchronized
    fun castPlayer(context: Context): RemoteCastPlayer? {
        castPlayer?.let { return it }
        if (initFailed) return null
        return try {
            // Media3 1.9 split the old remote-only CastPlayer into a dual
            // CastPlayer (local+remote delegation) and RemoteCastPlayer.
            // The app manages its own ExoPlayer, so the remote-only one is
            // the right fit — same semantics as the deprecated constructor.
            val ctx = CastContext.getSharedInstance(context.applicationContext)
            val created = RemoteCastPlayer.Builder(context.applicationContext)
                .setMediaItemConverter(LiveAwareMediaItemConverter())
                .build()
            castContext = ctx
            castPlayer = created
            created
        } catch (e: Exception) {
            initFailed = true
            Log.w(TAG, "Cast framework unavailable", e)
            null
        }
    }

    private const val TAG = "CastPlayerRuntime"
}
