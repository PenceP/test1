package com.test1.tv.data.repository

import com.test1.tv.data.local.dao.PlaybackProgressDao
import com.test1.tv.data.local.entity.PlaybackProgress
import com.test1.tv.data.model.ContentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing local playback progress.
 *
 * Provides offline-first storage of playback progress with Trakt sync support.
 */
@Singleton
class PlaybackProgressRepository @Inject constructor(
    private val playbackProgressDao: PlaybackProgressDao
) {
    /**
     * Save playback progress for a movie or episode
     */
    suspend fun saveProgress(
        contentItem: ContentItem,
        positionMs: Long,
        durationMs: Long,
        season: Int? = null,
        episode: Int? = null,
        showTitle: String? = null
    ) = withContext(Dispatchers.IO) {
        val percent = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs)
        } else {
            0f
        }

        val isEpisode = contentItem.type == ContentItem.ContentType.TV_SHOW &&
                season != null && episode != null && season > 0 && episode > 0

        val id = if (isEpisode) {
            PlaybackProgress.createEpisodeId(contentItem.tmdbId, season!!, episode!!)
        } else {
            PlaybackProgress.createMovieId(contentItem.tmdbId)
        }

        val progress = PlaybackProgress(
            id = id,
            tmdbId = contentItem.tmdbId,
            type = if (isEpisode) PlaybackProgress.TYPE_EPISODE else PlaybackProgress.TYPE_MOVIE,
            title = if (isEpisode) {
                "S${season}E${episode}: ${contentItem.title}"
            } else {
                contentItem.title
            },
            posterUrl = contentItem.posterUrl,
            showTitle = showTitle,
            season = if (isEpisode) season else null,
            episode = if (isEpisode) episode else null,
            positionMs = positionMs,
            durationMs = durationMs,
            percent = percent,
            lastWatchedAt = System.currentTimeMillis(),
            syncedToTrakt = false
        )

        playbackProgressDao.saveProgress(progress)
    }

    /**
     * Get progress for a specific movie
     */
    suspend fun getMovieProgress(tmdbId: Int): PlaybackProgress? =
        withContext(Dispatchers.IO) {
            playbackProgressDao.getMovieProgress(tmdbId)
        }

    /**
     * Get progress for a specific episode
     */
    suspend fun getEpisodeProgress(showTmdbId: Int, season: Int, episode: Int): PlaybackProgress? =
        withContext(Dispatchers.IO) {
            playbackProgressDao.getEpisodeProgress(showTmdbId, season, episode)
        }

    /**
     * Get all progress for a TV show (all episodes)
     */
    suspend fun getShowProgress(showTmdbId: Int): List<PlaybackProgress> =
        withContext(Dispatchers.IO) {
            playbackProgressDao.getShowProgress(showTmdbId)
        }

    /**
     * Get items for "Continue Watching" row
     * Returns items between 5% and 90% progress
     */
    fun getContinueWatchingFlow(limit: Int = 20): Flow<List<PlaybackProgress>> =
        playbackProgressDao.getContinueWatchingFlow(limit)

    /**
     * Get continue watching items (non-flow version)
     */
    suspend fun getContinueWatching(limit: Int = 20): List<PlaybackProgress> =
        withContext(Dispatchers.IO) {
            playbackProgressDao.getContinueWatching(limit)
        }

    /**
     * Mark an item as fully watched
     */
    suspend fun markWatched(
        tmdbId: Int,
        type: String,
        season: Int? = null,
        episode: Int? = null,
        synced: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val id = PlaybackProgress.createId(tmdbId, type, season, episode)
        playbackProgressDao.markWatched(id, synced)
    }

    /**
     * Mark item as synced to Trakt
     */
    suspend fun markSynced(id: String) = withContext(Dispatchers.IO) {
        playbackProgressDao.markSynced(id)
    }

    /**
     * Get all items pending Trakt sync
     */
    suspend fun getUnsyncedProgress(): List<PlaybackProgress> =
        withContext(Dispatchers.IO) {
            playbackProgressDao.getUnsyncedProgress()
        }

    /**
     * Delete progress for an item
     */
    suspend fun deleteProgress(
        tmdbId: Int,
        type: String,
        season: Int? = null,
        episode: Int? = null
    ) = withContext(Dispatchers.IO) {
        val id = PlaybackProgress.createId(tmdbId, type, season, episode)
        playbackProgressDao.deleteProgress(id)
    }

    /**
     * Clear all progress (for logout/reset)
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        playbackProgressDao.clearAll()
    }
}
