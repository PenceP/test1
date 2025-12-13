package com.strmr.tv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.strmr.tv.data.local.entity.PlaybackProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackProgressDao {

    /**
     * Get progress for a specific item by ID
     */
    @Query("SELECT * FROM playback_progress WHERE id = :id")
    suspend fun getProgress(id: String): PlaybackProgress?

    /**
     * Get progress for a movie by TMDB ID
     */
    @Query("SELECT * FROM playback_progress WHERE tmdbId = :tmdbId AND type = 'movie'")
    suspend fun getMovieProgress(tmdbId: Int): PlaybackProgress?

    /**
     * Get progress for an episode by TMDB show ID, season, and episode
     */
    @Query("SELECT * FROM playback_progress WHERE tmdbId = :showTmdbId AND season = :season AND episode = :episode AND type = 'episode'")
    suspend fun getEpisodeProgress(showTmdbId: Int, season: Int, episode: Int): PlaybackProgress?

    /**
     * Get all progress items for a TV show (all episodes)
     */
    @Query("SELECT * FROM playback_progress WHERE tmdbId = :showTmdbId AND type = 'episode' ORDER BY season, episode")
    suspend fun getShowProgress(showTmdbId: Int): List<PlaybackProgress>

    /**
     * Get items for "Continue Watching" - between 5% and 90% progress
     * Ordered by most recently watched
     */
    @Query("""
        SELECT * FROM playback_progress
        WHERE percent > 0.05 AND percent < 0.90
        ORDER BY lastWatchedAt DESC
        LIMIT :limit
    """)
    fun getContinueWatchingFlow(limit: Int = 20): Flow<List<PlaybackProgress>>

    /**
     * Get continue watching items (non-flow version)
     */
    @Query("""
        SELECT * FROM playback_progress
        WHERE percent > 0.05 AND percent < 0.90
        ORDER BY lastWatchedAt DESC
        LIMIT :limit
    """)
    suspend fun getContinueWatching(limit: Int = 20): List<PlaybackProgress>

    /**
     * Save or update progress
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlaybackProgress)

    /**
     * Delete progress for a specific item
     */
    @Query("DELETE FROM playback_progress WHERE id = :id")
    suspend fun deleteProgress(id: String)

    /**
     * Delete all progress for a show (all episodes)
     */
    @Query("DELETE FROM playback_progress WHERE tmdbId = :showTmdbId AND type = 'episode'")
    suspend fun deleteShowProgress(showTmdbId: Int)

    /**
     * Mark item as watched (set percent to 1.0)
     */
    @Query("UPDATE playback_progress SET percent = 1.0, syncedToTrakt = :synced WHERE id = :id")
    suspend fun markWatched(id: String, synced: Boolean = false)

    /**
     * Mark item as synced to Trakt
     */
    @Query("UPDATE playback_progress SET syncedToTrakt = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    /**
     * Get all items not yet synced to Trakt
     */
    @Query("SELECT * FROM playback_progress WHERE syncedToTrakt = 0")
    suspend fun getUnsyncedProgress(): List<PlaybackProgress>

    /**
     * Clear all progress (for logout/reset)
     */
    @Query("DELETE FROM playback_progress")
    suspend fun clearAll()

    /**
     * Get recently watched items (any progress)
     */
    @Query("""
        SELECT * FROM playback_progress
        ORDER BY lastWatchedAt DESC
        LIMIT :limit
    """)
    suspend fun getRecentlyWatched(limit: Int = 50): List<PlaybackProgress>
}
