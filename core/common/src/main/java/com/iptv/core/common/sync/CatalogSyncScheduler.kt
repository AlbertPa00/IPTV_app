package com.iptv.core.common.sync

/**
 * Programación del refresco automático del catálogo + EPG. La interfaz vive
 * en :core:common para que cualquier slice pueda activarla; la
 * implementación (WorkManager) la provee :app.
 */
interface CatalogSyncScheduler {
    /** Crea, actualiza o cancela el trabajo periódico según los ajustes. */
    fun apply(enabled: Boolean, wifiOnly: Boolean)
}
