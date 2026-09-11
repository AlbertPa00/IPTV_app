package com.iptv.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.iptv.core.storage.entity.SourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {

    @Insert
    suspend fun insert(source: SourceEntity): Long

    @Update
    suspend fun update(source: SourceEntity)

    @Query("SELECT * FROM sources ORDER BY isActive DESC, name")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<SourceEntity?>

    @Query("SELECT COUNT(*) FROM sources")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sources")
    suspend fun count(): Int

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun findById(id: Long): SourceEntity?

    @Query("SELECT * FROM sources WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): SourceEntity?

    @Query("SELECT * FROM sources WHERE type = :type AND host = :host AND username = :username LIMIT 1")
    suspend fun findAccount(type: String, host: String, username: String): SourceEntity?

    @Query("UPDATE sources SET isActive = (id = :id)")
    suspend fun setActive(id: Long)

    @Query("UPDATE sources SET lastSyncAt = :timestamp WHERE id = :id")
    suspend fun markSynced(id: Long, timestamp: Long)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteById(id: Long)
}
