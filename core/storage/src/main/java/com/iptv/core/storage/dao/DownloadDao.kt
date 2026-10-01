package com.iptv.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iptv.core.storage.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity): Long

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE channelId = :channelId")
    fun observeByChannel(channelId: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE channelId = :channelId")
    suspend fun findByChannel(channelId: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE channelId = :channelId AND status = 'DONE'")
    suspend fun findCompleted(channelId: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE sourceId = :sourceId AND externalId = :externalId")
    suspend fun findByExternalId(sourceId: Long, externalId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun findById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE sourceId = :sourceId")
    suspend fun bySource(sourceId: Long): List<DownloadEntity>

    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadEntity>

    @Query(
        "UPDATE downloads SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, " +
            "status = :status, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateProgress(
        id: Long,
        downloadedBytes: Long,
        totalBytes: Long,
        status: String,
        updatedAt: Long,
    )

    @Query(
        "UPDATE downloads SET status = :status, localPath = :localPath, errorDetail = :error, " +
            "downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, updatedAt = :updatedAt " +
            "WHERE id = :id",
    )
    suspend fun updateDone(
        id: Long,
        status: String,
        localPath: String?,
        error: String?,
        downloadedBytes: Long,
        totalBytes: Long,
        updatedAt: Long,
    )

    @Query("UPDATE downloads SET status = :status, errorDetail = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String?, updatedAt: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM downloads WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    /** Ids de canales con descarga registrada: el merge los preserva al resincronizar. */
    @Query("SELECT channelId FROM downloads")
    suspend fun downloadedChannelIds(): List<Long>
}
