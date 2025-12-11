package com.test1.tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Player settings entity - stores user preferences for video playback
 * Single row table (id = 1)
 */
@Entity(tableName = "player_settings")
data class PlayerSettings(
    @PrimaryKey
    val id: Int = 1,

    // Skip mode: "instant" (always 10s) or "adaptive" (Kodi-style progressive)
    val skipMode: String = SKIP_MODE_INSTANT,

    // Default subtitle language (ISO 639-1 code, e.g., "en", "es")
    val defaultSubtitleLanguage: String? = "en",

    // Default audio language (null = original/first track)
    val defaultAudioLanguage: String? = null,

    // Subtitle delay in milliseconds (-10000 to +10000)
    val subtitleDelayMs: Long = 0L,

    // Audio delay in milliseconds (-10000 to +10000)
    val audioDelayMs: Long = 0L,

    // Auto-play next episode for TV shows
    val autoplayNextEpisode: Boolean = true,

    // Remember playback position for resume
    val rememberPosition: Boolean = true,

    // Autoplay countdown duration in seconds
    val autoplayCountdownSeconds: Int = 15,

    // Decoder mode: "auto", "hw_only", "sw_prefer"
    val decoderMode: String = DECODER_MODE_AUTO,

    // Enable tunneled playback for 4K/HDR (better performance on supported devices)
    val tunnelingEnabled: Boolean = true,

    // Buffer settings
    val minBufferMs: Int = 30_000,
    val maxBufferMs: Int = 60_000,
    val bufferForPlaybackMs: Int = 2_500,
    val bufferForPlaybackAfterRebufferMs: Int = 5_000
) {
    companion object {
        const val SKIP_MODE_INSTANT = "instant"
        const val SKIP_MODE_ADAPTIVE = "adaptive"

        const val DECODER_MODE_AUTO = "auto"
        const val DECODER_MODE_HW_ONLY = "hw_only"
        const val DECODER_MODE_SW_PREFER = "sw_prefer"

        // Delay adjustment step in milliseconds (0.1 seconds)
        const val DELAY_STEP_MS = 100L

        // Maximum delay in either direction (10 seconds)
        const val MAX_DELAY_MS = 10_000L

        fun default() = PlayerSettings()
    }

    fun isInstantSkip(): Boolean = skipMode == SKIP_MODE_INSTANT
    fun isAdaptiveSkip(): Boolean = skipMode == SKIP_MODE_ADAPTIVE

    fun isHardwareOnly(): Boolean = decoderMode == DECODER_MODE_HW_ONLY
    fun isSoftwarePreferred(): Boolean = decoderMode == DECODER_MODE_SW_PREFER
}
