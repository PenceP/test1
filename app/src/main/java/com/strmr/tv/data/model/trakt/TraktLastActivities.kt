package com.strmr.tv.data.model.trakt

import com.google.gson.annotations.SerializedName

data class TraktLastActivities(
    @SerializedName("all") val all: String?,
    @SerializedName("movies") val movies: TraktActivityGroup?,
    @SerializedName("shows") val shows: TraktActivityGroup?,
    @SerializedName("episodes") val episodes: TraktActivityGroup?,
    @SerializedName("seasons") val seasons: TraktActivityGroup?,
    @SerializedName("comments") val comments: TraktActivityGroup?,
    @SerializedName("lists") val lists: TraktActivityGroup?
)

data class TraktActivityGroup(
    @SerializedName("watched_at") val watchedAt: String?,
    @SerializedName("collected_at") val collectedAt: String?,
    @SerializedName("watchlisted_at") val watchlistedAt: String?,
    @SerializedName("paused_at") val pausedAt: String?,
    @SerializedName("hidden_at") val hiddenAt: String?
)
