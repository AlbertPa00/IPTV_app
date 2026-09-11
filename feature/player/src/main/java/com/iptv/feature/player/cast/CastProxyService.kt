package com.iptv.feature.player.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.cast.MediaError
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.iptv.feature.player.R

/**
 * Foreground service that keeps the phone's Wi-Fi radio and CPU awake while
 * a Cast session is active, so the local stream proxy isn't suspended by
 * Doze when the screen turns off. Its notification doubles as the cast
 * remote control (play/pause, channel zap, stop) so the phone stays usable
 * while the TV plays. It stops itself when the last Cast session ends —
 * even if the player screen is already gone — which also tears the proxy
 * down via [CastProxyRuntime].
 */
class CastProxyService : Service() {

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var castContext: CastContext? = null
    private var remoteMediaClient: RemoteMediaClient? = null

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = attachMediaClient()
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) = stopIfSessionGone()
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionEnded(session: CastSession, error: Int) = stopIfSessionGone()
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = attachMediaClient()
        override fun onSessionResumeFailed(session: CastSession, error: Int) = stopIfSessionGone()
        override fun onSessionSuspended(session: CastSession, reason: Int) = stopIfSessionGone()
    }

    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = updateNotification()
        override fun onMediaError(mediaError: MediaError) {}
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        castContext = runCatching { CastContext.getSharedInstance(this) }.getOrNull()
        if (castContext == null) {
            // No Cast framework available — nothing can be casting; don't
            // hold locks or linger as a foreground service.
            CastProxyRuntime.stopServer()
            stopSelf()
            return
        }
        castContext?.sessionManager
            ?.addSessionManagerListener(sessionListener, CastSession::class.java)
        attachMediaClient()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> togglePlayback()
            ACTION_STOP -> runCatching {
                castContext?.sessionManager?.endCurrentSession(true)
            }
            ACTION_ZAP_PREV -> CastProxyRuntime.onZap?.invoke(-1)
            ACTION_ZAP_NEXT -> CastProxyRuntime.onZap?.invoke(1)
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        acquireLocks()
        return START_STICKY
    }

    private fun attachMediaClient() {
        val client = runCatching {
            castContext?.sessionManager?.currentCastSession?.remoteMediaClient
        }.getOrNull() ?: return
        if (remoteMediaClient === client) return
        remoteMediaClient?.unregisterCallback(mediaCallback)
        remoteMediaClient = client
        client.registerCallback(mediaCallback)
        updateNotification()
    }

    private fun togglePlayback() {
        val client = remoteMediaClient ?: return
        if (client.mediaStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING) {
            client.pause()
        } else {
            client.play()
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val isPlaying =
            remoteMediaClient?.mediaStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING

        val openApp = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun actionIntent(action: String) = PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, CastProxyService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(CastProxyRuntime.channelTitle
                ?: getString(R.string.cast_service_notification_title))
            .setContentText(getString(R.string.cast_service_notification_title))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val canZap = CastProxyRuntime.onZap != null
        if (canZap) {
            builder.addAction(
                android.R.drawable.ic_media_previous,
                getString(R.string.player_previous_channel),
                actionIntent(ACTION_ZAP_PREV),
            )
        }
        builder.addAction(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            getString(if (isPlaying) R.string.player_pause else R.string.player_play),
            actionIntent(ACTION_TOGGLE),
        )
        if (canZap) {
            builder.addAction(
                android.R.drawable.ic_media_next,
                getString(R.string.player_next_channel),
                actionIntent(ACTION_ZAP_NEXT),
            )
        }
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.cast_service_stop),
            actionIntent(ACTION_STOP),
        )

        // Attach the MediaSession token when available — gives the
        // notification proper media styling on the lock screen.
        runCatching {
            CastProxyRuntime.mediaSession?.sessionCompatToken?.let { token ->
                builder.setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(token)
                        .setShowActionsInCompactView(0, 1, 2),
                )
            }
        }
        return builder.build()
    }

    private fun stopIfSessionGone() {
        if (castContext?.sessionManager?.currentCastSession == null) {
            CastProxyRuntime.stopServer()
            CastProxyRuntime.clearPlaybackExtras()
            stopSelf()
        }
    }

    private fun acquireLocks() {
        if (wifiLock == null) {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifi.createWifiLock(mode, "iptv:cast").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wakeLock == null) {
            val power = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "iptv:castproxy").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.cast_service_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    override fun onDestroy() {
        remoteMediaClient?.unregisterCallback(mediaCallback)
        remoteMediaClient = null
        castContext?.sessionManager
            ?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        releaseLocks()
        CastProxyRuntime.stopServer()
        CastProxyRuntime.clearPlaybackExtras()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL_ID = "cast_proxy"
        const val NOTIFICATION_ID = 4201
        const val ACTION_TOGGLE = "com.iptv.cast.TOGGLE"
        const val ACTION_STOP = "com.iptv.cast.STOP"
        const val ACTION_ZAP_PREV = "com.iptv.cast.ZAP_PREV"
        const val ACTION_ZAP_NEXT = "com.iptv.cast.ZAP_NEXT"
    }
}
