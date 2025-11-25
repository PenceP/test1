package com.test1.tv.data.repository

import android.util.Log
import com.test1.tv.BuildConfig
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.remote.api.OMDbApiService
import com.test1.tv.data.remote.api.TMDBApiService
import com.test1.tv.data.remote.api.TraktApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class ContentRepository(
    private val traktApiService: TraktApiService,
    private val tmdbApiService: TMDBApiService,
    private val omdbApiService: com.test1.tv.data.remote.api.OMDbApiService,
    private val cacheRepository: CacheRepository
) {

    companion object {
        private const val TAG = "ContentRepository"
        const val CATEGORY_TRENDING_MOVIES = "TRENDING_MOVIES"
        const val CATEGORY_POPULAR_MOVIES = "POPULAR_MOVIES"
        const val CATEGORY_TRENDING_SHOWS = "TRENDING_SHOWS"
        const val CATEGORY_POPULAR_SHOWS = "POPULAR_SHOWS"
        const val CATEGORY_CONTINUE_WATCHING = "CONTINUE_WATCHING"
        private const val DEFAULT_PAGE_SIZE = 20
    }

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightRefresh = ConcurrentHashMap<String, Boolean>()

    suspend fun getTrendingMovies(forceRefresh: Boolean = false): Result<List<ContentItem>> {
        return getTrendingMoviesPage(1, DEFAULT_PAGE_SIZE, forceRefresh)
    }

    suspend fun getPopularMovies(forceRefresh: Boolean = false): Result<List<ContentItem>> {
        return getPopularMoviesPage(1, DEFAULT_PAGE_SIZE, forceRefresh)
    }

    suspend fun getTrendingShows(forceRefresh: Boolean = false): Result<List<ContentItem>> {
        return getTrendingShowsPage(1, DEFAULT_PAGE_SIZE, forceRefresh)
    }

    suspend fun getPopularShows(forceRefresh: Boolean = false): Result<List<ContentItem>> {
        return getPopularShowsPage(1, DEFAULT_PAGE_SIZE, forceRefresh)
    }

    suspend fun getTrendingMoviesPage(
        page: Int,
        pageSize: Int,
        forceRefresh: Boolean = false
    ): Result<List<ContentItem>> {
        return getPagedContent(
            category = CATEGORY_TRENDING_MOVIES,
            page = page,
            pageSize = pageSize,
            forceRefresh = forceRefresh
        ) {
            fetchTrendingMoviesFromApis(page, pageSize)
        }
    }

    suspend fun getPopularMoviesPage(
        page: Int,
        pageSize: Int,
        forceRefresh: Boolean = false
    ): Result<List<ContentItem>> {
        return getPagedContent(
            category = CATEGORY_POPULAR_MOVIES,
            page = page,
            pageSize = pageSize,
            forceRefresh = forceRefresh
        ) {
            fetchPopularMoviesFromApis(page, pageSize)
        }
    }

    suspend fun getTrendingShowsPage(
        page: Int,
        pageSize: Int,
        forceRefresh: Boolean = false
    ): Result<List<ContentItem>> {
        return getPagedContent(
            category = CATEGORY_TRENDING_SHOWS,
            page = page,
            pageSize = pageSize,
            forceRefresh = forceRefresh
        ) {
            fetchTrendingShowsFromApis(page, pageSize)
        }
    }

    suspend fun getPopularShowsPage(
        page: Int,
        pageSize: Int,
        forceRefresh: Boolean = false
    ): Result<List<ContentItem>> {
        return getPagedContent(
            category = CATEGORY_POPULAR_SHOWS,
            page = page,
            pageSize = pageSize,
            forceRefresh = forceRefresh
        ) {
            fetchPopularShowsFromApis(page, pageSize)
        }
    }

    suspend fun prefetchCategoryPage(category: String, page: Int, pageSize: Int) {
        when (category) {
            CATEGORY_TRENDING_MOVIES -> getTrendingMoviesPage(page, pageSize, forceRefresh = false)
            CATEGORY_POPULAR_MOVIES -> getPopularMoviesPage(page, pageSize, forceRefresh = false)
            CATEGORY_TRENDING_SHOWS -> getTrendingShowsPage(page, pageSize, forceRefresh = false)
            CATEGORY_POPULAR_SHOWS -> getPopularShowsPage(page, pageSize, forceRefresh = false)
            CATEGORY_CONTINUE_WATCHING -> getTrendingShowsPage(page, pageSize, forceRefresh = false)
        }
    }

    private suspend fun getPagedContent(
        category: String,
        page: Int,
        pageSize: Int,
        forceRefresh: Boolean,
        fetcher: suspend () -> List<ContentItem>
    ): Result<List<ContentItem>> = withContext(Dispatchers.IO) {
        try {
            val cachedPage = cacheRepository.getCachedPage(category, page, pageSize)
            val cacheFresh = cacheRepository.isCacheFresh(category)

            // Prioritize showing cached data immediately (even if stale)
            // Only fetch if: forced refresh, no cache, or cache has too few items
            val hasEnoughCachedItems = cachedPage.size >= pageSize
            val shouldUseCacheImmediately = !forceRefresh && hasEnoughCachedItems

            if (shouldUseCacheImmediately) {
                Log.d(
                    TAG,
                    "Serving cached page $page for $category (${cachedPage.size} items), fresh=$cacheFresh"
                )

                // Trigger background refresh if cache is stale
                if (!cacheFresh && page == 1) {
                    triggerBackgroundRefresh(category, page, pageSize, fetcher)
                }

                return@withContext Result.success(cachedPage)
            }

            // Only fetch synchronously if we have no/insufficient cache or forced
            Log.d(
                TAG,
                "Fetching page $page for $category (force=$forceRefresh, cacheFresh=$cacheFresh, cachedItems=${cachedPage.size})"
            )
            val freshData = fetcher()
            if (freshData.isNotEmpty()) {
                cacheRepository.cacheContentPage(category, freshData, page, pageSize)
                Log.d(TAG, "Cached page $page for $category (${freshData.size} items)")
                Result.success(freshData)
            } else if (cachedPage.isNotEmpty()) {
                Log.d(TAG, "Using cached page $page for $category because API returned empty list")
                Result.success(cachedPage)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching $category page $page", e)
            val fallback = cacheRepository.getCachedPage(category, page, pageSize)
            if (fallback.isNotEmpty()) {
                Result.success(fallback)
            } else {
                Result.failure(e)
            }
        }
    }

    private fun triggerBackgroundRefresh(
        category: String,
        page: Int,
        pageSize: Int,
        fetcher: suspend () -> List<ContentItem>
    ) {
        if (inFlightRefresh.putIfAbsent(category, true) == true) return

        refreshScope.launch {
            try {
                Log.d(TAG, "Background refresh for stale $category page $page")
                val freshData = fetcher()
                if (freshData.isNotEmpty()) {
                    cacheRepository.cacheContentPage(category, freshData, page, pageSize)
                    Log.d(TAG, "Background refreshed cache for $category (${freshData.size} items)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background refresh failed for $category", e)
            } finally {
                inFlightRefresh.remove(category)
            }
        }
    }

    private suspend fun fetchTrendingMoviesFromApis(
        page: Int,
        pageSize: Int
    ): List<ContentItem> = withContext(Dispatchers.IO) {
        val traktMovies = traktApiService.getTrendingMovies(
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            limit = pageSize,
            page = page
        )

        traktMovies.mapNotNull { traktMovie ->
            val tmdbId = traktMovie.movie.ids.tmdb ?: return@mapNotNull null

            async {
                try {
                    val tmdbDetails = tmdbApiService.getMovieDetails(
                        movieId = tmdbId,
                        apiKey = BuildConfig.TMDB_API_KEY,
                        appendToResponse = "images,external_ids,credits"
                    )
                    val omdbRatings = fetchOmdbRatings(traktMovie.movie.ids.imdb)

                    ContentItem(
                        id = tmdbId,
                        tmdbId = tmdbId,
                        title = tmdbDetails.title,
                        overview = tmdbDetails.overview,
                        posterUrl = tmdbDetails.getPosterUrl(),
                        backdropUrl = tmdbDetails.getBackdropUrl(),
                        logoUrl = tmdbDetails.getLogoUrl(),
                        year = tmdbDetails.getYear(),
                        rating = tmdbDetails.voteAverage,
                        ratingPercentage = tmdbDetails.getRatingPercentage(),
                        genres = tmdbDetails.genres?.joinToString(", ") { it.name },
                        type = ContentItem.ContentType.MOVIE,
                        runtime = formatRuntime(tmdbDetails.runtime),
                        cast = tmdbDetails.getCastNames(),
                        certification = tmdbDetails.getCertification(),
                        imdbRating = omdbRatings?.imdb,
                        rottenTomatoesRating = omdbRatings?.rottenTomatoes,
                        traktRating = traktMovie.movie.rating
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching TMDB details for movie $tmdbId", e)
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun fetchPopularMoviesFromApis(
        page: Int,
        pageSize: Int
    ): List<ContentItem> = withContext(Dispatchers.IO) {
        val traktMovies = traktApiService.getPopularMovies(
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            limit = pageSize,
            page = page
        )

        traktMovies.mapNotNull { traktMovie ->
            val tmdbId = traktMovie.ids.tmdb ?: return@mapNotNull null

            async {
                try {
                    val tmdbDetails = tmdbApiService.getMovieDetails(
                        movieId = tmdbId,
<<<<<<< HEAD
                        apiKey = BuildConfig.TMDB_API_KEY
=======
                        apiKey = BuildConfig.TMDB_API_KEY,
                        appendToResponse = "images,external_ids,credits"
>>>>>>> e48ec6a (Add ActorDetailsActivity and ActorDetailsFragment for displaying actor information. Update AndroidManifest.xml to include new activity. Enhance ContentRepository and TMDBApiService for fetching actor details and credits. Implement UI layout for actor details with dynamic content loading.)
                    )
                    val omdbRatings = fetchOmdbRatings(traktMovie.ids.imdb)

                    ContentItem(
                        id = tmdbId,
                        tmdbId = tmdbId,
                        title = tmdbDetails.title,
                        overview = tmdbDetails.overview,
                        posterUrl = tmdbDetails.getPosterUrl(),
                        backdropUrl = tmdbDetails.getBackdropUrl(),
                        logoUrl = tmdbDetails.getLogoUrl(),
                        year = tmdbDetails.getYear(),
                        rating = tmdbDetails.voteAverage,
                        ratingPercentage = tmdbDetails.getRatingPercentage(),
                        genres = tmdbDetails.genres?.joinToString(", ") { it.name },
                        type = ContentItem.ContentType.MOVIE,
                        runtime = formatRuntime(tmdbDetails.runtime),
                        cast = tmdbDetails.getCastNames(),
                        certification = tmdbDetails.getCertification(),
                        imdbRating = omdbRatings?.imdb,
                        rottenTomatoesRating = omdbRatings?.rottenTomatoes,
                        traktRating = traktMovie.rating
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching TMDB details for movie $tmdbId", e)
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun fetchTrendingShowsFromApis(
        page: Int,
        pageSize: Int
    ): List<ContentItem> = withContext(Dispatchers.IO) {
        val traktShows = traktApiService.getTrendingShows(
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            limit = pageSize,
            page = page
        )

        traktShows.mapNotNull { traktShow ->
            val tmdbId = traktShow.show.ids.tmdb ?: return@mapNotNull null

            async {
                try {
                    val tmdbDetails = tmdbApiService.getShowDetails(
                        showId = tmdbId,
                        apiKey = BuildConfig.TMDB_API_KEY,
                        appendToResponse = "images,external_ids,credits"
                    )
                    val omdbRatings = fetchOmdbRatings(traktShow.show.ids.imdb)

                    ContentItem(
                        id = tmdbId,
                        tmdbId = tmdbId,
                        title = tmdbDetails.name,
                        overview = tmdbDetails.overview,
                        posterUrl = tmdbDetails.getPosterUrl(),
                        backdropUrl = tmdbDetails.getBackdropUrl(),
                        logoUrl = tmdbDetails.getLogoUrl(),
                        year = tmdbDetails.getYear(),
                        rating = tmdbDetails.voteAverage,
                        ratingPercentage = tmdbDetails.getRatingPercentage(),
                        genres = tmdbDetails.genres?.joinToString(", ") { it.name },
                        type = ContentItem.ContentType.TV_SHOW,
                        runtime = formatRuntime(tmdbDetails.episodeRunTime?.firstOrNull()),
                        cast = tmdbDetails.getCastNames(),
                        certification = tmdbDetails.getCertification(),
                        imdbRating = omdbRatings?.imdb,
                        rottenTomatoesRating = omdbRatings?.rottenTomatoes,
                        traktRating = traktShow.show.rating
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching TMDB details for show $tmdbId", e)
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun fetchPopularShowsFromApis(
        page: Int,
        pageSize: Int
    ): List<ContentItem> = withContext(Dispatchers.IO) {
        val traktShows = traktApiService.getPopularShows(
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            limit = pageSize,
            page = page
        )

        traktShows.mapNotNull { traktShow ->
            val tmdbId = traktShow.ids.tmdb ?: return@mapNotNull null

            async {
                try {
                    val tmdbDetails = tmdbApiService.getShowDetails(
                        showId = tmdbId,
<<<<<<< HEAD
                        apiKey = BuildConfig.TMDB_API_KEY
=======
                        apiKey = BuildConfig.TMDB_API_KEY,
                        appendToResponse = "images,external_ids,credits"
>>>>>>> e48ec6a (Add ActorDetailsActivity and ActorDetailsFragment for displaying actor information. Update AndroidManifest.xml to include new activity. Enhance ContentRepository and TMDBApiService for fetching actor details and credits. Implement UI layout for actor details with dynamic content loading.)
                    )
                    val omdbRatings = fetchOmdbRatings(traktShow.ids.imdb)

                    ContentItem(
                        id = tmdbId,
                        tmdbId = tmdbId,
                        title = tmdbDetails.name,
                        overview = tmdbDetails.overview,
                        posterUrl = tmdbDetails.getPosterUrl(),
                        backdropUrl = tmdbDetails.getBackdropUrl(),
                        logoUrl = tmdbDetails.getLogoUrl(),
                        year = tmdbDetails.getYear(),
                        rating = tmdbDetails.voteAverage,
                        ratingPercentage = tmdbDetails.getRatingPercentage(),
                        genres = tmdbDetails.genres?.joinToString(", ") { it.name },
                        type = ContentItem.ContentType.TV_SHOW,
                        runtime = formatRuntime(tmdbDetails.episodeRunTime?.firstOrNull()),
                        cast = tmdbDetails.getCastNames(),
                        certification = tmdbDetails.getCertification(),
                        imdbRating = omdbRatings?.imdb,
                        rottenTomatoesRating = omdbRatings?.rottenTomatoes,
                        traktRating = traktShow.rating
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching TMDB details for show $tmdbId", e)
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun cleanupCache() {
        cacheRepository.cleanupOldCache()
    }

    private fun formatRuntime(minutes: Int?): String? {
        if (minutes == null || minutes <= 0) return null

        return if (minutes >= 60) {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            "${hours}h ${remainingMinutes}m"
        } else {
            "${minutes}m"
        }
    }

    private suspend fun fetchOmdbRatings(imdbId: String?): OmdbRatings? {
        // OMDb ratings disabled for performance - not needed on home page
        return null

        /* Original implementation - commented out for performance
        if (imdbId.isNullOrBlank()) return null
        return try {
            val response = omdbApiService.getTitleDetails(
                imdbId = imdbId,
                apiKey = BuildConfig.OMDB_API_KEY
            )
            if (response.response.equals("False", true)) {
                null
            } else {
                val imdb = response.imdbRating?.takeIf { it != "N/A" }
                val rotten = response.ratings
                    ?.firstOrNull { it.source.equals("Rotten Tomatoes", ignoreCase = true) }
                    ?.value
                    ?.takeIf { it != "N/A" }
                OmdbRatings(imdb = imdb, rottenTomatoes = rotten)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching OMDb ratings for $imdbId", e)
            null
        }
        */
    }
}

data class OmdbRatings(
    val imdb: String?,
    val rottenTomatoes: String?
)
