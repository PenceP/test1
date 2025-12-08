# ExoPlayer Video Player Implementation Plan

## Overview

A custom ExoPlayer-based video player for Android TV with advanced features including subtitle management, audio controls, Trakt integration, and Kodi-style adaptive skip functionality.

---

## Feature Summary

| Feature | Priority | Complexity |
|---------|----------|------------|
| Basic ExoPlayer Setup | P0 | Medium |
| Loading Screen with Logo | P0 | Low |
| Playback Controls UI | P0 | Medium |
| Trakt Scrobbling | P0 | Medium |
| Subtitle Support (Embedded) | P1 | Medium |
| Audio Track Selector | P1 | Medium |
| Skip Modes (Instant/Adaptive) | P1 | High |
| Subtitle Delay Adjustment | P1 | Medium |
| Audio Delay Adjustment | P1 | Medium |
| OpenSubtitles Integration | P2 | High |
| Next Episode Autoplay | P2 | Medium |
| Player Settings Persistence | P1 | Low |

---

## Phase 1: Core Player Infrastructure

### 1.1 Database Schema for Player Settings

```kotlin
@Entity(tableName = "player_settings")
data class PlayerSettings(
    @PrimaryKey val id: Int = 1, // Single row
    val skipMode: String = "instant", // "instant" or "adaptive"
    val defaultSubtitleLanguage: String? = "en",
    val defaultAudioLanguage: String? = null, // null = original
    val subtitleDelay: Long = 0L, // milliseconds
    val audioDelay: Long = 0L, // milliseconds
    val autoplayNextEpisode: Boolean = true,
    val rememberPosition: Boolean = true
)
```

**Migration**: Add `MIGRATION_16_17` for `player_settings` table.

### 1.2 PlayerSettingsRepository

```kotlin
@Singleton
class PlayerSettingsRepository @Inject constructor(
    private val playerSettingsDao: PlayerSettingsDao
) {
    suspend fun getSettings(): PlayerSettings
    suspend fun updateSkipMode(mode: String)
    suspend fun updateSubtitleDelay(delayMs: Long)
    suspend fun updateAudioDelay(delayMs: Long)
    suspend fun updateAutoplayNextEpisode(enabled: Boolean)
    // ... other update methods
}
```

### 1.3 Files to Create

```
ui/player/
├── VideoPlayerActivity.kt          # Main activity
├── VideoPlayerFragment.kt          # Fragment with ExoPlayer
├── PlayerViewModel.kt              # ViewModel for state management
├── model/
│   ├── PlaybackState.kt           # Sealed class for player states
│   ├── MediaInfo.kt               # Current media info
│   └── TrackInfo.kt               # Audio/subtitle track info
├── controls/
│   ├── PlayerControlsView.kt      # Custom controls overlay
│   ├── SkipController.kt          # Handles skip logic (instant/adaptive)
│   ├── TrackSelectorDialog.kt     # Audio/subtitle picker dialog
│   └── DelayAdjustmentView.kt     # +/- delay controls
├── loading/
│   └── LoadingOverlay.kt          # Logo fade in/out during load
├── scrobble/
│   └── TraktScrobbler.kt          # Trakt watch progress tracker
└── autoplay/
    └── NextEpisodeOverlay.kt      # Autoplay countdown UI
```

---

## Phase 2: Loading Screen with Logo Art

### 2.1 Design
- Black background with centered logo
- Logo fades in over 500ms
- Logo pulses slowly (subtle scale animation 1.0 → 1.02 → 1.0)
- When video ready, logo fades out over 300ms, then video plays

### 2.2 Implementation

```kotlin
class LoadingOverlay(context: Context) : FrameLayout(context) {
    private val logoImage: ImageView
    private val pulseAnimator: ObjectAnimator

    fun show(logoUrl: String?) {
        // Load logo from cache/URL
        // Start pulse animation
        // Fade in
    }

    fun hide(onComplete: () -> Unit) {
        // Stop pulse
        // Fade out
        // Call onComplete
    }
}
```

### 2.3 Layout: `layout/overlay_player_loading.xml`
```xml
<FrameLayout>
    <View android:background="#000000" /> <!-- Black backdrop -->
    <ImageView
        android:id="@+id/loading_logo"
        android:layout_gravity="center"
        android:maxWidth="400dp"
        android:maxHeight="200dp" />
</FrameLayout>
```

---

## Phase 3: ExoPlayer Setup

### 3.1 Dependencies (build.gradle.kts)

```kotlin
// ExoPlayer
implementation("androidx.media3:media3-exoplayer:1.2.1")
implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
implementation("androidx.media3:media3-exoplayer-dash:1.2.1")
implementation("androidx.media3:media3-ui:1.2.1")
implementation("androidx.media3:media3-session:1.2.1")

// For subtitle parsing
implementation("androidx.media3:media3-extractor:1.2.1")
```

### 3.2 VideoPlayerActivity

```kotlin
@AndroidEntryPoint
class VideoPlayerActivity : FragmentActivity() {
    companion object {
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_CONTENT_ITEM = "content_item"
        const val EXTRA_STREAM_INFO = "stream_info"
        const val EXTRA_SEASON = "season"
        const val EXTRA_EPISODE = "episode"

        fun start(
            context: Context,
            streamUrl: String,
            contentItem: ContentItem,
            streamInfo: StreamInfo,
            season: Int? = null,
            episode: Int? = null
        )
    }
}
```

### 3.3 PlayerViewModel State

```kotlin
sealed class PlaybackState {
    object Loading : PlaybackState()
    object Ready : PlaybackState()
    object Playing : PlaybackState()
    object Paused : PlaybackState()
    object Buffering : PlaybackState()
    data class Error(val message: String) : PlaybackState()
    object Ended : PlaybackState()
}

data class PlayerUiState(
    val playbackState: PlaybackState = PlaybackState.Loading,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val isControlsVisible: Boolean = false,
    val audioTracks: List<TrackInfo> = emptyList(),
    val subtitleTracks: List<TrackInfo> = emptyList(),
    val selectedAudioTrack: Int = 0,
    val selectedSubtitleTrack: Int = -1, // -1 = off
    val subtitleDelay: Long = 0L,
    val audioDelay: Long = 0L,
    val skipMode: SkipMode = SkipMode.INSTANT
)
```

---

## Phase 4: Playback Controls UI

### 4.1 Control Bar Layout

```
┌─────────────────────────────────────────────────────────────────────┐
│  Movie Title (2024)                                     advancement  │
│  S1 E5 • Episode Title (if TV)                         runtime     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│                         [  🔊  ]  [  CC  ]  [  ⚙️  ]                │
│                                                                     │
│    ◀◀ 10s        [ ▶ PLAY / ⏸ PAUSE ]        10s ▶▶               │
│                                                                     │
│  ▬▬▬▬▬▬▬▬▬▬▬▬▬▬●────────────────────────────────────               │
│  12:34                                           1:45:22            │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.2 Control Elements

| Element | Function |
|---------|----------|
| Title | Movie/show name |
| Subtitle | Episode info (TV only) |
| Audio Button | Opens audio track selector |
| CC Button | Opens subtitle selector + delay |
| Settings Button | Opens player settings menu |
| Play/Pause | Toggle playback |
| Skip Buttons | Skip forward/backward |
| Seek Bar | Scrub through video |
| Time Display | Current / Total duration |

### 4.3 Remote Control Mapping (D-pad)

| Button | Action |
|--------|--------|
| Center/Enter | Play/Pause (or select if controls visible) |
| Left | Skip backward |
| Right | Skip forward |
| Up | Show controls / Navigate up |
| Down | Navigate down |
| Back | Hide controls / Exit player |
| Menu | Toggle settings menu |

---

## Phase 5: Skip Controller (Kodi-style)

### 5.1 Skip Mode Enum

```kotlin
enum class SkipMode {
    INSTANT,  // Always 10s
    ADAPTIVE  // Kodi-style progressive
}
```

### 5.2 Adaptive Skip Logic

```kotlin
class SkipController(
    private val settings: PlayerSettingsRepository
) {
    private var consecutivePresses = 0
    private var lastPressTime = 0L
    private var pendingSkipJob: Job? = null

    private val skipDurations = listOf(
        10_000L,   // 1 press = 10s
        30_000L,   // 2 presses = 30s
        60_000L,   // 3 presses = 60s
        180_000L,  // 4 presses = 3min
        300_000L,  // 5 presses = 5min
        600_000L   // 6+ presses = 10min (max)
    )

    private val adaptiveDelayMs = 400L // Time window to detect multi-press

    fun onSkipPressed(direction: SkipDirection): SkipResult {
        return when (settings.skipMode) {
            SkipMode.INSTANT -> SkipResult.Immediate(10_000L * direction.multiplier)
            SkipMode.ADAPTIVE -> handleAdaptiveSkip(direction)
        }
    }

    private fun handleAdaptiveSkip(direction: SkipDirection): SkipResult {
        val now = System.currentTimeMillis()

        if (now - lastPressTime > adaptiveDelayMs) {
            // Reset if too much time passed
            consecutivePresses = 0
        }

        consecutivePresses++
        lastPressTime = now

        // Cancel pending skip
        pendingSkipJob?.cancel()

        // Get skip duration for current press count
        val skipIndex = (consecutivePresses - 1).coerceIn(0, skipDurations.lastIndex)
        val skipDuration = skipDurations[skipIndex]

        // Show preview immediately
        val previewDuration = skipDuration * direction.multiplier

        // Schedule actual skip after delay
        return SkipResult.Pending(
            previewDuration = previewDuration,
            delayMs = adaptiveDelayMs,
            onConfirm = { actualSkipDuration ->
                // Execute skip
            }
        )
    }
}

enum class SkipDirection(val multiplier: Int) {
    BACKWARD(-1),
    FORWARD(1)
}

sealed class SkipResult {
    data class Immediate(val durationMs: Long) : SkipResult()
    data class Pending(
        val previewDuration: Long,
        val delayMs: Long,
        val onConfirm: (Long) -> Unit
    ) : SkipResult()
}
```

### 5.3 Skip Indicator UI

```
┌─────────────────┐
│   ◀◀ -30s      │  ← Shows during adaptive skip
│   (2 presses)   │
└─────────────────┘
```

---

## Phase 6: Audio & Subtitle Track Selection

### 6.1 TrackInfo Model

```kotlin
data class TrackInfo(
    val index: Int,
    val type: TrackType,
    val language: String?,
    val label: String?,
    val isDefault: Boolean,
    val isForced: Boolean,
    val codec: String?,
    val channelCount: Int? = null // For audio
) {
    enum class TrackType {
        AUDIO, SUBTITLE
    }

    fun getDisplayName(): String {
        val langName = language?.let { Locale(it).displayLanguage } ?: "Unknown"
        val channelInfo = channelCount?.let {
            when (it) {
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> "${it}ch"
            }
        }
        return buildString {
            append(label ?: langName)
            channelInfo?.let { append(" ($it)") }
            if (isDefault) append(" ★")
            if (isForced) append(" [Forced]")
        }
    }
}
```

### 6.2 Track Selector Dialog

```kotlin
class TrackSelectorDialog(
    private val context: Context,
    private val trackType: TrackInfo.TrackType,
    private val tracks: List<TrackInfo>,
    private val selectedIndex: Int,
    private val onTrackSelected: (Int) -> Unit,
    private val onDelayAdjust: ((Long) -> Unit)? = null // For subtitles
) {
    fun show() {
        // Show dialog with:
        // - List of tracks (radio buttons)
        // - "Off" option for subtitles
        // - Delay adjustment slider (subtitles only)
        // - "Search OpenSubtitles" button (subtitles only)
    }
}
```

### 6.3 Delay Adjustment

```kotlin
// Subtitle/Audio delay: -10s to +10s in 100ms increments
data class DelayRange(
    val minMs: Long = -10_000L,
    val maxMs: Long = 10_000L,
    val stepMs: Long = 100L
)

// UI shows: "Subtitle Delay: +0.3s" or "Audio Delay: -0.5s"
```

---

## Phase 7: OpenSubtitles Integration

### 7.1 OpenSubtitles API Service

```kotlin
interface OpenSubtitlesApiService {
    @POST("login")
    suspend fun login(
        @Body credentials: OpenSubtitlesCredentials
    ): OpenSubtitlesLoginResponse

    @GET("subtitles")
    suspend fun searchSubtitles(
        @Header("Authorization") token: String,
        @Query("imdb_id") imdbId: String?,
        @Query("tmdb_id") tmdbId: Int?,
        @Query("season_number") season: Int?,
        @Query("episode_number") episode: Int?,
        @Query("languages") languages: String = "en"
    ): OpenSubtitlesSearchResponse

    @POST("download")
    suspend fun downloadSubtitle(
        @Header("Authorization") token: String,
        @Body request: OpenSubtitlesDownloadRequest
    ): OpenSubtitlesDownloadResponse
}
```

### 7.2 OpenSubtitles Repository

```kotlin
@Singleton
class OpenSubtitlesRepository @Inject constructor(
    private val apiService: OpenSubtitlesApiService,
    private val cacheDir: File
) {
    suspend fun searchSubtitles(
        imdbId: String?,
        tmdbId: Int?,
        season: Int?,
        episode: Int?,
        languages: List<String> = listOf("en")
    ): List<SubtitleResult>

    suspend fun downloadSubtitle(
        subtitleId: String
    ): Result<File> // Returns local .srt file
}
```

### 7.3 Subtitle Search UI

```
┌─────────────────────────────────────────────┐
│  Search Subtitles                      [X]  │
├─────────────────────────────────────────────┤
│  Language: [English ▼]                      │
├─────────────────────────────────────────────┤
│  ○ Movie.Name.2024.1080p.BluRay.srt         │
│    Downloads: 45,234 | Rating: 9.2          │
│                                             │
│  ○ Movie.Name.2024.WEB-DL.srt               │
│    Downloads: 12,456 | Rating: 8.8          │
│                                             │
│  ○ Movie.Name.2024.SDH.srt                  │
│    Downloads: 8,901 | Rating: 8.5           │
├─────────────────────────────────────────────┤
│           [ Download Selected ]             │
└─────────────────────────────────────────────┘
```

---

## Phase 8: Trakt Scrobbling

### 8.1 TraktScrobbler Class

```kotlin
class TraktScrobbler(
    private val traktApiService: TraktApiService,
    private val traktAccountRepository: TraktAccountRepository
) {
    private var scrobbleJob: Job? = null
    private var hasScrobbledStart = false
    private var hasMarkedWatched = false

    fun startScrobbling(
        contentItem: ContentItem,
        season: Int? = null,
        episode: Int? = null
    ) {
        hasScrobbledStart = false
        hasMarkedWatched = false
    }

    fun updateProgress(
        currentPosition: Long,
        duration: Long
    ) {
        val progress = (currentPosition.toFloat() / duration * 100).toInt()

        // Scrobble "start" at beginning
        if (!hasScrobbledStart && progress >= 1) {
            scrobbleStart()
            hasScrobbledStart = true
        }

        // Mark as watched at 90%
        if (!hasMarkedWatched && progress >= 90) {
            markAsWatched()
            hasMarkedWatched = true
        }

        // Periodic progress updates (every 5 minutes or 10% progress)
        updateScrobbleProgress(progress)
    }

    fun stopScrobbling() {
        scrobbleJob?.cancel()
        scrobblePause()
    }

    private suspend fun scrobbleStart() { /* POST to Trakt */ }
    private suspend fun scrobblePause() { /* POST to Trakt */ }
    private suspend fun updateScrobbleProgress(progress: Int) { /* POST to Trakt */ }
    private suspend fun markAsWatched() { /* POST to Trakt history */ }
}
```

### 8.2 Trakt Scrobble API Endpoints

```kotlin
// In TraktApiService
@POST("scrobble/start")
suspend fun scrobbleStart(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktScrobbleRequest
): TraktScrobbleResponse

@POST("scrobble/pause")
suspend fun scrobblePause(...)

@POST("scrobble/stop")
suspend fun scrobbleStop(...)

@POST("sync/history")
suspend fun addToHistory(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktHistoryRequest
): TraktSyncResponse
```

---

## Phase 9: Next Episode Autoplay

### 9.1 Autoplay Overlay

Shows when video reaches 90% (or configurable threshold):

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│     Up Next: S1 E6 "Episode Title"                             │
│                                                                 │
│     ┌──────────────┐                                           │
│     │   Episode    │   Playing in 15 seconds...                │
│     │   Thumbnail  │                                           │
│     │              │   [ ▶ Play Now ]  [ ✕ Cancel ]            │
│     └──────────────┘                                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 9.2 NextEpisodeOverlay Implementation

```kotlin
class NextEpisodeOverlay(context: Context) : FrameLayout(context) {
    private var countdownJob: Job? = null
    private var countdownSeconds = 15

    fun show(
        nextEpisode: EpisodeInfo,
        onPlayNow: () -> Unit,
        onCancel: () -> Unit
    ) {
        // Show overlay
        // Start countdown
        // Auto-trigger onPlayNow when countdown reaches 0
    }

    fun cancel() {
        countdownJob?.cancel()
        hide()
    }
}
```

### 9.3 Episode Resolution for Autoplay

```kotlin
// In PlayerViewModel
private suspend fun prepareNextEpisode() {
    if (!settings.autoplayNextEpisode) return
    if (contentItem.type != ContentType.TV_SHOW) return

    // Fetch next episode info
    val nextEpisode = getNextEpisode(
        showTmdbId = contentItem.tmdbId,
        currentSeason = currentSeason,
        currentEpisode = currentEpisode
    )

    if (nextEpisode != null) {
        // Pre-scrape sources for next episode (in background)
        nextEpisodeSources = torrentioRepository.scrapeEpisode(
            imdbId = contentItem.imdbId,
            season = nextEpisode.season,
            episode = nextEpisode.episode,
            runtime = nextEpisode.runtime
        )
    }
}
```

---

## Phase 10: Player Settings Menu

### 10.1 Settings Menu Items

```
┌─────────────────────────────────┐
│  Player Settings           [X]  │
├─────────────────────────────────┤
│  Skip Mode                      │
│  ● Instant (10s)                │
│  ○ Adaptive (Kodi-style)        │
├─────────────────────────────────┤
│  Autoplay Next Episode          │
│  [✓] Enabled                    │
├─────────────────────────────────┤
│  Default Subtitle Language      │
│  [English ▼]                    │
├─────────────────────────────────┤
│  Default Audio Language         │
│  [Original ▼]                   │
└─────────────────────────────────┘
```

---

## Implementation Order

### Sprint 1: Core Player (P0)
1. Database schema + migration for PlayerSettings
2. PlayerSettingsRepository + DAO
3. VideoPlayerActivity + Fragment scaffold
4. Basic ExoPlayer integration
5. Loading overlay with logo fade
6. Wire up from SourcesActivity

### Sprint 2: Controls & Navigation (P0)
7. PlayerControlsView UI
8. D-pad navigation handling
9. Play/Pause, Seek bar
10. Time display

### Sprint 3: Skip & Tracks (P1)
11. SkipController (Instant mode)
12. SkipController (Adaptive mode)
13. Audio track extraction & selector
14. Embedded subtitle track selector

### Sprint 4: Delays & Settings (P1)
15. Subtitle delay adjustment
16. Audio delay adjustment
17. Player settings menu
18. Settings persistence

### Sprint 5: Trakt Integration (P0)
19. TraktScrobbler implementation
20. Scrobble start/pause/stop
21. Mark watched at 90%
22. Progress sync

### Sprint 6: Autoplay & Polish (P2)
23. Next episode detection
24. NextEpisodeOverlay
25. Pre-scraping next episode
26. Seamless transition

### Sprint 7: OpenSubtitles (P2)
27. OpenSubtitles API service
28. OpenSubtitlesRepository
29. Subtitle search UI
30. Download & apply subtitles

---

## File Summary

### New Files to Create

```
data/local/entity/PlayerSettings.kt
data/local/dao/PlayerSettingsDao.kt
data/local/DatabaseMigrations.kt (update)
data/repository/PlayerSettingsRepository.kt
data/repository/OpenSubtitlesRepository.kt
data/remote/api/OpenSubtitlesApiService.kt
data/remote/model/opensubtitles/OpenSubtitlesModels.kt

ui/player/VideoPlayerActivity.kt
ui/player/VideoPlayerFragment.kt
ui/player/PlayerViewModel.kt
ui/player/model/PlaybackState.kt
ui/player/model/MediaInfo.kt
ui/player/model/TrackInfo.kt
ui/player/controls/PlayerControlsView.kt
ui/player/controls/SkipController.kt
ui/player/controls/TrackSelectorDialog.kt
ui/player/controls/DelayAdjustmentView.kt
ui/player/controls/PlayerSettingsDialog.kt
ui/player/loading/LoadingOverlay.kt
ui/player/scrobble/TraktScrobbler.kt
ui/player/autoplay/NextEpisodeOverlay.kt
ui/player/subtitle/SubtitleSearchDialog.kt

res/layout/activity_video_player.xml
res/layout/fragment_video_player.xml
res/layout/overlay_player_loading.xml
res/layout/overlay_player_controls.xml
res/layout/overlay_next_episode.xml
res/layout/dialog_track_selector.xml
res/layout/dialog_player_settings.xml
res/layout/dialog_subtitle_search.xml
res/layout/item_subtitle_result.xml

res/drawable/bg_player_controls.xml
res/drawable/bg_skip_indicator.xml
res/drawable/selector_player_button.xml
```

### Files to Modify

```
app/build.gradle.kts (add ExoPlayer dependencies)
di/AppModule.kt (add DI for new repos)
AndroidManifest.xml (register VideoPlayerActivity)
ui/sources/SourcesActivity.kt (launch player on resolve)
data/remote/api/TraktApiService.kt (add scrobble endpoints)
```

---

## Notes & Considerations

### Performance
- Pre-load next episode sources while current episode plays
- Cache subtitle files locally
- Use ExoPlayer's built-in caching for streaming

### Error Handling
- Network errors during scrobbling shouldn't interrupt playback
- Graceful fallback if OpenSubtitles is unavailable
- Resume position saved on app kill/crash

### Accessibility
- Subtitle text size options
- High contrast subtitle backgrounds
- Audio descriptions track support (if available)

### Future Enhancements
- Picture-in-picture mode
- Chromecast support
- Multiple audio delay presets
- Subtitle appearance customization (font, color, size)
- Playback speed adjustment (0.5x - 2.0x)
