package com.test1.tv.data.sync

import com.test1.tv.BuildConfig
import com.test1.tv.data.local.entity.WatchStatusEntity
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.remote.api.TraktApiService
import com.test1.tv.data.repository.TraktAccountRepository
import com.test1.tv.data.repository.WatchStatusRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class TraktSyncManager @Inject constructor(
    private val traktApi: TraktApiService,
    private val accountRepository: TraktAccountRepository,
    private val watchStatusRepository: WatchStatusRepository
) {

    /**
     * Syncs all watched history (movies + shows) with optional progress callbacks.
     * Runs on IO dispatcher; UI layer should call from a coroutine.
     */
    suspend fun syncAllWatched(
        onProgress: suspend (Float, String) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val account = accountRepository.refreshTokenIfNeeded() ?: return@withContext
        val auth = accountRepository.buildAuthHeader(account.accessToken)
        val limit = 1000 // Trakt max per page

        val allMovieEntities = mutableListOf<WatchStatusEntity>()
        val allShowEntities = mutableListOf<WatchStatusEntity>()

        // Phase 1: Movies with pagination
        onProgress(0.05f, "Fetching watched movies...")
        var page = 1
        while (true) {
            val movies = traktApi.getWatchedMovies(
                authHeader = auth,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                page = page,
                limit = limit
            )

            if (movies.isEmpty()) break

            onProgress(0.05f + (0.2f * page / 10), "Processing movies page $page...")

            allMovieEntities.addAll(
                movies.mapNotNull { watched ->
                    val tmdbId = watched.movie?.ids?.tmdb ?: return@mapNotNull null
                    WatchStatusEntity(
                        key = "${ContentItem.ContentType.MOVIE.name}_$tmdbId",
                        tmdbId = tmdbId,
                        type = ContentItem.ContentType.MOVIE.name,
                        progress = 1.0,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            )

            if (movies.size < limit) break // Last page
            page++
        }

        // Phase 2: Shows with pagination
        onProgress(0.35f, "Fetching watched shows...")
        page = 1
        while (true) {
            val shows = traktApi.getWatchedShows(
                authHeader = auth,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                page = page,
                limit = limit
            )

            if (shows.isEmpty()) break

            onProgress(0.35f + (0.2f * page / 10), "Processing shows page $page...")

            allShowEntities.addAll(
                shows.mapNotNull { watched ->
                    val tmdbId = watched.show?.ids?.tmdb ?: return@mapNotNull null
                    WatchStatusEntity(
                        key = "${ContentItem.ContentType.TV_SHOW.name}_$tmdbId",
                        tmdbId = tmdbId,
                        type = ContentItem.ContentType.TV_SHOW.name,
                        progress = 1.0,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            )

            if (shows.size < limit) break // Last page
            page++
        }

        // Phase 3: Save
        val total = allMovieEntities.size + allShowEntities.size
        onProgress(0.85f, "Saving $total items...")
        (allMovieEntities + allShowEntities).chunked(200).forEach { batch ->
            watchStatusRepository.upsertAll(batch)
        }

        onProgress(1.0f, "Sync complete! ($total items)")
    }
}
