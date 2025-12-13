package com.test1.tv.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkFilterPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    // ==================== Quality Filters ====================

    var quality4kEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUALITY_4K, true)
        set(value) = prefs.edit().putBoolean(KEY_QUALITY_4K, value).apply()

    var quality1080pEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUALITY_1080P, true)
        set(value) = prefs.edit().putBoolean(KEY_QUALITY_1080P, value).apply()

    var quality720pEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUALITY_720P, true)
        set(value) = prefs.edit().putBoolean(KEY_QUALITY_720P, value).apply()

    var qualitySdEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUALITY_SD, false)
        set(value) = prefs.edit().putBoolean(KEY_QUALITY_SD, value).apply()

    var qualityCamEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUALITY_CAM, false)
        set(value) = prefs.edit().putBoolean(KEY_QUALITY_CAM, value).apply()

    var qualityUnknownEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUALITY_UNKNOWN, true)
        set(value) = prefs.edit().putBoolean(KEY_QUALITY_UNKNOWN, value).apply()

    // ==================== Bitrate Filters ====================

    var minBitrateMbps: Int
        get() = prefs.getInt(KEY_MIN_BITRATE, 0)
        set(value) = prefs.edit().putInt(KEY_MIN_BITRATE, value).apply()

    var maxBitrateMbps: Int
        get() = prefs.getInt(KEY_MAX_BITRATE, 0) // 0 = no limit
        set(value) = prefs.edit().putInt(KEY_MAX_BITRATE, value).apply()

    // ==================== Exclude Phrases ====================

    var excludePhrases: Set<String>
        get() = prefs.getStringSet(KEY_EXCLUDE_PHRASES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_EXCLUDE_PHRASES, value).apply()

    // ==================== Link Resolving / Autoselect ====================

    var autoselectEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOSELECT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTOSELECT_ENABLED, value).apply()

    var minLinkCountForAutoselect: Int
        get() = prefs.getInt(KEY_MIN_LINK_COUNT, 10)
        set(value) = prefs.edit().putInt(KEY_MIN_LINK_COUNT, value).apply()

    var primarySortMethod: SortMethod
        get() = SortMethod.fromValue(prefs.getString(KEY_PRIMARY_SORT, SortMethod.QUALITY.value) ?: SortMethod.QUALITY.value)
        set(value) = prefs.edit().putString(KEY_PRIMARY_SORT, value.value).apply()

    var secondarySortMethod: SortMethod
        get() = SortMethod.fromValue(prefs.getString(KEY_SECONDARY_SORT, SortMethod.BITRATE.value) ?: SortMethod.BITRATE.value)
        set(value) = prefs.edit().putString(KEY_SECONDARY_SORT, value.value).apply()

    // ==================== Enums ====================

    enum class SortMethod(val value: String, val label: String) {
        QUALITY("quality", "Quality (Resolution)"),
        BITRATE("bitrate", "Bitrate");

        companion object {
            fun fromValue(value: String): SortMethod {
                return entries.find { it.value == value } ?: QUALITY
            }
        }
    }

    // ==================== Helper Methods ====================

    fun getEnabledQualities(): Set<Quality> {
        return buildSet {
            if (quality4kEnabled) add(Quality.UHD_4K)
            if (quality1080pEnabled) add(Quality.FHD_1080P)
            if (quality720pEnabled) add(Quality.HD_720P)
            if (qualitySdEnabled) add(Quality.SD)
            if (qualityCamEnabled) add(Quality.CAM)
            if (qualityUnknownEnabled) add(Quality.UNKNOWN)
        }
    }

    fun addExcludePhrase(phrase: String) {
        val current = excludePhrases.toMutableSet()
        current.add(phrase.trim())
        excludePhrases = current
    }

    fun removeExcludePhrase(phrase: String) {
        val current = excludePhrases.toMutableSet()
        current.remove(phrase)
        excludePhrases = current
    }

    companion object {
        private const val PREFS_NAME = "link_filter_preferences"

        // Quality keys
        private const val KEY_QUALITY_4K = "quality_4k"
        private const val KEY_QUALITY_1080P = "quality_1080p"
        private const val KEY_QUALITY_720P = "quality_720p"
        private const val KEY_QUALITY_SD = "quality_sd"
        private const val KEY_QUALITY_CAM = "quality_cam"
        private const val KEY_QUALITY_UNKNOWN = "quality_unknown"

        // Bitrate keys
        private const val KEY_MIN_BITRATE = "min_bitrate_mbps"
        private const val KEY_MAX_BITRATE = "max_bitrate_mbps"

        // Exclude phrases
        private const val KEY_EXCLUDE_PHRASES = "exclude_phrases"

        // Autoselect keys
        private const val KEY_AUTOSELECT_ENABLED = "autoselect_enabled"
        private const val KEY_MIN_LINK_COUNT = "min_link_count"
        private const val KEY_PRIMARY_SORT = "primary_sort"
        private const val KEY_SECONDARY_SORT = "secondary_sort"
    }
}

/**
 * Quality/Resolution enum with sorting order (higher = better)
 */
enum class Quality(val value: String, val label: String, val sortOrder: Int) {
    UHD_4K("4k", "4K", 5),
    FHD_1080P("1080p", "1080p", 4),
    HD_720P("720p", "720p", 3),
    SD("sd", "SD", 2),
    CAM("cam", "CAM", 1),
    UNKNOWN("unknown", "Unknown", 0);

    companion object {
        fun fromString(quality: String): Quality {
            val normalized = quality.lowercase().trim()
            return when {
                normalized.contains("2160") || normalized.contains("4k") || normalized.contains("uhd") -> UHD_4K
                normalized.contains("1080") || normalized.contains("fhd") -> FHD_1080P
                normalized.contains("720") || normalized.contains("hd") && !normalized.contains("uhd") -> HD_720P
                normalized.contains("480") || normalized.contains("sd") || normalized.contains("dvd") -> SD
                normalized.contains("cam") || normalized.contains("ts") || normalized.contains("hdts") ||
                    normalized.contains("telesync") || normalized.contains("hdcam") -> CAM
                else -> UNKNOWN
            }
        }
    }
}
