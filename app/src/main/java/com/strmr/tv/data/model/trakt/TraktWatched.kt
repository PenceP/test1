package com.strmr.tv.data.model.trakt

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
    @SerializedName("last_watched_at") val lastWatchedAt: String?,
    @SerializedName("seasons") val seasons: List<TraktSeasonProgress>?,
    @SerializedName("next_episode") val nextEpisode: TraktEpisode?
) {
    /**
     * Get a set of watched episode keys in format "S{season}E{episode}"
     */
    fun getWatchedEpisodeKeys(): Set<String> {
        val keys = mutableSetOf<String>()
        seasons?.forEach { season ->
            season.episodes?.forEach { episode ->
                if (episode.completed == true) {
                    keys.add("S${season.number}E${episode.number}")
                }
            }
        }
        return keys
    }

    /**
     * Check if a specific episode is watched
     */
    fun isEpisodeWatched(seasonNumber: Int, episodeNumber: Int): Boolean {
        return seasons?.find { it.number == seasonNumber }
            ?.episodes?.find { it.number == episodeNumber }
            ?.completed == true
    }

    /**
     * Check if an entire season is watched
     */
    fun isSeasonWatched(seasonNumber: Int): Boolean {
        val season = seasons?.find { it.number == seasonNumber } ?: return false
        return season.completed == season.aired && season.aired != null && season.aired > 0
    }
}

data class TraktSeasonProgress(
    @SerializedName("number") val number: Int?,
    @SerializedName("aired") val aired: Int?,
    @SerializedName("completed") val completed: Int?,
    @SerializedName("episodes") val episodes: List<TraktEpisodeProgress>?
)

data class TraktEpisodeProgress(
    @SerializedName("number") val number: Int?,
    @SerializedName("completed") val completed: Boolean?,
    @SerializedName("last_watched_at") val lastWatchedAt: String?
)

/**
 * Search result from Trakt ID lookup API
 */
data class TraktSearchResult(
    @SerializedName("type") val type: String?,
    @SerializedName("score") val score: Double?,
    @SerializedName("show") val show: TraktShow?,
    @SerializedName("movie") val movie: TraktMovie?
)
