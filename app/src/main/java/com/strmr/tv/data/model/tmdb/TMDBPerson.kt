package com.strmr.tv.data.model.tmdb

import com.google.gson.annotations.SerializedName

data class TMDBPersonDetails(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("biography")
    val biography: String?,
    @SerializedName("profile_path")
    val profilePath: String?,
    @SerializedName("movie_credits")
    val movieCredits: TMDBPersonMovieCredits?,
    @SerializedName("tv_credits")
    val tvCredits: TMDBPersonTVCredits?
) {
    fun getProfileUrl(): String? {
        return profilePath?.let { "https://image.tmdb.org/t/p/w500$it" }
    }
}

data class TMDBPersonMovieCredits(
    @SerializedName("cast")
    val cast: List<TMDBPersonMovieCredit>?
)

data class TMDBPersonMovieCredit(
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
    @SerializedName("character")
    val character: String?,
    @SerializedName("genre_ids")
    val genreIds: List<Int>?
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
}

data class TMDBPersonTVCredits(
    @SerializedName("cast")
    val cast: List<TMDBPersonTVCredit>?
)

data class TMDBPersonTVCredit(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("overview")
    val overview: String?,
    @SerializedName("poster_path")
    val posterPath: String?,
    @SerializedName("backdrop_path")
    val backdropPath: String?,
    @SerializedName("first_air_date")
    val firstAirDate: String?,
    @SerializedName("vote_average")
    val voteAverage: Double?,
    @SerializedName("character")
    val character: String?,
    @SerializedName("genre_ids")
    val genreIds: List<Int>?
) {
    fun getPosterUrl(): String? {
        return posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    }

    fun getBackdropUrl(): String? {
        return backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
    }

    fun getYear(): String? {
        return firstAirDate?.take(4)
    }
}
