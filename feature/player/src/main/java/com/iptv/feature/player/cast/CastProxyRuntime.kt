package com.iptv.feature.player.cast

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import java.net.InetAddress

/**
 * App-scoped owner of the [CastProxyServer]. The proxy must outlive the
 * player screen — a Cast session continues after the user navigates away —
 * so it lives here instead of the ViewModel, backed by [CastProxyService]
 * to keep the process foreground and the Wi-Fi radio awake.
 */
object CastProxyRuntime {

    private var server: CastProxyServer? = null

    /** Extras the foreground service uses for its media notification. */
    @Volatile var channelTitle: String? = null
    @Volatile var mediaSession: androidx.media3.session.MediaSession? = null
    @Volatile var onZap: ((Int) -> Unit)? = null

    fun clearPlaybackExtras() {
        channelTitle = null
        mediaSession = null
        onZap = null
    }

    @Synchronized
    fun ensureStarted(
        context: Context,
        userAgent: String,
        referrer: String?,
        remoteAddress: InetAddress?,
    ): CastProxyServer? {
        server?.takeIf { it.handles(userAgent, referrer) }?.let { return it }
        // Credentials changed (different source) or first start: rebuild.
        stopServer()
        val host = CastProxyServer.selectLocalAddress(remoteAddress) ?: return null
        val created = CastProxyServer(
            userAgent, referrer, host,
            java.io.File(context.cacheDir, "casthls"),
        )
        return try {
            created.start()
            server = created
            ContextCompat.startForegroundService(
                context,
                Intent(context, CastProxyService::class.java),
            )
            created
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start cast proxy", e)
            runCatching { created.stop() }
            null
        }
    }

    /** Stops the HTTP proxy. Called by [CastProxyService] when the session ends. */
    @Synchronized
    internal fun stopServer() {
        server?.stop()
        server = null
    }

    @Synchronized
    fun stop(context: Context) {
        stopServer()
        runCatching {
            context.stopService(Intent(context, CastProxyService::class.java))
        }
    }

    private const val TAG = "CastProxyRuntime"
}
