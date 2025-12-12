package com.test1.tv.data.subtitle

import android.content.Context
import android.util.Log
import com.test1.tv.BuildConfig
import com.test1.tv.data.remote.api.OpenSubtitlesApiService
import com.test1.tv.data.remote.api.OpenSubtitlesDownloadRequest
import com.test1.tv.data.remote.api.OpenSubtitlesSubtitle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for OpenSubtitles.com subtitle search and download
 */
@Singleton
class OpenSubtitlesProvider @Inject constructor(
    private val apiService: OpenSubtitlesApiService,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OpenSubtitlesProvider"
        private const val SUBTITLE_CACHE_DIR = "subtitles"
        private const val MAX_RESULTS = 20

        private val LANGUAGE_NAMES = mapOf(
            "en" to "English",
            "es" to "Spanish",
            "fr" to "French",
            "de" to "German",
            "it" to "Italian",
            "pt" to "Portuguese",
            "nl" to "Dutch",
            "pl" to "Polish",
            "ru" to "Russian",
            "ja" to "Japanese",
            "ko" to "Korean",
            "zh" to "Chinese",
            "ar" to "Arabic",
            "he" to "Hebrew",
            "tr" to "Turkish",
            "sv" to "Swedish",
            "no" to "Norwegian",
            "da" to "Danish",
            "fi" to "Finnish",
            "cs" to "Czech",
            "hu" to "Hungarian",
            "ro" to "Romanian",
            "el" to "Greek",
            "th" to "Thai",
            "vi" to "Vietnamese",
            "id" to "Indonesian",
            "ms" to "Malay"
        )
    }

    private val apiKey: String
        get() = BuildConfig.OPENSUBTITLES_API_KEY

    private val subtitleCacheDir: File by lazy {
        File(context.cacheDir, SUBTITLE_CACHE_DIR).also {
            if (!it.exists()) it.mkdirs()
        }
    }

    /**
     * Search for subtitles matching the query
     */
    suspend fun search(query: SubtitleQuery): Result<List<SubtitleResult>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            Log.w(TAG, "OpenSubtitles API key not configured")
            return@withContext Result.success(emptyList())
        }

        runCatching {
            val languagesParam = query.languages.joinToString(",")
            val typeParam = if (query.isEpisode()) "episode" else "movie"

            // Clean IMDB ID format (remove "tt" prefix if present, API expects just the number)
            val cleanImdbId = query.imdbId?.removePrefix("tt")?.takeIf { it.isNotBlank() }

            Log.d(TAG, "Searching subtitles: imdb=$cleanImdbId, tmdb=${query.tmdbId}, " +
                    "title=${query.title}, type=$typeParam, languages=$languagesParam")

            val response = apiService.searchSubtitles(
                apiKey = apiKey,
                imdbId = cleanImdbId,
                tmdbId = query.tmdbId,
                query = query.title,
                year = query.year,
                season = query.season,
                episode = query.episode,
                movieHash = query.hash,
                languages = languagesParam,
                type = typeParam,
                hearingImpaired = "include",
                machineTranslated = "exclude",
                aiTranslated = "include",
                orderBy = "download_count",
                orderDirection = "desc"
            )

            val subtitles = response.data?.mapNotNull { it.toSubtitleResult() } ?: emptyList()

            Log.d(TAG, "Found ${subtitles.size} subtitles")

            // Sort: hash matches first, then by download count/rating
            subtitles.sortedWith(
                compareByDescending<SubtitleResult> { it.hashMatched }
                    .thenByDescending { it.downloadCount ?: 0 }
                    .thenByDescending { it.rating ?: 0f }
            ).take(MAX_RESULTS)
        }.onFailure { error ->
            Log.e(TAG, "Subtitle search failed", error)
        }
    }

    /**
     * Download a subtitle file and return the local file path
     * Includes retry logic for transient 503 errors
     */
    suspend fun download(subtitle: SubtitleResult): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            // Check cache first
            val cacheFile = File(subtitleCacheDir, "${subtitle.id}.${subtitle.format}")
            if (cacheFile.exists()) {
                Log.d(TAG, "Using cached subtitle: ${cacheFile.absolutePath}")
                return@runCatching cacheFile
            }

            // Get download link from API
            val fileId = subtitle.id.toIntOrNull()
            if (fileId == null) {
                // If id is not numeric, try using the download URL directly
                return@runCatching downloadFromUrl(subtitle.downloadUrl, cacheFile)
            }

            // Try to get download link with retries for 503 errors
            // OpenSubtitles has aggressive rate limiting, use more retries with longer delays
            val downloadUrl = getDownloadLinkWithRetry(fileId, maxRetries = 5)

            Log.d(TAG, "Downloading subtitle from: $downloadUrl")
            downloadFromUrl(downloadUrl, cacheFile)
        }.onFailure { error ->
            Log.e(TAG, "Subtitle download failed", error)
        }
    }

    /**
     * Get download link with retry logic for transient 503 errors
     */
    private suspend fun getDownloadLinkWithRetry(fileId: Int, maxRetries: Int): String {
        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                val downloadResponse = apiService.getDownloadLink(
                    apiKey = apiKey,
                    request = OpenSubtitlesDownloadRequest(
                        file_id = fileId,
                        sub_format = "srt"
                    )
                )

                val link = downloadResponse.link
                if (link != null) {
                    return link
                }

                // No link but no exception - treat as error
                throw IllegalStateException("No download link in response")

            } catch (e: retrofit2.HttpException) {
                lastException = e
                if ((e.code() == 503 || e.code() == 429) && attempt < maxRetries) {
                    // Service temporarily unavailable or rate limited - wait and retry
                    // Use longer delays: 2s, 4s, 6s, 8s, 10s for aggressive rate limiting
                    val delayMs = attempt * 2000L
                    Log.w(TAG, "Got ${e.code()} error, retrying in ${delayMs}ms (attempt $attempt/$maxRetries)")
                    kotlinx.coroutines.delay(delayMs)
                } else {
                    throw e
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    val delayMs = attempt * 1000L
                    Log.w(TAG, "Download error, retrying in ${delayMs}ms: ${e.message}")
                    kotlinx.coroutines.delay(delayMs)
                } else {
                    throw e
                }
            }
        }

        throw lastException ?: IllegalStateException("Failed to get download link after $maxRetries attempts")
    }

    private fun downloadFromUrl(url: String, targetFile: File): File {
        URL(url).openStream().use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
        Log.d(TAG, "Downloaded subtitle to: ${targetFile.absolutePath}")
        return targetFile
    }

    /**
     * Clear the subtitle cache
     */
    fun clearCache() {
        subtitleCacheDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "Subtitle cache cleared")
    }

    /**
     * Convert API response to SubtitleResult
     */
    private fun OpenSubtitlesSubtitle.toSubtitleResult(): SubtitleResult? {
        val attrs = attributes ?: return null
        val file = attrs.files?.firstOrNull() ?: return null
        val fileId = file.file_id ?: return null

        return SubtitleResult(
            id = fileId.toString(),
            fileName = file.file_name ?: attrs.release ?: "Unknown",
            language = getLanguageName(attrs.language ?: "en"),
            languageCode = attrs.language ?: "en",
            downloadUrl = attrs.url ?: "",
            rating = attrs.ratings,
            downloadCount = attrs.download_count,
            hashMatched = attrs.moviehash_match == true,
            aiTranslated = attrs.ai_translated == true,
            machineTranslated = attrs.machine_translated == true,
            format = "srt",
            fps = attrs.fps,
            hearingImpaired = attrs.hearing_impaired == true,
            foreignPartsOnly = attrs.foreign_parts_only == true,
            uploadDate = attrs.upload_date,
            uploader = attrs.uploader?.name
        )
    }

    /**
     * Get full language name from ISO code
     */
    private fun getLanguageName(code: String): String {
        return LANGUAGE_NAMES[code.lowercase()] ?: code.uppercase()
    }
}
