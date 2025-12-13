package com.strmr.tv.data.local

import androidx.room.*

/**
 * Core media entity - stores basic metadata.
 * Split from images/ratings for efficient partial updates.
 */
@Entity(
    tableName = "media_content",
    indices = [
        Index(value = ["category", "position"]),
        Index(value = ["tmdbId"], unique = true),
        Index(value = ["cachedAt"]),
        Index(value = ["contentType", "category"])
    ]
)
data class MediaContentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tmdbId: Int,
    val imdbId: String?,
    val title: String,
    val overview: String?,
    val year: String?,
    val runtime: Int?,
    val certification: String?,
    val contentType: String,  // "movie" or "tv"
    val category: String,     // "trending_movies", "popular_shows", etc.
    val position: Int,        // Position within category for ordering
    val genres: String? = null, // Comma-separated genres
    val cast: String? = null,   // Comma-separated cast
    val cachedAt: Long = System.currentTimeMillis()
)

/**
 * Separate entity for images - allows updating images without touching metadata.
 */
@Entity(
    tableName = "media_images",
    foreignKeys = [
        ForeignKey(
            entity = MediaContentEntity::class,
            parentColumns = ["tmdbId"],
            childColumns = ["tmdbId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tmdbId"])]
)
data class MediaImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tmdbId: Int,
    val posterUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?
)

/**
 * Separate entity for ratings from multiple sources.
 */
@Entity(
    tableName = "media_ratings",
    foreignKeys = [
        ForeignKey(
            entity = MediaContentEntity::class,
            parentColumns = ["tmdbId"],
            childColumns = ["tmdbId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tmdbId"])]
)
data class MediaRatingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tmdbId: Int,
    val tmdbRating: Float?,
    val imdbRating: Float?,
    val traktRating: Float?,
    val rottenTomatoesRating: Int?,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Watch progress - separate for frequent updates.
 */
@Entity(
    tableName = "watch_progress",
    indices = [Index(value = ["tmdbId"], unique = true)]
)
data class WatchProgressEntity(
    @PrimaryKey
    val tmdbId: Int,
    val progress: Float,          // 0.0 to 1.0
    val lastWatchedAt: Long,
    val episodeId: Int? = null,   // For TV shows
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
)

/**
 * Aggregate object for full media data with relations.
 */
data class MediaWithDetails(
    @Embedded
    val content: MediaContentEntity,

    @Relation(
        parentColumn = "tmdbId",
        entityColumn = "tmdbId"
    )
    val images: MediaImageEntity?,

    @Relation(
        parentColumn = "tmdbId",
        entityColumn = "tmdbId"
    )
    val ratings: MediaRatingEntity?,

    @Relation(
        parentColumn = "tmdbId",
        entityColumn = "tmdbId"
    )
    val progress: WatchProgressEntity?
)

/**
 * Lightweight version for list display (no ratings).
 */
data class MediaWithImages(
    @Embedded
    val content: MediaContentEntity,

    @Relation(
        parentColumn = "tmdbId",
        entityColumn = "tmdbId"
    )
    val images: MediaImageEntity?,

    @Relation(
        parentColumn = "tmdbId",
        entityColumn = "tmdbId"
    )
    val ratings: MediaRatingEntity?,

    @Relation(
        parentColumn = "tmdbId",
        entityColumn = "tmdbId"
    )
    val progress: WatchProgressEntity?
)
