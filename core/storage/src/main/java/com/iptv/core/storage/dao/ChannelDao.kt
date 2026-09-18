package com.iptv.core.storage.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.ChannelStagingEntity
import kotlinx.coroutines.flow.Flow

/** Excluye canales de categorías bloqueadas u ocultas por el usuario. */
private const val UNLOCKED_ONLY =
    "(categoryId IS NULL OR categoryId NOT IN " +
        "(SELECT id FROM categories WHERE isLocked = 1 OR hidden = 1))"

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

    /** true si el canal pertenece a una categoría bloqueada por control parental. */
    @Query("SELECT EXISTS(SELECT 1 FROM channels c INNER JOIN categories cat ON cat.id = c.categoryId WHERE c.id = :channelId AND cat.isLocked = 1)")
    suspend fun isInLockedCategory(channelId: Long): Boolean

    // Las filas "episode:*" son sintéticas (se crean al reproducir un
    // episodio para poder reanudarlo); nunca deben aparecer en el catálogo.
    // Los canales de categorías bloqueadas tampoco aparecen nunca.
    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND externalId NOT LIKE 'episode:%' AND $UNLOCKED_ONLY ORDER BY sortOrder, name")
    fun pagingBySource(sourceId: Long, kind: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND language = :language AND externalId NOT LIKE 'episode:%' AND $UNLOCKED_ONLY ORDER BY sortOrder, name")
    fun pagingByLanguage(sourceId: Long, kind: String, language: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND categoryId = :categoryId AND externalId NOT LIKE 'episode:%' AND $UNLOCKED_ONLY ORDER BY sortOrder, name")
    fun pagingByCategory(sourceId: Long, categoryId: Long): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND externalId NOT LIKE 'episode:%' AND nameNorm LIKE '%' || :query || '%' ESCAPE '\\' AND $UNLOCKED_ONLY ORDER BY sortOrder, name")
    fun pagingBySearch(sourceId: Long, kind: String, query: String): PagingSource<Int, ChannelEntity>

    /**
     * Búsqueda por FTS4: [match] es una consulta MATCH ya construida
     * (`"tok" * "otro" *`). El MATCH va en una subconsulta IN: materializa el
     * conjunto una vez y recorre `channels` por el índice de orden — un JOIN
     * directo haría una sonda FTS por cada fila (~30 s en catálogos grandes).
     */
    @Query(
        "SELECT c.* FROM channels c WHERE c.id IN " +
            "(SELECT rowid FROM channels_fts WHERE channels_fts MATCH :match) " +
            "AND c.sourceId = :sourceId AND c.kind = :kind " +
            "AND c.externalId NOT LIKE 'episode:%' AND " +
            "(c.categoryId IS NULL OR c.categoryId NOT IN " +
            "(SELECT id FROM categories WHERE isLocked = 1 OR hidden = 1)) " +
            "ORDER BY c.sortOrder, c.name",
    )
    fun pagingByFts(sourceId: Long, kind: String, match: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND isFavorite = 1 AND externalId NOT LIKE 'episode:%' AND $UNLOCKED_ONLY ORDER BY sortOrder, name")
    fun pagingFavorites(sourceId: Long, kind: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND categoryId IS NULL AND externalId NOT LIKE 'episode:%' ORDER BY sortOrder, name")
    fun pagingUncategorized(sourceId: Long, kind: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND isFavorite = 1 AND externalId NOT LIKE 'episode:%' AND $UNLOCKED_ONLY ORDER BY sortOrder, name LIMIT :limit")
    fun observeTopFavorites(sourceId: Long, kind: String, limit: Int): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE categoryId = :categoryId AND externalId NOT LIKE 'episode:%' ORDER BY sortOrder, name LIMIT :limit")
    fun observeTopByCategory(categoryId: Long, limit: Int): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND categoryId IS NULL AND externalId NOT LIKE 'episode:%' ORDER BY sortOrder, name LIMIT :limit")
    fun observeUncategorized(sourceId: Long, kind: String, limit: Int): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND externalId NOT LIKE 'episode:%' AND $UNLOCKED_ONLY ORDER BY sortOrder, name LIMIT :limit")
    fun observeTopByKind(sourceId: Long, kind: String, limit: Int): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND language = :language AND externalId NOT LIKE 'episode:%' AND $UNLOCKED_ONLY ORDER BY sortOrder, name LIMIT :limit")
    fun observeTopByLanguage(sourceId: Long, kind: String, language: String, limit: Int): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND categoryId = :categoryId AND externalId NOT LIKE 'episode:%' AND $UNLOCKED_ONLY ORDER BY sortOrder, name LIMIT :limit")
    fun observeTopInCategory(sourceId: Long, kind: String, categoryId: Long, limit: Int): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = :kind AND externalId NOT LIKE 'episode:%' AND nameNorm LIKE '%' || :query || '%' ESCAPE '\\' AND $UNLOCKED_ONLY ORDER BY sortOrder, name LIMIT :limit")
    fun searchTop(sourceId: Long, kind: String, query: String, limit: Int): Flow<List<ChannelEntity>>

    @Query("UPDATE channels SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE channels SET language = (SELECT language FROM categories WHERE categories.id = channels.categoryId) WHERE categoryId IS NOT NULL AND language = ''")
    suspend fun propagateLanguageFromCategories(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM channels WHERE language = '' LIMIT 1)")
    suspend fun hasWithoutLanguage(): Boolean

    @Query("SELECT id, name FROM channels WHERE language = ''")
    suspend fun withoutLanguage(): List<ChannelIdName>

    /** Proyección (id, name, categoryId) de TODOS los canales: re-etiquetado. */
    @Query("SELECT id, name, categoryId FROM channels")
    suspend fun allIdNameCategory(): List<ChannelIdNameCategory>

    @Query("UPDATE channels SET language = :language WHERE id IN (:ids)")
    suspend fun setLanguageForIds(ids: List<Long>, language: String)

    @Query("SELECT externalId FROM channels WHERE sourceId = :sourceId AND isFavorite = 1")
    suspend fun favoriteExternalIds(sourceId: Long): List<String>

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId AND kind NOT IN (:keptKinds)")
    suspend fun deleteBySourceExceptKinds(sourceId: Long, keptKinds: Collection<String>)

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    fun observeCountBySource(sourceId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun countBySource(sourceId: Long): Int

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = 'LIVE' AND $UNLOCKED_ONLY ORDER BY sortOrder, name")
    suspend fun liveBySource(sourceId: Long): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND kind = 'SERIES' AND externalId NOT LIKE 'episode:%' AND $UNLOCKED_ONLY ORDER BY sortOrder, name")
    suspend fun seriesBySource(sourceId: Long): List<ChannelEntity>

    @Query("SELECT id FROM channels WHERE sourceId = :sourceId AND kind = :kind AND $UNLOCKED_ONLY ORDER BY sortOrder, name")
    suspend fun channelIds(sourceId: Long, kind: String): List<Long>

    // -- Staging de importación ----------------------------------------------
    // El catálogo nuevo se vuelca en `channels_staging` durante el parseo y se
    // funde en `channels` en una transacción: REPLACE conserva id e isFavorite
    // (playback_history y favoritos sobreviven a la resincronización), la
    // cláusula IS NOT evita reescribir filas idénticas y el NOT IN anti-join
    // borra lo que el proveedor retiró. Las filas "episode:*" se conservan:
    // son la clave del historial de episodios.

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStaging(rows: List<ChannelStagingEntity>)

    @Query("DELETE FROM channels_staging WHERE sourceId = :sourceId")
    suspend fun clearStaging(sourceId: Long)

    @Query(
        """
        INSERT OR REPLACE INTO channels
            (id, sourceId, categoryId, externalId, name, nameNorm, streamUrl, logoUrl,
             tvgId, groupTitle, kind, containerExt, sortOrder, isFavorite, language,
             userAgent, referrer)
        SELECT x.id, s.sourceId, cat.id, s.externalId, s.name, s.nameNorm, s.streamUrl,
               s.logoUrl, s.tvgId, s.groupTitle, s.kind, s.containerExt, s.sortOrder,
               x.isFavorite, s.language, s.userAgent, s.referrer
        FROM channels_staging s
        JOIN channels x ON x.sourceId = s.sourceId AND x.externalId = s.externalId
        LEFT JOIN categories cat ON cat.sourceId = s.sourceId AND cat.externalId = s.groupTitle
        WHERE s.sourceId = :sourceId AND (
            x.categoryId IS NOT cat.id OR x.name IS NOT s.name OR x.nameNorm IS NOT s.nameNorm
            OR x.streamUrl IS NOT s.streamUrl OR x.logoUrl IS NOT s.logoUrl OR x.tvgId IS NOT s.tvgId
            OR x.groupTitle IS NOT s.groupTitle OR x.kind IS NOT s.kind
            OR x.containerExt IS NOT s.containerExt OR x.sortOrder IS NOT s.sortOrder
            OR x.language IS NOT s.language OR x.userAgent IS NOT s.userAgent
            OR x.referrer IS NOT s.referrer)
        """,
    )
    suspend fun mergeStagedUpdates(sourceId: Long)

    @Query(
        """
        INSERT INTO channels
            (sourceId, categoryId, externalId, name, nameNorm, streamUrl, logoUrl,
             tvgId, groupTitle, kind, containerExt, sortOrder, isFavorite, language,
             userAgent, referrer)
        SELECT s.sourceId, cat.id, s.externalId, s.name, s.nameNorm, s.streamUrl,
               s.logoUrl, s.tvgId, s.groupTitle, s.kind, s.containerExt, s.sortOrder,
               0, s.language, s.userAgent, s.referrer
        FROM channels_staging s
        LEFT JOIN categories cat ON cat.sourceId = s.sourceId AND cat.externalId = s.groupTitle
        WHERE s.sourceId = :sourceId AND NOT EXISTS (
            SELECT 1 FROM channels x
            WHERE x.sourceId = s.sourceId AND x.externalId = s.externalId)
        """,
    )
    suspend fun insertStagedNew(sourceId: Long)

    @Query(
        "DELETE FROM channels WHERE sourceId = :sourceId AND externalId NOT LIKE 'episode:%' " +
            "AND externalId NOT IN (SELECT externalId FROM channels_staging WHERE sourceId = :sourceId)",
    )
    suspend fun deleteAbsent(sourceId: Long)

    @Query(
        "DELETE FROM channels WHERE sourceId = :sourceId AND kind NOT IN (:keptKinds) " +
            "AND externalId NOT LIKE 'episode:%' " +
            "AND externalId NOT IN (SELECT externalId FROM channels_staging WHERE sourceId = :sourceId)",
    )
    suspend fun deleteAbsentExcept(sourceId: Long, keptKinds: Collection<String>)
}

/** Proyección ligera para el backfill de idioma (sin cargar la entidad completa). */
data class ChannelIdName(val id: Long, val name: String)

/** Proyección para el re-etiquetado completo: nombre + categoría heredable. */
data class ChannelIdNameCategory(val id: Long, val name: String, val categoryId: Long?)
