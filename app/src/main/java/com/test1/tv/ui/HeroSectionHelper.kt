package com.test1.tv.ui

import android.view.View
import android.widget.TextView
import com.test1.tv.data.model.ContentItem
import java.util.Locale

/**
 * Shared utility class for hero section logic across all fragments
 * (HomeFragment, MoviesFragment, TvShowsFragment, DetailsFragment).
 *
 * This consolidates common hero section update methods to follow the
 * "reuse instead of reinvent" principle.
 */
object HeroSectionHelper {

    /**
     * Updates the metadata line (e.g., "98% Match • 2025 • PG-13 • 1h 48m")
     */
    fun updateHeroMetadata(metadataView: TextView, item: ContentItem) {
        val metadata = buildMetadataLine(item)
        metadataView.text = metadata ?: ""
        metadataView.visibility = if (metadata.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    /**
     * Builds the metadata line from ContentItem properties
     */
    fun buildMetadataLine(item: ContentItem): CharSequence? {
        val parts = mutableListOf<String>()
        val matchScore = formatMatchScore(item)
        matchScore?.let { parts.add(it) }
        item.year?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        item.certification?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        formatRuntimeText(item.runtime)?.let { parts.add(it) }

        if (parts.isEmpty()) return null
        return parts.joinToString(" • ")
    }

    /**
     * Formats match score from rating percentage, vote average, or trakt rating
     */
    fun formatMatchScore(item: ContentItem): String? {
        val fromPercentage = item.ratingPercentage?.takeIf { it in 1..100 }
        if (fromPercentage != null) {
            return String.format(Locale.US, "%d%% Match", fromPercentage)
        }

        val voteAverage = item.rating?.takeIf { it > 0 }
            ?.let { (it * 10).toInt().coerceAtMost(100) }
        if (voteAverage != null) {
            return String.format(Locale.US, "%d%% Match", voteAverage)
        }

        val trakt = item.traktRating?.takeIf { it > 0 }
            ?.let { (it * 10).toInt().coerceAtMost(100) }
        return trakt?.let { String.format(Locale.US, "%d%% Match", it) }
    }

    /**
     * Updates genre text view with comma-separated genres joined by bullets
     */
    fun updateGenres(genreView: TextView, genres: String?) {
        if (genres.isNullOrBlank()) {
            genreView.visibility = View.GONE
            genreView.text = ""
            return
        }

        val formatted = genres.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (formatted.isEmpty()) {
            genreView.visibility = View.GONE
            genreView.text = ""
            return
        }

        genreView.text = formatted.joinToString(" • ")
        genreView.visibility = View.VISIBLE
    }

    /**
     * Updates cast text view with formatted cast list
     */
    fun updateCast(castView: TextView, cast: String?) {
        val castText = cast?.let { formatCastList(it) }
        if (castText != null) {
            castView.text = castText
            castView.visibility = View.VISIBLE
        } else {
            castView.visibility = View.GONE
        }
    }

    /**
     * Formats cast list string (comma-separated) into "Starring: Name1, Name2, ..."
     * Takes first 4 names only
     */
    private fun formatCastList(raw: String): String? {
        val names = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(4)
        if (names.isEmpty()) return null
        return "Starring: ${names.joinToString(", ")}"
    }

    /**
     * Formats runtime text (e.g., "108" -> "1h 48m", "45" -> "45m")
     */
    fun formatRuntimeText(runtime: String?): String? {
        if (runtime.isNullOrBlank()) return null
        if (runtime.contains("h")) return runtime

        val minutes = runtime.filter { it.isDigit() }.toIntOrNull() ?: return runtime
        return if (minutes >= 60) {
            val hours = minutes / 60
            val remaining = minutes % 60
            if (remaining == 0) "${hours}h" else "${hours}h ${remaining}m"
        } else {
            "${minutes}m"
        }
    }
}
