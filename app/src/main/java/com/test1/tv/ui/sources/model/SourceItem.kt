package com.test1.tv.ui.sources.model

import com.test1.tv.data.model.StreamInfo
import com.test1.tv.data.repository.Quality

/**
 * UI model for displaying source items in the sources list
 */
sealed class SourceItem {
    /**
     * A stream/link source item
     */
    data class Stream(
        val streamInfo: StreamInfo,
        val position: Int
    ) : SourceItem() {
        val debridBadge: String? get() = streamInfo.getDebridBadge()
        val qualityBadge: String get() = streamInfo.getQualityBadge()
        val fileName: String get() = streamInfo.fileName
        val sizeFormatted: String get() = streamInfo.sizeFormatted
        val isCached: Boolean get() = streamInfo.isCached
        val isFilteredOut: Boolean get() = streamInfo.isFilteredOut
        val quality: Quality get() = streamInfo.quality
    }

    /**
     * Separator between passed and filtered-out streams
     */
    data class Separator(
        val text: String = "Filtered Out"
    ) : SourceItem()
}

/**
 * Badge color constants
 */
object BadgeColors {
    // Quality badge colors
    const val QUALITY_4K = 0xFFFFD700.toInt()      // Yellow/Gold
    const val QUALITY_1080P = 0xFF4A90D9.toInt()   // Blue
    const val QUALITY_720P = 0xFFE74C3C.toInt()    // Red
    const val QUALITY_OTHER = 0xFF888888.toInt()   // Gray

    // Debrid badge color (muted gray)
    const val DEBRID = 0xFF666666.toInt()

    // Size badge color
    const val SIZE = 0xFF888888.toInt()

    fun getQualityColor(quality: Quality): Int {
        return when (quality) {
            Quality.UHD_4K -> QUALITY_4K
            Quality.FHD_1080P -> QUALITY_1080P
            Quality.HD_720P -> QUALITY_720P
            Quality.SD, Quality.CAM, Quality.UNKNOWN -> QUALITY_OTHER
        }
    }
}
