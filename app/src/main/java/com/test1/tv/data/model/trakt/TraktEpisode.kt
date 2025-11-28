package com.test1.tv.data.model.trakt

import com.google.gson.annotations.SerializedName

data class TraktEpisode(
    @SerializedName("season") val season: Int?,
    @SerializedName("number") val number: Int?,
    @SerializedName("title") val title: String?,
    @SerializedName("ids") val ids: TraktIds?,
    @SerializedName("runtime") val runtime: Int? = null,
    @SerializedName("first_aired") val firstAired: String? = null
)
