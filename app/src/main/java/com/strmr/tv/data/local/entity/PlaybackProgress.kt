package com.strmr.tv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local storage for playback progress.
 *
 * Stores both local progress and sync status with Trakt.
 * Used for:
 * - Continue Watching functionality
 * - Resume from where you left off
 * - Offline resilience (sync to Trakt when online)
 */
@Entity(
    tableName = "playback_progress",
    indices = [
        Index(value = ["tmdbId", "type"]),
        Index(value = ["lastWatchedAt"]),
        Index(value = ["syncedToTrakt"])
    ]
)
data class PlaybackProgress(
    @PrimaryKey
    val id: String,  // "movie_{tmdbId}" or "show_{tmdbId}_{season}_{episode}"

    val tmdbId: Int,
    val type: String,  // "movie" or "episode"
    val title: String,
    val posterUrl: String? = null,

    // For episodes
    val showTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,

    // Progress tracking
    val positionMs: Long,
    val durationMs: Long,
    val percent: Float,  // 0.0 to 1.0

    // Timestamps
    val lastWatchedAt: Long = System.currentTimeMillis(),

    // Sync status
    val syncedToTrakt: Boolean = false
) {
    companion object {
        const val TYPE_MOVIE = "movie"
        const val TYPE_EPISODE = "episode"

        /**
         * Create a unique ID for a movie
         */
        fun createMovieId(tmdbId: Int): String = "movie_$tmdbId"

        /**
         * Create a unique ID for a TV episode
         */
        fun createEpisodeId(tmdbId: Int, season: Int, episode: Int): String =
            "show_${tmdbId}_${season}_$episode"

        /**
         * Create an ID based on type and parameters
         */
        fun createId(
            tmdbId: Int,
            type: String,
            season: Int? = null,
            episode: Int? = null
        ): String {
            return if (type == TYPE_EPISODE && season != null && episode != null) {
                createEpisodeId(tmdbId, season, episode)
            } else {
                createMovieId(tmdbId)
            }
        }
    }

    /**
     * Check if progress is in "continue watching" range (5% to 90%)
     */
    fun isContinueWatching(): Boolean = percent > 0.05f && percent < 0.90f

    /**
     * Check if item is considered watched (>= 90%)
     */
    fun isWatched(): Boolean = percent >= 0.90f
}
