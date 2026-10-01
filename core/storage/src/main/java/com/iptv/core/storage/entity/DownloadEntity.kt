package com.iptv.core.storage.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una descarga local de una película o episodio para verla sin conexión.
 * El fichero vive en filesDir/downloads/dl_<id>.<ext> (parcial: .part).
 */
@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["channelId"], unique = true),
        Index("sourceId"),
        Index("section"),
    ],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val sourceId: Long,
    /** externalId del canal: permite relocalizar la fila si cambia su id. */
    val externalId: String,
    /** "movie" o "episode": reparte los items entre los carruseles de la app. */
    val section: String,
    val title: String,
    val imageUrl: String? = null,
    val remoteUrl: String,
    val userAgent: String? = null,
    val referrer: String? = null,
    val localPath: String? = null,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: String = DownloadStatus.QUEUED,
    val errorDetail: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

object DownloadStatus {
    const val QUEUED = "QUEUED"
    const val DOWNLOADING = "DOWNLOADING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
}

object DownloadSections {
    const val MOVIE = "movie"
    const val EPISODE = "episode"
}
