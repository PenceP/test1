package com.strmr.tv.data.model.trakt

import com.google.gson.annotations.SerializedName

data class TraktUser(
    @SerializedName("username") val username: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("ids") val ids: TraktUserIds?
)

data class TraktUserIds(
    @SerializedName("slug") val slug: String?
)

data class TraktUserStats(
    @SerializedName("movies") val movies: TraktUserMovieStats?,
    @SerializedName("shows") val shows: TraktUserShowStats?
)

data class TraktUserMovieStats(
    @SerializedName("watched") val watched: Int?,
    @SerializedName("minutes") val minutes: Long?
)

data class TraktUserShowStats(
    @SerializedName("watched") val watched: Int?,
    @SerializedName("minutes") val minutes: Long?
)
