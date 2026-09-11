package com.iptv.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.iptv.core.storage.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    /** Categorías visibles en catálogo: las bloqueadas nunca aparecen. */
    @Query("SELECT * FROM categories WHERE sourceId = :sourceId AND kind = :kind AND isLocked = 0 ORDER BY sortOrder, name")
    fun observeBySource(sourceId: Long, kind: String): Flow<List<CategoryEntity>>

    /** Todas las categorías (incluidas las bloqueadas) para gestión parental. */
    @Query("SELECT * FROM categories WHERE sourceId = :sourceId ORDER BY kind, sortOrder, name")
    fun observeAllBySource(sourceId: Long): Flow<List<CategoryEntity>>

    @Query("UPDATE categories SET isLocked = :locked WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean)

    /** externalIds de las categorías bloqueadas: se preservan al resincronizar. */
    @Query("SELECT externalId FROM categories WHERE sourceId = :sourceId AND isLocked = 1")
    suspend fun lockedExternalIds(sourceId: Long): List<String>

    @Query("UPDATE categories SET isLocked = 0 WHERE sourceId = :sourceId")
    suspend fun unlockAll(sourceId: Long)

    @Query("DELETE FROM categories WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)
}
