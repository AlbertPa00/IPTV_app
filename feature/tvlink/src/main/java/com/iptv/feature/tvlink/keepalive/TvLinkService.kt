package com.iptv.feature.tvlink.keepalive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.iptv.feature.tvlink.R

/**
 * Mantiene vivo el proceso mientras el servidor de enlace escucha, para que
 * la TV siga siendo controlable con la app en segundo plano (el usuario sale
 * al launcher o apaga la pantalla del móvil). Tipo `specialUse`: `dataSync`
 * tiene un presupuesto de ~6 h/día en Android 15 que cortaría una sesión de
 * tarde. Sin wake locks: si el dispositivo entra en Doze el enlace muere —
 * preferible a impedir el sueño de una TV.
 */
class TvLinkService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        return START_STICKY
    }

    private fun buildNotification(): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.tvlink_service_title))
            .setContentText(getString(R.string.tvlink_service_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.tvlink_service_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "tvlink_keepalive"
        const val NOTIFICATION_ID = 4301

        fun start(context: Context) {
            // Desde segundo plano esto puede lanzar: el enlace sigue vivo sin
            // keep-alive igualmente, así que se tolera el fallo.
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, TvLinkService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TvLinkService::class.java))
        }
    }
}
