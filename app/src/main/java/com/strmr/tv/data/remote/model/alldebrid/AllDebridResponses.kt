package com.strmr.tv.data.remote.model.alldebrid

import com.google.gson.annotations.SerializedName

/**
 * AllDebrid API wrapper - all responses are wrapped in this structure
 */
data class AllDebridResponse<T>(
    @SerializedName("status")
    val status: String,  // "success" or "error"
    @SerializedName("data")
    val data: T?,
    @SerializedName("error")
    val error: AllDebridError?
)

data class AllDebridError(
    @SerializedName("code")
    val code: String,
    @SerializedName("message")
    val message: String
)

/**
 * Response from /pin/get endpoint (device authentication)
 * GET https://api.alldebrid.com/v4/pin/get
 */
data class AllDebridPinData(
    @SerializedName("pin")
    val pin: String,
    @SerializedName("check")
    val check: String,
    @SerializedName("expires_in")
    val expiresIn: Int,
    @SerializedName("user_url")
    val userUrl: String,
    @SerializedName("base_url")
    val baseUrl: String,
    @SerializedName("check_url")
    val checkUrl: String
)

/**
 * Response from /pin/check endpoint (poll for authentication)
 * GET https://api.alldebrid.com/v4/pin/check
 */
data class AllDebridPinCheckData(
    @SerializedName("activated")
    val activated: Boolean,
    @SerializedName("expires_in")
    val expiresIn: Int?,
    @SerializedName("apikey")
    val apikey: String?  // Only present when activated=true
)

/**
 * Response from /user endpoint
 * GET https://api.alldebrid.com/v4/user
 */
data class AllDebridUserData(
    @SerializedName("user")
    val user: AllDebridUser
)

data class AllDebridUser(
    @SerializedName("username")
    val username: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("isPremium")
    val isPremium: Boolean,
    @SerializedName("isSubscribed")
    val isSubscribed: Boolean?,
    @SerializedName("isTrial")
    val isTrial: Boolean?,
    @SerializedName("premiumUntil")
    val premiumUntil: Long?,  // Unix timestamp
    @SerializedName("lang")
    val lang: String?,
    @SerializedName("preferedDomain")
    val preferredDomain: String?,
    @SerializedName("fidelityPoints")
    val fidelityPoints: Int?,
    @SerializedName("limitedHostersQuotas")
    val limitedHostersQuotas: Map<String, AllDebridQuota>?
)

data class AllDebridQuota(
    @SerializedName("used")
    val used: Long,
    @SerializedName("remaining")
    val remaining: Long,
    @SerializedName("limit")
    val limit: Long
)

/**
 * Response from /link/unlock endpoint
 * POST https://api.alldebrid.com/v4/link/unlock
 */
data class AllDebridUnlockData(
    @SerializedName("link")
    val link: String,
    @SerializedName("host")
    val host: String,
    @SerializedName("filename")
    val filename: String,
    @SerializedName("streaming")
    val streaming: List<AllDebridStreamingLink>?,
    @SerializedName("paws")
    val paws: Boolean?,
    @SerializedName("filesize")
    val filesize: Long,
    @SerializedName("id")
    val id: String?,
    @SerializedName("hostDomain")
    val hostDomain: String?,
    @SerializedName("delayed")
    val delayed: String?  // Delayed ID if link needs processing
)

data class AllDebridStreamingLink(
    @SerializedName("quality")
    val quality: String,
    @SerializedName("ext")
    val ext: String,
    @SerializedName("filesize")
    val filesize: Long,
    @SerializedName("link")
    val link: String
)

/**
 * Response from /magnet/instant endpoint (cache check)
 * GET https://api.alldebrid.com/v4/magnet/instant
 */
data class AllDebridInstantData(
    @SerializedName("magnets")
    val magnets: List<AllDebridMagnetStatus>
)

data class AllDebridMagnetStatus(
    @SerializedName("magnet")
    val magnet: String,
    @SerializedName("hash")
    val hash: String?,
    @SerializedName("instant")
    val instant: Boolean,
    @SerializedName("files")
    val files: List<AllDebridMagnetFile>?
)

data class AllDebridMagnetFile(
    @SerializedName("n")
    val name: String,  // File name
    @SerializedName("s")
    val size: Long     // File size in bytes
)

/**
 * Response from /magnet/upload endpoint
 * POST https://api.alldebrid.com/v4/magnet/upload
 */
data class AllDebridMagnetUploadData(
    @SerializedName("magnets")
    val magnets: List<AllDebridUploadedMagnet>
)

data class AllDebridUploadedMagnet(
    @SerializedName("magnet")
    val magnet: String,
    @SerializedName("hash")
    val hash: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("size")
    val size: Long?,
    @SerializedName("ready")
    val ready: Boolean,
    @SerializedName("id")
    val id: Long?,
    @SerializedName("error")
    val error: AllDebridMagnetError?
)

data class AllDebridMagnetError(
    @SerializedName("code")
    val code: String,
    @SerializedName("message")
    val message: String
)

/**
 * Response from /magnet/status endpoint
 * GET https://api.alldebrid.com/v4/magnet/status
 */
data class AllDebridMagnetStatusData(
    @SerializedName("magnets")
    val magnets: AllDebridMagnetDetail
)

data class AllDebridMagnetDetail(
    @SerializedName("id")
    val id: Long,
    @SerializedName("filename")
    val filename: String,
    @SerializedName("size")
    val size: Long,
    @SerializedName("hash")
    val hash: String?,
    @SerializedName("status")
    val status: String,  // "Ready", "Downloading", "Processing", "Uploading", "Error"
    @SerializedName("statusCode")
    val statusCode: Int,
    @SerializedName("downloaded")
    val downloaded: Long?,
    @SerializedName("uploaded")
    val uploaded: Long?,
    @SerializedName("seeders")
    val seeders: Int?,
    @SerializedName("downloadSpeed")
    val downloadSpeed: Long?,
    @SerializedName("uploadSpeed")
    val uploadSpeed: Long?,
    @SerializedName("uploadDate")
    val uploadDate: Long?,
    @SerializedName("completionDate")
    val completionDate: Long?,
    @SerializedName("links")
    val links: List<AllDebridMagnetLink>?
)

data class AllDebridMagnetLink(
    @SerializedName("link")
    val link: String,
    @SerializedName("filename")
    val filename: String,
    @SerializedName("size")
    val size: Long,
    @SerializedName("files")
    val files: List<AllDebridLinkFile>?
)

data class AllDebridLinkFile(
    @SerializedName("n")
    val name: String,
    @SerializedName("s")
    val size: Long,
    @SerializedName("l")
    val link: String?
)
