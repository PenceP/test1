package com.test1.tv.data.model.trakt

import com.google.gson.annotations.SerializedName

data class TraktWatchlistItem(
    @SerializedName("listed_at") val listedAt: String?,
    @SerializedName("rank") val rank: Int?,
    @SerializedName("type") val type: String?,
    @SerializedName("movie") val movie: TraktMovie? = null,
    @SerializedName("show") val show: TraktShow? = null
)

data class TraktCollectionItem(
    @SerializedName("collected_at") val collectedAt: String?,
    @SerializedName("movie") val movie: TraktMovie? = null,
    @SerializedName("show") val show: TraktShow? = null
)

data class TraktHistoryItem(
    @SerializedName("id") val id: Long?,
    @SerializedName("watched_at") val watchedAt: String?,
    @SerializedName("action") val action: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("movie") val movie: TraktMovie? = null,
    @SerializedName("show") val show: TraktShow? = null
)
