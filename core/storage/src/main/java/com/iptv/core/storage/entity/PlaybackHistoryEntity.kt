package com.iptv.core.storage.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Progreso de reproducción de un elemento bajo demanda (película o episodio). */
@Entity(
    tableName = "playback_history",
    indices = [Index("sourceId")],
)
data class PlaybackHistoryEntity(
    @PrimaryKey val channelId: Long,
    val sourceId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)
