package com.strmr.tv.data.remote.model.realdebrid

import com.google.gson.annotations.SerializedName

/**
 * Response from OAuth device code request
 * GET https://api.real-debrid.com/oauth/v2/device/code
 */
data class RealDebridDeviceCodeResponse(
    @SerializedName("device_code")
    val deviceCode: String,
    @SerializedName("user_code")
    val userCode: String,
    @SerializedName("interval")
    val interval: Int,
    @SerializedName("expires_in")
    val expiresIn: Int,
    @SerializedName("verification_url")
    val verificationUrl: String,
    @SerializedName("direct_verification_url")
    val directVerificationUrl: String?
)

/**
 * Response from device credentials request (for opensource apps)
 * GET https://api.real-debrid.com/oauth/v2/device/credentials
 */
data class RealDebridCredentialsResponse(
    @SerializedName("client_id")
    val clientId: String?,
    @SerializedName("client_secret")
    val clientSecret: String?,
    @SerializedName("error")
    val error: String?,
    @SerializedName("error_code")
    val errorCode: Int?
)

/**
 * Response from OAuth token request
 * POST https://api.real-debrid.com/oauth/v2/token
 */
data class RealDebridTokenResponse(
    @SerializedName("access_token")
    val accessToken: String?,
    @SerializedName("expires_in")
    val expiresIn: Int?,
    @SerializedName("token_type")
    val tokenType: String?,
    @SerializedName("refresh_token")
    val refreshToken: String?,
    @SerializedName("error")
    val error: String?,
    @SerializedName("error_description")
    val errorDescription: String?
)

/**
 * Response from /user endpoint
 * GET https://api.real-debrid.com/rest/1.0/user
 */
data class RealDebridUserResponse(
    @SerializedName("id")
    val id: Int,
    @SerializedName("username")
    val username: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("points")
    val points: Int?,
    @SerializedName("locale")
    val locale: String?,
    @SerializedName("avatar")
    val avatar: String?,
    @SerializedName("type")
    val type: String,  // "premium" or "free"
    @SerializedName("premium")
    val premium: Int,  // Days remaining
    @SerializedName("expiration")
    val expiration: String?  // ISO date string
)

/**
 * Response from /unrestrict/link endpoint
 * POST https://api.real-debrid.com/rest/1.0/unrestrict/link
 */
data class RealDebridUnrestrictResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("filename")
    val filename: String,
    @SerializedName("mimeType")
    val mimeType: String?,
    @SerializedName("filesize")
    val filesize: Long,
    @SerializedName("link")
    val link: String?,
    @SerializedName("host")
    val host: String,
    @SerializedName("host_icon")
    val hostIcon: String?,
    @SerializedName("chunks")
    val chunks: Int?,
    @SerializedName("crc")
    val crc: Int?,
    @SerializedName("download")
    val download: String,  // Direct download link
    @SerializedName("streamable")
    val streamable: Int?,
    @SerializedName("error")
    val error: String?,
    @SerializedName("error_code")
    val errorCode: Int?
)

/**
 * Response from /torrents/instantAvailability endpoint
 * GET https://api.real-debrid.com/rest/1.0/torrents/instantAvailability/{hash}
 * Returns a map where keys are the hashes and values contain cached file info
 */
data class RealDebridInstantAvailability(
    val rd: List<Map<String, RealDebridCachedFile>>?
)

data class RealDebridCachedFile(
    @SerializedName("filename")
    val filename: String,
    @SerializedName("filesize")
    val filesize: Long
)

/**
 * Response from /torrents/addMagnet endpoint
 * POST https://api.real-debrid.com/rest/1.0/torrents/addMagnet
 */
data class RealDebridAddMagnetResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("uri")
    val uri: String?,
    @SerializedName("error")
    val error: String?,
    @SerializedName("error_code")
    val errorCode: Int?
)

/**
 * Response from /torrents/info endpoint
 * GET https://api.real-debrid.com/rest/1.0/torrents/info/{id}
 */
data class RealDebridTorrentInfo(
    @SerializedName("id")
    val id: String,
    @SerializedName("filename")
    val filename: String,
    @SerializedName("original_filename")
    val originalFilename: String?,
    @SerializedName("hash")
    val hash: String,
    @SerializedName("bytes")
    val bytes: Long,
    @SerializedName("original_bytes")
    val originalBytes: Long?,
    @SerializedName("host")
    val host: String,
    @SerializedName("split")
    val split: Int,
    @SerializedName("progress")
    val progress: Int,
    @SerializedName("status")
    val status: String,  // "magnet_error", "magnet_conversion", "waiting_files_selection", "queued", "downloading", "downloaded", "error", "virus", "compressing", "uploading", "dead"
    @SerializedName("added")
    val added: String,
    @SerializedName("files")
    val files: List<RealDebridTorrentFile>?,
    @SerializedName("links")
    val links: List<String>?,
    @SerializedName("ended")
    val ended: String?,
    @SerializedName("speed")
    val speed: Long?,
    @SerializedName("seeders")
    val seeders: Int?
)

data class RealDebridTorrentFile(
    @SerializedName("id")
    val id: Int,
    @SerializedName("path")
    val path: String,
    @SerializedName("bytes")
    val bytes: Long,
    @SerializedName("selected")
    val selected: Int  // 0 or 1
)

/**
 * Error response wrapper for Real-Debrid API errors
 */
data class RealDebridErrorResponse(
    @SerializedName("error")
    val error: String?,
    @SerializedName("error_code")
    val errorCode: Int?
)
