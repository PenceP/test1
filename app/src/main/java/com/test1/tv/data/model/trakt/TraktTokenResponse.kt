package com.test1.tv.data.model.trakt

import com.google.gson.annotations.SerializedName

data class TraktTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String?,
    @SerializedName("expires_in") val expiresIn: Long,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("scope") val scope: String?,
    @SerializedName("created_at") val createdAt: Long?
)
