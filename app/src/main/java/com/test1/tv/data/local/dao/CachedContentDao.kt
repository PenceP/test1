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

    @Query("SELECT MIN(cachedAt) FROM cached_content WHERE category = :category")
    suspend fun getCategoryTimestamp(category: String): Long?

    @Query(
        "SELECT * FROM cached_content WHERE category = :category " +
            "ORDER BY position ASC LIMIT :limit OFFSET :offset"
    )
    suspend fun getContentPage(
        category: String,
        limit: Int,
        offset: Int
    ): List<CachedContent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContent(content: List<CachedContent>)

    @Query("DELETE FROM cached_content WHERE category = :category")
    suspend fun deleteCategoryContent(category: String)

    @Query(
        "DELETE FROM cached_content WHERE category = :category " +
            "AND position BETWEEN :start AND :end"
    )
    suspend fun deleteRange(
        category: String,
        start: Int,
        end: Int
    )

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
