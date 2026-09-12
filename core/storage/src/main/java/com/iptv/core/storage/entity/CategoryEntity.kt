package com.iptv.core.storage.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [
        Index("sourceId"),
        Index(value = ["sourceId", "externalId"], unique = true),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    /** Identificador externo: group-title (M3U) o category_id (Xtream). */
    val externalId: String,
    val kind: String = Kinds.LIVE,
    val name: String,
    val sortOrder: Int = 0,
    /** Categoría bloqueada por control parental: se oculta en toda la app. */
    val isLocked: Boolean = false,
    /** Categoría oculta por el usuario (ordenación/visibilidad del catálogo). */
    val hidden: Boolean = false,
)

object Kinds {
    const val LIVE = "LIVE"
    const val VOD = "VOD"
    const val SERIES = "SERIES"
}
