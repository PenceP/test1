package com.strmr.tv.data.model.continuewatching

sealed class ContinueWatchingItem {
    abstract val playbackId: Long?
    abstract val progress: Float
    abstract val lastWatchedAtMillis: Long

    data class Movie(
        override val playbackId: Long?,
        override val progress: Float,
        override val lastWatchedAtMillis: Long,
        val traktId: Long?,
        val tmdbId: Int,
        val title: String,
        val year: Int?
    ) : ContinueWatchingItem()

    data class Episode(
        override val playbackId: Long?,
        override val progress: Float,
        override val lastWatchedAtMillis: Long,
        val showTraktId: Long,
        val showTmdbId: Int,
        val showTitle: String,
        val showYear: Int?,
        val season: Int,
        val episode: Int,
        val episodeTitle: String?
    ) : ContinueWatchingItem()

    data class NextEpisode(
        override val playbackId: Long?,
        override val progress: Float,
        override val lastWatchedAtMillis: Long,
        val showTraktId: Long,
        val showTmdbId: Int,
        val showTitle: String,
        val showYear: Int?,
        val season: Int,
        val episode: Int,
        val episodeTitle: String?,
        val firstAiredAtMillis: Long?
    ) : ContinueWatchingItem()
}

internal fun String?.toZonedMillisOrNow(): Long =
    try {
        if (this.isNullOrBlank()) System.currentTimeMillis()
        else java.time.ZonedDateTime.parse(this).toInstant().toEpochMilli()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }

internal fun String?.toZonedMillisOrNull(): Long? =
    try {
        if (this.isNullOrBlank()) null
        else java.time.ZonedDateTime.parse(this).toInstant().toEpochMilli()
    } catch (_: Exception) {
        null
    }
