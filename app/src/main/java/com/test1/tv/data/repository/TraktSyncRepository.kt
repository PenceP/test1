package com.test1.tv.data.repository

import com.test1.tv.BuildConfig
import com.test1.tv.data.local.dao.TraktUserItemDao
import com.test1.tv.data.local.entity.TraktAccount
import com.test1.tv.data.local.entity.TraktUserItem
import com.test1.tv.data.model.trakt.TraktCollectionItem
import com.test1.tv.data.model.trakt.TraktHistoryItem
import com.test1.tv.data.model.trakt.TraktLastActivities
import com.test1.tv.data.model.trakt.TraktWatchlistItem
import com.test1.tv.data.remote.api.TraktApiService
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class TraktSyncRepository(
    private val traktApiService: TraktApiService,
    private val accountRepository: TraktAccountRepository,
    private val userItemDao: TraktUserItemDao
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

    companion object {
        private const val LIST_WATCHLIST = "WATCHLIST"
        private const val LIST_COLLECTION = "COLLECTION"
        private const val LIST_HISTORY = "HISTORY"
        private const val ITEM_MOVIE = "MOVIE"
        private const val ITEM_SHOW = "SHOW"
    }
}
