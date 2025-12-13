package com.strmr.tv.data.model.trakt

import com.google.gson.annotations.SerializedName

data class TraktTrendingShow(
    @SerializedName("watchers")
    val watchers: Int,
    @SerializedName("show")
    val show: TraktShow
)

data class TraktShow(
    @SerializedName("title")
    val title: String,
    @SerializedName("year")
    val year: Int?,
    @SerializedName("rating")
    val rating: Double? = null,
    @SerializedName("ids")
    val ids: TraktIds
)
