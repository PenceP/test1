package com.strmr.tv.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    // Trailer Player Setting
    var trailerPlayer: TrailerPlayer
        get() = TrailerPlayer.fromValue(prefs.getString(KEY_TRAILER_PLAYER, TrailerPlayer.YOUTUBE_APP.value) ?: TrailerPlayer.YOUTUBE_APP.value)
        set(value) = prefs.edit().putString(KEY_TRAILER_PLAYER, value.value).apply()

    // Autoplay Next Episode Setting
    var autoplayNextEpisode: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY_NEXT_EPISODE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOPLAY_NEXT_EPISODE, value).apply()

    // Video Player Engine Setting
    var videoPlayerEngine: VideoPlayerEngine
        get() = VideoPlayerEngine.fromValue(prefs.getString(KEY_VIDEO_PLAYER_ENGINE, VideoPlayerEngine.EXOPLAYER.value) ?: VideoPlayerEngine.EXOPLAYER.value)
        set(value) = prefs.edit().putString(KEY_VIDEO_PLAYER_ENGINE, value.value).apply()

    enum class TrailerPlayer(val value: String, val label: String) {
        YOUTUBE_APP("youtube_app", "YouTube App"),
        WEBVIEW("webview", "In-App WebView");

        companion object {
            fun fromValue(value: String): TrailerPlayer {
                return entries.find { it.value == value } ?: YOUTUBE_APP
            }
        }
    }

    enum class VideoPlayerEngine(val value: String, val label: String) {
        EXOPLAYER("exoplayer", "ExoPlayer"),
        VLC("vlc", "VLC");

        companion object {
            fun fromValue(value: String): VideoPlayerEngine {
                return entries.find { it.value == value } ?: EXOPLAYER
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "app_preferences"
        private const val KEY_TRAILER_PLAYER = "trailer_player"
        private const val KEY_AUTOPLAY_NEXT_EPISODE = "autoplay_next_episode"
        private const val KEY_VIDEO_PLAYER_ENGINE = "video_player_engine"
    }
}
