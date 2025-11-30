package com.test1.tv.data.repository

import com.test1.tv.BuildConfig
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.model.continuewatching.ContinueWatchingItem
import com.test1.tv.data.model.continuewatching.ContinueWatchingMapper
import com.test1.tv.data.model.trakt.RemovePlaybackRequest
import com.test1.tv.data.remote.api.TMDBApiService
import com.test1.tv.data.model.tmdb.TMDBEpisode
import com.test1.tv.data.remote.api.TraktApiService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContinueWatchingRepository @Inject constructor(
    private val traktApiService: TraktApiService,
    private val tmdbApiService: TMDBApiService,
    private val accountRepository: TraktAccountRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun hasAccount(): Boolean = accountRepository.getAccount() != null

    /**
     * Load continue-watching directly from Trakt /sync/playback.
     * Keeps Trakt ordering, drops items >=92% complete.
     */
    suspend fun load(limit: Int = 20, forceRefresh: Boolean = false): List<ContentItem> =
        withContext(ioDispatcher) {
            val account = accountRepository.refreshTokenIfNeeded() ?: return@withContext emptyList()
            val authHeader = accountRepository.buildAuthHeader(account.accessToken)

            val playback = runCatching {
                traktApiService.getPlayback(
                    authHeader = authHeader,
                    clientId = BuildConfig.TRAKT_CLIENT_ID,
                    limit = limit
                )
            }.getOrDefault(emptyList())

            val mapped = playback.mapNotNull(ContinueWatchingMapper::map)
            val deduped = dedupeEpisodes(mapped)
            deduped.mapNotNull { enrichToContentItem(it) }
        }

    /**
     * Remove a single playback entry from Trakt.
     */
    suspend fun removePlayback(playbackId: Long) = withContext(ioDispatcher) {
        val account = accountRepository.refreshTokenIfNeeded() ?: return@withContext
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)
        traktApiService.removePlayback(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            body = RemovePlaybackRequest(id = playbackId)
        )
    }

    /**
     * Remove all playback entries sequentially (rate-limit friendly).
     */
    suspend fun clearAll(
        items: List<ContinueWatchingItem>,
        perItemDelayMillis: Long = 1050L
    ): Boolean = withContext(ioDispatcher) {
        var allOk = true
        items.forEach { item ->
            runCatching { removePlayback(item.playbackId) }
                .onFailure { allOk = false }
            delay(perItemDelayMillis)
        }
        allOk
    }

    /**
     * Remove duplicate episodes per show, keeping the most recent (Trakt is already sorted by recency).
     */
    private fun dedupeEpisodes(items: List<ContinueWatchingItem>): List<ContinueWatchingItem> {
        val seenShows = mutableSetOf<Long>()
        val result = mutableListOf<ContinueWatchingItem>()
        items.forEach { item ->
            if (item is ContinueWatchingItem.Episode) {
                val key = (item.showTmdbId ?: item.showTraktId)?.toLong()
                if (key != null) {
                    if (seenShows.contains(key)) {
                        return@forEach
                    }
                    seenShows.add(key)
                }
            }
            result.add(item)
        }
        return result
    }

    private suspend fun enrichToContentItem(item: ContinueWatchingItem): ContentItem? {
        return when (item) {
            is ContinueWatchingItem.Movie -> {
                val tmdbId = item.tmdbId ?: return null
                val details = runCatching {
                    tmdbApiService.getMovieDetails(
                        movieId = tmdbId,
                        apiKey = BuildConfig.TMDB_API_KEY,
                        appendToResponse = "images,credits"
                    )
                }.getOrNull() ?: return null

                ContentItem(
                    id = tmdbId,
                    tmdbId = tmdbId,
                    imdbId = details.imdbId,
                    title = details.title ?: item.title,
                    overview = details.overview,
                    posterUrl = details.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                    backdropUrl = details.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                    logoUrl = details.images?.logos?.firstOrNull()?.filePath?.let { "https://image.tmdb.org/t/p/w500$it" },
                    year = details.releaseDate?.take(4) ?: item.year?.toString(),
                    rating = details.voteAverage,
                    ratingPercentage = details.getRatingPercentage(),
                    genres = details.genres?.joinToString(",") { it.name ?: "" },
                    type = ContentItem.ContentType.MOVIE,
                    runtime = details.runtime?.toString(),
                    cast = details.credits?.cast?.joinToString(",") { it.name ?: "" },
                    certification = details.getCertification(),
                    imdbRating = null,
                    rottenTomatoesRating = null,
                    traktRating = null,
                    watchProgress = item.progress.toDouble()
                )
            }
            is ContinueWatchingItem.Episode -> {
                val tmdbId = item.showTmdbId ?: return null
                val showDetails = runCatching {
                    tmdbApiService.getShowDetails(
                        showId = tmdbId,
                        apiKey = BuildConfig.TMDB_API_KEY
                    )
                }.getOrNull() ?: return null

                val episodeDetails: TMDBEpisode? = runCatching {
                    tmdbApiService.getEpisodeDetails(
                        showId = tmdbId,
                        seasonNumber = item.season,
                        episodeNumber = item.episode,
                        apiKey = BuildConfig.TMDB_API_KEY
                    )
                }.getOrNull()

                val showBackdrop = showDetails.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
                val showPoster = showDetails.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                val episodeStill = episodeDetails?.getStillUrl()

                ContentItem(
                    id = tmdbId,
                    tmdbId = tmdbId,
                    imdbId = showDetails.externalIds?.imdbId,
                    title = "${item.showTitle} S${item.season}E${item.episode}",
                    overview = episodeDetails?.overview
                        ?: item.episodeTitle
                        ?: showDetails.overview,
                    posterUrl = episodeStill ?: showPoster,
                    backdropUrl = showBackdrop, // keep hero backdrop on show art
                    logoUrl = showDetails.images?.logos?.firstOrNull()?.filePath?.let { "https://image.tmdb.org/t/p/w500$it" },
                    year = showDetails.firstAirDate?.take(4) ?: item.showYear?.toString(),
                    rating = showDetails.voteAverage,
                    ratingPercentage = showDetails.getRatingPercentage(),
                    genres = buildEpisodeLabel(item, episodeDetails),
                    type = ContentItem.ContentType.TV_SHOW,
                    runtime = episodeDetails?.runtime?.toString()
                        ?: showDetails.episodeRunTime?.firstOrNull()?.toString(),
                    cast = showDetails.credits?.cast?.joinToString(",") { it.name ?: "" },
                    certification = showDetails.getCertification(),
                    imdbRating = null,
                    rottenTomatoesRating = null,
                    traktRating = null,
                    watchProgress = item.progress.toDouble()
                )
            }
        }
    }

    private fun buildEpisodeLabel(item: ContinueWatchingItem.Episode, episodeDetails: TMDBEpisode?): String? {
        val season = item.season.takeIf { it > 0 } ?: return null
        val episode = item.episode.takeIf { it > 0 } ?: return null
        val seasonStr = season.toString().padStart(2, '0')
        val episodeStr = episode.toString().padStart(2, '0')
        val epTitle = episodeDetails?.name ?: item.episodeTitle
        return if (epTitle.isNullOrBlank()) {
            "S${seasonStr}E${episodeStr}"
        } else {
            "S${seasonStr}E${episodeStr} • $epTitle"
        }
    }
}
