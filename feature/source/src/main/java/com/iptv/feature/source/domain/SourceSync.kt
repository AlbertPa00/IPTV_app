package com.iptv.feature.source.domain

/**
 * Errores de alta/sincronización de fuentes, mapeables a mensajes para el usuario.
 */
sealed interface SourceError {
    data object InvalidUrl : SourceError
    data object Network : SourceError
    data object InvalidCredentials : SourceError
    data class AccountInactive(val status: String) : SourceError
    data object EmptyPlaylist : SourceError
    data object FileAccess : SourceError
    data object NotFound : SourceError
    data object Unknown : SourceError
}

/** Pasos de una sincronización, para mostrar progreso en la UI. */
enum class SyncStep { CONNECTING, AUTHENTICATING, FETCHING, IMPORTING }

/** Fases emitidas durante la alta o actualización de una fuente. */
sealed interface SourceSyncPhase {
    data class Progress(val step: SyncStep, val count: Int) : SourceSyncPhase

    /**
     * [missingSections]: secciones que el servidor no devolvió tras reintentar
     * ("vod", "series"). Vacío = sincronización completa.
     */
    data class Done(val sourceId: Long, val missingSections: Set<String> = emptySet()) : SourceSyncPhase
    data class Failed(val error: SourceError) : SourceSyncPhase
}
