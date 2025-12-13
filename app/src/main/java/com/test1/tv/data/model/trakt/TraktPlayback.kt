package com.test1.tv.data.model.trakt

import com.google.gson.annotations.SerializedName
import java.time.Instant

data class TraktPlaybackItem(
    @SerializedName("id") val id: Long?,
    @SerializedName("type") val type: String?,
    @SerializedName("progress") val progress: Double?,
    @SerializedName("paused_at") val pausedAt: String?,
    @SerializedName("movie") val movie: TraktMovie?,
    @SerializedName("show") val show: TraktShow?,
    @SerializedName("episode") val episode: TraktEpisode?
)

data class RemovePlaybackRequest(
    val id: Long
)
