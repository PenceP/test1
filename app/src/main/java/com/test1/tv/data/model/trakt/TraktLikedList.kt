package com.test1.tv.data.model.trakt

import com.google.gson.annotations.SerializedName

/**
 * Wrapper for liked list response from Trakt API.
 * The liked lists endpoint returns an array of objects containing the list inside.
 */
data class TraktLikedList(
    @SerializedName("liked_at") val likedAt: String?,
    @SerializedName("list") val list: TraktUserList?
)
