package com.iptv.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.iptv.core.common.sync.CatalogSyncScheduler
import com.iptv.core.storage.dao.SourceDao
import com.iptv.feature.epg.data.EpgRepository
import com.iptv.feature.epg.data.EpgSyncState
import com.iptv.feature.source.data.SourceRepository
import com.iptv.feature.source.domain.SourceSyncPhase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Refresco diario en segundo plano: vuelve a descargar el catálogo de la
 * fuente activa y después la guía EPG. Restricciones según ajustes
 * (por defecto sólo con Wi-Fi y red disponible).
 */
@HiltWorker
class CatalogSyncWorker @dagger.assisted.AssistedInject constructor(
    @dagger.assisted.Assisted context: Context,
    @dagger.assisted.Assisted params: WorkerParameters,
    private val sourceDao: SourceDao,
    private val sourceRepository: SourceRepository,
    private val epgRepository: EpgRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val source = sourceDao.observeActive().first() ?: return Result.success()
        var failed = false
        sourceRepository.refresh(source).collect { phase ->
            if (phase is SourceSyncPhase.Failed) failed = true
        }
        epgRepository.syncActive().collect { state ->
            if (state is EpgSyncState.Failed && state.reason != EpgSyncState.Reason.NO_URL) {
                failed = true
            }
        }
        return if (failed) Result.retry() else Result.success()
    }
}

@Singleton
class WorkManagerCatalogSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : CatalogSyncScheduler {

    override fun apply(enabled: Boolean, wifiOnly: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .build()
        val request = PeriodicWorkRequestBuilder<CatalogSyncWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 2,
            flexTimeIntervalUnit = TimeUnit.HOURS,
        ).setConstraints(constraints).build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private companion object {
        const val WORK_NAME = "catalog_sync"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    @Singleton
    abstract fun catalogSyncScheduler(
        impl: WorkManagerCatalogSyncScheduler,
    ): CatalogSyncScheduler
}
