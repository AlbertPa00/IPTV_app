package com.iptv.core.storage.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Volcado temporal de una importación de catálogo: se rellena por lotes
 * durante el parseo y se funde en `channels` dentro de una transacción
 * (merge por (sourceId, externalId) que conserva id e isFavorite).
 * Sin esto, importar una lista grande materializaba todas las entidades
 * en memoria y cada resincronización regeneraba los ids de los canales.
 */
@Entity(
    tableName = "channels_staging",
    indices = [Index(value = ["sourceId", "externalId"], unique = true)],
)
data class ChannelStagingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val externalId: String,
    val name: String,
    val nameNorm: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val tvgId: String? = null,
    val groupTitle: String? = null,
    val kind: String = Kinds.LIVE,
    val containerExt: String? = null,
    val sortOrder: Int = 0,
    val language: String = "",
    val userAgent: String? = null,
    val referrer: String? = null,
)
