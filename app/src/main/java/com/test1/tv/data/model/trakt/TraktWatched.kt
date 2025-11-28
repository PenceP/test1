package com.test1.tv.data.model.trakt

import com.google.gson.annotations.SerializedName

data class TraktWatchedMovie(
    @SerializedName("plays") val plays: Int?,
    @SerializedName("last_watched_at") val lastWatchedAt: String?,
    @SerializedName("movie") val movie: TraktMovie?
)

data class TraktWatchedShow(
    @SerializedName("plays") val plays: Int?,
    @SerializedName("last_watched_at") val lastWatchedAt: String?,
    @SerializedName("show") val show: TraktShow?
)

data class TraktShowProgress(
    @SerializedName("aired") val aired: Int?,
    @SerializedName("completed") val completed: Int?,
    @SerializedName("next_episode") val nextEpisode: TraktEpisode?
)
