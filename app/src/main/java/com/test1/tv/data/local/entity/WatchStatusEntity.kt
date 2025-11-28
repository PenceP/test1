package com.test1.tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_status")
data class WatchStatusEntity(
    @PrimaryKey val key: String,
    val tmdbId: Int,
    val type: String,
    val progress: Double?,
    val updatedAt: Long
)
