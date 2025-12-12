package com.test1.tv.data.subtitle

import androidx.media3.common.Format

/**
 * Represents different subtitle options available for the player
 */
sealed class SubtitleOption {
    /**
     * Embedded subtitle track from the video file
     */
    data class Embedded(
        val trackIndex: Int,
        val groupIndex: Int,
        val language: String,
        val label: String,
        val isForced: Boolean = false,
        val isDefault: Boolean = false
    ) : SubtitleOption() {
        override fun getDisplayLabel(): String = buildString {
            append(label)
            if (isForced) append(" [Forced]")
            if (isDefault) append(" [Default]")
        }
    }

    /**
     * External subtitle from OpenSubtitles or other providers
     */
    data class External(
        val subtitle: SubtitleResult
    ) : SubtitleOption() {
        override fun getDisplayLabel(): String = subtitle.getDisplayLabel()
    }

    /**
     * Subtitles turned off
     */
    data object Off : SubtitleOption() {
        override fun getDisplayLabel(): String = "Off"
    }

    abstract fun getDisplayLabel(): String
}

/**
 * Result from subtitle search (OpenSubtitles, etc.)
 */
data class SubtitleResult(
    val id: String,
    val fileName: String,
    val language: String,
    val languageCode: String,
    val downloadUrl: String,
    val rating: Float?,
    val downloadCount: Int?,
    val hashMatched: Boolean = false,
    val aiTranslated: Boolean = false,
    val machineTranslated: Boolean = false,
    val format: String = "srt",
    val fps: Float? = null,
    val hearingImpaired: Boolean = false,
    val foreignPartsOnly: Boolean = false,
    val uploadDate: String? = null,
    val uploader: String? = null
) {
    fun getDisplayLabel(): String = buildString {
        append(language)
        if (hashMatched) append(" [Hash]")
        if (hearingImpaired) append(" [HI]")
        if (foreignPartsOnly) append(" [Foreign]")
        rating?.let { append(" (${String.format("%.1f", it)})") }
    }

    fun getDetailedLabel(): String = buildString {
        append(language)
        append(" - ")
        append(fileName.take(50))
        if (fileName.length > 50) append("...")
        downloadCount?.let { append(" | ${it} downloads") }
    }
}

/**
 * Query parameters for subtitle search
 */
data class SubtitleQuery(
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val title: String? = null,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val hash: String? = null,
    val fileSize: Long? = null,
    val languages: List<String> = listOf("en"),
    val type: QueryType = QueryType.MOVIE
) {
    enum class QueryType { MOVIE, EPISODE }

    fun isEpisode(): Boolean = type == QueryType.EPISODE && season != null && episode != null
}

/**
 * Subtitle preferences for user settings
 */
data class SubtitlePreferences(
    val preferredLanguages: List<String> = listOf("en"),
    val preferHashMatch: Boolean = true,
    val preferHearingImpaired: Boolean = false,
    val autoSelectSubtitle: Boolean = true,
    val fontSize: Float = 1.0f,
    val fontColor: Int = 0xFFFFFFFF.toInt(),
    val backgroundColor: Int = 0x80000000.toInt(),
    val edgeType: Int = 0 // 0=none, 1=outline, 2=drop_shadow
)

/**
 * Extension function to extract subtitle info from ExoPlayer Format
 */
fun Format.toEmbeddedSubtitleInfo(): SubtitleOption.Embedded? {
    // Only process text tracks
    if (sampleMimeType?.startsWith("text/") != true &&
        sampleMimeType?.startsWith("application/") != true) {
        return null
    }

    return SubtitleOption.Embedded(
        trackIndex = -1, // Will be set by caller
        groupIndex = -1, // Will be set by caller
        language = language ?: "Unknown",
        label = label ?: language ?: "Subtitle",
        isForced = (selectionFlags and androidx.media3.common.C.SELECTION_FLAG_FORCED) != 0,
        isDefault = (selectionFlags and androidx.media3.common.C.SELECTION_FLAG_DEFAULT) != 0
    )
}
