package com.iptv.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Una fuente de contenido: lista M3U (por URL o archivo) o cuenta Xtream Codes.
 * Las contraseñas se guardan cifradas en [passwordEnc] (nunca en claro).
 */
@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val name: String,
    val url: String? = null,
    val host: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val passwordEnc: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val epgUrl: String? = null,
    val isActive: Boolean = false,
    val lastSyncAt: Long? = null,
)

object SourceTypes {
    const val M3U_URL = "M3U_URL"
    const val M3U_FILE = "M3U_FILE"
    const val XTREAM = "XTREAM"
}
