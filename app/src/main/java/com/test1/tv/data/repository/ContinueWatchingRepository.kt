package com.test1.tv.data.repository

import com.test1.tv.BuildConfig
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.model.continuewatching.ContinueWatchingItem
import com.test1.tv.data.model.continuewatching.toZonedMillisOrNow
import com.test1.tv.data.model.continuewatching.toZonedMillisOrNull
import com.test1.tv.data.model.tmdb.TMDBEpisode
import com.test1.tv.data.model.trakt.RemovePlaybackRequest
import com.test1.tv.data.model.trakt.TraktHistoryItem
import com.test1.tv.data.model.trakt.TraktPlaybackItem
import com.test1.tv.data.model.trakt.TraktShowProgress
import com.test1.tv.data.remote.api.TMDBApiService
import com.test1.tv.data.remote.api.TraktApiService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
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
     * Hybrid continue watching:
     * 1) load paused movies + episodes from Trakt playback,
     * 2) inflate next episodes for shows that finished or are near completion,
     * 3) backfill new episodes for recently completed shows via history,
     * 4) sort by the most recent timestamp while deduping per-show.
     */
    suspend fun load(limit: Int = 20, forceRefresh: Boolean = false): List<ContentItem> =
        withContext(ioDispatcher) {
            val account = accountRepository.refreshTokenIfNeeded() ?: return@withContext emptyList()
            val authHeader = accountRepository.buildAuthHeader(account.accessToken)

            val playbackEntries = fetchPlaybackEntries(authHeader)
            val processedTraktIds = playbackEntries.mapNotNull {
                when (it) {
                    is ContinueWatchingItem.Episode -> it.showTraktId
                    is ContinueWatchingItem.NextEpisode -> it.showTraktId
                    else -> null
                }
            }.toSet()
            val processedTmdbIds = playbackEntries.mapNotNull {
                when (it) {
                    is ContinueWatchingItem.Episode -> it.showTmdbId
                    is ContinueWatchingItem.NextEpisode -> it.showTmdbId
                    else -> null
                }
            }.toSet()

            val historyEntries = fetchNextUpFromHistory(
                authHeader = authHeader,
                processedTraktIds = processedTraktIds,
                processedTmdbIds = processedTmdbIds
            )

            (playbackEntries + historyEntries)
                .sortedByDescending { it.lastWatchedAtMillis }
                .let { dedupeEpisodes(it) }
                .take(limit)
                .mapNotNull { enrichToContentItem(it) }
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
            val playbackId = item.playbackId ?: return@forEach
            runCatching { removePlayback(playbackId) }
                .onFailure { allOk = false }
            delay(perItemDelayMillis)
        }
        allOk
    }

    private suspend fun fetchPlaybackEntries(authHeader: String): List<ContinueWatchingItem> =
        coroutineScope {
            val movies = runCatching {
                traktApiService.getPlaybackMovies(
                    authHeader = authHeader,
                    clientId = BuildConfig.TRAKT_CLIENT_ID,
                    limit = PLAYBACK_MOVIE_LIMIT
                )
            }.getOrDefault(emptyList())

            val episodes = runCatching {
                traktApiService.getPlaybackEpisodes(
                    authHeader = authHeader,
                    clientId = BuildConfig.TRAKT_CLIENT_ID,
                    limit = PLAYBACK_EPISODE_LIMIT
                )
            }.getOrDefault(emptyList())

            val result = mutableListOf<ContinueWatchingItem>()
            result.addAll(movies.mapNotNull(::mapPlaybackMovie))

            val nextEpisodeDeferreds = episodes.mapNotNull { dto ->
                val progress = normalizeProgress(dto.progress)
                if (progress >= NEAR_COMPLETE_PROGRESS) {
                    async { buildNextEpisodeFromPlayback(dto, authHeader) }
                } else {
                    mapPlaybackEpisode(dto)?.let { result.add(it) }
                    null
                }
            }

            result.addAll(nextEpisodeDeferreds.awaitAll().filterNotNull())
            return@coroutineScope result
        }

    private fun mapPlaybackMovie(dto: TraktPlaybackItem): ContinueWatchingItem.Movie? {
        if (dto.type != "movie") return null
        val movie = dto.movie ?: return null
        val tmdbId = movie.ids?.tmdb ?: return null
        val title = movie.title ?: return null
        val progress = normalizeProgress(dto.progress)
        return ContinueWatchingItem.Movie(
            playbackId = dto.id,
            progress = progress,
            lastWatchedAtMillis = dto.pausedAt.toZonedMillisOrNow(),
            traktId = movie.ids?.trakt?.toLong(),
            tmdbId = tmdbId,
            title = title,
            year = movie.year
        )
    }

    private fun mapPlaybackEpisode(dto: TraktPlaybackItem): ContinueWatchingItem.Episode? {
        if (dto.type != "episode") return null
        val show = dto.show ?: return null
        val ep = dto.episode ?: return null
        val showTmdbId = show.ids?.tmdb ?: return null
        val showTraktId = show.ids?.trakt?.toLong() ?: return null
        val showTitle = show.title ?: return null
        val progress = normalizeProgress(dto.progress)
        val season = ep.season ?: return null
        val episodeNumber = ep.number ?: return null
        return ContinueWatchingItem.Episode(
            playbackId = dto.id,
            progress = progress,
            lastWatchedAtMillis = dto.pausedAt.toZonedMillisOrNow(),
            showTraktId = showTraktId,
            showTmdbId = showTmdbId,
            showTitle = showTitle,
            showYear = show.year,
            season = season,
            episode = episodeNumber,
            episodeTitle = ep.title
        )
    }

    private suspend fun buildNextEpisodeFromPlayback(
        dto: TraktPlaybackItem,
        authHeader: String
    ): ContinueWatchingItem.NextEpisode? {
        val show = dto.show ?: return null
        val showTraktId = show.ids?.trakt ?: return null
        val showTmdbId = show.ids?.tmdb ?: return null
        val showTitle = show.title ?: return null
        val lastWatched = dto.pausedAt.toZonedMillisOrNow()

        val progress = fetchShowProgress(showTraktId, authHeader) ?: return null
        val nextEpisode = progress.nextEpisode ?: return null
        val season = nextEpisode.season ?: return null
        val episode = nextEpisode.number ?: return null
        val firstAiredMillis = nextEpisode.firstAired.toZonedMillisOrNull()
        val sortTimestamp = max(lastWatched, firstAiredMillis ?: lastWatched)

        return ContinueWatchingItem.NextEpisode(
            playbackId = null,
            progress = 0f,
            lastWatchedAtMillis = sortTimestamp,
            showTraktId = showTraktId.toLong(),
            showTmdbId = showTmdbId,
            showTitle = showTitle,
            showYear = show.year,
            season = season,
            episode = episode,
            episodeTitle = nextEpisode.title,
            firstAiredAtMillis = firstAiredMillis
        )
    }

    private suspend fun fetchNextUpFromHistory(
        authHeader: String,
        processedTraktIds: Set<Long>,
        processedTmdbIds: Set<Int>
    ): List<ContinueWatchingItem.NextEpisode> = coroutineScope {
        val historyShows = runCatching {
            traktApiService.getHistoryShows(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                limit = HISTORY_FETCH_LIMIT
            )
        }.getOrDefault(emptyList())

        historyShows.mapNotNull { historyItem ->
            val show = historyItem.show ?: return@mapNotNull null
            val showTraktId = show.ids?.trakt ?: return@mapNotNull null
            val showTmdbId = show.ids?.tmdb ?: return@mapNotNull null
            if (processedTmdbIds.contains(showTmdbId) || processedTraktIds.contains(showTraktId.toLong())) {
                return@mapNotNull null
            }
            async {
                buildNextEpisodeFromHistory(historyItem, authHeader)
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun buildNextEpisodeFromHistory(
        historyItem: TraktHistoryItem,
        authHeader: String
    ): ContinueWatchingItem.NextEpisode? {
        val show = historyItem.show ?: return null
        val showTraktId = show.ids?.trakt ?: return null
        val showTmdbId = show.ids?.tmdb ?: return null
        val showTitle = show.title ?: return null
        val watchedAtMillis = historyItem.watchedAt.toZonedMillisOrNow()

        val progress = fetchShowProgress(showTraktId, authHeader) ?: return null
        val nextEpisode = progress.nextEpisode ?: return null
        val season = nextEpisode.season ?: return null
        val episode = nextEpisode.number ?: return null
        val firstAiredMillis = nextEpisode.firstAired.toZonedMillisOrNull()
        val sortTimestamp = max(watchedAtMillis, firstAiredMillis ?: watchedAtMillis)

        return ContinueWatchingItem.NextEpisode(
            playbackId = null,
            progress = 0f,
            lastWatchedAtMillis = sortTimestamp,
            showTraktId = showTraktId.toLong(),
            showTmdbId = showTmdbId,
            showTitle = showTitle,
            showYear = show.year,
            season = season,
            episode = episode,
            episodeTitle = nextEpisode.title,
            firstAiredAtMillis = firstAiredMillis
        )
    }

    private suspend fun fetchShowProgress(
        showTraktId: Int,
        authHeader: String
    ): TraktShowProgress? {
        return runCatching {
            traktApiService.getShowProgress(
                showId = showTraktId,
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID
            )
        }.getOrNull()
    }

    /**
     * Remove duplicate episodes per show, keeping the first (most recent) occurrence after sorting.
     */
    private fun dedupeEpisodes(items: List<ContinueWatchingItem>): List<ContinueWatchingItem> {
        val seenShows = mutableSetOf<Long>()
        val result = mutableListOf<ContinueWatchingItem>()
        items.forEach { item ->
            if (item is ContinueWatchingItem.Movie) {
                result.add(item)
                return@forEach
            }
            val showId = when (item) {
                is ContinueWatchingItem.Episode -> item.showTraktId
                is ContinueWatchingItem.NextEpisode -> item.showTraktId
                else -> null
            } ?: return@forEach
            if (seenShows.add(showId)) {
                result.add(item)
            }
        }
        return result
    }

    private suspend fun enrichToContentItem(item: ContinueWatchingItem): ContentItem? {
        return when (item) {
            is ContinueWatchingItem.Movie -> enrichMovie(item)
            is ContinueWatchingItem.Episode -> enrichEpisode(item)
            is ContinueWatchingItem.NextEpisode -> enrichEpisode(item)
        }
    }

    private suspend fun enrichMovie(item: ContinueWatchingItem.Movie): ContentItem? {
        val tmdbId = item.tmdbId
        val details = runCatching {
            tmdbApiService.getMovieDetails(
                movieId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY,
                appendToResponse = "images,credits"
            )
        }.getOrNull() ?: return null

        return ContentItem(
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

    private suspend fun enrichEpisode(item: ContinueWatchingItem): ContentItem? {
        val tmdbId = when (item) {
            is ContinueWatchingItem.Episode -> item.showTmdbId
            is ContinueWatchingItem.NextEpisode -> item.showTmdbId
            else -> return null
        }
        val showDetails = runCatching {
            tmdbApiService.getShowDetails(
                showId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY
            )
        }.getOrNull() ?: return null

        val season = when (item) {
            is ContinueWatchingItem.Episode -> item.season
            is ContinueWatchingItem.NextEpisode -> item.season
            else -> return null
        }
        val episodeNumber = when (item) {
            is ContinueWatchingItem.Episode -> item.episode
            is ContinueWatchingItem.NextEpisode -> item.episode
            else -> return null
        }

        val episodeDetails: TMDBEpisode? = runCatching {
            tmdbApiService.getEpisodeDetails(
                showId = tmdbId,
                seasonNumber = season,
                episodeNumber = episodeNumber,
                apiKey = BuildConfig.TMDB_API_KEY
            )
        }.getOrNull()

        val showBackdrop = showDetails.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
        val showPoster = showDetails.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
        val episodeStill = episodeDetails?.getStillUrl()

        val showTitle = when (item) {
            is ContinueWatchingItem.Episode -> item.showTitle
            is ContinueWatchingItem.NextEpisode -> item.showTitle
            else -> showDetails.name ?: ""
        }
        val showYear = when (item) {
            is ContinueWatchingItem.Episode -> item.showYear
            is ContinueWatchingItem.NextEpisode -> item.showYear
            else -> showDetails.firstAirDate?.take(4)?.toIntOrNull()
        }
        val episodeTitle = when (item) {
            is ContinueWatchingItem.Episode -> item.episodeTitle
            is ContinueWatchingItem.NextEpisode -> item.episodeTitle
            else -> null
        }

        return ContentItem(
            id = tmdbId,
            tmdbId = tmdbId,
            imdbId = showDetails.externalIds?.imdbId,
            title = "$showTitle S${season}E${episodeNumber}",
            overview = episodeDetails?.overview
                ?: episodeTitle
                ?: showDetails.overview,
            posterUrl = episodeStill ?: showPoster,
            backdropUrl = showBackdrop,
            logoUrl = showDetails.images?.logos?.firstOrNull()?.filePath?.let { "https://image.tmdb.org/t/p/w500$it" },
            year = showDetails.firstAirDate?.take(4) ?: showYear?.toString(),
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

    private fun buildEpisodeLabel(item: ContinueWatchingItem, episodeDetails: TMDBEpisode?): String? {
        val (season, episode, episodeTitle) = when (item) {
            is ContinueWatchingItem.Episode -> Triple(item.season, item.episode, item.episodeTitle)
            is ContinueWatchingItem.NextEpisode -> Triple(item.season, item.episode, item.episodeTitle)
            else -> return null
        }
        val seasonVal = season.takeIf { it > 0 } ?: return null
        val episodeVal = episode.takeIf { it > 0 } ?: return null
        val seasonStr = seasonVal.toString().padStart(2, '0')
        val episodeStr = episodeVal.toString().padStart(2, '0')
        val epTitle = episodeDetails?.name ?: episodeTitle
        return if (epTitle.isNullOrBlank()) {
            "S${seasonStr}E${episodeStr}"
        } else {
            "S${seasonStr}E${episodeStr} • $epTitle"
        }
    }

    companion object {
        private const val PLAYBACK_MOVIE_LIMIT = 50
        private const val PLAYBACK_EPISODE_LIMIT = 50
        private const val HISTORY_FETCH_LIMIT = 20
        private const val NEAR_COMPLETE_PROGRESS = 0.9f

        private fun normalizeProgress(raw: Double?): Float =
            ((raw ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    }
}
