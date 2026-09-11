package com.iptv.app

import android.app.Application
import androidx.work.Configuration
import com.iptv.core.common.prefs.AppPreferences
import com.iptv.core.common.sync.CatalogSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@HiltAndroidApp
class IptvApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: CatalogSyncScheduler
    @Inject lateinit var prefs: AppPreferences

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // WorkManager arranca perezoso con la factoría Hilt; el inicializador
    // por defecto está desactivado en el manifiesto.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Aplica y mantiene al día la programación del refresco automático
        // según los ajustes (también tras cambios del usuario).
        applicationScope.launch {
            combine(prefs.autoRefreshEnabled, prefs.autoRefreshWifiOnly, ::Pair)
                .collect { (enabled, wifiOnly) -> syncScheduler.apply(enabled, wifiOnly) }
        }
    }
}
