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
    val imdbId: String?,
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
    val traktRating: Double?,
    val watchProgress: Double? = null,
    val isPlaceholder: Boolean = false
) : Parcelable {
    enum class ContentType {
        MOVIE, TV_SHOW
    }

    companion object {
        /**
         * Create skeleton placeholder items for loading states.
         * Used to show shimmer effect while content is being fetched.
         */
        fun createPlaceholders(count: Int): List<ContentItem> {
            return (0 until count).map { index ->
                ContentItem(
                    id = -1000 - index,  // Negative IDs for skeletons
                    tmdbId = -1,
                    imdbId = null,
                    title = "",
                    overview = null,
                    posterUrl = null,
                    backdropUrl = null,
                    logoUrl = null,
                    year = null,
                    rating = null,
                    ratingPercentage = null,
                    genres = null,
                    type = ContentType.MOVIE,
                    runtime = null,
                    cast = null,
                    certification = null,
                    imdbRating = null,
                    rottenTomatoesRating = null,
                    traktRating = null,
                    watchProgress = null,
                    isPlaceholder = true
                )
            }
        }
    }
}
