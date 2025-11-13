package com.test1.tv.data.model.tmdb

import com.google.gson.annotations.SerializedName

data class TMDBMovie(
    @SerializedName("id")
    val id: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("overview")
    val overview: String?,
    @SerializedName("poster_path")
    val posterPath: String?,
    @SerializedName("backdrop_path")
    val backdropPath: String?,
    @SerializedName("release_date")
    val releaseDate: String?,
    @SerializedName("vote_average")
    val voteAverage: Double?,
    @SerializedName("genre_ids")
    val genreIds: List<Int>?,
    @SerializedName("original_language")
    val originalLanguage: String?,
    @SerializedName("popularity")
    val popularity: Double?
) {
    fun getPosterUrl(): String? {
        return posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    }

    fun getBackdropUrl(): String? {
        return backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
    }
}

data class TMDBMovieDetails(
    @SerializedName("id")
    val id: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("overview")
    val overview: String?,
    @SerializedName("poster_path")
    val posterPath: String?,
    @SerializedName("backdrop_path")
    val backdropPath: String?,
    @SerializedName("release_date")
    val releaseDate: String?,
    @SerializedName("vote_average")
    val voteAverage: Double?,
    @SerializedName("genres")
    val genres: List<TMDBGenre>?,
    @SerializedName("runtime")
    val runtime: Int?,
    @SerializedName("tagline")
    val tagline: String?,
    @SerializedName("status")
    val status: String?
) {
    fun getPosterUrl(): String? {
        return posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    }

    fun getBackdropUrl(): String? {
        return backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
    }

    fun getYear(): String? {
        return releaseDate?.take(4)
    }

    fun getRatingPercentage(): Int? {
        return voteAverage?.times(10)?.toInt()
    }
}

data class TMDBGenre(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String
)
