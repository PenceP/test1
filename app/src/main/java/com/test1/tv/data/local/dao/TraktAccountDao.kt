package com.test1.tv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.test1.tv.data.local.entity.TraktAccount

@Dao
interface TraktAccountDao {
    @Query("SELECT * FROM accounts WHERE providerId = :providerId LIMIT 1")
    suspend fun getAccount(providerId: String = "trakt"): TraktAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: TraktAccount)

    @Query(
        """
        UPDATE accounts 
        SET accessToken = :accessToken, 
            refreshToken = :refreshToken,
            expiresAt = :expiresAt,
            tokenType = :tokenType,
            scope = :scope
        WHERE providerId = :providerId
        """
    )
    suspend fun updateTokens(
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long,
        tokenType: String?,
        scope: String?,
        providerId: String = "trakt"
    )

    @Query(
        """
        UPDATE accounts
        SET statsMoviesWatched = :moviesWatched,
            statsShowsWatched = :showsWatched,
            statsMinutesWatched = :minutesWatched,
            userName = :userName,
            userSlug = :userSlug
        WHERE providerId = :providerId
        """
    )
    suspend fun updateStats(
        moviesWatched: Int?,
        showsWatched: Int?,
        minutesWatched: Long?,
        userName: String?,
        userSlug: String?,
        providerId: String = "trakt"
    )

    @Query(
        """
        UPDATE accounts 
        SET lastSyncAt = :lastSyncAt,
            lastHistorySync = :history,
            lastCollectionSync = :collection,
            lastWatchlistSync = :watchlist,
            lastActivitiesAt = :lastActivities
        WHERE providerId = :providerId
        """
    )
    suspend fun updateSyncTimestamps(
        lastSyncAt: Long?,
        history: Long?,
        collection: Long?,
        watchlist: Long?,
        lastActivities: Long?,
        providerId: String = "trakt"
    )

    @Query("DELETE FROM accounts WHERE providerId = :providerId")
    suspend fun deleteAccount(providerId: String = "trakt")
}
