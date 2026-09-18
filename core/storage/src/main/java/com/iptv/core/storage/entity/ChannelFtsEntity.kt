package com.iptv.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Índice de texto completo sobre `channels.nameNorm`: la búsqueda del
 * catálogo usaba `LIKE '%q%'`, un escaneo completo por pulsación que no
 * puede usar ningún índice. La tabla se mantiene sola vía triggers
 * (creados en la migración y en `StorageModule` para instalaciones nuevas).
 */
@Fts4(contentEntity = ChannelEntity::class)
@Entity(tableName = "channels_fts")
data class ChannelFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int,
    @ColumnInfo(name = "nameNorm")
    val nameNorm: String,
)
