package com.test1.tv.data.remote.model.premiumize

import com.google.gson.annotations.SerializedName

/**
 * Response from /account/info endpoint
 */
data class PremiumizeAccountResponse(
    @SerializedName("status")
    val status: String?,
    @SerializedName("customer_id")
    val customerId: String?,
    @SerializedName("premium_until")
    val premiumUntil: Long?,
    @SerializedName("limit_used")
    val limitUsed: Double?,
    @SerializedName("space_used")
    val spaceUsed: Double?,
    @SerializedName("points_used")
    val pointsUsed: Double?,
    @SerializedName("points_available")
    val pointsAvailable: Double?,
    @SerializedName("fair_use_used")
    val fairUseUsed: Double?,
    @SerializedName("message")
    val message: String?
)

/**
 * Response from /cache/check endpoint
 */
data class PremiumizeCacheCheckResponse(
    @SerializedName("status")
    val status: String?,
    @SerializedName("response")
    val response: List<Boolean>?,
    @SerializedName("transcoded")
    val transcoded: List<Boolean>?,
    @SerializedName("filename")
    val filename: List<String>?,
    @SerializedName("filesize")
    val filesize: List<Long>?,
    @SerializedName("message")
    val message: String?
)

/**
 * Response from /transfer/directdl endpoint
 */
data class PremiumizeDirectLinkResponse(
    @SerializedName("status")
    val status: String?,
    @SerializedName("content")
    val content: List<PremiumizeContent>?,
    @SerializedName("filename")
    val filename: String?,
    @SerializedName("filesize")
    val filesize: Long?,
    @SerializedName("message")
    val message: String?
)

data class PremiumizeContent(
    @SerializedName("path")
    val path: String?,
    @SerializedName("size")
    val size: Long?,
    @SerializedName("link")
    val link: String?,
    @SerializedName("stream_link")
    val streamLink: String?,
    @SerializedName("transcode_status")
    val transcodeStatus: String?
)

/**
 * Response from /transfer/create endpoint
 */
data class PremiumizeTransferResponse(
    @SerializedName("status")
    val status: String?,
    @SerializedName("id")
    val id: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("message")
    val message: String?
)
