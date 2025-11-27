package com.test1.tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trakt_user_items")
data class TraktUserItem(
    @PrimaryKey val id: String,
    val listType: String, // WATCHLIST, COLLECTION, HISTORY
    val itemType: String, // MOVIE, SHOW
    val traktId: Int?,
    val tmdbId: Int?,
    val slug: String?,
    val title: String?,
    val year: String?,
    val updatedAt: Long,
    val listedAt: Long?,
    val collectedAt: Long?,
    val watchedAt: Long?
) {
    companion object {
        fun key(listType: String, itemType: String, traktId: Int?): String {
            return "${listType}_${itemType}_${traktId ?: 0}"
        }
    }
}
