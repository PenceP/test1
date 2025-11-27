package com.test1.tv.data.model.trakt

import com.google.gson.annotations.SerializedName

data class TraktDeviceCodeResponse(
    @SerializedName("device_code")
    val deviceCode: String,
    @SerializedName("user_code")
    val userCode: String,
    @SerializedName("verification_url")
    val verificationUrl: String,
    @SerializedName("expires_in")
    val expiresIn: Int,
    @SerializedName("interval")
    val interval: Int
)
