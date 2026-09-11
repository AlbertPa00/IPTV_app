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

    @Query("SELECT * FROM categories WHERE sourceId = :sourceId AND kind = :kind ORDER BY sortOrder, name")
    fun observeBySource(sourceId: Long, kind: String): Flow<List<CategoryEntity>>

    @Query("DELETE FROM categories WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)
}
