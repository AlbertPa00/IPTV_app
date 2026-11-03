package com.iptv.core.common.tvlink

import kotlinx.coroutines.flow.Flow

/**
 * Progreso neutro de una operación de fuente iniciada por el enlace:
 * el último evento es `done` o `failed` (excluyentes).
 */
data class RemoteSourceSync(
    val step: String? = null,
    val count: Int = 0,
    val done: Boolean = false,
    val failed: String? = null,
)

/**
 * Operaciones de fuentes expuestas al enlace móvil ↔ TV. Contrato neutro
 * en `:core:common` (mismo patrón que `CastController`/`PlaybackSession`):
 * la implementación vive en `:feature:source`, dueña de la lógica de
 * importación, y `:feature:tvlink` la consume sin depender de otro slice.
 *
 * Las referencias son ids de Room SÓLO dentro del proceso — por el cable
 * viaja una clave opaca que el servidor resuelve vía `SourceDao`.
 */
interface RemoteSourceController {

    /** Alta de lista M3U por URL en este dispositivo. */
    fun addM3u(name: String?, url: String): Flow<RemoteSourceSync>

    /** Alta de cuenta Xtream Codes en este dispositivo. */
    fun addXtream(
        name: String?,
        server: String,
        username: String,
        password: String,
    ): Flow<RemoteSourceSync>

    /** Resincroniza una fuente existente (su tipo lo decide el adaptador). */
    fun refresh(sourceId: Long): Flow<RemoteSourceSync>
}
