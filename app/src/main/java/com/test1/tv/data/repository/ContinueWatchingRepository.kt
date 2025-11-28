package com.test1.tv.data.repository

import com.test1.tv.BuildConfig
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.model.continuewatching.ContinueWatchingItem
import com.test1.tv.data.model.continuewatching.EpisodeNotification
import com.test1.tv.data.model.continuewatching.LibraryItem
import com.test1.tv.data.model.continuewatching.LibraryItemState
import com.test1.tv.data.model.continuewatching.MediaType
import com.test1.tv.data.model.continuewatching.buildContinueWatchingRow
import com.test1.tv.data.model.tmdb.TMDBMovieDetails
import com.test1.tv.data.model.trakt.TraktWatchedShow
import com.test1.tv.data.remote.api.TMDBApiService
import com.test1.tv.data.remote.api.TraktApiService
import android.util.Log
import com.test1.tv.data.local.dao.ContinueWatchingDao
import com.test1.tv.data.local.entity.ContinueWatchingEntity
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.format.DateTimeParseException

class ContinueWatchingRepository(
    private val traktApiService: TraktApiService,
    private val tmdbApiService: TMDBApiService,
    private val accountRepository: TraktAccountRepository,
    private val continueWatchingDao: ContinueWatchingDao,
    private val watchStatusRepository: WatchStatusRepository
) {
    companion object {
        private const val TAG = "ContinueWatching"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
    }

    private var cachedItems: List<ContentItem> = emptyList()
    private var lastLoadedAt: Long = 0L
    private var lastActivitiesCached: Long? = null

    suspend fun hasAccount(): Boolean = accountRepository.getAccount() != null

    suspend fun load(maxItems: Int = 20, forceRefresh: Boolean = false): List<ContentItem> {
        val account = accountRepository.refreshTokenIfNeeded() ?: return emptyList()
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        val cachedDb = continueWatchingDao.getAll()
        if (!forceRefresh && cachedDb.isNotEmpty()) {
            val mapped = cachedDb.map { it.toContentItem() }
            cachedItems = mapped
            lastLoadedAt = System.currentTimeMillis()
            lastActivitiesCached = account.lastActivitiesAt
        }

        if (!forceRefresh && cachedItems.isNotEmpty() && System.currentTimeMillis() - lastLoadedAt < CACHE_TTL_MS) {
            Log.d(TAG, "Serving continue watching from cache (${cachedItems.size} items)")
            return cachedItems.take(maxItems)
        }

        val lastActivities = runCatching {
            traktApiService.getLastActivities(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID
            )
        }.getOrNull()
        val latestActivityMillis = extractLatestActivity(lastActivities)
        if (!forceRefresh &&
            cachedItems.isNotEmpty() &&
            latestActivityMillis != null &&
            account.lastActivitiesAt != null &&
            latestActivityMillis <= account.lastActivitiesAt
        ) {
            Log.d(TAG, "Last activities unchanged, serving cached CW (${cachedItems.size})")
            return cachedItems.take(maxItems)
        }

        val playbackMovies = runCatching {
            traktApiService.getPlaybackMovies(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID
            )
        }.getOrElse {
            Log.e(TAG, "playback movies failed", it)
            emptyList()
        }
        val playbackEpisodes = runCatching {
            traktApiService.getPlaybackEpisodes(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID
            )
        }.getOrElse {
            Log.e(TAG, "playback episodes failed", it)
            emptyList()
        }
        val watchedShows = runCatching {
            traktApiService.getWatchedShows(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID
            )
        }.getOrElse {
            Log.e(TAG, "watched shows failed", it)
            emptyList()
        }
        val historyMovies = runCatching {
            traktApiService.getHistoryMovies(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                limit = 100
            )
        }.getOrElse {
            Log.e(TAG, "history movies failed", it)
            emptyList()
        }
        val historyShows = runCatching {
            traktApiService.getHistoryShows(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                limit = 100
            )
        }.getOrElse {
            Log.e(TAG, "history shows failed", it)
            emptyList()
        }

        val moviePairs = buildMoviePairs(playbackMovies)
        val showPairs = buildShowPairs(playbackEpisodes)
        val watchedShowPairs = buildWatchedShowPairs(watchedShows)
        val historyMoviePairs = buildHistoryMoviePairs(historyMovies)
        val historyShowPairs = buildHistoryShowPairs(historyShows)

        val mergedPairs = mergePairs(
            primary = moviePairs + showPairs,
            fallback = watchedShowPairs + historyMoviePairs + historyShowPairs
        )
        Log.d(
            TAG,
            "sizes: playbackMovies=${playbackMovies.size}, playbackEpisodes=${playbackEpisodes.size}, watchedShows=${watchedShows.size}, historyMovies=${historyMovies.size}, historyShows=${historyShows.size}, merged=${mergedPairs.size}"
        )
        val libraryItems = mergedPairs.map { it.first }
        val contentItemsById = mergedPairs.associate { it.first.id to it.second }

        val showTitles = mergedPairs
            .filter { it.first.type == MediaType.SHOW }
            .associate { it.first.id to it.second.title }
        val notifications = buildEpisodeNotifications(showTitles, authHeader)

        val cwItems: List<ContinueWatchingItem> = buildContinueWatchingRow(
            libraryItems = libraryItems,
            notificationsByItemId = notifications,
            maxItems = maxItems
        )
        Log.d(TAG, "cwItems=${cwItems.size}")

        val result = cwItems.mapNotNull { contentItemsById[it.libraryItem.id] }
        cachedItems = result
        lastLoadedAt = System.currentTimeMillis()
        lastActivitiesCached = latestActivityMillis
        continueWatchingDao.clear()
        continueWatchingDao.upsertAll(result.map { it.toEntity() })
        watchStatusRepository.upsertAll(
            (moviePairs + showPairs + watchedShowPairs + historyMoviePairs + historyShowPairs).mapNotNull { pair ->
                val item = pair.second
                val type = item.type
                val progress = item.watchProgress ?: return@mapNotNull null
                com.test1.tv.data.local.entity.WatchStatusEntity(
                    key = "${type.name}_${item.tmdbId}",
                    tmdbId = item.tmdbId,
                    type = type.name,
                    progress = progress,
                    updatedAt = System.currentTimeMillis()
                )
            }
        )
        accountRepository.updateSyncTimestamps(
            lastSyncAt = System.currentTimeMillis(),
            history = null,
            collection = null,
            watchlist = null,
            lastActivities = latestActivityMillis
        )
        return result
    }

    fun clearCache() {
        cachedItems = emptyList()
        lastLoadedAt = 0L
    }

    private fun parseIsoMillis(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            0L
        }
    }

    private suspend fun buildMoviePairs(
        playback: List<com.test1.tv.data.model.trakt.TraktPlaybackItem>
    ): List<Pair<LibraryItem, ContentItem>> = coroutineScope {
        playback.mapNotNull { item ->
            val tmdbId = item.movie?.ids?.tmdb ?: return@mapNotNull null
            val libraryId = item.movie.ids.trakt?.toString() ?: tmdbId.toString()
            async {
                val details = runCatching {
                    tmdbApiService.getMovieDetails(
                        movieId = tmdbId,
                        apiKey = BuildConfig.TMDB_API_KEY,
                        appendToResponse = "images,external_ids,credits"
                    )
                }.getOrNull() ?: return@async null

                val runtimeSeconds = details.runtime?.let { it * 60L }
                val timeOffset = computeOffsetSeconds(item.progress, runtimeSeconds)
                val progressRatio = computeProgressRatio(timeOffset, runtimeSeconds)

                val library = LibraryItem(
                    id = libraryId,
                    type = MediaType.MOVIE,
                    state = LibraryItemState(
                        timeOffsetSeconds = timeOffset,
                        runtimeSeconds = runtimeSeconds,
                        lastUpdated = parseInstant(item.pausedAt) ?: Instant.now()
                    )
                )
                val content = ContentItem(
                    id = details.id,
                    tmdbId = details.id,
                    imdbId = resolveImdb(details),
                    title = details.title,
                    overview = details.overview,
                    posterUrl = details.getPosterUrl(),
                    backdropUrl = details.getBackdropUrl(),
                    logoUrl = details.getLogoUrl(),
                    year = details.getYear(),
                    rating = details.voteAverage,
                    ratingPercentage = details.getRatingPercentage(),
                    genres = details.genres?.joinToString(", ") { it.name },
                    type = ContentItem.ContentType.MOVIE,
                    runtime = details.runtime?.let { "$it min" },
                    cast = details.getCastNames(),
                    certification = details.getCertification(),
                    imdbRating = null,
                    rottenTomatoesRating = null,
                    traktRating = null,
                    watchProgress = progressRatio
                )
                Pair(library, content)
            }
        }.mapNotNull { it.await() }
    }

    private suspend fun buildShowPairs(
        playbackEpisodes: List<com.test1.tv.data.model.trakt.TraktPlaybackItem>
    ): List<Pair<LibraryItem, ContentItem>> = coroutineScope {
        playbackEpisodes.mapNotNull { item ->
            val tmdbShowId = item.show?.ids?.tmdb ?: return@mapNotNull null
            val traktShowId = item.show.ids.trakt ?: return@mapNotNull null
            val libraryId = traktShowId.toString()
            async {
                val details = runCatching {
                    tmdbApiService.getShowDetails(
                        showId = tmdbShowId,
                        apiKey = BuildConfig.TMDB_API_KEY,
                        appendToResponse = "images,external_ids,credits"
                    )
                }.getOrNull() ?: return@async null

                val episodeRuntimeSeconds = item.episode?.runtime?.let { it * 60L }
                    ?: details.episodeRunTime?.firstOrNull()?.let { it * 60L }
                val timeOffset = computeOffsetSeconds(item.progress, episodeRuntimeSeconds)
                val progressRatio = computeProgressRatio(timeOffset, episodeRuntimeSeconds)

                // Skip fully finished shows if progress is essentially complete and no next episode later
                val library = LibraryItem(
                    id = libraryId,
                    type = MediaType.SHOW,
                    state = LibraryItemState(
                        timeOffsetSeconds = timeOffset,
                        runtimeSeconds = episodeRuntimeSeconds,
                        lastUpdated = parseInstant(item.pausedAt) ?: Instant.now()
                    )
                )

                val nextEpisodeLabel = item.episode?.let {
                    val season = it.season ?: 0
                    val number = it.number ?: 0
                    "S${season.toString().padStart(2, '0')}E${number.toString().padStart(2, '0')}"
                }

                val content = ContentItem(
                    id = details.id,
                    tmdbId = details.id,
                    imdbId = details.externalIds?.imdbId,
                    title = details.name,
                    overview = details.overview,
                    posterUrl = details.getPosterUrl(),
                    backdropUrl = details.getBackdropUrl(),
                    logoUrl = details.getLogoUrl(),
                    year = details.getYear(),
                    rating = details.voteAverage,
                    ratingPercentage = details.getRatingPercentage(),
                    genres = details.genres?.joinToString(", ") { it.name },
                    type = ContentItem.ContentType.TV_SHOW,
                    runtime = nextEpisodeLabel ?: details.episodeRunTime?.firstOrNull()?.let { "$it min" },
                    cast = details.getCastNames(),
                    certification = details.getCertification(),
                    imdbRating = null,
                    rottenTomatoesRating = null,
                    traktRating = null,
                    watchProgress = progressRatio
                )
                Pair(library, content)
            }
        }.mapNotNull { it.await() }
    }

    private suspend fun buildHistoryMoviePairs(
        historyMovies: List<com.test1.tv.data.model.trakt.TraktHistoryItem>
    ): List<Pair<LibraryItem, ContentItem>> = coroutineScope {
        historyMovies
            .sortedByDescending { parseIsoMillis(it.watchedAt) }
            .distinctBy { it.movie?.ids?.trakt }
            .mapNotNull { history ->
                val tmdbId = history.movie?.ids?.tmdb ?: return@mapNotNull null
                val traktId = history.movie.ids.trakt ?: return@mapNotNull null
                val watchedAt = Instant.ofEpochMilli(parseIsoMillis(history.watchedAt))

                async {
                    val details = runCatching {
                        tmdbApiService.getMovieDetails(
                            movieId = tmdbId,
                            apiKey = BuildConfig.TMDB_API_KEY,
                            appendToResponse = "images,external_ids,credits"
                        )
                    }.getOrNull() ?: return@async null

                    val runtimeSeconds = details.runtime?.let { it * 60L }
                    val library = LibraryItem(
                        id = traktId.toString(),
                        type = MediaType.MOVIE,
                        state = LibraryItemState(
                            timeOffsetSeconds = 1L,
                            runtimeSeconds = runtimeSeconds,
                            lastUpdated = watchedAt
                        )
                    )

                    val content = ContentItem(
                        id = details.id,
                        tmdbId = details.id,
                        imdbId = resolveImdb(details),
                        title = details.title,
                        overview = details.overview,
                        posterUrl = details.getPosterUrl(),
                        backdropUrl = details.getBackdropUrl(),
                        logoUrl = details.getLogoUrl(),
                        year = details.getYear(),
                        rating = details.voteAverage,
                        ratingPercentage = details.getRatingPercentage(),
                        genres = details.genres?.joinToString(", ") { it.name },
                        type = ContentItem.ContentType.MOVIE,
                        runtime = details.runtime?.let { "$it min" },
                    cast = details.getCastNames(),
                    certification = details.getCertification(),
                    imdbRating = null,
                    rottenTomatoesRating = null,
                    traktRating = null,
                    watchProgress = 1.0
                )
                Pair(library, content)
            }
        }.mapNotNull { it.await() }
    }

    private suspend fun buildHistoryShowPairs(
        historyShows: List<com.test1.tv.data.model.trakt.TraktHistoryItem>
    ): List<Pair<LibraryItem, ContentItem>> = coroutineScope {
        historyShows
            .sortedByDescending { parseIsoMillis(it.watchedAt) }
            .distinctBy { it.show?.ids?.trakt }
            .mapNotNull { history ->
                val tmdbId = history.show?.ids?.tmdb ?: return@mapNotNull null
                val traktId = history.show.ids.trakt ?: return@mapNotNull null
                val watchedAt = Instant.ofEpochMilli(parseIsoMillis(history.watchedAt))

                async {
                    val details = runCatching {
                        tmdbApiService.getShowDetails(
                            showId = tmdbId,
                            apiKey = BuildConfig.TMDB_API_KEY,
                            appendToResponse = "images,external_ids,credits"
                        )
                    }.getOrNull() ?: return@async null

                    val episodeRuntimeSeconds = details.episodeRunTime?.firstOrNull()?.let { it * 60L }
                    val library = LibraryItem(
                        id = traktId.toString(),
                        type = MediaType.SHOW,
                        state = LibraryItemState(
                            timeOffsetSeconds = 1L,
                            runtimeSeconds = episodeRuntimeSeconds,
                            lastUpdated = watchedAt
                        )
                    )

                    val content = ContentItem(
                        id = details.id,
                        tmdbId = details.id,
                        imdbId = details.externalIds?.imdbId,
                        title = details.name,
                        overview = details.overview,
                        posterUrl = details.getPosterUrl(),
                        backdropUrl = details.getBackdropUrl(),
                        logoUrl = details.getLogoUrl(),
                        year = details.getYear(),
                        rating = details.voteAverage,
                        ratingPercentage = details.getRatingPercentage(),
                        genres = details.genres?.joinToString(", ") { it.name },
                        type = ContentItem.ContentType.TV_SHOW,
                        runtime = details.episodeRunTime?.firstOrNull()?.let { "$it min" },
                    cast = details.getCastNames(),
                    certification = details.getCertification(),
                    imdbRating = null,
                    rottenTomatoesRating = null,
                    traktRating = null,
                    watchProgress = 1.0
                )
                Pair(library, content)
            }
        }.mapNotNull { it.await() }
    }

    private suspend fun buildWatchedShowPairs(
        watchedShows: List<TraktWatchedShow>
    ): List<Pair<LibraryItem, ContentItem>> = coroutineScope {
        watchedShows
            .sortedByDescending { parseIsoMillis(it.lastWatchedAt) }
            .mapNotNull { watched ->
                val tmdbId = watched.show?.ids?.tmdb ?: return@mapNotNull null
                val traktId = watched.show.ids.trakt ?: return@mapNotNull null
                val lastUpdated = Instant.ofEpochMilli(parseIsoMillis(watched.lastWatchedAt))

                async {
                    val details = runCatching {
                        tmdbApiService.getShowDetails(
                            showId = tmdbId,
                            apiKey = BuildConfig.TMDB_API_KEY,
                            appendToResponse = "images,external_ids,credits"
                        )
                    }.getOrNull() ?: return@async null

                    val episodeRuntimeSeconds = details.episodeRunTime?.firstOrNull()?.let { it * 60L }
                    val library = LibraryItem(
                        id = traktId.toString(),
                        type = MediaType.SHOW,
                        state = LibraryItemState(
                            timeOffsetSeconds = 1L,
                            runtimeSeconds = episodeRuntimeSeconds,
                            lastUpdated = lastUpdated
                        )
                    )

                    val content = ContentItem(
                        id = details.id,
                        tmdbId = details.id,
                        imdbId = details.externalIds?.imdbId,
                        title = details.name,
                        overview = details.overview,
                        posterUrl = details.getPosterUrl(),
                        backdropUrl = details.getBackdropUrl(),
                        logoUrl = details.getLogoUrl(),
                        year = details.getYear(),
                        rating = details.voteAverage,
                        ratingPercentage = details.getRatingPercentage(),
                        genres = details.genres?.joinToString(", ") { it.name },
                        type = ContentItem.ContentType.TV_SHOW,
                        runtime = details.episodeRunTime?.firstOrNull()?.let { "$it min" },
                        cast = details.getCastNames(),
                        certification = details.getCertification(),
                        imdbRating = null,
                        rottenTomatoesRating = null,
                        traktRating = null
                    )
                    Pair(library, content)
                }
            }.mapNotNull { it.await() }
    }

    private fun mergePairs(
        primary: List<Pair<LibraryItem, ContentItem>>,
        fallback: List<Pair<LibraryItem, ContentItem>>
    ): List<Pair<LibraryItem, ContentItem>> {
        val map = LinkedHashMap<String, Pair<LibraryItem, ContentItem>>()
        primary.forEach { pair -> map[pair.first.id] = pair }
        fallback.forEach { pair ->
            val existing = map[pair.first.id]
            if (existing == null || pair.first.state.lastUpdated.isAfter(existing.first.state.lastUpdated)) {
                map[pair.first.id] = pair
            }
        }
        return map.values.toList()
    }

    private suspend fun buildEpisodeNotifications(
        showTitles: Map<String, String>,
        authHeader: String
    ): Map<String, List<EpisodeNotification>> {
        val uniqueIds = showTitles.keys
        val notifications = mutableListOf<EpisodeNotification>()

        uniqueIds.forEach { showId ->
            val traktId = showId.toIntOrNull() ?: return@forEach
            val progress = runCatching {
                traktApiService.getShowProgress(
                    showId = traktId,
                    authHeader = authHeader,
                    clientId = BuildConfig.TRAKT_CLIENT_ID
                )
            }.getOrNull()

            val next = progress?.nextEpisode ?: return@forEach
            val releasedMillis = parseIsoMillis(next.firstAired)
            val released = if (releasedMillis == 0L) Instant.now() else Instant.ofEpochMilli(releasedMillis)
            notifications.add(
                EpisodeNotification(
                    id = "$showId-S${next.season ?: 0}E${next.number ?: 0}",
                    libraryItemId = showId,
                    showTitle = showTitles[showId] ?: "",
                    season = next.season ?: 0,
                    episode = next.number ?: 0,
                    videoReleased = released
                )
            )
        }

        return notifications.groupBy { it.libraryItemId }
    }

    private fun resolveImdb(details: TMDBMovieDetails): String? {
        return details.imdbId ?: details.externalIds?.imdbId
    }

    private fun computeOffsetSeconds(progress: Double?, runtimeSeconds: Long?): Long {
        val pct = progress ?: 0.0
        val rt = runtimeSeconds
        return if (rt != null && rt > 0) {
            ((pct / 100.0) * rt).toLong()
        } else if (pct > 0) {
            1L
        } else {
            0L
        }
    }

    private fun computeProgressRatio(timeOffset: Long, runtimeSeconds: Long?): Double? {
        val rt = runtimeSeconds ?: return null
        if (rt <= 0) return null
        return timeOffset.toDouble() / rt.toDouble()
    }

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }.getOrNull()
    }

    private fun extractLatestActivity(activities: com.test1.tv.data.model.trakt.TraktLastActivities?): Long? {
        activities ?: return null
        val candidates = listOfNotNull(
            activities.all,
            activities.movies?.watchedAt,
            activities.movies?.pausedAt,
            activities.shows?.watchedAt,
            activities.shows?.pausedAt,
            activities.episodes?.watchedAt,
            activities.episodes?.pausedAt
        ).mapNotNull { parseInstant(it)?.toEpochMilli() }
        return candidates.maxOrNull()
    }

    private fun ContentItem.toEntity(): ContinueWatchingEntity {
        return ContinueWatchingEntity(
            cacheId = "${type.name}_${tmdbId}_${id}",
            id = id,
            tmdbId = tmdbId,
            imdbId = imdbId,
            title = title,
            overview = overview,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            logoUrl = logoUrl,
            year = year,
            rating = rating,
            ratingPercentage = ratingPercentage,
            genres = genres,
            type = type.name,
            runtime = runtime,
            cast = cast,
            certification = certification,
            imdbRating = imdbRating,
            rottenTomatoesRating = rottenTomatoesRating,
            traktRating = traktRating,
            watchProgress = watchProgress,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun ContinueWatchingEntity.toContentItem(): ContentItem {
        return ContentItem(
            id = id,
            tmdbId = tmdbId,
            imdbId = imdbId,
            title = title,
            overview = overview,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            logoUrl = logoUrl,
            year = year,
            rating = rating,
            ratingPercentage = ratingPercentage,
            genres = genres,
            type = if (type == ContentItem.ContentType.TV_SHOW.name) {
                ContentItem.ContentType.TV_SHOW
            } else {
                ContentItem.ContentType.MOVIE
            },
            runtime = runtime,
            cast = cast,
            certification = certification,
            imdbRating = imdbRating,
            rottenTomatoesRating = rottenTomatoesRating,
            traktRating = traktRating,
            watchProgress = watchProgress
        )
    }
}
