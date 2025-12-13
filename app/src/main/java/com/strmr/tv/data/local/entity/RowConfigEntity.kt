package com.strmr.tv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "row_config",
    indices = [Index(value = ["screenType", "position"])]
)
data class RowConfigEntity(
    @PrimaryKey val id: String,           // Unique row identifier
    val screenType: String,               // "home", "movies", "tvshows"
    val title: String,                    // Display title
    val rowType: String,                  // "trending", "popular", "continue", etc.
    val contentType: String?,             // "movies", "shows", null for mixed
    val presentation: String,             // "portrait", "landscape"
    val dataSourceUrl: String?,           // For Trakt list URLs
    val defaultPosition: Int,             // Default sort order
    val position: Int,                    // User-customized position
    val enabled: Boolean = true,          // User can hide rows
    val requiresAuth: Boolean = false,    // Needs Trakt login
    val pageSize: Int = 20,
    val isSystemRow: Boolean = false      // Can't be deleted, only hidden
)
