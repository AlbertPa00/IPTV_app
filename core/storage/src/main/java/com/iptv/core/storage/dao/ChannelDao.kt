package com.iptv.core.storage.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iptv.core.storage.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(channel: ChannelEntity): Long

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND externalId = :externalId LIMIT 1")
    suspend fun findByExternalId(sourceId: Long, externalId: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun findById(id: Long): ChannelEntity?

    // Las filas "episode:*" son sintéticas (se crean al reproducir un
    // episodio para poder reanudarlo); nunca deben aparecer en el catálogo.
    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND externalId NOT LIKE 'episode:%' ORDER BY sortOrder, name")
    fun pagingBySource(sourceId: Long, kind: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND categoryId = :categoryId AND externalId NOT LIKE 'episode:%' ORDER BY sortOrder, name")
    fun pagingByCategory(sourceId: Long, categoryId: Long): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND externalId NOT LIKE 'episode:%' AND nameNorm LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY sortOrder, name")
    fun pagingBySearch(sourceId: Long, kind: String, query: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND isFavorite = 1 AND externalId NOT LIKE 'episode:%' ORDER BY sortOrder, name")
    fun pagingFavorites(sourceId: Long, kind: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND categoryId IS NULL AND externalId NOT LIKE 'episode:%' ORDER BY sortOrder, name")
    fun pagingUncategorized(sourceId: Long, kind: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND isFavorite = 1 AND externalId NOT LIKE 'episode:%' ORDER BY sortOrder, name LIMIT :limit")
    fun observeTopFavorites(sourceId: Long, kind: String, limit: Int): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE categoryId = :categoryId AND externalId NOT LIKE 'episode:%' ORDER BY sortOrder, name LIMIT :limit")
    fun observeTopByCategory(categoryId: Long, limit: Int): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND categoryId IS NULL AND externalId NOT LIKE 'episode:%' ORDER BY sortOrder, name LIMIT :limit")
    fun observeUncategorized(sourceId: Long, kind: String, limit: Int): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND externalId NOT LIKE 'episode:%' ORDER BY sortOrder, name LIMIT :limit")
    fun observeTopByKind(sourceId: Long, kind: String, limit: Int): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND externalId NOT LIKE 'episode:%' AND nameNorm LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY sortOrder, name LIMIT :limit")
    fun searchTop(sourceId: Long, kind: String, query: String, limit: Int): Flow<List<ChannelEntity>>

    @Query("UPDATE channels SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("SELECT externalId FROM channels WHERE sourceId = :sourceId AND isFavorite = 1")
    suspend fun favoriteExternalIds(sourceId: Long): List<String>

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    fun observeCountBySource(sourceId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun countBySource(sourceId: Long): Int

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = 'LIVE' ORDER BY sortOrder, name")
    suspend fun liveBySource(sourceId: Long): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = 'SERIES' AND externalId NOT LIKE 'episode:%' ORDER BY sortOrder, name")
    suspend fun seriesBySource(sourceId: Long): List<ChannelEntity>

    @Query("SELECT id FROM channels WHERE sourceId = :sourceId AND kind = :kind ORDER BY sortOrder, name")
    suspend fun channelIds(sourceId: Long, kind: String): List<Long>
}
