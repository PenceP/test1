package com.test1.tv.data.repository

import android.util.Log
import com.test1.tv.BuildConfig
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.remote.api.TMDBApiService
import com.test1.tv.data.remote.api.TraktApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class ContentRepository(
    private val traktApiService: TraktApiService,
    private val tmdbApiService: TMDBApiService,
    private val cacheRepository: CacheRepository
) {

    companion object {
        private const val TAG = "ContentRepository"
        const val CATEGORY_TRENDING_MOVIES = "TRENDING_MOVIES"
        const val CATEGORY_POPULAR_MOVIES = "POPULAR_MOVIES"
        const val CATEGORY_TRENDING_SHOWS = "TRENDING_SHOWS"
        const val CATEGORY_POPULAR_SHOWS = "POPULAR_SHOWS"
    }

    /**
     * Get trending movies with caching
     */
    suspend fun getTrendingMovies(forceRefresh: Boolean = false): Result<List<ContentItem>> {
        return getContentWithCache(
            category = CATEGORY_TRENDING_MOVIES,
            forceRefresh = forceRefresh
        ) {
            fetchTrendingMoviesFromApis()
        }
    }

    /**
     * Get popular movies with caching
     */
    suspend fun getPopularMovies(forceRefresh: Boolean = false): Result<List<ContentItem>> {
        return getContentWithCache(
            category = CATEGORY_POPULAR_MOVIES,
            forceRefresh = forceRefresh
        ) {
            fetchPopularMoviesFromApis()
        }
    }

    /**
     * Get trending shows with caching
     */
    suspend fun getTrendingShows(forceRefresh: Boolean = false): Result<List<ContentItem>> {
        return getContentWithCache(
            category = CATEGORY_TRENDING_SHOWS,
            forceRefresh = forceRefresh
        ) {
            fetchTrendingShowsFromApis()
        }
    }

    /**
     * Get popular shows with caching
     */
    suspend fun getPopularShows(forceRefresh: Boolean = false): Result<List<ContentItem>> {
        return getContentWithCache(
            category = CATEGORY_POPULAR_SHOWS,
            forceRefresh = forceRefresh
        ) {
            fetchPopularShowsFromApis()
        }
    }

    /**
     * Generic method to get content with caching logic
     */
    private suspend fun getContentWithCache(
        category: String,
        forceRefresh: Boolean,
        fetcher: suspend () -> List<ContentItem>
    ): Result<List<ContentItem>> = withContext(Dispatchers.IO) {
        try {
            // Check if cache is fresh and return cached data if available
            if (!forceRefresh && cacheRepository.isCacheFresh(category)) {
                val cachedData = cacheRepository.getCachedContent(category)
                if (cachedData != null && cachedData.isNotEmpty()) {
                    Log.d(TAG, "Returning cached data for $category (${cachedData.size} items)")
                    return@withContext Result.success(cachedData)
                }
            }

            // Fetch fresh data from APIs
            Log.d(TAG, "Fetching fresh data for $category")
            val freshData = fetcher()

            // Cache the fresh data
            if (freshData.isNotEmpty()) {
                cacheRepository.cacheContent(category, freshData)
                Log.d(TAG, "Cached ${freshData.size} items for $category")
            }

            Result.success(freshData)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching $category", e)

            // Try to return stale cache if network fails
            try {
                val staleCache = cacheRepository.getCachedContent(category)
                if (staleCache != null && staleCache.isNotEmpty()) {
                    Log.d(TAG, "Returning stale cache for $category due to error")
                    return@withContext Result.success(staleCache)
                }
            } catch (cacheError: Exception) {
                Log.e(TAG, "Error reading cache for $category", cacheError)
            }

            Result.failure(e)
        }
    }

    /**
     * Fetch trending movies from Trakt and enrich with TMDB data
     */
    private suspend fun fetchTrendingMoviesFromApis(): List<ContentItem> = withContext(Dispatchers.IO) {
        val traktMovies = traktApiService.getTrendingMovies(
            clientId = BuildConfig.TRAKT_CLIENT_ID
        )

        // Fetch TMDB details for each movie in parallel
        traktMovies.mapNotNull { traktMovie ->
            val tmdbId = traktMovie.movie.ids.tmdb ?: return@mapNotNull null

            async {
                try {
                    val tmdbDetails = tmdbApiService.getMovieDetails(
                        movieId = tmdbId,
                        apiKey = BuildConfig.TMDB_API_KEY
                    )

                    ContentItem(
                        id = tmdbId,
                        tmdbId = tmdbId,
                        title = tmdbDetails.title,
                        overview = tmdbDetails.overview,
                        posterUrl = tmdbDetails.getPosterUrl(),
                        backdropUrl = tmdbDetails.getBackdropUrl(),
                        year = tmdbDetails.getYear(),
                        rating = tmdbDetails.voteAverage,
                        ratingPercentage = tmdbDetails.getRatingPercentage(),
                        genres = tmdbDetails.genres?.joinToString(", ") { it.name },
                        type = ContentItem.ContentType.MOVIE,
                        runtime = tmdbDetails.runtime?.let { "${it}m" }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching TMDB details for movie $tmdbId", e)
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Fetch popular movies from Trakt and enrich with TMDB data
     */
    private suspend fun fetchPopularMoviesFromApis(): List<ContentItem> = withContext(Dispatchers.IO) {
        val traktMovies = traktApiService.getPopularMovies(
            clientId = BuildConfig.TRAKT_CLIENT_ID
        )

        // Popular endpoint returns TraktMovie directly (no wrapper)
        traktMovies.mapNotNull { traktMovie ->
            val tmdbId = traktMovie.ids.tmdb ?: return@mapNotNull null

            async {
                try {
                    val tmdbDetails = tmdbApiService.getMovieDetails(
                        movieId = tmdbId,
                        apiKey = BuildConfig.TMDB_API_KEY
                    )

                    ContentItem(
                        id = tmdbId,
                        tmdbId = tmdbId,
                        title = tmdbDetails.title,
                        overview = tmdbDetails.overview,
                        posterUrl = tmdbDetails.getPosterUrl(),
                        backdropUrl = tmdbDetails.getBackdropUrl(),
                        year = tmdbDetails.getYear(),
                        rating = tmdbDetails.voteAverage,
                        ratingPercentage = tmdbDetails.getRatingPercentage(),
                        genres = tmdbDetails.genres?.joinToString(", ") { it.name },
                        type = ContentItem.ContentType.MOVIE,
                        runtime = tmdbDetails.runtime?.let { "${it}m" }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching TMDB details for movie $tmdbId", e)
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Fetch trending shows from Trakt and enrich with TMDB data
     */
    private suspend fun fetchTrendingShowsFromApis(): List<ContentItem> = withContext(Dispatchers.IO) {
        val traktShows = traktApiService.getTrendingShows(
            clientId = BuildConfig.TRAKT_CLIENT_ID
        )

        traktShows.mapNotNull { traktShow ->
            val tmdbId = traktShow.show.ids.tmdb ?: return@mapNotNull null

            async {
                try {
                    val tmdbDetails = tmdbApiService.getShowDetails(
                        showId = tmdbId,
                        apiKey = BuildConfig.TMDB_API_KEY
                    )

                    ContentItem(
                        id = tmdbId,
                        tmdbId = tmdbId,
                        title = tmdbDetails.name,
                        overview = tmdbDetails.overview,
                        posterUrl = tmdbDetails.getPosterUrl(),
                        backdropUrl = tmdbDetails.getBackdropUrl(),
                        year = tmdbDetails.getYear(),
                        rating = tmdbDetails.voteAverage,
                        ratingPercentage = tmdbDetails.getRatingPercentage(),
                        genres = tmdbDetails.genres?.joinToString(", ") { it.name },
                        type = ContentItem.ContentType.TV_SHOW,
                        runtime = tmdbDetails.episodeRunTime?.firstOrNull()?.let { "${it}m" }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching TMDB details for show $tmdbId", e)
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Fetch popular shows from Trakt and enrich with TMDB data
     */
    private suspend fun fetchPopularShowsFromApis(): List<ContentItem> = withContext(Dispatchers.IO) {
        val traktShows = traktApiService.getPopularShows(
            clientId = BuildConfig.TRAKT_CLIENT_ID
        )

        // Popular endpoint returns TraktShow directly (no wrapper)
        traktShows.mapNotNull { traktShow ->
            val tmdbId = traktShow.ids.tmdb ?: return@mapNotNull null

            async {
                try {
                    val tmdbDetails = tmdbApiService.getShowDetails(
                        showId = tmdbId,
                        apiKey = BuildConfig.TMDB_API_KEY
                    )

                    ContentItem(
                        id = tmdbId,
                        tmdbId = tmdbId,
                        title = tmdbDetails.name,
                        overview = tmdbDetails.overview,
                        posterUrl = tmdbDetails.getPosterUrl(),
                        backdropUrl = tmdbDetails.getBackdropUrl(),
                        year = tmdbDetails.getYear(),
                        rating = tmdbDetails.voteAverage,
                        ratingPercentage = tmdbDetails.getRatingPercentage(),
                        genres = tmdbDetails.genres?.joinToString(", ") { it.name },
                        type = ContentItem.ContentType.TV_SHOW,
                        runtime = tmdbDetails.episodeRunTime?.firstOrNull()?.let { "${it}m" }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching TMDB details for show $tmdbId", e)
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Clean up old cache
     */
    suspend fun cleanupCache() {
        cacheRepository.cleanupOldCache()
    }
}
