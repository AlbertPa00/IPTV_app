package com.iptv.core.storage.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow

/** Elemento del carrusel "Seguir viendo": canal con su progreso guardado. */
data class ContinueWatchingItem(
    @Embedded val channel: ChannelEntity,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

@Dao
interface PlaybackHistoryDao {

    @Upsert
    suspend fun upsert(entry: PlaybackHistoryEntity)

    @Query("SELECT * FROM playback_history WHERE channelId = :channelId")
    suspend fun find(channelId: Long): PlaybackHistoryEntity?

    @Query("DELETE FROM playback_history WHERE channelId = :channelId")
    suspend fun delete(channelId: Long)

    @Query("DELETE FROM playback_history WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    @Query(
        """
        SELECT c.*, h.positionMs AS positionMs, h.durationMs AS durationMs, h.updatedAt AS updatedAt
        FROM playback_history h INNER JOIN channels c ON c.id = h.channelId
        WHERE h.sourceId = :sourceId AND c.kind = :kind
          AND (c.categoryId IS NULL OR c.categoryId NOT IN
              (SELECT id FROM categories WHERE isLocked = 1))
        ORDER BY h.updatedAt DESC LIMIT :limit
        """,
    )
    fun observeContinueWatching(
        sourceId: Long,
        kind: String,
        limit: Int,
    ): Flow<List<ContinueWatchingItem>>

    @Query(
        """
        SELECT c.* FROM playback_history h INNER JOIN channels c ON c.id = h.channelId
        WHERE h.sourceId = :sourceId AND c.kind = :kind
          AND (c.categoryId IS NULL OR c.categoryId NOT IN
              (SELECT id FROM categories WHERE isLocked = 1))
        ORDER BY h.updatedAt DESC
        """,
    )
    fun pagingContinueWatching(sourceId: Long, kind: String): PagingSource<Int, ChannelEntity>
}
