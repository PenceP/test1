package com.test1.tv.data.local.dao

import androidx.room.*
import com.test1.tv.data.local.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    // ==================== Queries ====================

    /**
     * Get all media for a category with images and progress.
     * Uses Room's automatic JOIN for relations.
     */
    @Transaction
    @Query("""
        SELECT * FROM media_content
        WHERE category = :category
        ORDER BY position ASC
    """)
    fun getMediaByCategory(category: String): Flow<List<MediaWithImages>>

    /**
     * Get single media with full details.
     */
    @Transaction
    @Query("SELECT * FROM media_content WHERE tmdbId = :tmdbId")
    suspend fun getMediaDetails(tmdbId: Int): MediaWithDetails?

    /**
     * Get single media with full details as Flow.
     */
    @Transaction
    @Query("SELECT * FROM media_content WHERE tmdbId = :tmdbId")
    fun observeMediaDetails(tmdbId: Int): Flow<MediaWithDetails?>

    /**
     * Check if category cache is still valid.
     */
    @Query("""
        SELECT COUNT(*) FROM media_content
        WHERE category = :category
        AND cachedAt > :minCacheTime
    """)
    suspend fun getCachedCount(category: String, minCacheTime: Long): Int

    // ==================== Inserts ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContent(content: MediaContentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContents(contents: List<MediaContentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: MediaImageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImagesBatch(images: List<MediaImageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRatings(ratings: MediaRatingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRatingsBatch(ratings: List<MediaRatingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: WatchProgressEntity)

    // ==================== Updates ====================

    @Query("UPDATE watch_progress SET progress = :progress, lastWatchedAt = :timestamp WHERE tmdbId = :tmdbId")
    suspend fun updateProgress(tmdbId: Int, progress: Float, timestamp: Long)

    // ==================== Deletes ====================

    @Query("DELETE FROM media_content WHERE category = :category")
    suspend fun clearCategory(category: String)

    @Query("DELETE FROM media_content WHERE cachedAt < :maxAge")
    suspend fun clearOldCache(maxAge: Long)

    // ==================== Transactions ====================

    /**
     * Atomically replace all content for a category.
     * Used for page 1 (fresh data).
     */
    @Transaction
    suspend fun replaceCategory(
        category: String,
        contents: List<MediaContentEntity>,
        images: List<MediaImageEntity>,
        ratings: List<MediaRatingEntity>
    ) {
        clearCategory(category)
        insertContents(contents)
        insertImagesBatch(images)
        insertRatingsBatch(ratings)
    }

    /**
     * Append content to existing category (for pagination).
     * Used for pages > 1 to accumulate data.
     */
    @Transaction
    suspend fun appendToCategory(
        contents: List<MediaContentEntity>,
        images: List<MediaImageEntity>,
        ratings: List<MediaRatingEntity>
    ) {
        insertContents(contents)
        insertImagesBatch(images)
        insertRatingsBatch(ratings)
    }

    /**
     * Get the maximum position for a category (for pagination offset calculation).
     */
    @Query("SELECT COALESCE(MAX(position), -1) FROM media_content WHERE category = :category")
    suspend fun getMaxPosition(category: String): Int
}
