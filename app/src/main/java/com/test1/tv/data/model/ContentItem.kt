package com.test1.tv.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Unified model that combines Trakt and TMDB data for displaying content
 */
@Parcelize
data class ContentItem(
    val id: Int,
    val tmdbId: Int,
    val title: String,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val year: String?,
    val rating: Double?,
    val ratingPercentage: Int?,
    val genres: String?,
    val type: ContentType,
    val runtime: String?,
    val cast: String?,
    val certification: String?,
    val imdbRating: String?,
    val rottenTomatoesRating: String?,
    val traktRating: Double?
) : Parcelable {
    enum class ContentType {
        MOVIE, TV_SHOW
    }
}
