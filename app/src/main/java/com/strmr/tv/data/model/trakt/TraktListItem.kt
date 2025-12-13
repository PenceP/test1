package com.strmr.tv.data.model.trakt

import com.google.gson.annotations.SerializedName

data class TraktListItem(
    @SerializedName("listed_at") val listedAt: String?,
    @SerializedName("movie") val movie: TraktMovie?,
    @SerializedName("show") val show: TraktShow?
)
