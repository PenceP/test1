package com.test1.tv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.test1.tv.data.model.ContentItem

@Entity(
    tableName = "cached_content",
    indices = [
        Index(value = ["category", "position"]),  // Fast queries by category and position
        Index(value = ["category", "cachedAt"])   // Fast cache freshness checks
    ]
)
data class CachedContent(
    @PrimaryKey
    val id: String, // Format: "TYPE_CATEGORY_TMDB_ID" e.g., "MOVIE_TRENDING_12345"
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
    val contentType: String, // "MOVIE" or "TV_SHOW"
    val category: String, // "TRENDING_MOVIES", "POPULAR_MOVIES", etc.
    val runtime: String?,
    val cast: String?,
    val certification: String?,
    val imdbRating: String?,
    val rottenTomatoesRating: String?,
    val traktRating: Double?,
    val position: Int, // Position in the list for ordering
    val cachedAt: Long // Timestamp when cached
) {
    fun toContentItem(): ContentItem {
        return ContentItem(
            id = tmdbId,
            tmdbId = tmdbId,
            title = title,
            overview = overview,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            logoUrl = logoUrl,
            year = year,
            rating = rating,
            ratingPercentage = ratingPercentage,
            genres = genres,
            type = if (contentType == "MOVIE") ContentItem.ContentType.MOVIE else ContentItem.ContentType.TV_SHOW,
            runtime = runtime,
            cast = cast,
            certification = certification,
            imdbRating = imdbRating,
            rottenTomatoesRating = rottenTomatoesRating,
            traktRating = traktRating
        )
    }

    companion object {
        fun fromContentItem(
            item: ContentItem,
            category: String,
            position: Int
        ): CachedContent {
            return CachedContent(
                id = "${item.type.name}_${category}_${item.tmdbId}",
                tmdbId = item.tmdbId,
                title = item.title,
                overview = item.overview,
                posterUrl = item.posterUrl,
                backdropUrl = item.backdropUrl,
                logoUrl = item.logoUrl,
                year = item.year,
                rating = item.rating,
                ratingPercentage = item.ratingPercentage,
                genres = item.genres,
                contentType = item.type.name,
                category = category,
                runtime = item.runtime,
                cast = item.cast,
                certification = item.certification,
                imdbRating = item.imdbRating,
                rottenTomatoesRating = item.rottenTomatoesRating,
                traktRating = item.traktRating,
                position = position,
                cachedAt = System.currentTimeMillis()
            )
        }
    }
}
