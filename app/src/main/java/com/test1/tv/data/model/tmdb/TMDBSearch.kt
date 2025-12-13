package com.test1.tv.data.model.tmdb

import com.google.gson.annotations.SerializedName

data class TMDBSearchResponse(
    @SerializedName("results")
    val results: List<TMDBSearchResult>?
)

data class TMDBSearchResult(
    @SerializedName("media_type")
    val mediaType: String?,
    @SerializedName("id")
    val id: Int,
    @SerializedName("title")
    val title: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("overview")
    val overview: String?,
    @SerializedName("poster_path")
    val posterPath: String?,
    @SerializedName("backdrop_path")
    val backdropPath: String?,
    @SerializedName("first_air_date")
    val firstAirDate: String?,
    @SerializedName("release_date")
    val releaseDate: String?,
    @SerializedName("vote_average")
    val voteAverage: Double?,
    @SerializedName("profile_path")
    val profilePath: String?
) {
    fun getPosterUrl(): String? = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    fun getBackdropUrl(): String? = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
    fun getProfileUrl(): String? = profilePath?.let { "https://image.tmdb.org/t/p/w300$it" }
    fun getYear(): String? = (releaseDate ?: firstAirDate)?.take(4)
}
