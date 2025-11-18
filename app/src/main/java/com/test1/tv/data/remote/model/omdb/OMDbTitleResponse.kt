package com.test1.tv.data.remote.model.omdb

import com.google.gson.annotations.SerializedName

data class OMDbTitleResponse(
    @SerializedName("imdbRating")
    val imdbRating: String?,
    @SerializedName("Ratings")
    val ratings: List<OMDbRating>?,
    @SerializedName("Response")
    val response: String?,
    @SerializedName("Error")
    val error: String?
)

data class OMDbRating(
    @SerializedName("Source")
    val source: String?,
    @SerializedName("Value")
    val value: String?
)
