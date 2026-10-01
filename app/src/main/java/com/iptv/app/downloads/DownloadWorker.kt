package com.iptv.app.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.StatFs
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.iptv.app.R
import com.iptv.core.common.dispatchers.AppDispatchers
import com.iptv.core.storage.dao.DownloadDao
import com.iptv.core.storage.entity.DownloadEntity
import com.iptv.core.storage.entity.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Descarga un item VOD/episodio a `filesDir/downloads` para verlo sin
 * conexión. Va en primer plano (notificación con progreso) porque las
 * películas tardan minutos; reanuda sobre el `.part` con Range cuando el
 * servidor lo permite.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val client: OkHttpClient,
    private val dispatchers: AppDispatchers,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        val download = downloadDao.findById(downloadId) ?: return Result.success()
        if (download.status == DownloadStatus.DONE) return Result.success()

        return try {
            run(download)
        } catch (error: IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) {
                downloadDao.updateStatus(
                    download.id, DownloadStatus.QUEUED, null, System.currentTimeMillis(),
                )
                Result.retry()
            } else {
                markFailed(download, "Sin conexión estable")
                Result.failure()
            }
        }
    }

    private suspend fun run(download: DownloadEntity): Result = withContext(dispatchers.io) {
        ensureChannel()
        val files = DownloadFiles(context.filesDir, download.id, download.remoteUrl)
        val resumeAt = files.part.takeIf { it.exists() }?.length() ?: 0L

        val request = Request.Builder().url(download.remoteUrl)
            .header("User-Agent", download.userAgent ?: DEFAULT_UA)
            .apply {
                download.referrer?.let { header("Referer", it) }
                if (resumeAt > 0) header("Range", "bytes=$resumeAt-")
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // 416 = el .part ya cubre el archivo completo; se asume bueno.
                if (response.code == HTTP_RANGE_NOT_SATISFIABLE && resumeAt > 0) {
                    files.final.delete()
                    if (!files.part.renameTo(files.final)) {
                        markFailed(download, "No se pudo guardar el archivo")
                        return@withContext Result.failure()
                    }
                    downloadDao.updateDone(
                        download.id, DownloadStatus.DONE, files.final.absolutePath,
                        null, resumeAt, resumeAt, System.currentTimeMillis(),
                    )
                    notificationManager().cancel(notificationId(download.id))
                    return@withContext Result.success()
                }
                markFailed(download, "HTTP ${response.code}")
                return@withContext Result.failure()
            }
            val body = response.body ?: run {
                markFailed(download, "Respuesta vacía")
                return@withContext Result.failure()
            }
            val resumed = response.code == HTTP_PARTIAL && resumeAt > 0
            val chunk = body.contentLength().takeIf { it > 0 } ?: 0L
            val total = if (resumed) resumeAt + chunk else chunk

            if (total > 0 && StatFs(context.filesDir.path).availableBytes < total - resumeAt) {
                markFailed(download, "Sin espacio en el dispositivo")
                return@withContext Result.failure()
            }

            val startAt = if (resumed) resumeAt else 0L
            setForeground(progressNotification(download, startAt, total))
            if (!resumed) files.part.delete()

            FileOutputStream(files.part, resumed).use { out ->
                val buffer = ByteArray(BUFFER_BYTES)
                val input = body.byteStream()
                var written = startAt
                var lastTick = 0L
                while (true) {
                    if (!currentCoroutineContext().isActive) {
                        downloadDao.updateStatus(
                            download.id, DownloadStatus.QUEUED, null, System.currentTimeMillis(),
                        )
                        return@withContext Result.retry()
                    }
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    written += read
                    val now = System.currentTimeMillis()
                    if (now - lastTick >= PROGRESS_TICK_MS) {
                        lastTick = now
                        downloadDao.updateProgress(
                            download.id, written, total,
                            DownloadStatus.DOWNLOADING, now,
                        )
                        setForegroundAsync(progressNotification(download, written, total))
                    }
                }
                out.flush()
                if (total > 0 && written < total) throw IOException("Descarga truncada")
                files.final.delete()
                if (!files.part.renameTo(files.final)) throw IOException("No se pudo guardar el archivo")
                downloadDao.updateDone(
                    download.id, DownloadStatus.DONE, files.final.absolutePath,
                    null, written, total, System.currentTimeMillis(),
                )
                notificationManager().cancel(notificationId(download.id))
                Result.success()
            }
        }
    }

    private suspend fun markFailed(download: DownloadEntity, error: String) {
        downloadDao.updateStatus(
            download.id, DownloadStatus.FAILED, error, System.currentTimeMillis(),
        )
        notificationManager().cancel(notificationId(download.id))
    }

    private fun progressNotification(
        download: DownloadEntity,
        downloaded: Long,
        total: Long,
    ): ForegroundInfo {
        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.download_notification_title, download.title))
            .setContentText(
                if (percent >= 0) "$percent%"
                else context.getString(R.string.download_notification_progress_unknown),
            )
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .setOngoing(true)
            .setContentIntent(intent)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId(download.id), notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId(download.id), notification)
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.download_channel_desc) }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val KEY_DOWNLOAD_ID = "downloadId"
        const val CHANNEL_ID = "downloads"
        fun notificationId(downloadId: Long): Int = (NOTIFICATION_BASE + downloadId).toInt()

        private const val NOTIFICATION_BASE = 40_000L
        private const val BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_TICK_MS = 600L
        private const val MAX_ATTEMPTS = 3
        private const val HTTP_PARTIAL = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val DEFAULT_UA = "IPTV-App"
    }
}

/** Rutas de fichero de una descarga: `.part` mientras baja, final al completar. */
internal class DownloadFiles(filesDir: File, downloadId: Long, remoteUrl: String) {
    private val directory = File(filesDir, "downloads").apply { mkdirs() }
    val part = File(directory, "dl_$downloadId.part")
    val final = File(directory, "dl_$downloadId.${extensionOf(remoteUrl)}")

    fun deleteAll() {
        part.delete()
        final.delete()
    }

    companion object {
        fun extensionOf(url: String): String =
            url.toHttpUrlOrNull()?.pathSegments?.lastOrNull()
                ?.substringAfterLast('.', "")
                ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,6}")) }
                ?: "mp4"
    }
}
