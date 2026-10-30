package com.iptv.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Índice de texto completo sobre `programmes.title`: permite buscar
 * "cuándo echan X" dentro de la guía sin escanear la tabla de programas
 * (cientos de miles de filas en EPGs grandes). Sólo se indexa el título —
 * la descripción duplicaría el coste para un valor marginal de búsqueda.
 * La tabla se mantiene sola vía triggers content_sync (creados por Room),
 * incluido el purgado de programas terminados.
 */
@Fts4(contentEntity = ProgrammeEntity::class)
@Entity(tableName = "programmes_fts")
data class ProgrammeFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int,
    @ColumnInfo(name = "title")
    val title: String,
)
