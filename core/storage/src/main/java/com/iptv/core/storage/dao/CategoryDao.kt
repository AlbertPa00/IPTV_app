package com.iptv.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iptv.core.storage.entity.CategoryEntity
import com.iptv.core.storage.entity.CategoryStagingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    /** Categorías visibles en catálogo: bloqueadas u ocultas nunca aparecen. */
    @Query("SELECT * FROM categories WHERE sourceId = :sourceId AND kind = :kind AND isLocked = 0 AND hidden = 0 ORDER BY sortOrder, name")
    fun observeBySource(sourceId: Long, kind: String): Flow<List<CategoryEntity>>

    /** Todas las categorías (incluidas bloqueadas/ocultas) para la gestión. */
    @Query("SELECT * FROM categories WHERE sourceId = :sourceId ORDER BY kind, sortOrder, name")
    fun observeAllBySource(sourceId: Long): Flow<List<CategoryEntity>>

    /** Snapshot no reactivo de todas las categorías de la fuente. */
    @Query("SELECT * FROM categories WHERE sourceId = :sourceId")
    suspend fun allBySource(sourceId: Long): List<CategoryEntity>

    @Query("UPDATE categories SET isLocked = :locked WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean)

    @Query("UPDATE categories SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @androidx.room.Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    /** externalIds de las categorías bloqueadas: se preservan al resincronizar. */
    @Query("SELECT externalId FROM categories WHERE sourceId = :sourceId AND isLocked = 1")
    suspend fun lockedExternalIds(sourceId: Long): List<String>

    /** externalIds de las categorías ocultas: se preservan al resincronizar. */
    @Query("SELECT externalId FROM categories WHERE sourceId = :sourceId AND hidden = 1")
    suspend fun hiddenExternalIds(sourceId: Long): List<String>

    @Query("UPDATE categories SET isLocked = 0 WHERE sourceId = :sourceId")
    suspend fun unlockAll(sourceId: Long)

    @Query("DELETE FROM categories WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    @Query("DELETE FROM categories WHERE sourceId = :sourceId AND kind NOT IN (:keptKinds)")
    suspend fun deleteBySourceExceptKinds(sourceId: Long, keptKinds: Collection<String>)

    @Query("SELECT * FROM categories WHERE language = ''")
    suspend fun withoutLanguage(): List<CategoryEntity>

    /** Proyección ligera de todas las categorías (re-etiquetado de idioma). */
    @Query("SELECT id, name FROM categories")
    suspend fun allIdName(): List<ChannelIdName>

    @Query("UPDATE categories SET language = :language WHERE id = :id")
    suspend fun setLanguage(id: Long, language: String)

    // -- Staging de importación ----------------------------------------------
    // REPLACE conserva id, isLocked, hidden y el sortOrder previo (el orden
    // personalizado del usuario manda sobre el del proveedor, igual que antes).

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStaging(rows: List<CategoryStagingEntity>)

    @Query("DELETE FROM categories_staging WHERE sourceId = :sourceId")
    suspend fun clearStaging(sourceId: Long)

    @Query(
        """
        INSERT OR REPLACE INTO categories
            (id, sourceId, externalId, kind, name, sortOrder, isLocked, hidden, language)
        SELECT x.id, s.sourceId, s.externalId, s.kind, s.name, x.sortOrder,
               x.isLocked, x.hidden, s.language
        FROM categories_staging s
        JOIN categories x ON x.sourceId = s.sourceId AND x.externalId = s.externalId
        WHERE s.sourceId = :sourceId AND (
            x.kind IS NOT s.kind OR x.name IS NOT s.name OR x.language IS NOT s.language)
        """,
    )
    suspend fun mergeStagedUpdates(sourceId: Long)

    @Query(
        """
        INSERT INTO categories (sourceId, externalId, kind, name, sortOrder, isLocked, hidden, language)
        SELECT s.sourceId, s.externalId, s.kind, s.name, s.sortOrder, 0, 0, s.language
        FROM categories_staging s
        WHERE s.sourceId = :sourceId AND NOT EXISTS (
            SELECT 1 FROM categories x
            WHERE x.sourceId = s.sourceId AND x.externalId = s.externalId)
        """,
    )
    suspend fun insertStagedNew(sourceId: Long)

    @Query(
        "DELETE FROM categories WHERE sourceId = :sourceId " +
            "AND externalId NOT IN (SELECT externalId FROM categories_staging WHERE sourceId = :sourceId)",
    )
    suspend fun deleteAbsent(sourceId: Long)

    @Query(
        "DELETE FROM categories WHERE sourceId = :sourceId AND kind NOT IN (:keptKinds) " +
            "AND externalId NOT IN (SELECT externalId FROM categories_staging WHERE sourceId = :sourceId)",
    )
    suspend fun deleteAbsentExcept(sourceId: Long, keptKinds: Collection<String>)
}
