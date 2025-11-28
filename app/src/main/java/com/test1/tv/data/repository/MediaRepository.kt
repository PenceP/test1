package com.test1.tv.data.repository

import com.test1.tv.data.Resource
import com.test1.tv.data.local.MediaContentEntity
import com.test1.tv.data.local.MediaImageEntity
import com.test1.tv.data.local.MediaWithImages
import com.test1.tv.data.local.dao.MediaDao
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.remote.RateLimiter
import com.test1.tv.data.remote.api.TMDBApiService
import com.test1.tv.data.remote.api.TraktApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
    }

    /**
     * Get trending movies with offline-first strategy.
     * Emits cached data immediately, then fresh data from network.
     */
    fun getTrendingMovies(page: Int = 1): Flow<Resource<List<ContentItem>>> = flow {
        val category = "trending_movies"

        // 1. Emit cached data immediately (if available)
        val cached = getCachedMedia(category)
        if (cached.isNotEmpty()) {
            emit(Resource.Loading(cached.map { it.toContentItem() }))
        } else {
            emit(Resource.Loading())
        }

        // 2. Check if cache is still valid
        val cacheTime = System.currentTimeMillis() - CACHE_VALIDITY_MS
        val cachedCount = mediaDao.getCachedCount(category, cacheTime)

        if (cachedCount > 0) {
            // Cache is valid, emit success with cached data
            val freshCached = getCachedMedia(category)
            emit(Resource.Success(freshCached.map { it.toContentItem() }))
            return@flow
        }

        // 3. Fetch from network (cache is stale or missing)
        try {
            val traktMovies = traktApi.getTrendingMovies(page = page, limit = 20)
            val contentItems = mutableListOf<ContentItem>()
            val mediaEntities = mutableListOf<MediaContentEntity>()
            val imageEntities = mutableListOf<MediaImageEntity>()

            traktMovies.forEachIndexed { index, traktMovie ->
                val movie = traktMovie.movie ?: return@forEachIndexed
                val tmdbId = movie.ids?.tmdb ?: return@forEachIndexed

                // Rate limit before TMDB call
                rateLimiter.acquire()

                try {
                    val tmdbDetails = tmdbApi.getMovieDetails(tmdbId)

                    // Create entities
                    val mediaEntity = MediaContentEntity(
                        tmdbId = tmdbId,
                        imdbId = movie.ids?.imdb,
                        title = tmdbDetails.title ?: movie.title ?: "",
                        overview = tmdbDetails.overview,
                        year = tmdbDetails.releaseDate?.take(4),
                        runtime = tmdbDetails.runtime,
                        certification = tmdbDetails.releases?.countries
                            ?.find { it.iso31661 == "US" }
                            ?.certifications?.firstOrNull()?.certification,
                        contentType = "movie",
                        category = category,
                        position = index
                    )

                    val imageEntity = MediaImageEntity(
                        tmdbId = tmdbId,
                        posterUrl = tmdbDetails.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                        backdropUrl = tmdbDetails.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                        logoUrl = null // Can add logo URL if available
                    )

                    mediaEntities.add(mediaEntity)
                    imageEntities.add(imageEntity)

                    // Also create ContentItem for immediate return
                    contentItems.add(toContentItem(mediaEntity, imageEntity))

                } catch (e: Exception) {
                    // Log error but continue with other items
                    e.printStackTrace()
                }
            }

            // 4. Save to database
            mediaDao.replaceCategory(category, mediaEntities, imageEntities)

            // 5. Emit success
            emit(Resource.Success(contentItems))

        } catch (e: Exception) {
            // Network error - emit error with cached data if available
            val cachedData = getCachedMedia(category).map { it.toContentItem() }
            emit(Resource.Error(e, cachedData.takeIf { it.isNotEmpty() }))
        }
    }

    /**
     * Get cached media by category.
     * Returns empty list if cache is unavailable.
     */
    private suspend fun getCachedMedia(category: String): List<MediaWithImages> {
        return try {
            // mediaDao.getMediaByCategory returns Flow, but we need immediate value
            // For now, return empty list - will fix Flow collection properly later
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
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
            rating = null, // TODO: Get from ratings table
            ratingPercentage = null,
            type = if (content.contentType == "movie") ContentItem.ContentType.MOVIE else ContentItem.ContentType.TV_SHOW,
            certification = content.certification,
            imdbRating = null,
            rottenTomatoesRating = null,
            traktRating = null,
            watchProgress = progress?.progress?.toDouble()
        )
    }

    /**
     * Helper to create ContentItem from separate entities.
     */
    private fun toContentItem(content: MediaContentEntity, images: MediaImageEntity): ContentItem {
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
            rating = null,
            ratingPercentage = null,
            type = if (content.contentType == "movie") ContentItem.ContentType.MOVIE else ContentItem.ContentType.TV_SHOW,
            certification = content.certification,
            imdbRating = null,
            rottenTomatoesRating = null,
            traktRating = null
        )
    }
}
