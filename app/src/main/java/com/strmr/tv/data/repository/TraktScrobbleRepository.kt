package com.strmr.tv.data.repository

import android.util.Log
import com.strmr.tv.BuildConfig
import com.strmr.tv.data.model.ContentItem
import com.strmr.tv.data.model.trakt.TraktScrobbleEpisode
import com.strmr.tv.data.model.trakt.TraktScrobbleIds
import com.strmr.tv.data.model.trakt.TraktScrobbleMovie
import com.strmr.tv.data.model.trakt.TraktScrobbleRequest
import com.strmr.tv.data.model.trakt.TraktScrobbleShow
import com.strmr.tv.data.remote.api.TraktApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing Trakt scrobbling.
 *
 * Scrobbling flow:
 * 1. Call startWatching() when playback begins
 * 2. Call updateProgress() periodically (every 60s) or when paused
 * 3. Call stopWatching() when playback ends or user exits
 *
 * At 80%+ completion, Trakt auto-marks the item as watched.
 */
@Singleton
class TraktScrobbleRepository @Inject constructor(
    private val traktApiService: TraktApiService,
    private val accountRepository: TraktAccountRepository
) {
    companion object {
        private const val TAG = "TraktScrobble"
        private const val SCROBBLE_THRESHOLD = 80.0  // 80% = auto-mark watched
        private const val UPDATE_INTERVAL_MS = 60_000L  // Update every 60 seconds
    }

    private var lastUpdateTime = 0L
    private var hasScrobbled = false

    /**
     * Start watching - call when playback begins
     */
    suspend fun startWatching(
        contentItem: ContentItem,
        season: Int? = null,
        episode: Int? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val account = accountRepository.refreshTokenIfNeeded()
        if (account == null) {
            Log.d(TAG, "Not authenticated - skipping scrobble")
            return@withContext Result.success(Unit)
        }

        hasScrobbled = false
        lastUpdateTime = System.currentTimeMillis()

        val request = buildScrobbleRequest(contentItem, season, episode, progress = 0.0)

        runCatching {
            traktApiService.startScrobble(
                authHeader = "Bearer ${account.accessToken}",
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            Log.d(TAG, "Started watching: ${contentItem.title}")
        }.onFailure { e ->
            Log.e(TAG, "Failed to start scrobble", e)
        }

        Result.success(Unit)
    }

    /**
     * Update progress - call periodically or when paused
     * Returns true if progress was actually sent (respects rate limiting)
     */
    suspend fun updateProgress(
        contentItem: ContentItem,
        currentPositionMs: Long,
        durationMs: Long,
        isPaused: Boolean,
        season: Int? = null,
        episode: Int? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        // Rate limit updates (unless paused - always report pause)
        val now = System.currentTimeMillis()
        if (!isPaused && now - lastUpdateTime < UPDATE_INTERVAL_MS) {
            return@withContext Result.success(false)
        }

        val account = accountRepository.refreshTokenIfNeeded()
        if (account == null) {
            return@withContext Result.success(false)
        }

        val progress = calculateProgress(currentPositionMs, durationMs)
        val request = buildScrobbleRequest(contentItem, season, episode, progress)

        runCatching {
            when {
                isPaused -> {
                    traktApiService.pauseScrobble(
                        authHeader = "Bearer ${account.accessToken}",
                        clientId = BuildConfig.TRAKT_CLIENT_ID,
                        body = request
                    )
                    Log.d(TAG, "Paused at ${progress.toInt()}%")
                }
                !hasScrobbled && progress >= SCROBBLE_THRESHOLD -> {
                    // Stop scrobble at 80%+ triggers auto-watched
                    traktApiService.stopScrobble(
                        authHeader = "Bearer ${account.accessToken}",
                        clientId = BuildConfig.TRAKT_CLIENT_ID,
                        body = request
                    )
                    hasScrobbled = true
                    Log.d(TAG, "Auto-scrobbled (marked watched) at ${progress.toInt()}%")
                }
                else -> {
                    // Just update - Trakt doesn't have a dedicated "update" endpoint,
                    // so we use start again which updates the current watching status
                    traktApiService.startScrobble(
                        authHeader = "Bearer ${account.accessToken}",
                        clientId = BuildConfig.TRAKT_CLIENT_ID,
                        body = request
                    )
                    Log.d(TAG, "Updated progress: ${progress.toInt()}%")
                }
            }
            lastUpdateTime = now
        }.onFailure { e ->
            Log.e(TAG, "Failed to update progress", e)
        }

        Result.success(true)
    }

    /**
     * Stop watching - call when playback ends or user exits
     */
    suspend fun stopWatching(
        contentItem: ContentItem,
        currentPositionMs: Long,
        durationMs: Long,
        season: Int? = null,
        episode: Int? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val account = accountRepository.refreshTokenIfNeeded()
        if (account == null) {
            return@withContext Result.success(Unit)
        }

        val progress = calculateProgress(currentPositionMs, durationMs)
        val request = buildScrobbleRequest(contentItem, season, episode, progress)

        runCatching {
            traktApiService.stopScrobble(
                authHeader = "Bearer ${account.accessToken}",
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            Log.d(TAG, "Stopped watching at ${progress.toInt()}%")
        }.onFailure { e ->
            Log.e(TAG, "Failed to stop scrobble", e)
        }

        // Reset state
        hasScrobbled = false
        lastUpdateTime = 0L

        Result.success(Unit)
    }

    /**
     * Reset scrobble state - call when starting a new playback session
     */
    fun resetState() {
        hasScrobbled = false
        lastUpdateTime = 0L
    }

    private fun calculateProgress(currentPositionMs: Long, durationMs: Long): Double {
        return if (durationMs > 0) {
            (currentPositionMs.toDouble() / durationMs) * 100.0
        } else {
            0.0
        }
    }

    private fun buildScrobbleRequest(
        contentItem: ContentItem,
        season: Int?,
        episode: Int?,
        progress: Double
    ): TraktScrobbleRequest {
        val ids = TraktScrobbleIds(
            tmdb = contentItem.tmdbId,
            imdb = contentItem.imdbId
        )

        return when (contentItem.type) {
            ContentItem.ContentType.MOVIE -> TraktScrobbleRequest(
                movie = TraktScrobbleMovie(ids = ids),
                progress = progress,
                appVersion = BuildConfig.VERSION_NAME
            )
            ContentItem.ContentType.TV_SHOW -> TraktScrobbleRequest(
                show = TraktScrobbleShow(ids = ids),
                episode = if (season != null && episode != null && season > 0 && episode > 0) {
                    TraktScrobbleEpisode(season = season, number = episode)
                } else {
                    null
                },
                progress = progress,
                appVersion = BuildConfig.VERSION_NAME
            )
        }
    }
}
