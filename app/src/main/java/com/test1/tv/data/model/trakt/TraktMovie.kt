package com.test1.tv.data.model.trakt

import com.google.gson.annotations.SerializedName

data class TraktTrendingMovie(
    @SerializedName("watchers")
    val watchers: Int,
    @SerializedName("movie")
    val movie: TraktMovie
)

data class TraktMovie(
    @SerializedName("title")
    val title: String,
    @SerializedName("year")
    val year: Int?,
    @SerializedName("rating")
    val rating: Double? = null,
    @SerializedName("ids")
    val ids: TraktIds
)

data class TraktIds(
    @SerializedName("trakt")
    val trakt: Int,
    @SerializedName("slug")
    val slug: String?,
    @SerializedName("imdb")
    val imdb: String?,
    @SerializedName("tmdb")
    val tmdb: Int?
)
