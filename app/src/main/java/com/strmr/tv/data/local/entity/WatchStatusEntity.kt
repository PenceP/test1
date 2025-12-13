package com.strmr.tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_status")
data class WatchStatusEntity(
    @PrimaryKey val key: String,
    val tmdbId: Int,
    val type: String,
    val progress: Double?,
    val updatedAt: Long,
    val lastWatched: Long? = null,
    val nextEpisodeTitle: String? = null,
    val nextEpisodeSeason: Int? = null,
    val nextEpisodeNumber: Int? = null,
    val nextEpisodeTmdbId: Int? = null
)
