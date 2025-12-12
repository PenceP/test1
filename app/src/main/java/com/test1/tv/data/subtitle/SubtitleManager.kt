package com.test1.tv.data.subtitle

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import com.test1.tv.data.local.entity.PlayerSettings
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.repository.PlayerSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages subtitle discovery, loading, and selection for the video player.
 *
 * Features:
 * - Embedded subtitle track discovery from video streams
 * - External subtitle search from OpenSubtitles
 * - Auto-selection based on user language preferences
 * - Subtitle caching for offline use
 */
@Singleton
class SubtitleManager @Inject constructor(
    private val openSubtitlesProvider: OpenSubtitlesProvider,
    private val playerSettingsRepository: PlayerSettingsRepository,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SubtitleManager"

        private val LANGUAGE_NAMES = mapOf(
            "en" to "English",
            "eng" to "English",
            "es" to "Spanish",
            "spa" to "Spanish",
            "fr" to "French",
            "fra" to "French",
            "fre" to "French",
            "de" to "German",
            "deu" to "German",
            "ger" to "German",
            "it" to "Italian",
            "ita" to "Italian",
            "pt" to "Portuguese",
            "por" to "Portuguese",
            "nl" to "Dutch",
            "nld" to "Dutch",
            "dut" to "Dutch",
            "pl" to "Polish",
            "pol" to "Polish",
            "ru" to "Russian",
            "rus" to "Russian",
            "ja" to "Japanese",
            "jpn" to "Japanese",
            "ko" to "Korean",
            "kor" to "Korean",
            "zh" to "Chinese",
            "zho" to "Chinese",
            "chi" to "Chinese",
            "ar" to "Arabic",
            "ara" to "Arabic",
            "he" to "Hebrew",
            "heb" to "Hebrew",
            "tr" to "Turkish",
            "tur" to "Turkish",
            "sv" to "Swedish",
            "swe" to "Swedish",
            "no" to "Norwegian",
            "nor" to "Norwegian",
            "da" to "Danish",
            "dan" to "Danish",
            "fi" to "Finnish",
            "fin" to "Finnish",
            "und" to "Undetermined"
        )
    }

    /**
     * Get all available subtitle options for the current video.
     * Returns embedded subtitles first, then external subtitles from providers.
     *
     * @param tracks Current ExoPlayer tracks
     * @param contentItem Content being played (for metadata)
     * @param season Season number for TV episodes (null for movies)
     * @param episode Episode number for TV episodes (null for movies)
     */
    suspend fun getAvailableSubtitles(
        tracks: Tracks,
        contentItem: ContentItem?,
        season: Int? = null,
        episode: Int? = null
    ): List<SubtitleOption> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SubtitleOption>()

        // Always add "Off" option first
        results.add(SubtitleOption.Off)

        // 1. Add embedded subtitles (show at top of list after "Off")
        val embeddedSubtitles = extractEmbeddedSubtitles(tracks)
        results.addAll(embeddedSubtitles)

        Log.d(TAG, "Found ${embeddedSubtitles.size} embedded subtitle tracks")

        // 2. Search for external subtitles if content info is available
        if (contentItem != null) {
            val externalSubtitles = searchExternalSubtitles(contentItem, season, episode)
            results.addAll(externalSubtitles)
            Log.d(TAG, "Found ${externalSubtitles.size} external subtitles")
        }

        results
    }

    /**
     * Extract embedded subtitle tracks from ExoPlayer Tracks object
     */
    private fun extractEmbeddedSubtitles(tracks: Tracks): List<SubtitleOption.Embedded> {
        val subtitles = mutableListOf<SubtitleOption.Embedded>()

        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type != C.TRACK_TYPE_TEXT) continue

            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)

                // Skip non-subtitle formats
                if (!isSubtitleFormat(format)) continue

                val subtitle = SubtitleOption.Embedded(
                    trackIndex = trackIndex,
                    groupIndex = groupIndex,
                    language = getLanguageName(format.language),
                    label = format.label ?: getLanguageName(format.language),
                    isForced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0,
                    isDefault = (format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0
                )
                subtitles.add(subtitle)
            }
        }

        // Sort: Default first, then forced, then by language
        return subtitles.sortedWith(
            compareByDescending<SubtitleOption.Embedded> { it.isDefault }
                .thenByDescending { it.isForced }
                .thenBy { it.language }
        )
    }

    private fun isSubtitleFormat(format: Format): Boolean {
        val mimeType = format.sampleMimeType ?: return false
        return mimeType.startsWith("text/") ||
                mimeType.startsWith("application/x-subrip") ||
                mimeType.startsWith("application/ttml") ||
                mimeType == MimeTypes.APPLICATION_SUBRIP ||
                mimeType == MimeTypes.APPLICATION_TTML ||
                mimeType == MimeTypes.TEXT_VTT ||
                mimeType == MimeTypes.APPLICATION_MP4VTT ||
                mimeType == MimeTypes.APPLICATION_CEA608 ||
                mimeType == MimeTypes.APPLICATION_CEA708
    }

    /**
     * Search for external subtitles from providers
     */
    private suspend fun searchExternalSubtitles(
        contentItem: ContentItem,
        season: Int?,
        episode: Int?
    ): List<SubtitleOption.External> {
        val settings = playerSettingsRepository.getSettings()
        val preferredLanguage = settings.defaultSubtitleLanguage ?: "en"

        val query = SubtitleQuery(
            imdbId = contentItem.imdbId,
            tmdbId = contentItem.tmdbId,
            title = contentItem.title,
            year = contentItem.year?.toIntOrNull(),
            season = season?.takeIf { it > 0 },
            episode = episode?.takeIf { it > 0 },
            languages = listOf(preferredLanguage),
            type = if (season != null && season > 0) SubtitleQuery.QueryType.EPISODE else SubtitleQuery.QueryType.MOVIE
        )

        return openSubtitlesProvider.search(query)
            .getOrElse { emptyList() }
            .map { SubtitleOption.External(it) }
    }

    /**
     * Download an external subtitle and return the file path
     */
    suspend fun downloadSubtitle(subtitle: SubtitleResult): Result<File> {
        return openSubtitlesProvider.download(subtitle)
    }

    /**
     * Create a MediaItem.SubtitleConfiguration for an external subtitle file
     */
    fun createSubtitleConfiguration(
        subtitleFile: File,
        language: String,
        label: String
    ): MediaItem.SubtitleConfiguration {
        val mimeType = when {
            subtitleFile.name.endsWith(".srt", true) -> MimeTypes.APPLICATION_SUBRIP
            subtitleFile.name.endsWith(".vtt", true) -> MimeTypes.TEXT_VTT
            subtitleFile.name.endsWith(".ttml", true) ||
                    subtitleFile.name.endsWith(".xml", true) -> MimeTypes.APPLICATION_TTML

            subtitleFile.name.endsWith(".ass", true) ||
                    subtitleFile.name.endsWith(".ssa", true) -> MimeTypes.TEXT_SSA

            else -> MimeTypes.APPLICATION_SUBRIP
        }

        return MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subtitleFile))
            .setMimeType(mimeType)
            .setLanguage(language)
            .setLabel(label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    }

    /**
     * Get the recommended subtitle based on user preferences
     */
    suspend fun getRecommendedSubtitle(options: List<SubtitleOption>): SubtitleOption? {
        val settings = playerSettingsRepository.getSettings()
        val preferredLanguage = settings.defaultSubtitleLanguage?.lowercase() ?: return null

        // Priority 1: Embedded subtitle matching preferred language
        val embeddedMatch = options.filterIsInstance<SubtitleOption.Embedded>()
            .firstOrNull { it.language.lowercase().contains(preferredLanguage) }
        if (embeddedMatch != null) return embeddedMatch

        // Priority 2: External subtitle with hash match
        val hashMatch = options.filterIsInstance<SubtitleOption.External>()
            .firstOrNull { it.subtitle.hashMatched && it.subtitle.languageCode.lowercase() == preferredLanguage }
        if (hashMatch != null) return hashMatch

        // Priority 3: Any external subtitle in preferred language
        val languageMatch = options.filterIsInstance<SubtitleOption.External>()
            .firstOrNull { it.subtitle.languageCode.lowercase() == preferredLanguage }

        return languageMatch
    }

    /**
     * Clear cached subtitles
     */
    fun clearCache() {
        openSubtitlesProvider.clearCache()
    }

    private fun getLanguageName(code: String?): String {
        if (code == null) return "Unknown"
        return LANGUAGE_NAMES[code.lowercase()] ?: code.uppercase()
    }
}
