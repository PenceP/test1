package com.strmr.tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val key: String,  // e.g., "trending_movies", "continue_watching", "network_netflix"
    val lastSyncedAt: Long,
    val traktActivityTimestamp: String? = null  // For Trakt last_activities comparison
)
