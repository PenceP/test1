package com.test1.tv.data.repository

import com.test1.tv.BuildConfig
import com.test1.tv.data.local.dao.TraktUserItemDao
import com.test1.tv.data.local.entity.TraktAccount
import com.test1.tv.data.local.entity.TraktUserItem
import com.test1.tv.data.repository.CacheRepository
import com.test1.tv.data.model.trakt.TraktCollectionItem
import com.test1.tv.data.model.trakt.TraktHistoryItem
import com.test1.tv.data.model.trakt.TraktLastActivities
import com.test1.tv.data.model.trakt.TraktWatchlistItem
import com.test1.tv.data.model.trakt.TraktSyncRequest
import com.test1.tv.data.model.trakt.TraktSyncMovie
import com.test1.tv.data.model.trakt.TraktSyncShow
import com.test1.tv.data.model.trakt.TraktSyncSeason
import com.test1.tv.data.model.trakt.TraktSyncSeasonEpisode
import com.test1.tv.data.model.trakt.TraktIds
import com.test1.tv.data.model.trakt.TraktRatingRequest
import com.test1.tv.data.model.trakt.TraktRatingItem
import com.test1.tv.data.model.trakt.TraktShowProgress
import com.test1.tv.data.remote.api.TraktApiService
import com.test1.tv.data.local.entity.WatchStatusEntity
import com.test1.tv.data.model.ContentItem
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktSyncRepository @Inject constructor(
    private val traktApiService: TraktApiService,
    private val accountRepository: TraktAccountRepository,
    private val userItemDao: TraktUserItemDao,
    private val cacheRepository: CacheRepository,
    private val watchStatusRepository: WatchStatusRepository
) {
    suspend fun syncAll(): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return true
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        val activities = traktApiService.getLastActivities(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID
        )

        var updatedHistory: Long? = account.lastHistorySync
        var updatedCollection: Long? = account.lastCollectionSync
        var updatedWatchlist: Long? = account.lastWatchlistSync

        if (shouldSyncWatchlist(account, activities)) {
            val watchlist = fetchWatchlist(authHeader)
            userItemDao.clearList(LIST_WATCHLIST)
            userItemDao.insertAll(watchlist)
            updatedWatchlist = parseDateMillis(
                activities.movies?.watchlistedAt
                    ?: activities.shows?.watchlistedAt
            ) ?: System.currentTimeMillis()
        }

        if (shouldSyncCollection(account, activities)) {
            val collection = fetchCollection(authHeader)
            userItemDao.clearList(LIST_COLLECTION)
            userItemDao.insertAll(collection)
            updatedCollection = parseDateMillis(
                activities.movies?.collectedAt
                    ?: activities.shows?.collectedAt
            ) ?: System.currentTimeMillis()
        }

        if (shouldSyncHistory(account, activities)) {
            val history = fetchHistory(authHeader)
            userItemDao.clearList(LIST_HISTORY)
            userItemDao.insertAll(history)
            // Also update WatchStatusRepository as the single source of truth for watched status
            syncWatchStatusFromHistory(history)
            updatedHistory = parseDateMillis(
                activities.movies?.watchedAt
                    ?: activities.shows?.watchedAt
            ) ?: System.currentTimeMillis()
        }

        val stats = traktApiService.getUserStats(
            userSlug = account.userSlug ?: "me",
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID
        )

        accountRepository.updateStats(
            moviesWatched = stats.movies?.watched,
            showsWatched = stats.shows?.watched,
            minutesWatched = (stats.movies?.minutes ?: 0L) + (stats.shows?.minutes ?: 0L),
            userName = account.userName,
            userSlug = account.userSlug
        )

        accountRepository.updateSyncTimestamps(
            lastSyncAt = System.currentTimeMillis(),
            history = updatedHistory,
            collection = updatedCollection,
            watchlist = updatedWatchlist,
            lastActivities = parseDateMillis(activities.all)
        )

        return true
    }

    private suspend fun fetchWatchlist(authHeader: String): List<TraktUserItem> {
        val movies = traktApiService.getWatchlistMovies(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID
        )
        val shows = traktApiService.getWatchlistShows(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID
        )
        return movies.mapNotNull { it.toUserItem(LIST_WATCHLIST) } +
            shows.mapNotNull { it.toUserItem(LIST_WATCHLIST) }
    }

    private suspend fun fetchCollection(authHeader: String): List<TraktUserItem> {
        val movies = traktApiService.getCollectionMovies(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID
        )
        val shows = traktApiService.getCollectionShows(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID
        )
        return movies.mapNotNull { it.toUserItem(LIST_COLLECTION) } +
            shows.mapNotNull { it.toUserItem(LIST_COLLECTION) }
    }

    private suspend fun fetchHistory(authHeader: String): List<TraktUserItem> {
        val movies = traktApiService.getHistoryMovies(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID
        )
        val shows = traktApiService.getHistoryShows(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID
        )
        return movies.mapNotNull { it.toUserItem(LIST_HISTORY) } +
            shows.mapNotNull { it.toUserItem(LIST_HISTORY) }
    }

    private fun shouldSyncWatchlist(
        account: TraktAccount,
        activities: TraktLastActivities
    ): Boolean {
        val lastRemote = parseDateMillis(
            activities.movies?.watchlistedAt ?: activities.shows?.watchlistedAt
        ) ?: return false
        return (account.lastWatchlistSync ?: 0L) < lastRemote
    }

    private fun shouldSyncCollection(
        account: TraktAccount,
        activities: TraktLastActivities
    ): Boolean {
        val lastRemote = parseDateMillis(
            activities.movies?.collectedAt ?: activities.shows?.collectedAt
        ) ?: return false
        return (account.lastCollectionSync ?: 0L) < lastRemote
    }

    private fun shouldSyncHistory(
        account: TraktAccount,
        activities: TraktLastActivities
    ): Boolean {
        val lastRemote = parseDateMillis(
            activities.movies?.watchedAt ?: activities.shows?.watchedAt
        ) ?: return false
        return (account.lastHistorySync ?: 0L) < lastRemote
    }

    private fun TraktWatchlistItem.toUserItem(listType: String): TraktUserItem? {
        val targetMovie = movie
        val targetShow = show
        val itemType = when {
            targetMovie != null -> ITEM_MOVIE
            targetShow != null -> ITEM_SHOW
            else -> return null
        }
        val ids = targetMovie?.ids ?: targetShow?.ids
        val traktId = ids?.trakt
        val tmdbId = ids?.tmdb
        val slug = ids?.slug
        val title = targetMovie?.title ?: targetShow?.title
        val year = targetMovie?.year?.toString() ?: targetShow?.year?.toString()

        return TraktUserItem(
            id = TraktUserItem.key(listType, itemType, traktId),
            listType = listType,
            itemType = itemType,
            traktId = traktId,
            tmdbId = tmdbId,
            slug = slug,
            title = title,
            year = year,
            updatedAt = System.currentTimeMillis(),
            listedAt = parseDateMillis(listedAt),
            collectedAt = null,
            watchedAt = null
        )
    }

    private fun TraktCollectionItem.toUserItem(listType: String): TraktUserItem? {
        val targetMovie = movie
        val targetShow = show
        val itemType = when {
            targetMovie != null -> ITEM_MOVIE
            targetShow != null -> ITEM_SHOW
            else -> return null
        }
        val ids = targetMovie?.ids ?: targetShow?.ids
        val traktId = ids?.trakt
        val tmdbId = ids?.tmdb
        val slug = ids?.slug
        val title = targetMovie?.title ?: targetShow?.title
        val year = targetMovie?.year?.toString() ?: targetShow?.year?.toString()

        return TraktUserItem(
            id = TraktUserItem.key(listType, itemType, traktId),
            listType = listType,
            itemType = itemType,
            traktId = traktId,
            tmdbId = tmdbId,
            slug = slug,
            title = title,
            year = year,
            updatedAt = System.currentTimeMillis(),
            listedAt = null,
            collectedAt = parseDateMillis(collectedAt),
            watchedAt = null
        )
    }

    private fun TraktHistoryItem.toUserItem(listType: String): TraktUserItem? {
        val targetMovie = movie
        val targetShow = show
        val itemType = when {
            targetMovie != null -> ITEM_MOVIE
            targetShow != null -> ITEM_SHOW
            else -> return null
        }
        val ids = targetMovie?.ids ?: targetShow?.ids
        val traktId = ids?.trakt
        val tmdbId = ids?.tmdb
        val slug = ids?.slug
        val title = targetMovie?.title ?: targetShow?.title
        val year = targetMovie?.year?.toString() ?: targetShow?.year?.toString()

        return TraktUserItem(
            id = TraktUserItem.key(listType, itemType, traktId),
            listType = listType,
            itemType = itemType,
            traktId = traktId,
            tmdbId = tmdbId,
            slug = slug,
            title = title,
            year = year,
            updatedAt = System.currentTimeMillis(),
            listedAt = null,
            collectedAt = null,
            watchedAt = parseDateMillis(watchedAt)
        )
    }

    private fun parseDateMillis(raw: String?): Long? {
        raw ?: return null
        return try {
            OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    // ==================== CONTEXT MENU ACTIONS ====================

    /**
     * Mark a movie as watched
     */
    suspend fun markMovieWatched(tmdbId: Int): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return false
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val request = TraktSyncRequest(
                movies = listOf(TraktSyncMovie(ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = tmdbId)))
            )
            traktApiService.addToHistory(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            // Update local DB
            val item = TraktUserItem(
                id = TraktUserItem.key(LIST_HISTORY, ITEM_MOVIE, null),
                listType = LIST_HISTORY,
                itemType = ITEM_MOVIE,
                traktId = null,
                tmdbId = tmdbId,
                slug = null,
                title = null,
                year = null,
                updatedAt = System.currentTimeMillis(),
                listedAt = null,
                collectedAt = null,
                watchedAt = System.currentTimeMillis()
            )
            userItemDao.insertAll(listOf(item))
            // Update WatchStatusRepository (single source of truth)
            watchStatusRepository.upsert(tmdbId, ContentItem.ContentType.MOVIE, 1.0)
            true
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to mark movie watched", e)
            false
        }
    }

    /**
     * Mark a movie as unwatched
     */
    suspend fun markMovieUnwatched(tmdbId: Int): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return false
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val request = TraktSyncRequest(
                movies = listOf(TraktSyncMovie(ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = tmdbId)))
            )
            traktApiService.removeFromHistory(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            // Remove from local DB (by tmdbId pattern)
            userItemDao.deleteByTmdbId(tmdbId, LIST_HISTORY)
            // Update WatchStatusRepository (single source of truth)
            watchStatusRepository.upsert(tmdbId, ContentItem.ContentType.MOVIE, null)
            true
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to mark movie unwatched", e)
            false
        }
    }

    /**
     * Mark a show as watched (all episodes)
     */
    suspend fun markShowWatched(tmdbId: Int): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return false
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val request = TraktSyncRequest(
                shows = listOf(TraktSyncShow(ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = tmdbId)))
            )
            traktApiService.addToHistory(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            // Update local DB
            val item = TraktUserItem(
                id = TraktUserItem.key(LIST_HISTORY, ITEM_SHOW, null),
                listType = LIST_HISTORY,
                itemType = ITEM_SHOW,
                traktId = null,
                tmdbId = tmdbId,
                slug = null,
                title = null,
                year = null,
                updatedAt = System.currentTimeMillis(),
                listedAt = null,
                collectedAt = null,
                watchedAt = System.currentTimeMillis()
            )
            userItemDao.insertAll(listOf(item))
            // Update WatchStatusRepository (single source of truth)
            watchStatusRepository.upsert(tmdbId, ContentItem.ContentType.TV_SHOW, 1.0)
            true
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to mark show watched", e)
            false
        }
    }

    /**
     * Mark a show as unwatched
     */
    suspend fun markShowUnwatched(tmdbId: Int): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return false
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val request = TraktSyncRequest(
                shows = listOf(TraktSyncShow(ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = tmdbId)))
            )
            traktApiService.removeFromHistory(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            // Remove from local DB
            userItemDao.deleteByTmdbId(tmdbId, LIST_HISTORY)
            // Update WatchStatusRepository (single source of truth)
            watchStatusRepository.upsert(tmdbId, ContentItem.ContentType.TV_SHOW, null)
            true
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to mark show unwatched", e)
            false
        }
    }

    /**
     * Mark a specific season as watched
     */
    suspend fun markSeasonWatched(showTmdbId: Int, seasonNumber: Int): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return false
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val request = TraktSyncRequest(
                shows = listOf(
                    TraktSyncShow(
                        ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = showTmdbId),
                        seasons = listOf(TraktSyncSeason(number = seasonNumber))
                    )
                )
            )
            traktApiService.addToHistory(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            true
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to mark season watched", e)
            false
        }
    }

    /**
     * Mark a specific episode as watched
     */
    suspend fun markEpisodeWatched(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return false
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val request = TraktSyncRequest(
                shows = listOf(
                    TraktSyncShow(
                        ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = showTmdbId),
                        seasons = listOf(
                            TraktSyncSeason(
                                number = seasonNumber,
                                episodes = listOf(TraktSyncSeasonEpisode(number = episodeNumber))
                            )
                        )
                    )
                )
            )
            traktApiService.addToHistory(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            true
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to mark episode watched", e)
            false
        }
    }

    /**
     * Mark a specific episode as unwatched
     */
    suspend fun markEpisodeUnwatched(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return false
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val request = TraktSyncRequest(
                shows = listOf(
                    TraktSyncShow(
                        ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = showTmdbId),
                        seasons = listOf(
                            TraktSyncSeason(
                                number = seasonNumber,
                                episodes = listOf(TraktSyncSeasonEpisode(number = episodeNumber))
                            )
                        )
                    )
                )
            )
            traktApiService.removeFromHistory(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            true
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to mark episode unwatched", e)
            false
        }
    }

    /**
     * Add item to collection
     */
    suspend fun addToCollection(tmdbId: Int, isMovie: Boolean): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return false
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val request = if (isMovie) {
                TraktSyncRequest(movies = listOf(TraktSyncMovie(ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = tmdbId))))
            } else {
                TraktSyncRequest(shows = listOf(TraktSyncShow(ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = tmdbId))))
            }
            traktApiService.addToCollection(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            // Update local DB
            val itemType = if (isMovie) ITEM_MOVIE else ITEM_SHOW
            val item = TraktUserItem(
                id = "${LIST_COLLECTION}_${itemType}_tmdb_$tmdbId",
                listType = LIST_COLLECTION,
                itemType = itemType,
                traktId = null,
                tmdbId = tmdbId,
                slug = null,
                title = null,
                year = null,
                updatedAt = System.currentTimeMillis(),
                listedAt = null,
                collectedAt = System.currentTimeMillis(),
                watchedAt = null
            )
            userItemDao.insertAll(listOf(item))
            true
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to add to collection", e)
            false
        }
    }

    /**
     * Remove item from collection
     */
    suspend fun removeFromCollection(tmdbId: Int, isMovie: Boolean): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return false
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val request = if (isMovie) {
                TraktSyncRequest(movies = listOf(TraktSyncMovie(ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = tmdbId))))
            } else {
                TraktSyncRequest(shows = listOf(TraktSyncShow(ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = tmdbId))))
            }
            traktApiService.removeFromCollection(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            userItemDao.deleteByTmdbId(tmdbId, LIST_COLLECTION)
            true
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to remove from collection", e)
            false
        }
    }

    /**
     * Add item to watchlist
     */
    suspend fun addToWatchlist(tmdbId: Int, isMovie: Boolean): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return false
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val request = if (isMovie) {
                TraktSyncRequest(movies = listOf(TraktSyncMovie(ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = tmdbId))))
            } else {
                TraktSyncRequest(shows = listOf(TraktSyncShow(ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = tmdbId))))
            }
            traktApiService.addToWatchlist(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            // Update local DB
            val itemType = if (isMovie) ITEM_MOVIE else ITEM_SHOW
            val item = TraktUserItem(
                id = "${LIST_WATCHLIST}_${itemType}_tmdb_$tmdbId",
                listType = LIST_WATCHLIST,
                itemType = itemType,
                traktId = null,
                tmdbId = tmdbId,
                slug = null,
                title = null,
                year = null,
                updatedAt = System.currentTimeMillis(),
                listedAt = System.currentTimeMillis(),
                collectedAt = null,
                watchedAt = null
            )
            userItemDao.insertAll(listOf(item))
            true
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to add to watchlist", e)
            false
        }
    }

    /**
     * Remove item from watchlist
     */
    suspend fun removeFromWatchlist(tmdbId: Int, isMovie: Boolean): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return false
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val request = if (isMovie) {
                TraktSyncRequest(movies = listOf(TraktSyncMovie(ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = tmdbId))))
            } else {
                TraktSyncRequest(shows = listOf(TraktSyncShow(ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = tmdbId))))
            }
            traktApiService.removeFromWatchlist(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            userItemDao.deleteByTmdbId(tmdbId, LIST_WATCHLIST)
            true
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to remove from watchlist", e)
            false
        }
    }

    /**
     * Add rating to an item
     * @param rating 1-10, where 8 = Like, 4 = Dislike
     */
    suspend fun addRating(tmdbId: Int, isMovie: Boolean, rating: Int): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return false
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val ratingItem = TraktRatingItem(
                ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = tmdbId),
                rating = rating.coerceIn(1, 10)
            )
            val request = if (isMovie) {
                TraktRatingRequest(movies = listOf(ratingItem))
            } else {
                TraktRatingRequest(shows = listOf(ratingItem))
            }
            traktApiService.addRating(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            true
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to add rating", e)
            false
        }
    }

    /**
     * Remove rating from an item
     */
    suspend fun removeRating(tmdbId: Int, isMovie: Boolean): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return false
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val request = if (isMovie) {
                TraktSyncRequest(movies = listOf(TraktSyncMovie(ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = tmdbId))))
            } else {
                TraktSyncRequest(shows = listOf(TraktSyncShow(ids = TraktIds(trakt = 0, slug = null, imdb = null, tmdb = tmdbId))))
            }
            traktApiService.removeRating(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            true
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to remove rating", e)
            false
        }
    }

    /**
     * Check if item is in a specific list
     */
    suspend fun isInList(tmdbId: Int, listType: String, itemType: String): Boolean {
        val items = userItemDao.getItems(listType, itemType)
        return items.any { it.tmdbId == tmdbId }
    }

    /**
     * Get all watched episodes for a show as a set of "S{season}E{episode}" strings
     */
    suspend fun getWatchedEpisodesForShow(showTmdbId: Int): Set<String> {
        // Note: This requires storing episode-level watch data
        // For now, return empty set - full implementation requires schema changes
        return emptySet()
    }

    // ==================== TARGETED SYNC METHODS ====================

    /**
     * Sync only the watch history from Trakt
     */
    suspend fun syncHistoryOnly(): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return false
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val history = fetchHistory(authHeader)
            userItemDao.clearList(LIST_HISTORY)
            userItemDao.insertAll(history)
            // Also update WatchStatusRepository as the single source of truth
            syncWatchStatusFromHistory(history)
            accountRepository.updateSyncTimestamps(
                lastSyncAt = System.currentTimeMillis(),
                history = System.currentTimeMillis(),
                collection = null,
                watchlist = null,
                lastActivities = null
            )
            true
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to sync history", e)
            false
        }
    }

    /**
     * Sync WatchStatusRepository from Trakt history items.
     * Sets progress = 1.0 for all watched items.
     */
    private suspend fun syncWatchStatusFromHistory(history: List<TraktUserItem>) {
        val watchStatusEntities = history.mapNotNull { item ->
            val tmdbId = item.tmdbId ?: return@mapNotNull null
            val type = when (item.itemType) {
                ITEM_MOVIE -> ContentItem.ContentType.MOVIE
                ITEM_SHOW -> ContentItem.ContentType.TV_SHOW
                else -> return@mapNotNull null
            }
            WatchStatusEntity(
                key = "${type.name}_$tmdbId",
                tmdbId = tmdbId,
                type = type.name,
                progress = 1.0, // Fully watched
                updatedAt = item.watchedAt ?: System.currentTimeMillis()
            )
        }
        if (watchStatusEntities.isNotEmpty()) {
            watchStatusRepository.upsertAll(watchStatusEntities)
        }
    }

    /**
     * Sync only the collection from Trakt and clear cache
     */
    suspend fun syncCollectionOnly(): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return false
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val collection = fetchCollection(authHeader)
            userItemDao.clearList(LIST_COLLECTION)
            userItemDao.insertAll(collection)
            accountRepository.updateSyncTimestamps(
                lastSyncAt = System.currentTimeMillis(),
                history = null,
                collection = System.currentTimeMillis(),
                watchlist = null,
                lastActivities = null
            )
            // Clear cache so UI loads fresh data
            cacheRepository.clearCategoryCache("MY_TRAKT_MOVIE_COLLECTION")
            cacheRepository.clearCategoryCache("MY_TRAKT_TV_COLLECTION")
            true
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to sync collection", e)
            false
        }
    }

    /**
     * Sync only the watchlist from Trakt and clear cache
     */
    suspend fun syncWatchlistOnly(): Boolean {
        val account = accountRepository.refreshTokenIfNeeded() ?: return false
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val watchlist = fetchWatchlist(authHeader)
            userItemDao.clearList(LIST_WATCHLIST)
            userItemDao.insertAll(watchlist)
            accountRepository.updateSyncTimestamps(
                lastSyncAt = System.currentTimeMillis(),
                history = null,
                collection = null,
                watchlist = System.currentTimeMillis(),
                lastActivities = null
            )
            // Clear cache so UI loads fresh data
            cacheRepository.clearCategoryCache("MY_TRAKT_MOVIE_WATCHLIST")
            cacheRepository.clearCategoryCache("MY_TRAKT_TV_WATCHLIST")
            true
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to sync watchlist", e)
            false
        }
    }

    /**
     * Lookup Trakt ID from TMDB ID
     */
    private suspend fun lookupTraktId(tmdbId: Int, type: String = "show"): Int? {
        return try {
            val results = traktApiService.lookupByTmdbId(
                tmdbId = tmdbId,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                type = type
            )
            val traktId = when (type) {
                "show" -> results.firstOrNull()?.show?.ids?.trakt
                "movie" -> results.firstOrNull()?.movie?.ids?.trakt
                else -> null
            }
            android.util.Log.d("TraktSync", "Lookup TMDB $tmdbId -> Trakt $traktId")
            traktId
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to lookup Trakt ID for TMDB $tmdbId", e)
            null
        }
    }

    /**
     * Get show progress (watched episodes) from Trakt
     * Returns null if not authenticated or on error
     */
    suspend fun getShowProgress(showTmdbId: Int): TraktShowProgress? {
        val account = accountRepository.refreshTokenIfNeeded()
        if (account == null) {
            android.util.Log.d("TraktSync", "getShowProgress: Not authenticated")
            return null
        }
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        // First, lookup the Trakt ID from TMDB ID
        val traktId = lookupTraktId(showTmdbId, "show")
        if (traktId == null) {
            android.util.Log.e("TraktSync", "Could not find Trakt ID for TMDB $showTmdbId")
            return null
        }

        return try {
            android.util.Log.d("TraktSync", "Fetching show progress for Trakt ID: $traktId (TMDB: $showTmdbId)")
            val progress = traktApiService.getShowProgress(
                showId = traktId.toString(),
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID
            )
            android.util.Log.d("TraktSync", "Show progress result: aired=${progress.aired}, completed=${progress.completed}, seasons=${progress.seasons?.size ?: 0}")
            progress.seasons?.forEach { season ->
                val watchedEps = season.episodes?.count { it.completed == true } ?: 0
                android.util.Log.d("TraktSync", "  Season ${season.number}: $watchedEps/${season.episodes?.size ?: 0} watched")
            }
            progress
        } catch (e: retrofit2.HttpException) {
            android.util.Log.e("TraktSync", "HTTP error getting show progress for Trakt $traktId: ${e.code()} ${e.message()}", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to get show progress for Trakt $traktId", e)
            null
        }
    }

    /**
     * Get in-progress episode playback data for a specific show.
     * Returns a map of "S{season}E{episode}" to progress (0.0 - 1.0).
     * Only returns episodes with progress between 5% and 90% (in progress, not completed).
     */
    suspend fun getEpisodePlaybackProgress(showTmdbId: Int): Map<String, Float> {
        val account = accountRepository.refreshTokenIfNeeded() ?: return emptyMap()
        val authHeader = accountRepository.buildAuthHeader(account.accessToken)

        return try {
            val playbackItems = traktApiService.getPlaybackEpisodes(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                limit = 100
            )

            // Filter by show and convert to map
            playbackItems
                .filter { item ->
                    item.type == "episode" && item.show?.ids?.tmdb == showTmdbId
                }
                .mapNotNull { item ->
                    val episode = item.episode ?: return@mapNotNull null
                    val season = episode.season ?: return@mapNotNull null
                    val epNum = episode.number ?: return@mapNotNull null
                    val progress = ((item.progress ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                    // Only include in-progress episodes (5% - 90%)
                    if (progress >= 0.05f && progress < 0.90f) {
                        "S${season}E${epNum}" to progress
                    } else {
                        null
                    }
                }
                .toMap()
        } catch (e: Exception) {
            android.util.Log.e("TraktSync", "Failed to get episode playback for TMDB $showTmdbId", e)
            emptyMap()
        }
    }

    companion object {
        private const val LIST_WATCHLIST = "WATCHLIST"
        private const val LIST_COLLECTION = "COLLECTION"
        private const val LIST_HISTORY = "HISTORY"
        private const val ITEM_MOVIE = "MOVIE"
        private const val ITEM_SHOW = "SHOW"
    }
}
