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
    @SerializedName("imdb_id")
    val imdbId: String? = null,
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
    @SerializedName("imdb_id")
    val imdbId: String?,
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
    val status: String?,
    @SerializedName("images")
    val images: TMDBImages?,
    @SerializedName("credits")
    val credits: TMDBCredits?,
    @SerializedName("release_dates")
    val releaseDates: TMDBReleaseDatesResponse?,
    @SerializedName("belongs_to_collection")
    val belongsToCollection: TMDBCollection?,
    @SerializedName("external_ids")
    val externalIds: TMDBExternalIds? = null
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
        // Look for US certification
        val usRelease = releaseDates?.results?.find { it.iso31661 == "US" }
        // Get the first certification from release dates (usually theatrical release)
        return usRelease?.releaseDates?.firstOrNull { !it.certification.isNullOrBlank() }?.certification
    }
}

data class TMDBGenre(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String
)

data class TMDBCast(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("character")
    val character: String?,
    @SerializedName("profile_path")
    val profilePath: String?,
    @SerializedName("order")
    val order: Int?
) {
    fun getProfileUrl(): String? {
        return profilePath?.let { "https://image.tmdb.org/t/p/w185$it" }
    }
}

data class TMDBCredits(
    @SerializedName("cast")
    val cast: List<TMDBCast>?
)

data class TMDBReleaseDatesResponse(
    @SerializedName("results")
    val results: List<TMDBReleaseDate>?
)

data class TMDBReleaseDate(
    @SerializedName("iso_3166_1")
    val iso31661: String,
    @SerializedName("release_dates")
    val releaseDates: List<TMDBReleaseDateInfo>?
)

data class TMDBReleaseDateInfo(
    @SerializedName("certification")
    val certification: String?,
    @SerializedName("release_date")
    val releaseDate: String?,
    @SerializedName("type")
    val type: Int?
)

data class TMDBCollection(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("poster_path")
    val posterPath: String?,
    @SerializedName("backdrop_path")
    val backdropPath: String?
)

data class TMDBCollectionDetails(
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
    @SerializedName("parts")
    val parts: List<TMDBMovie>?
)

data class TMDBMovieListResponse(
    @SerializedName("page")
    val page: Int,
    @SerializedName("results")
    val results: List<TMDBMovie>?,
    @SerializedName("total_pages")
    val totalPages: Int?,
    @SerializedName("total_results")
    val totalResults: Int?
)
