package com.strmr.tv.data.model.trakt

import com.google.gson.annotations.SerializedName

data class TraktUserList(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String?,
    @SerializedName("privacy") val privacy: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("display_numbers") val displayNumbers: Boolean?,
    @SerializedName("allow_comments") val allowComments: Boolean?,
    @SerializedName("sort_by") val sortBy: String?,
    @SerializedName("sort_how") val sortHow: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("item_count") val itemCount: Int?,
    @SerializedName("comment_count") val commentCount: Int?,
    @SerializedName("likes") val likes: Int?,
    @SerializedName("ids") val ids: TraktListIds?,
    @SerializedName("user") val user: TraktUser?
)

data class TraktListIds(
    @SerializedName("trakt") val trakt: Int?,
    @SerializedName("slug") val slug: String?
)
