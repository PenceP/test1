package com.test1.tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class TraktAccount(
    @PrimaryKey val providerId: String = "trakt",
    val userSlug: String?,
    val userName: String?,
    val accessToken: String,
    val refreshToken: String?,
    val tokenType: String?,
    val scope: String?,
    val expiresAt: Long,
    val createdAt: Long,
    val statsMoviesWatched: Int?,
    val statsShowsWatched: Int?,
    val statsMinutesWatched: Long?,
    val lastSyncAt: Long?,
    val lastHistorySync: Long?,
    val lastCollectionSync: Long?,
    val lastWatchlistSync: Long?,
    val lastActivitiesAt: Long?
)
