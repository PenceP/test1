package com.test1.tv.data.service

import com.test1.tv.data.model.StreamInfo
import com.test1.tv.data.repository.LinkFilterPreferences
import com.test1.tv.data.repository.Quality
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for filtering and sorting stream links based on user preferences
 */
@Singleton
class LinkFilterService @Inject constructor(
    private val preferences: LinkFilterPreferences
) {

    /**
     * Filter and sort streams based on user preferences
     * Returns a pair of (filteredStreams, filteredOutStreams)
     */
    fun filterAndSort(
        streams: List<StreamInfo>,
        runtimeMinutes: Int? = null
    ): FilterResult {
        val enabledQualities = preferences.getEnabledQualities()
        val excludePhrases = preferences.excludePhrases
        val minBitrate = preferences.minBitrateMbps
        val maxBitrate = preferences.maxBitrateMbps

        val passed = mutableListOf<StreamInfo>()
        val filtered = mutableListOf<StreamInfo>()

        for (stream in streams) {
            // Calculate estimated bitrate if runtime is provided
            val streamWithBitrate = if (runtimeMinutes != null && runtimeMinutes > 0) {
                val bitrate = estimateBitrate(stream.sizeBytes, runtimeMinutes)
                stream.copy(estimatedBitrateMbps = bitrate)
            } else {
                stream
            }

            val filterResult = checkFilters(streamWithBitrate, enabledQualities, excludePhrases, minBitrate, maxBitrate)

            if (filterResult.passed) {
                passed.add(streamWithBitrate)
            } else {
                filtered.add(streamWithBitrate.copy(
                    isFilteredOut = true,
                    filterReason = filterResult.reason
                ))
            }
        }

        // Sort passed streams
        val sortedPassed = sortStreams(passed)

        // Sort filtered streams (same logic, just for display)
        val sortedFiltered = sortStreams(filtered)

        return FilterResult(
            passedStreams = sortedPassed,
            filteredOutStreams = sortedFiltered
        )
    }

    /**
     * Check if a stream passes all filters
     */
    private fun checkFilters(
        stream: StreamInfo,
        enabledQualities: Set<Quality>,
        excludePhrases: Set<String>,
        minBitrate: Int,
        maxBitrate: Int
    ): FilterCheckResult {
        // Quality filter
        if (stream.quality !in enabledQualities) {
            return FilterCheckResult(false, "Quality: ${stream.quality.label}")
        }

        // Exclude phrases filter (case-insensitive exact match)
        for (phrase in excludePhrases) {
            if (phrase.isNotBlank()) {
                val regex = Regex("""\b${Regex.escape(phrase)}\b""", RegexOption.IGNORE_CASE)
                if (regex.containsMatchIn(stream.fileName)) {
                    return FilterCheckResult(false, "Contains: $phrase")
                }
            }
        }

        // Bitrate filter (only if we have estimated bitrate)
        val bitrate = stream.estimatedBitrateMbps
        if (bitrate != null) {
            if (minBitrate > 0 && bitrate < minBitrate) {
                return FilterCheckResult(false, "Bitrate too low: ${bitrate.toInt()} Mbps")
            }
            if (maxBitrate > 0 && bitrate > maxBitrate) {
                return FilterCheckResult(false, "Bitrate too high: ${bitrate.toInt()} Mbps")
            }
        }

        return FilterCheckResult(true, null)
    }

    /**
     * Sort streams according to user preferences
     * Cached links always on top, then sorted by primary/secondary sort method
     */
    private fun sortStreams(streams: List<StreamInfo>): List<StreamInfo> {
        val primarySort = preferences.primarySortMethod
        val secondarySort = preferences.secondarySortMethod

        return streams.sortedWith(
            compareBy<StreamInfo> { !it.isCached } // Cached first (false < true)
                .thenByDescending { getPrimarySortValue(it, primarySort) }
                .thenByDescending { getSecondarySortValue(it, secondarySort) }
                .thenByDescending { it.seeds ?: 0 } // Tie-breaker: seeds
        )
    }

    private fun getPrimarySortValue(stream: StreamInfo, sortMethod: LinkFilterPreferences.SortMethod): Double {
        return when (sortMethod) {
            LinkFilterPreferences.SortMethod.QUALITY -> stream.quality.sortOrder.toDouble()
            LinkFilterPreferences.SortMethod.BITRATE -> stream.estimatedBitrateMbps ?: 0.0
        }
    }

    private fun getSecondarySortValue(stream: StreamInfo, sortMethod: LinkFilterPreferences.SortMethod): Double {
        return when (sortMethod) {
            LinkFilterPreferences.SortMethod.QUALITY -> stream.quality.sortOrder.toDouble()
            LinkFilterPreferences.SortMethod.BITRATE -> stream.estimatedBitrateMbps ?: 0.0
        }
    }

    /**
     * Estimate bitrate from file size and runtime
     * @param sizeBytes File size in bytes
     * @param runtimeMinutes Runtime in minutes
     * @return Estimated bitrate in Mbps
     */
    private fun estimateBitrate(sizeBytes: Long, runtimeMinutes: Int): Double {
        if (runtimeMinutes <= 0) return 0.0

        val runtimeSeconds = runtimeMinutes * 60.0
        val sizeBits = sizeBytes * 8.0
        val bitsPerSecond = sizeBits / runtimeSeconds
        val mbps = bitsPerSecond / 1_000_000.0

        return mbps
    }

    /**
     * Check if autoselect should trigger and return the best stream
     */
    fun getAutoselectStream(filterResult: FilterResult): StreamInfo? {
        if (!preferences.autoselectEnabled) return null

        val minCount = preferences.minLinkCountForAutoselect
        if (filterResult.passedStreams.size < minCount) return null

        // Return the top cached stream, or top stream if none cached
        return filterResult.passedStreams.firstOrNull { it.isCached }
            ?: filterResult.passedStreams.firstOrNull()
    }

    /**
     * Format bitrate for display
     */
    fun formatBitrate(mbps: Double?): String {
        if (mbps == null) return "N/A"
        return when {
            mbps >= 100 -> "${mbps.toInt()} Mbps"
            mbps >= 10 -> String.format("%.1f Mbps", mbps)
            else -> String.format("%.2f Mbps", mbps)
        }
    }
}

data class FilterResult(
    val passedStreams: List<StreamInfo>,
    val filteredOutStreams: List<StreamInfo>
) {
    val totalCount: Int get() = passedStreams.size + filteredOutStreams.size
    val cachedCount: Int get() = passedStreams.count { it.isCached }
}

private data class FilterCheckResult(
    val passed: Boolean,
    val reason: String?
)
