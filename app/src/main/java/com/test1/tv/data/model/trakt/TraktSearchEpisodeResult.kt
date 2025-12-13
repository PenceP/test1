package com.test1.tv.data.model.trakt

import com.google.gson.annotations.SerializedName

data class TraktSearchEpisodeResult(
    @SerializedName("episode")
    val episode: TraktEpisode,
    @SerializedName("show")
    val show: TraktShow
)
