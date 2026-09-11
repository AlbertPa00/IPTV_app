package com.iptv.core.storage.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "programmes",
    indices = [
        Index("sourceId"),
        Index(value = ["sourceId", "channelKey"]),
        Index(value = ["sourceId", "channelNameNorm"]),
        Index(value = ["sourceId", "startUtc", "endUtc"]),
    ],
)
data class ProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    /** XMLTV channel id, normally matching the playlist tvg-id. */
    val channelKey: String,
    val channelNameNorm: String,
    val title: String,
    val description: String? = null,
    val startUtc: Long,
    val endUtc: Long,
    val iconUrl: String? = null,
)
