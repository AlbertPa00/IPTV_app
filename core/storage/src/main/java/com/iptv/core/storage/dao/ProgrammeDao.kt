package com.iptv.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.iptv.core.storage.entity.ProgrammeEntity
import kotlinx.coroutines.flow.Flow

/** A channel plus its current and next programme, used by guide UIs. */
data class GuideRow(
    val channelId: Long,
    val channelName: String,
    val channelLogoUrl: String?,
    val channelTvgId: String?,
    val currentId: Long?,
    val currentTitle: String?,
    val currentDescription: String?,
    val currentStartUtc: Long?,
    val currentEndUtc: Long?,
    val currentIconUrl: String?,
    val nextTitle: String?,
    val nextStartUtc: Long?,
    val nextEndUtc: Long?,
)

@Dao
interface ProgrammeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programmes: List<ProgrammeEntity>)

    @Query("DELETE FROM programmes WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    @Transaction
    suspend fun replace(sourceId: Long, programmes: List<ProgrammeEntity>) {
        deleteBySource(sourceId)
        programmes.chunked(500).forEach { insertAll(it) }
    }

    @Query("DELETE FROM programmes WHERE endUtc < :beforeUtc")
    suspend fun purgeEndedBefore(beforeUtc: Long): Int

    /** true si el origen tiene al menos un programa (EPG) importado. */
    @Query("SELECT EXISTS(SELECT 1 FROM programmes WHERE sourceId = :sourceId LIMIT 1)")
    fun observeHasProgrammes(sourceId: Long): Flow<Boolean>

    @Query("SELECT * FROM programmes WHERE sourceId = :sourceId AND channelKey = :channelKey AND startUtc <= :atUtc AND endUtc > :atUtc ORDER BY startUtc DESC LIMIT 1")
    fun observeCurrent(sourceId: Long, channelKey: String, atUtc: Long): Flow<ProgrammeEntity?>

    @Query("SELECT * FROM programmes WHERE sourceId = :sourceId AND channelKey = :channelKey AND startUtc > :atUtc ORDER BY startUtc LIMIT 1")
    fun observeNext(sourceId: Long, channelKey: String, atUtc: Long): Flow<ProgrammeEntity?>

    @Query(
        """
        SELECT c.id AS channelId, c.name AS channelName, c.logoUrl AS channelLogoUrl,
               c.tvgId AS channelTvgId,
               p.id AS currentId, p.title AS currentTitle, p.description AS currentDescription,
               p.startUtc AS currentStartUtc, p.endUtc AS currentEndUtc, p.iconUrl AS currentIconUrl,
               (SELECT n.title FROM programmes n WHERE n.sourceId = c.sourceId
                    AND (n.channelKey = c.tvgId OR n.channelNameNorm = c.nameNorm)
                    AND n.startUtc >= :atUtc ORDER BY n.startUtc LIMIT 1) AS nextTitle,
               (SELECT n.startUtc FROM programmes n WHERE n.sourceId = c.sourceId
                    AND (n.channelKey = c.tvgId OR n.channelNameNorm = c.nameNorm)
                    AND n.startUtc >= :atUtc ORDER BY n.startUtc LIMIT 1) AS nextStartUtc,
               (SELECT n.endUtc FROM programmes n WHERE n.sourceId = c.sourceId
                    AND (n.channelKey = c.tvgId OR n.channelNameNorm = c.nameNorm)
                    AND n.startUtc >= :atUtc ORDER BY n.startUtc LIMIT 1) AS nextEndUtc
        FROM channels c
        LEFT JOIN programmes p ON p.id = (
            SELECT cp.id FROM programmes cp WHERE cp.sourceId = c.sourceId
              AND (cp.channelKey = c.tvgId OR cp.channelNameNorm = c.nameNorm)
              AND cp.startUtc <= :atUtc AND cp.endUtc > :atUtc
            ORDER BY cp.startUtc DESC LIMIT 1
        )
        WHERE c.sourceId = :sourceId AND c.kind = 'LIVE'
          AND (c.categoryId IS NULL OR c.categoryId NOT IN
              (SELECT id FROM categories WHERE isLocked = 1 OR hidden = 1))
          AND EXISTS (
              SELECT 1 FROM programmes px WHERE px.sourceId = c.sourceId
                AND (px.channelKey = c.tvgId OR px.channelNameNorm = c.nameNorm)
          )
        ORDER BY c.sortOrder, c.name
        """
    )
    fun observeGuide(sourceId: Long, atUtc: Long): Flow<List<GuideRow>>
}
