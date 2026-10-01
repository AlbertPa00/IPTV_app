package com.iptv.app

import android.app.Application
import android.os.Build
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.iptv.app.downloads.WorkManagerDownloadController
import com.iptv.core.common.prefs.AppPreferences
import com.iptv.core.common.sync.CatalogSyncScheduler
import com.iptv.core.network.DEFAULT_USER_AGENT
import com.iptv.core.storage.db.LanguageBackfill
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Inject
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@HiltAndroidApp
class IptvApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var syncScheduler: CatalogSyncScheduler
    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var crashReporter: CrashReporter
    @Inject lateinit var languageBackfill: LanguageBackfill
    @Inject lateinit var downloadController: WorkManagerDownloadController

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // WorkManager arranca perezoso con la factoría Hilt; el inicializador
    // por defecto está desactivado en el manifiesto.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /**
     * ImageLoader global de Coil:
     *  - fetcher OkHttp con el User-Agent de la app: servidores como
     *    Wikimedia responden 403 al UA genérico de Coil → logo vacío.
     *  - decodificador GIF (los paneles Xtream los usan como logos de
     *    canal; sin él la petición falla tras descargar).
     * El cliente se deriva del OkHttp compartido: mismos pools y timeouts.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            okHttpClient.newBuilder()
                                .addInterceptor { chain ->
                                    chain.proceed(
                                        chain.request().newBuilder()
                                            .header("User-Agent", DEFAULT_USER_AGENT)
                                            .build(),
                                    )
                                }
                                .build()
                        },
                    ),
                )
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()

    override fun onCreate() {
        super.onCreate()
        // Aplica y mantiene al día la programación del refresco automático
        // según los ajustes (también tras cambios del usuario).
        applicationScope.launch {
            combine(prefs.autoRefreshEnabled, prefs.autoRefreshWifiOnly, ::Pair)
                .collect { (enabled, wifiOnly) -> syncScheduler.apply(enabled, wifiOnly) }
        }
        // Opt-in del usuario; sin google-services.json es un no-op seguro.
        applicationScope.launch {
            prefs.crashReportingEnabled.collect { crashReporter.setEnabled(it) }
        }
        // Rellena `language` en catálogos importados antes de la v6 (idempotente).
        applicationScope.launch { languageBackfill.run() }
        // Reconcilia descargas: re-encola las cortadas al morir el proceso y
        // borra filas/ficheros huérfanos.
        applicationScope.launch { downloadController.reconcile() }
    }
}
