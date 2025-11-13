package com.test1.tv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.test1.tv.data.local.entity.CachedContent

@Dao
interface CachedContentDao {

    @Query("SELECT * FROM cached_content WHERE category = :category ORDER BY position ASC")
    suspend fun getContentByCategory(category: String): List<CachedContent>

    @Query("SELECT * FROM cached_content WHERE id = :id LIMIT 1")
    suspend fun getContentById(id: String): CachedContent?

    @Query("SELECT cachedAt FROM cached_content WHERE category = :category LIMIT 1")
    suspend fun getCategoryTimestamp(category: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContent(content: List<CachedContent>)

    @Query("DELETE FROM cached_content WHERE category = :category")
    suspend fun deleteCategoryContent(category: String)

    @Query("DELETE FROM cached_content WHERE cachedAt < :timestamp")
    suspend fun deleteOldContent(timestamp: Long)

    @Transaction
    suspend fun replaceCategoryContent(category: String, content: List<CachedContent>) {
        deleteCategoryContent(category)
        insertContent(content)
    }

    @Query("SELECT COUNT(*) FROM cached_content WHERE category = :category")
    suspend fun getCategoryCount(category: String): Int
}
