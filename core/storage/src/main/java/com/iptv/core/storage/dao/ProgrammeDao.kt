package com.iptv.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.iptv.core.storage.entity.ProgrammeEntity
import kotlinx.coroutines.flow.Flow

/** Canal reducido para componer la guía en memoria. */
data class GuideChannelRow(
    val id: Long,
    val name: String,
    val logoUrl: String?,
    val tvgId: String?,
    val nameNorm: String,
)

/** Próximo programa por canal EPG (channelKey) con su nombre alternativo. */
data class GuideNextRow(
    val channelKey: String,
    val channelNameNorm: String,
    val startUtc: Long,
    val endUtc: Long,
    val title: String,
)

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
        SELECT c.id AS id, c.name AS name, c.logoUrl AS logoUrl,
               c.tvgId AS tvgId, c.nameNorm AS nameNorm
        FROM channels c
        WHERE c.sourceId = :sourceId AND c.kind = 'LIVE'
          AND (c.categoryId IS NULL OR c.categoryId NOT IN
              (SELECT id FROM categories WHERE isLocked = 1 OR hidden = 1))
        ORDER BY c.sortOrder, c.name
        """
    )
    suspend fun guideChannels(sourceId: Long): List<GuideChannelRow>

    @Query(
        """
        SELECT * FROM programmes
        WHERE sourceId = :sourceId
          AND startUtc <= :atUtc AND startUtc >= :atUtc - 86400000
          AND endUtc > :atUtc
        """
    )
    suspend fun currentProgrammes(sourceId: Long, atUtc: Long): List<ProgrammeEntity>

    @Query(
        """
        SELECT channelKey, channelNameNorm,
               MIN(startUtc) AS startUtc, endUtc, title
        FROM programmes
        WHERE sourceId = :sourceId AND startUtc > :atUtc AND startUtc <= :atUtc + 86400000
        GROUP BY channelKey
        """
    )
    suspend fun nextProgrammes(sourceId: Long, atUtc: Long): List<GuideNextRow>

    /**
     * Guía completa sin subconsultas por canal: tres consultas indexadas y el
     * emparejamiento en memoria. Los programas casan por tvgId (channelKey) o
     * por nombre normalizado (channelNameNorm), como hacía la consulta previa.
     */
    suspend fun guideSnapshot(sourceId: Long, atUtc: Long): List<GuideRow> {
        val channels = guideChannels(sourceId)
        if (channels.isEmpty()) return emptyList()
        val current = currentProgrammes(sourceId, atUtc)
        val next = nextProgrammes(sourceId, atUtc)
        if (current.isEmpty() && next.isEmpty()) return emptyList()

        val curByKey = HashMap<String, ProgrammeEntity>(current.size)
        val curByName = HashMap<String, ProgrammeEntity>(current.size)
        for (p in current) {
            curByKey.merge(p.channelKey, p) { a, b -> if (b.startUtc >= a.startUtc) b else a }
            curByName.merge(p.channelNameNorm, p) { a, b -> if (b.startUtc >= a.startUtc) b else a }
        }
        val nextByKey = HashMap<String, GuideNextRow>(next.size)
        val nextByName = HashMap<String, GuideNextRow>(next.size)
        for (n in next) {
            nextByKey[n.channelKey] = n
            nextByName[n.channelNameNorm] = n
        }

        return channels.mapNotNull { c ->
            val cur = c.tvgId?.let(curByKey::get) ?: curByName[c.nameNorm]
            val nxt = c.tvgId?.let(nextByKey::get) ?: nextByName[c.nameNorm]
            if (cur == null && nxt == null) {
                null
            } else {
                GuideRow(
                    channelId = c.id,
                    channelName = c.name,
                    channelLogoUrl = c.logoUrl,
                    channelTvgId = c.tvgId,
                    currentId = cur?.id,
                    currentTitle = cur?.title,
                    currentDescription = cur?.description,
                    currentStartUtc = cur?.startUtc,
                    currentEndUtc = cur?.endUtc,
                    currentIconUrl = cur?.iconUrl,
                    nextTitle = nxt?.title,
                    nextStartUtc = nxt?.startUtc,
                    nextEndUtc = nxt?.endUtc,
                )
            }
        }
    }
}
