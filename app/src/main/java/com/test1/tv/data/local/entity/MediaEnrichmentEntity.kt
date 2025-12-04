package com.test1.tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_enrichment")
data class MediaEnrichmentEntity(
    @PrimaryKey val tmdbId: Int,
    val posterUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val genres: String?,
    val cast: String?,
    val runtime: String?,
    val certification: String?,
    val lastUpdated: Long = System.currentTimeMillis()
)
