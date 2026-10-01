package com.iptv.app.downloads

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.iptv.core.common.download.DownloadController
import com.iptv.core.common.prefs.AppPreferences
import com.iptv.core.network.DEFAULT_USER_AGENT
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.DownloadDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.entity.DownloadEntity
import com.iptv.core.storage.entity.DownloadStatus
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Cola de descargas basada en WorkManager: una obra única por canal
 * ("download_<channelId>"), con Wi-Fi obligatorio si el ajuste está activo.
 * Las filas `downloads` guardan URL y cabeceras ya resueltas: reintentos y
 * relanzamientos tras reiniciar el proceso no dependen del catálogo.
 */
@Singleton
class WorkManagerDownloadController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val channelDao: ChannelDao,
    private val sourceDao: SourceDao,
    private val prefs: AppPreferences,
) : DownloadController {

    override suspend fun enqueue(channelId: Long, section: String) {
        val channel = channelDao.findById(channelId) ?: return
        val existing = downloadDao.findByChannel(channelId)
        // Ya descargado o en vuelo: no re-encolar (cancel se hace por cancel()).
        if (existing != null && existing.status != DownloadStatus.FAILED) return
        val now = System.currentTimeMillis()
        val row = DownloadEntity(
            id = existing?.id ?: 0,
            channelId = channel.id,
            sourceId = channel.sourceId,
            externalId = channel.externalId,
            section = section,
            title = channel.name,
            imageUrl = channel.logoUrl,
            remoteUrl = channel.streamUrl,
            userAgent = channel.userAgent
                ?: sourceDao.findById(channel.sourceId)?.userAgent
                ?: DEFAULT_USER_AGENT,
            referrer = channel.referrer
                ?: sourceDao.findById(channel.sourceId)?.referrer,
            localPath = if (existing?.status == DownloadStatus.DONE) existing.localPath else null,
            status = DownloadStatus.QUEUED,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        val id = downloadDao.upsert(row)
        enqueueWork(row.copy(id = if (row.id == 0L) id else row.id), ExistingWorkPolicy.REPLACE)
    }

    private fun enqueueWork(download: DownloadEntity, policy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (prefs.isDownloadsWifiOnly()) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(DownloadWorker.KEY_DOWNLOAD_ID to download.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(download.channelId), policy, request,
        )
    }

    override suspend fun cancel(channelId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(channelId))
        downloadDao.findByChannel(channelId)?.let { delete(it.id) }
    }

    override suspend fun delete(downloadId: Long) {
        val download = downloadDao.findById(downloadId) ?: return
        WorkManager.getInstance(context).cancelUniqueWork(workName(download.channelId))
        DownloadFiles(context.filesDir, download.id, download.remoteUrl).deleteAll()
        downloadDao.delete(downloadId)
    }

    override suspend fun removeForSource(sourceId: Long) {
        downloadDao.bySource(sourceId).forEach { delete(it.id) }
    }

    override suspend fun removeAll() {
        downloadDao.all().forEach { delete(it.id) }
    }

    /**
     * Arranque de la app: re-encola descargas interrumpidas (proceso muerto,
     * reinicio) y borra filas cuyo canal ya no existe.
     */
    suspend fun reconcile() {
        val downloads = downloadDao.all()
        downloads.forEach { download ->
            val channelGone = channelDao.findById(download.channelId) == null
            val fileGone = download.status == DownloadStatus.DONE &&
                download.localPath?.let { java.io.File(it).exists() } != true
            if (channelGone || fileGone) delete(download.id)
        }
        // KEEP: WorkManager ya reprograma lo que estaba vivo al morir el
        // proceso; sólo se re-encola lo que quedó colgado sin work asociado.
        downloads.filter {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
        }.forEach { enqueueWork(it, ExistingWorkPolicy.KEEP) }
    }

    private fun workName(channelId: Long) = "download_$channelId"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadsModule {
    @Binds
    @Singleton
    abstract fun downloadController(impl: WorkManagerDownloadController): DownloadController
}
