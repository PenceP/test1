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
    @SerializedName("seasons")
    val seasons: List<TMDBSeason>?,
    @SerializedName("status")
    val status: String?,
    @SerializedName("images")
    val images: TMDBImages?,
    @SerializedName("credits")
    val credits: TMDBCredits?,
    @SerializedName("content_ratings")
    val contentRatings: TMDBContentRatingsResponse?
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

    fun getLogoUrl(): String? {
        return images.getPreferredLogoUrl()
    }

    fun getCastNames(limit: Int = 10): String? {
        val castList = credits?.cast
            ?.sortedBy { it.order ?: Int.MAX_VALUE }
            ?.take(limit)
            ?.mapNotNull { it.name }

        return if (castList.isNullOrEmpty()) null else castList.joinToString(", ")
    }

    fun getCertification(): String? {
        // Look for US content rating
        return contentRatings?.results?.find { it.iso31661 == "US" }?.rating
    }
}

data class TMDBContentRatingsResponse(
    @SerializedName("results")
    val results: List<TMDBContentRating>?
)

data class TMDBContentRating(
    @SerializedName("iso_3166_1")
    val iso31661: String,
    @SerializedName("rating")
    val rating: String?
)

data class TMDBShowListResponse(
    @SerializedName("page")
    val page: Int,
    @SerializedName("results")
    val results: List<TMDBShow>?,
    @SerializedName("total_pages")
    val totalPages: Int?,
    @SerializedName("total_results")
    val totalResults: Int?
)

data class TMDBSeason(
    @SerializedName("id")
    val id: Int?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("season_number")
    val seasonNumber: Int?,
    @SerializedName("episode_count")
    val episodeCount: Int?,
    @SerializedName("poster_path")
    val posterPath: String?,
    @SerializedName("air_date")
    val airDate: String?
) {
    fun getPosterUrl(): String? = posterPath?.let { "https://image.tmdb.org/t/p/w300$it" }
    fun getDisplayName(): String {
        return name ?: seasonNumber?.let { "Season $it" } ?: "Season"
    }
}

data class TMDBEpisode(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String?,
    @SerializedName("overview")
    val overview: String?,
    @SerializedName("air_date")
    val airDate: String?,
    @SerializedName("runtime")
    val runtime: Int?,
    @SerializedName("episode_number")
    val episodeNumber: Int?,
    @SerializedName("season_number")
    val seasonNumber: Int?,
    @SerializedName("still_path")
    val stillPath: String?
) {
    fun getStillUrl(): String? = stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    fun getDisplayName(): String {
        val number = episodeNumber ?: return name.orEmpty()
        val title = name?.takeIf { it.isNotBlank() } ?: "Episode $number"
        return "$number. $title"
    }
}

data class TMDBSeasonDetails(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String?,
    @SerializedName("season_number")
    val seasonNumber: Int?,
    @SerializedName("episodes")
    val episodes: List<TMDBEpisode>?
)
