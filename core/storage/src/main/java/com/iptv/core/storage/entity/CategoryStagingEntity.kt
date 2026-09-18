package com.iptv.core.storage.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Volcado temporal de categorías durante una importación; mismo patrón
 * que [ChannelStagingEntity]: merge por (sourceId, externalId) que
 * conserva id, isLocked, hidden y el sortOrder ya existente.
 */
@Entity(
    tableName = "categories_staging",
    indices = [Index(value = ["sourceId", "externalId"], unique = true)],
)
data class CategoryStagingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val externalId: String,
    val kind: String = Kinds.LIVE,
    val name: String,
    val sortOrder: Int = 0,
    val language: String = "",
)
