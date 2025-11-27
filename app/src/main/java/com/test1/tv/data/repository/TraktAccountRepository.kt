package com.test1.tv.data.repository

import com.test1.tv.BuildConfig
import com.test1.tv.data.local.dao.TraktAccountDao
import com.test1.tv.data.local.entity.TraktAccount
import com.test1.tv.data.model.trakt.TraktTokenResponse
import com.test1.tv.data.model.trakt.TraktUser
import com.test1.tv.data.remote.api.TraktApiService
import kotlin.math.max

class TraktAccountRepository(
    private val traktApiService: TraktApiService,
    private val accountDao: TraktAccountDao
)
{
    companion object {
        private const val PROVIDER_ID = "trakt"
        private const val REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"
        private const val EXPIRY_LEEWAY_MS = 60_000L
    }

    suspend fun getAccount(): TraktAccount? = accountDao.getAccount(PROVIDER_ID)

    suspend fun saveDeviceToken(
        token: TraktTokenResponse,
        profile: TraktUser
    ): TraktAccount {
        val expiresAt = computeExpiry(token)
        val account = TraktAccount(
            providerId = PROVIDER_ID,
            userSlug = profile.ids?.slug ?: profile.username,
            userName = profile.name ?: profile.username,
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            tokenType = token.tokenType,
            scope = token.scope,
            expiresAt = expiresAt,
            createdAt = (token.createdAt ?: System.currentTimeMillis() / 1000L) * 1000L,
            statsMoviesWatched = null,
            statsShowsWatched = null,
            statsMinutesWatched = null,
            lastSyncAt = null,
            lastHistorySync = null,
            lastCollectionSync = null,
            lastWatchlistSync = null,
            lastActivitiesAt = null
        )
        accountDao.upsert(account)
        return account
    }

    suspend fun refreshTokenIfNeeded(): TraktAccount? {
        val account = accountDao.getAccount(PROVIDER_ID) ?: return null
        val now = System.currentTimeMillis()
        if (account.expiresAt - EXPIRY_LEEWAY_MS > now) return account

        val refreshed = traktApiService.refreshToken(
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            clientSecret = BuildConfig.TRAKT_CLIENT_SECRET,
            refreshToken = account.refreshToken ?: return account,
            redirectUri = REDIRECT_URI
        )
        val expiresAt = computeExpiry(refreshed)
        accountDao.updateTokens(
            accessToken = refreshed.accessToken,
            refreshToken = refreshed.refreshToken,
            expiresAt = expiresAt,
            tokenType = refreshed.tokenType,
            scope = refreshed.scope
        )
        return accountDao.getAccount(PROVIDER_ID)
    }

    suspend fun updateStats(
        moviesWatched: Int?,
        showsWatched: Int?,
        minutesWatched: Long?,
        userName: String?,
        userSlug: String?
    ) {
        accountDao.updateStats(
            moviesWatched = moviesWatched,
            showsWatched = showsWatched,
            minutesWatched = minutesWatched,
            userName = userName,
            userSlug = userSlug
        )
    }

    suspend fun updateSyncTimestamps(
        lastSyncAt: Long?,
        history: Long?,
        collection: Long?,
        watchlist: Long?,
        lastActivities: Long?
    ) {
        accountDao.updateSyncTimestamps(
            lastSyncAt = lastSyncAt,
            history = history,
            collection = collection,
            watchlist = watchlist,
            lastActivities = lastActivities
        )
    }

    suspend fun clearAccount() {
        accountDao.deleteAccount()
    }

    fun buildAuthHeader(accessToken: String): String = "Bearer $accessToken"

    private fun computeExpiry(token: TraktTokenResponse): Long {
        val createdSeconds = token.createdAt ?: (System.currentTimeMillis() / 1000L)
        val expiresSeconds = createdSeconds + max(token.expiresIn, 0L)
        return expiresSeconds * 1000L
    }
}
