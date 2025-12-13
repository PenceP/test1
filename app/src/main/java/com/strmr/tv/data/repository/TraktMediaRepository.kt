package com.strmr.tv.data.repository

import com.strmr.tv.BuildConfig
import com.strmr.tv.data.local.dao.TraktUserItemDao
import com.strmr.tv.data.local.entity.TraktUserItem
import com.strmr.tv.data.model.ContentItem
import com.strmr.tv.data.model.trakt.TraktMediaList
import com.strmr.tv.data.remote.api.TMDBApiService
import com.strmr.tv.data.repository.CacheRepository
import com.strmr.tv.data.repository.WatchStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktMediaRepository @Inject constructor(
    private val traktUserItemDao: TraktUserItemDao,
    private val tmdbApiService: TMDBApiService,
    private val cacheRepository: CacheRepository,
    private val watchStatusRepository: WatchStatusRepository? = null,
    private val traktSyncRepository: TraktSyncRepository
) {

    suspend fun getMediaList(
        category: TraktMediaList,
        forceRefresh: Boolean = false
    ): Result<List<ContentItem>> = withContext(Dispatchers.IO) {
        val cached = cacheRepository.getCachedContent(category.cacheCategory)
        if (!forceRefresh && !cached.isNullOrEmpty()) {
            return@withContext Result.success(cached)
        }

        var traktItems = traktUserItemDao.getItems(category.listType, category.itemType)

        // If database is empty, trigger a sync first
        if (traktItems.isEmpty()) {
            traktSyncRepository.syncAll()
            traktItems = traktUserItemDao.getItems(category.listType, category.itemType)
        }

        val content = buildContentItems(traktItems, category)

        if (content.isNotEmpty()) {
            cacheRepository.cacheContent(category.cacheCategory, content)
        }

        Result.success(content)
    }

    private suspend fun buildContentItems(
        items: List<TraktUserItem>,
        category: TraktMediaList
    ): List<ContentItem> = coroutineScope {
        val limit = items.size.coerceAtMost(MAX_ITEMS)
        items.take(limit).mapNotNull { traktItem ->
            val tmdbId = traktItem.tmdbId ?: return@mapNotNull null
            async {
                when (category.itemType) {
                    "MOVIE" -> fetchMovieContent(tmdbId, traktItem)
                    "SHOW" -> fetchShowContent(tmdbId, traktItem)
                    else -> null
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun fetchMovieContent(
        tmdbId: Int,
        traktItem: TraktUserItem
    ): ContentItem? {
        return runCatching {
            tmdbApiService.getMovieDetails(
                movieId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY,
                appendToResponse = "images,credits,external_ids"
            )
        }.getOrNull()?.let { details ->
            ContentItem(
                id = tmdbId,
                tmdbId = tmdbId,
                imdbId = details.imdbId,
                title = details.title ?: traktItem.title.orEmpty(),
                overview = details.overview ?: traktItem.title,
                posterUrl = details.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                backdropUrl = details.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                logoUrl = details.images?.logos?.firstOrNull()?.filePath?.let { "https://image.tmdb.org/t/p/w500$it" },
                year = details.releaseDate?.take(4) ?: traktItem.year,
                rating = details.voteAverage,
                ratingPercentage = details.getRatingPercentage(),
                genres = details.genres?.joinToString(",") { it.name ?: "" },
                type = ContentItem.ContentType.MOVIE,
                runtime = details.runtime?.toString(),
                cast = details.credits?.cast?.joinToString(", ") { it.name ?: "" },
                certification = details.getCertification(),
                imdbRating = details.imdbId,
                rottenTomatoesRating = null,
                traktRating = null,
                watchProgress = watchStatusRepository?.getProgress(tmdbId, ContentItem.ContentType.MOVIE)
            )
        }
    }

    private suspend fun fetchShowContent(
        tmdbId: Int,
        traktItem: TraktUserItem
    ): ContentItem? {
        val showDetails = runCatching {
            tmdbApiService.getShowDetails(
                showId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY,
                appendToResponse = "images,external_ids,credits"
            )
        }.getOrNull() ?: return null

        val genres = showDetails.genres?.joinToString(",") { it.name ?: "" }
        return ContentItem(
            id = tmdbId,
            tmdbId = tmdbId,
            imdbId = showDetails.externalIds?.imdbId,
            title = showDetails.name ?: traktItem.title.orEmpty(),
            overview = showDetails.overview ?: traktItem.title,
            posterUrl = showDetails.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            backdropUrl = showDetails.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
            logoUrl = showDetails.images?.logos?.firstOrNull()?.filePath?.let { "https://image.tmdb.org/t/p/w500$it" },
            year = showDetails.firstAirDate?.take(4) ?: traktItem.year,
            rating = showDetails.voteAverage,
            ratingPercentage = showDetails.getRatingPercentage(),
            genres = genres,
            type = ContentItem.ContentType.TV_SHOW,
            runtime = showDetails.episodeRunTime?.firstOrNull()?.toString(),
            cast = showDetails.credits?.cast?.joinToString(", ") { it.name ?: "" },
            certification = showDetails.getCertification(),
            imdbRating = showDetails.externalIds?.imdbId,
            rottenTomatoesRating = null,
            traktRating = null,
            watchProgress = watchStatusRepository?.getProgress(tmdbId, ContentItem.ContentType.TV_SHOW)
        )
    }

    companion object {
        private const val MAX_ITEMS = 40
    }
}
