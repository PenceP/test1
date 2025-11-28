package com.test1.tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "continue_watching")
data class ContinueWatchingEntity(
    @PrimaryKey val cacheId: String,
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
    val type: String,
    val runtime: String?,
    val cast: String?,
    val certification: String?,
    val imdbRating: String?,
    val rottenTomatoesRating: String?,
    val traktRating: Double?,
    val watchProgress: Double?,
    val updatedAt: Long
)
