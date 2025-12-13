package com.strmr.tv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents update information from GitHub Releases
 */
data class UpdateInfo(
    @SerializedName("version")
    val version: String,

    @SerializedName("versionCode")
    val versionCode: Int,

    @SerializedName("releaseDate")
    val releaseDate: String,

    @SerializedName("apkUrl")
    val apkUrl: String,

    @SerializedName("releaseNotes")
    val releaseNotesUrl: String,

    @SerializedName("minAndroidSdk")
    val minAndroidSdk: Int
)

/**
 * Result of update check
 */
sealed class UpdateCheckResult {
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateCheckResult()
    data object NoUpdateAvailable : UpdateCheckResult()
    data class Error(val message: String, val exception: Throwable? = null) : UpdateCheckResult()
}
