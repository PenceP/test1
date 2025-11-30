package com.test1.tv.data.model.continuewatching

sealed class ContinueWatchingItem {
    abstract val playbackId: Long
    abstract val progress: Float      // 0.0 - 1.0
    abstract val lastPausedAtMillis: Long

    data class Movie(
        override val playbackId: Long,
        override val progress: Float,
        override val lastPausedAtMillis: Long,
        val traktId: Long?,
        val tmdbId: Int?,
        val title: String,
        val year: Int?
    ) : ContinueWatchingItem()

    data class Episode(
        override val playbackId: Long,
        override val progress: Float,
        override val lastPausedAtMillis: Long,
        val showTraktId: Long?,
        val showTmdbId: Int?,
        val showTitle: String,
        val showYear: Int?,
        val season: Int,
        val episode: Int,
        val episodeTitle: String?
    ) : ContinueWatchingItem()
}

object ContinueWatchingMapper {

    /**
        * Map Trakt playback DTO to domain; drops items >=92% complete.
        */
    fun map(dto: com.test1.tv.data.model.trakt.TraktPlaybackItem): ContinueWatchingItem? {
        val playbackId = dto.id ?: return null
        val normalizedProgress = ((dto.progress ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)

        // Drop nearly finished items (>= 92%) per requirement
        if (normalizedProgress >= 0.92f) return null

        val pausedAtMillis = dto.pausedAt.toZonedMillisOrNow()

        return when (dto.type) {
            "movie" -> {
                val movie = dto.movie ?: return null
                ContinueWatchingItem.Movie(
                    playbackId = playbackId,
                    progress = normalizedProgress,
                    lastPausedAtMillis = pausedAtMillis,
                    traktId = movie.ids?.trakt?.toLong(),
                    tmdbId = movie.ids?.tmdb,
                    title = movie.title ?: return null,
                    year = movie.year
                )
            }
            "episode" -> {
                val show = dto.show ?: return null
                val ep = dto.episode ?: return null
                ContinueWatchingItem.Episode(
                    playbackId = playbackId,
                    progress = normalizedProgress,
                    lastPausedAtMillis = pausedAtMillis,
                    showTraktId = show.ids?.trakt?.toLong(),
                    showTmdbId = show.ids?.tmdb,
                    showTitle = show.title ?: return null,
                    showYear = show.year,
                    season = ep.season ?: return null,
                    episode = ep.number ?: return null,
                    episodeTitle = ep.title
                )
            }
            else -> null
        }
    }

    private fun String?.toZonedMillisOrNow(): Long =
        try {
            if (this.isNullOrBlank()) System.currentTimeMillis()
            else java.time.ZonedDateTime.parse(this).toInstant().toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
}
