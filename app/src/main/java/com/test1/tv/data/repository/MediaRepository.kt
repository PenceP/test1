package com.test1.tv.data.repository

import com.test1.tv.BuildConfig
import com.test1.tv.data.Resource
import com.test1.tv.data.local.MediaContentEntity
import com.test1.tv.data.local.MediaImageEntity
import com.test1.tv.data.local.MediaRatingEntity
import com.test1.tv.data.local.MediaWithImages
import com.test1.tv.data.local.dao.MediaDao
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.remote.RateLimiter
import com.test1.tv.data.remote.api.TMDBApiService
import com.test1.tv.data.remote.api.TraktApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaRepository using new normalized database schema.
 * Replaces old ContentRepository's monolithic approach.
 *
 * Features:
 * - Rate-limited API calls
 * - Offline-first with cache
 * - Normalized database (separate tables for content/images/ratings)
 * - Flow-based reactive data
 */
@Singleton
class MediaRepository @Inject constructor(
    private val mediaDao: MediaDao,
    private val traktApi: TraktApiService,
    private val tmdbApi: TMDBApiService,
    private val rateLimiter: RateLimiter
) {

    companion object {
        private const val CACHE_VALIDITY_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val DEFAULT_PAGE_SIZE = 20
    }

    /**
     * Get trending movies with offline-first strategy and infinite scrolling support.
     *
     * - Page 1: Checks cache validity, returns cached data if fresh
     * - Page > 1: Always fetches from network, appends to existing data
     * - Page size: 20 items (trigger next page when <10 items remaining)
     *
     * @param page Page number (defaults to 1)
     * @param pageSize Items per page (defaults to 20)
     */
    fun getTrendingMovies(page: Int = 1, pageSize: Int = DEFAULT_PAGE_SIZE): Flow<Resource<List<ContentItem>>> = flow {
        val category = "trending_movies"

        // 1. Emit cached data immediately (if available and page 1)
        if (page == 1) {
            val cached = getCachedMedia(category)
            if (cached.isNotEmpty()) {
                emit(Resource.Loading(cached.map { it.toContentItem() }))
            } else {
                emit(Resource.Loading())
            }

            // 2. Check if cache is still valid for page 1 only
            val cacheTime = System.currentTimeMillis() - CACHE_VALIDITY_MS
            val cachedCount = mediaDao.getCachedCount(category, cacheTime)

            if (cachedCount > 0) {
                // Cache is valid, emit success with cached data
                val freshCached = getCachedMedia(category)
                emit(Resource.Success(freshCached.map { it.toContentItem() }))
                return@flow
            }
        } else {
            // For page > 1, always fetch from network (no cache)
            emit(Resource.Loading())
        }

        // 3. Fetch from network
        try {
            val resource = fetchMovies(
                category = category,
                page = page,
                pageSize = pageSize
            )
            emit(resource)

        } catch (e: Exception) {
            // Network error - emit error with cached data if available
            val cachedData = if (page == 1) getCachedMedia(category).map { it.toContentItem() } else emptyList()
            emit(Resource.Error(e, cachedData.takeIf { it.isNotEmpty() }))
        }
    }.flowOn(Dispatchers.IO)

    fun getPopularMovies(page: Int = 1, pageSize: Int = DEFAULT_PAGE_SIZE): Flow<Resource<List<ContentItem>>> = flow {
        val category = "popular_movies"

        if (page == 1) {
            emit(Resource.Loading(getCachedMedia(category).map { it.toContentItem() }.ifEmpty { null }))

            val cacheTime = System.currentTimeMillis() - CACHE_VALIDITY_MS
            val cachedCount = mediaDao.getCachedCount(category, cacheTime)
            if (cachedCount > 0) {
                val cached = getCachedMedia(category).map { it.toContentItem() }
                emit(Resource.Success(cached))
                return@flow
            }
        } else {
            emit(Resource.Loading())
        }

        try {
            val resource = fetchMovies(
                category = category,
                page = page,
                pageSize = pageSize,
                popular = true
            )
            emit(resource)
        } catch (e: Exception) {
            val cachedData = if (page == 1) getCachedMedia(category).map { it.toContentItem() } else emptyList()
            emit(Resource.Error(e, cachedData.takeIf { it.isNotEmpty() }))
        }
    }.flowOn(Dispatchers.IO)

    fun getTrendingShows(page: Int = 1, pageSize: Int = DEFAULT_PAGE_SIZE): Flow<Resource<List<ContentItem>>> = flow {
        val category = "trending_shows"

        if (page == 1) {
            emit(Resource.Loading(getCachedMedia(category).map { it.toContentItem() }.ifEmpty { null }))

            val cacheTime = System.currentTimeMillis() - CACHE_VALIDITY_MS
            val cachedCount = mediaDao.getCachedCount(category, cacheTime)
            if (cachedCount > 0) {
                val cached = getCachedMedia(category).map { it.toContentItem() }
                emit(Resource.Success(cached))
                return@flow
            }
        } else {
            emit(Resource.Loading())
        }

        try {
            val resource = fetchShows(
                category = category,
                page = page,
                pageSize = pageSize
            )
            emit(resource)
        } catch (e: Exception) {
            val cachedData = if (page == 1) getCachedMedia(category).map { it.toContentItem() } else emptyList()
            emit(Resource.Error(e, cachedData.takeIf { it.isNotEmpty() }))
        }
    }.flowOn(Dispatchers.IO)

    fun getPopularShows(page: Int = 1, pageSize: Int = DEFAULT_PAGE_SIZE): Flow<Resource<List<ContentItem>>> = flow {
        val category = "popular_shows"

        if (page == 1) {
            emit(Resource.Loading(getCachedMedia(category).map { it.toContentItem() }.ifEmpty { null }))

            val cacheTime = System.currentTimeMillis() - CACHE_VALIDITY_MS
            val cachedCount = mediaDao.getCachedCount(category, cacheTime)
            if (cachedCount > 0) {
                val cached = getCachedMedia(category).map { it.toContentItem() }
                emit(Resource.Success(cached))
                return@flow
            }
        } else {
            emit(Resource.Loading())
        }

        try {
            val resource = fetchShows(
                category = category,
                page = page,
                pageSize = pageSize,
                popular = true
            )
            emit(resource)
        } catch (e: Exception) {
            val cachedData = if (page == 1) getCachedMedia(category).map { it.toContentItem() } else emptyList()
            emit(Resource.Error(e, cachedData.takeIf { it.isNotEmpty() }))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get cached media by category.
     * Returns empty list if cache is unavailable.
     */
    private suspend fun getCachedMedia(category: String): List<MediaWithImages> {
        return try {
            mediaDao.getMediaByCategory(category).first()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchMovies(
        category: String,
        page: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        popular: Boolean = false
    ): Resource.Success<List<ContentItem>> {
        val traktMovies: List<com.test1.tv.data.model.trakt.TraktMovie> =
            if (popular) {
                traktApi.getPopularMovies(
                    clientId = BuildConfig.TRAKT_CLIENT_ID,
                    page = page,
                    limit = pageSize
                )
            } else {
                traktApi.getTrendingMovies(
                    clientId = BuildConfig.TRAKT_CLIENT_ID,
                    page = page,
                    limit = pageSize
                ).mapNotNull { it.movie }
            }

        // Calculate position offset for pagination (page > 1 appends to existing data)
        val basePosition = if (page > 1) {
            mediaDao.getMaxPosition(category) + 1
        } else {
            0
        }

        val contentItems = mutableListOf<ContentItem>()
        val mediaEntities = mutableListOf<MediaContentEntity>()
        val imageEntities = mutableListOf<MediaImageEntity>()
        val ratingEntities = mutableListOf<MediaRatingEntity>()

        traktMovies.forEachIndexed { index, movie ->
            val tmdbId = movie.ids?.tmdb ?: return@forEachIndexed

            rateLimiter.acquire()
            runCatching {
                val tmdbDetails = tmdbApi.getMovieDetails(
                    movieId = tmdbId,
                    apiKey = BuildConfig.TMDB_API_KEY
                )

                val mediaEntity = MediaContentEntity(
                    tmdbId = tmdbId,
                    imdbId = movie.ids?.imdb,
                    title = tmdbDetails.title ?: movie.title ?: "",
                    overview = tmdbDetails.overview,
                    year = tmdbDetails.releaseDate?.take(4),
                    runtime = tmdbDetails.runtime,
                    certification = tmdbDetails.getCertification(),
                    contentType = "movie",
                    category = category,
                    position = basePosition + index
                )

                val imageEntity = MediaImageEntity(
                    tmdbId = tmdbId,
                    posterUrl = tmdbDetails.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                    backdropUrl = tmdbDetails.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                    logoUrl = tmdbDetails.getLogoUrl()
                )

                val ratingEntity = MediaRatingEntity(
                    tmdbId = tmdbId,
                    tmdbRating = tmdbDetails.voteAverage?.toFloat(),
                    imdbRating = null,
                    traktRating = movie.rating?.toFloat(),
                    rottenTomatoesRating = null
                )

                mediaEntities.add(mediaEntity)
                imageEntities.add(imageEntity)
                ratingEntities.add(ratingEntity)
                contentItems.add(toContentItem(mediaEntity, imageEntity, ratingEntity))
            }
        }

        // Use replace for page 1 (clears old data), append for page > 1 (accumulates)
        if (page == 1) {
            mediaDao.replaceCategory(category, mediaEntities, imageEntities, ratingEntities)
        } else {
            mediaDao.appendToCategory(mediaEntities, imageEntities, ratingEntities)
        }

        return Resource.Success(contentItems)
    }

    private suspend fun fetchShows(
        category: String,
        page: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        popular: Boolean = false
    ): Resource.Success<List<ContentItem>> {
        val traktShows: List<com.test1.tv.data.model.trakt.TraktShow> =
            if (popular) {
                traktApi.getPopularShows(
                    clientId = BuildConfig.TRAKT_CLIENT_ID,
                    page = page,
                    limit = pageSize
                )
            } else {
                traktApi.getTrendingShows(
                    clientId = BuildConfig.TRAKT_CLIENT_ID,
                    page = page,
                    limit = pageSize
                ).mapNotNull { it.show }
            }

        // Calculate position offset for pagination
        val basePosition = if (page > 1) {
            mediaDao.getMaxPosition(category) + 1
        } else {
            0
        }

        val contentItems = mutableListOf<ContentItem>()
        val mediaEntities = mutableListOf<MediaContentEntity>()
        val imageEntities = mutableListOf<MediaImageEntity>()
        val ratingEntities = mutableListOf<MediaRatingEntity>()

        traktShows.forEachIndexed { index, show ->
            val tmdbId = show.ids?.tmdb ?: return@forEachIndexed

            rateLimiter.acquire()
            runCatching {
                val tmdbDetails = tmdbApi.getShowDetails(
                    showId = tmdbId,
                    apiKey = BuildConfig.TMDB_API_KEY
                )

                val mediaEntity = MediaContentEntity(
                    tmdbId = tmdbId,
                    imdbId = show.ids?.imdb,
                    title = tmdbDetails.name ?: show.title ?: "",
                    overview = tmdbDetails.overview,
                    year = tmdbDetails.firstAirDate?.take(4),
                    runtime = tmdbDetails.episodeRunTime?.firstOrNull(),
                    certification = tmdbDetails.getCertification(),
                    contentType = "tv",
                    category = category,
                    position = basePosition + index
                )

                val imageEntity = MediaImageEntity(
                    tmdbId = tmdbId,
                    posterUrl = tmdbDetails.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                    backdropUrl = tmdbDetails.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                    logoUrl = tmdbDetails.getLogoUrl()
                )

                val ratingEntity = MediaRatingEntity(
                    tmdbId = tmdbId,
                    tmdbRating = tmdbDetails.voteAverage?.toFloat(),
                    imdbRating = null,
                    traktRating = show.rating?.toFloat(),
                    rottenTomatoesRating = null
                )

                mediaEntities.add(mediaEntity)
                imageEntities.add(imageEntity)
                ratingEntities.add(ratingEntity)
                contentItems.add(toContentItem(mediaEntity, imageEntity, ratingEntity))
            }
        }

        // Use replace for page 1 (clears old data), append for page > 1 (accumulates)
        if (page == 1) {
            mediaDao.replaceCategory(category, mediaEntities, imageEntities, ratingEntities)
        } else {
            mediaDao.appendToCategory(mediaEntities, imageEntities, ratingEntities)
        }

        return Resource.Success(contentItems)
    }

    /**
     * Convert database entities to ContentItem.
     */
    private fun MediaWithImages.toContentItem(): ContentItem {
        return ContentItem(
            id = content.tmdbId,
            tmdbId = content.tmdbId,
            imdbId = content.imdbId,
            title = content.title,
            overview = content.overview,
            year = content.year,
            runtime = content.runtime?.toString(),
            posterUrl = images?.posterUrl,
            backdropUrl = images?.backdropUrl,
            logoUrl = images?.logoUrl,
            genres = null, // TODO: Add genres table
            cast = null, // TODO: Add cast table
            rating = ratings?.tmdbRating?.toDouble(),
            ratingPercentage = ratings?.tmdbRating?.times(10)?.toInt(),
            type = if (content.contentType == "movie") ContentItem.ContentType.MOVIE else ContentItem.ContentType.TV_SHOW,
            certification = content.certification,
            imdbRating = ratings?.imdbRating?.toString(),
            rottenTomatoesRating = ratings?.rottenTomatoesRating?.toString(),
            traktRating = ratings?.traktRating?.toDouble(),
            watchProgress = progress?.progress?.toDouble()
        )
    }

    /**
     * Helper to create ContentItem from separate entities.
     */
    private fun toContentItem(
        content: MediaContentEntity,
        images: MediaImageEntity,
        ratings: MediaRatingEntity
    ): ContentItem {
        return ContentItem(
            id = content.tmdbId,
            tmdbId = content.tmdbId,
            imdbId = content.imdbId,
            title = content.title,
            overview = content.overview,
            year = content.year,
            runtime = content.runtime?.toString(),
            posterUrl = images.posterUrl,
            backdropUrl = images.backdropUrl,
            logoUrl = images.logoUrl,
            genres = null,
            cast = null,
            rating = ratings.tmdbRating?.toDouble(),
            ratingPercentage = ratings.tmdbRating?.times(10)?.toInt(),
            type = if (content.contentType == "movie") ContentItem.ContentType.MOVIE else ContentItem.ContentType.TV_SHOW,
            certification = content.certification,
            imdbRating = ratings.imdbRating?.toString(),
            rottenTomatoesRating = ratings.rottenTomatoesRating?.toString(),
            traktRating = ratings.traktRating?.toDouble()
        )
    }
}
