package com.iptv.core.storage.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    indices = [
        Index("sourceId"),
        Index("categoryId"),
        Index("nameNorm"),
        Index(value = ["sourceId", "externalId"], unique = true),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val categoryId: Long? = null,
    /** Identificador externo: URL del stream (M3U) o stream_id (Xtream). */
    val externalId: String,
    val name: String,
    /** Nombre normalizado (sin acentos, minúsculas) para búsqueda. */
    val nameNorm: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val tvgId: String? = null,
    /** Clave de categoría externa (group-title / category_id). */
    val groupTitle: String? = null,
    val kind: String = Kinds.LIVE,
    val containerExt: String? = null,
    val sortOrder: Int = 0,
    val isFavorite: Boolean = false,
)
