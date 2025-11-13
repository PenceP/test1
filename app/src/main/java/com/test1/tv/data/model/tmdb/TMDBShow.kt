package com.test1.tv.data.model.tmdb

import com.google.gson.annotations.SerializedName

data class TMDBShow(
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

data class TMDBShowDetails(
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
    @SerializedName("genres")
    val genres: List<TMDBGenre>?,
    @SerializedName("episode_run_time")
    val episodeRunTime: List<Int>?,
    @SerializedName("number_of_seasons")
    val numberOfSeasons: Int?,
    @SerializedName("number_of_episodes")
    val numberOfEpisodes: Int?,
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
        return firstAirDate?.take(4)
    }

    fun getRatingPercentage(): Int? {
        return voteAverage?.times(10)?.toInt()
    }
}
