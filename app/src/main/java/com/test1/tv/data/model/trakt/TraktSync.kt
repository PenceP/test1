package com.test1.tv.data.model.trakt

import com.google.gson.annotations.SerializedName

// ==================== SYNC REQUEST/RESPONSE MODELS ====================

data class TraktSyncRequest(
    val movies: List<TraktSyncMovie>? = null,
    val shows: List<TraktSyncShow>? = null,
    val episodes: List<TraktSyncEpisode>? = null
)

data class TraktSyncMovie(
    val ids: TraktIds
)

data class TraktSyncShow(
    val ids: TraktIds,
    val seasons: List<TraktSyncSeason>? = null
)

data class TraktSyncSeason(
    val number: Int,
    val episodes: List<TraktSyncSeasonEpisode>? = null
)

data class TraktSyncSeasonEpisode(
    val number: Int
)

data class TraktSyncEpisode(
    val ids: TraktIds
)

data class TraktSyncResponse(
    val added: TraktSyncCounts? = null,
    val deleted: TraktSyncCounts? = null,
    val existing: TraktSyncCounts? = null,
    @SerializedName("not_found")
    val notFound: TraktSyncNotFound? = null
)

data class TraktSyncCounts(
    val movies: Int? = null,
    val shows: Int? = null,
    val seasons: Int? = null,
    val episodes: Int? = null
)

data class TraktSyncNotFound(
    val movies: List<TraktSyncMovie>? = null,
    val shows: List<TraktSyncShow>? = null,
    val episodes: List<TraktSyncEpisode>? = null
)

data class TraktRatingRequest(
    val movies: List<TraktRatingItem>? = null,
    val shows: List<TraktRatingItem>? = null,
    val episodes: List<TraktRatingItem>? = null
)

data class TraktRatingItem(
    val ids: TraktIds,
    val rating: Int,  // 1-10
    @SerializedName("rated_at")
    val ratedAt: String? = null  // ISO 8601 format
)
