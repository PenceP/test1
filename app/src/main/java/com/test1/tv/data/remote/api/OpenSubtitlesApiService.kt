package com.test1.tv.data.remote.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * OpenSubtitles.com API Service (REST API v1)
 * API Documentation: https://opensubtitles.stoplight.io/
 */
interface OpenSubtitlesApiService {

    companion object {
        const val BASE_URL = "https://api.opensubtitles.com/api/v1/"
        const val USER_AGENT = "STRMR v1.0"
    }

    /**
     * Search for subtitles
     * https://opensubtitles.stoplight.io/docs/opensubtitles-api/a172317bd5ccc-search-for-subtitles
     */
    @GET("subtitles")
    suspend fun searchSubtitles(
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String = USER_AGENT,
        @Query("imdb_id") imdbId: String? = null,
        @Query("tmdb_id") tmdbId: Int? = null,
        @Query("query") query: String? = null,
        @Query("year") year: Int? = null,
        @Query("season_number") season: Int? = null,
        @Query("episode_number") episode: Int? = null,
        @Query("moviehash") movieHash: String? = null,
        @Query("languages") languages: String? = null, // Comma-separated: "en,es,fr"
        @Query("type") type: String? = null, // "movie", "episode", "all"
        @Query("hearing_impaired") hearingImpaired: String? = null, // "include", "exclude", "only"
        @Query("foreign_parts_only") foreignPartsOnly: String? = null, // "include", "exclude", "only"
        @Query("machine_translated") machineTranslated: String? = null, // "include", "exclude"
        @Query("ai_translated") aiTranslated: String? = null, // "include", "exclude"
        @Query("order_by") orderBy: String? = null, // "download_count", "rating", "upload_date"
        @Query("order_direction") orderDirection: String? = null, // "asc", "desc"
        @Query("page") page: Int? = null
    ): OpenSubtitlesSearchResponse

    /**
     * Get download link for a subtitle file
     * https://opensubtitles.stoplight.io/docs/opensubtitles-api/6be7f6ae2d918-download
     */
    @POST("download")
    suspend fun getDownloadLink(
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String = USER_AGENT,
        @Header("Authorization") authToken: String? = null,
        @Body request: OpenSubtitlesDownloadRequest
    ): OpenSubtitlesDownloadResponse

    /**
     * Login to OpenSubtitles (optional, for higher rate limits)
     */
    @POST("login")
    suspend fun login(
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String = USER_AGENT,
        @Body request: OpenSubtitlesLoginRequest
    ): OpenSubtitlesLoginResponse

    /**
     * Get available subtitle languages
     */
    @GET("infos/languages")
    suspend fun getLanguages(
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): OpenSubtitlesLanguagesResponse
}

// ==================== Request/Response Models ====================

data class OpenSubtitlesSearchResponse(
    val total_pages: Int?,
    val total_count: Int?,
    val per_page: Int?,
    val page: Int?,
    val data: List<OpenSubtitlesSubtitle>?
)

data class OpenSubtitlesSubtitle(
    val id: String?,
    val type: String?,
    val attributes: OpenSubtitlesAttributes?
)

data class OpenSubtitlesAttributes(
    val subtitle_id: String?,
    val language: String?,
    val download_count: Int?,
    val new_download_count: Int?,
    val hearing_impaired: Boolean?,
    val hd: Boolean?,
    val fps: Float?,
    val votes: Int?,
    val ratings: Float?,
    val from_trusted: Boolean?,
    val foreign_parts_only: Boolean?,
    val upload_date: String?,
    val ai_translated: Boolean?,
    val machine_translated: Boolean?,
    val release: String?,
    val comments: String?,
    val legacy_subtitle_id: Int?,
    val uploader: OpenSubtitlesUploader?,
    val feature_details: OpenSubtitlesFeatureDetails?,
    val url: String?,
    val related_links: List<OpenSubtitlesRelatedLink>?,
    val files: List<OpenSubtitlesFile>?,
    val moviehash_match: Boolean?
)

data class OpenSubtitlesUploader(
    val uploader_id: Int?,
    val name: String?,
    val rank: String?
)

data class OpenSubtitlesFeatureDetails(
    val feature_id: Int?,
    val feature_type: String?,
    val year: Int?,
    val title: String?,
    val movie_name: String?,
    val imdb_id: Int?,
    val tmdb_id: Int?,
    val season_number: Int?,
    val episode_number: Int?,
    val parent_imdb_id: Int?,
    val parent_title: String?,
    val parent_tmdb_id: Int?,
    val parent_feature_id: Int?
)

data class OpenSubtitlesRelatedLink(
    val label: String?,
    val url: String?,
    val img_url: String?
)

data class OpenSubtitlesFile(
    val file_id: Int?,
    val cd_number: Int?,
    val file_name: String?
)

data class OpenSubtitlesDownloadRequest(
    val file_id: Int,
    val sub_format: String? = null, // "srt", "webvtt"
    val file_name: String? = null,
    val in_fps: Int? = null,
    val out_fps: Int? = null,
    val timeshift: Int? = null,
    val force_download: Boolean? = null
)

data class OpenSubtitlesDownloadResponse(
    val link: String?,
    val file_name: String?,
    val requests: Int?,
    val remaining: Int?,
    val message: String?,
    val reset_time: String?,
    val reset_time_utc: String?
)

data class OpenSubtitlesLoginRequest(
    val username: String,
    val password: String
)

data class OpenSubtitlesLoginResponse(
    val user: OpenSubtitlesUser?,
    val base_url: String?,
    val token: String?,
    val status: Int?
)

data class OpenSubtitlesUser(
    val allowed_downloads: Int?,
    val allowed_translations: Int?,
    val level: String?,
    val user_id: Int?,
    val ext_installed: Boolean?,
    val vip: Boolean?
)

data class OpenSubtitlesLanguagesResponse(
    val data: List<OpenSubtitlesLanguage>?
)

data class OpenSubtitlesLanguage(
    val language_code: String?,
    val language_name: String?
)
