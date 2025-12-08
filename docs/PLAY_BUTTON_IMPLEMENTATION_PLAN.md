# Play Button Implementation Plan

## Overview
This document outlines the phased implementation of the Play button functionality, including Premiumize integration, Torrentio scraper, link filtering, and source selection UI.

---

## Phase 1: Premiumize Account Integration

### 1.1 Database Schema
**Files to create/modify:**
- `data/local/entity/PremiumizeAccount.kt` - New entity
- `data/local/dao/PremiumizeAccountDao.kt` - New DAO
- `data/local/AppDatabase.kt` - Add entity, increment version, add migration

**PremiumizeAccount Entity:**
```kotlin
@Entity(tableName = "premiumize_accounts")
data class PremiumizeAccount(
    @PrimaryKey val providerId: String = "premiumize",
    val apiKey: String,
    val customerId: String?,
    val username: String?,
    val email: String?,
    val accountStatus: String?,      // "premium", "trial", etc.
    val expiresAt: Long?,            // Account expiry timestamp
    val pointsUsed: Double?,
    val pointsAvailable: Double?,
    val spaceLimitBytes: Long?,
    val spaceUsedBytes: Long?,
    val fairUsageLimitBytes: Long?,
    val fairUsageUsedBytes: Long?,
    val lastVerifiedAt: Long,
    val createdAt: Long
)
```

### 1.2 API Service
**Files to create:**
- `data/remote/api/PremiumizeApiService.kt` - Retrofit service
- `data/remote/model/premiumize/` - Response models

**Premiumize API Endpoints:**
```kotlin
interface PremiumizeApiService {
    // Account verification
    @GET("account/info")
    suspend fun getAccountInfo(@Query("apikey") apiKey: String): PremiumizeAccountResponse

    // Check cache status for multiple hashes
    @POST("cache/check")
    @FormUrlEncoded
    suspend fun checkCache(
        @Query("apikey") apiKey: String,
        @Field("items[]") hashes: List<String>
    ): PremiumizeCacheCheckResponse

    // Create transfer from magnet
    @POST("transfer/create")
    @FormUrlEncoded
    suspend fun createTransfer(
        @Query("apikey") apiKey: String,
        @Field("src") magnetOrHash: String
    ): PremiumizeTransferResponse

    // Get direct download link
    @POST("transfer/directdl")
    @FormUrlEncoded
    suspend fun getDirectLink(
        @Query("apikey") apiKey: String,
        @Field("src") magnetOrHash: String
    ): PremiumizeDirectLinkResponse
}
```

### 1.3 Repository Layer
**Files to create:**
- `data/repository/PremiumizeRepository.kt`

**Key Methods:**
```kotlin
suspend fun verifyApiKey(apiKey: String): Result<PremiumizeAccount>
suspend fun getAccount(): PremiumizeAccount?
suspend fun saveAccount(account: PremiumizeAccount)
suspend fun clearAccount()
suspend fun checkCacheStatus(hashes: List<String>): Map<String, Boolean>
suspend fun resolveToDirectLink(magnetOrHash: String): Result<String>
suspend fun refreshAccountInfo(): Result<PremiumizeAccount>
```

### 1.4 UI Components
**Files to modify:**
- `ui/settings/fragments/AccountsFragment.kt` - Add Premiumize card

**Premiumize Account Card Features:**
- API Key input field (visible text, paste-friendly)
- "Verify" button to authenticate
- Display after verification:
  - Username
  - Account status (Premium/Trial)
  - Time remaining (days until expiry)
  - Storage used/available
  - Fair usage stats
- "Disconnect" button to remove account

### 1.5 Dependency Injection
**Files to modify:**
- `di/AppModule.kt` - Add Premiumize Retrofit instance and service

---

## Phase 2: Link Filtering Settings

### 2.1 Preferences Storage
**Files to create/modify:**
- `data/repository/LinkFilterPreferences.kt` - New preferences handler

**Filter Settings:**
```kotlin
data class LinkFilterSettings(
    // Quality toggles
    val quality4kEnabled: Boolean = true,
    val quality1080pEnabled: Boolean = true,
    val quality720pEnabled: Boolean = true,
    val qualityCamEnabled: Boolean = false,
    val qualityUnknownEnabled: Boolean = true,

    // Bitrate limits (in Mbps, 0 = no limit)
    val minBitrateMbps: Int = 0,
    val maxBitrateMbps: Int = 0,

    // Exclude phrases (comma-separated stored as Set)
    val excludePhrases: Set<String> = setOf("DV", "3D", "HDR10+"),

    // Sort preferences
    val sortCachedFirst: Boolean = true,
    val sortBy: SortOption = SortOption.QUALITY // QUALITY, SIZE, SEEDS
)
```

### 2.2 UI Components
**Files to create:**
- `ui/settings/fragments/LinkFilteringFragment.kt`

**Layout Structure (Based on Reference Screenshot):**
```
┌─────────────────────────────────────────────────────────────────┐
│  Link Filtering                                                 │
│  Manage your link filtering preferences.                        │
├─────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  QUALITY RESOLUTION                                       │  │
│  │                                                           │  │
│  │  [  4k  ] [1080p ] [ 720p ]  [ SD  ]  [ CAM ]            │  │
│  │   purple   purple   purple    muted    muted              │  │
│  │                                                           │  │
│  │  [Unknown]                                                │  │
│  │    muted                                                  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Minimum Bitrate                              15  Mbps    │  │
│  │  [████████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░]  │  │
│  │                                                           │  │
│  │  Maximum Bitrate                              50  Mbps    │  │
│  │  [██████████████████████████████████░░░░░░░░░░░░░░░░░░]  │  │
│  │   purple slider tracks                                    │  │
│  │                                                           │  │
│  │  EXCLUDE PHRASES                                          │  │
│  │  ┌─────────────────────────────────────────────────────┐ │  │
│  │  │  DV, 3D, HEVC...                                    │ │  │
│  │  └─────────────────────────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘

Design Notes:
- Quality chips: Rounded rectangle buttons, purple when ON, dark/muted when OFF
- Bitrate sliders: Purple accent color, values displayed on right (Min AND Max)
- Exclude phrases: Text input field with placeholder text (comma-separated)
- Dark theme with card-style sections
```

### 2.3 Settings Items
**Files to modify:**
- `ui/settings/model/SettingsItem.kt` - Add ChipGroup and TagInput types if needed

---

## Phase 3: Torrentio Scraper Integration

### 3.1 API Service
**Files to create:**
- `data/remote/api/TorrentioService.kt`
- `data/remote/model/torrentio/` - Response models

**Torrentio Stremio Addon API:**
```kotlin
interface TorrentioService {
    // Base URL: https://torrentio.strem.fun
    // Config: providers=yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentgalaxy,magnetdl

    // Get streams for movie
    @GET("{config}/stream/movie/{imdbId}.json")
    suspend fun getMovieStreams(
        @Path("config") config: String,
        @Path("imdbId") imdbId: String
    ): TorrentioStreamResponse

    // Get streams for TV episode
    @GET("{config}/stream/series/{imdbId}:{season}:{episode}.json")
    suspend fun getEpisodeStreams(
        @Path("config") config: String,
        @Path("imdbId") imdbId: String,
        @Path("season") season: Int,
        @Path("episode") episode: Int
    ): TorrentioStreamResponse
}
```

**Response Model:**
```kotlin
data class TorrentioStreamResponse(
    val streams: List<TorrentioStream>
)

data class TorrentioStream(
    val name: String,           // e.g., "Torrentio\n4K"
    val title: String,          // Full title with metadata
    val infoHash: String,       // Torrent hash for cache check
    val fileIdx: Int?,          // File index in torrent
    val behaviorHints: BehaviorHints?
)

// Parsed from title string:
data class ParsedStreamInfo(
    val quality: String,        // 4K, 1080p, 720p, etc.
    val source: String,         // RARBG, 1337x, etc.
    val size: String,           // "2.5 GB"
    val sizeBytes: Long,
    val seeds: Int?,
    val codec: String?,         // x265, x264
    val audio: String?,         // DTS, AAC, etc.
    val hdr: String?,           // HDR, HDR10, DV
    val fileName: String
)
```

### 3.2 Repository Layer
**Files to create:**
- `data/repository/TorrentioRepository.kt`

**Key Methods:**
```kotlin
suspend fun searchMovieStreams(imdbId: String): List<ParsedStreamInfo>
suspend fun searchEpisodeStreams(imdbId: String, season: Int, episode: Int): List<ParsedStreamInfo>
private fun parseStreamTitle(title: String): ParsedStreamInfo
private fun buildConfigString(): String  // Builds provider config
```

### 3.3 Link Resolution Service
**Files to create:**
- `data/service/LinkResolutionService.kt`

**Orchestrates the full flow:**
```kotlin
@Singleton
class LinkResolutionService @Inject constructor(
    private val torrentioRepository: TorrentioRepository,
    private val premiumizeRepository: PremiumizeRepository,
    private val linkFilterPreferences: LinkFilterPreferences,
    private val cachedLinksDao: CachedLinksDao
) {
    // Main entry point
    suspend fun getStreamsForMovie(imdbId: String): Result<List<ResolvedStream>>
    suspend fun getStreamsForEpisode(imdbId: String, season: Int, episode: Int): Result<List<ResolvedStream>>

    // Internal flow:
    // 1. Check cache for previously resolved links
    // 2. Fetch from Torrentio
    // 3. Parse and filter based on user preferences
    // 4. Check Premiumize cache status for all hashes
    // 5. Sort (cached first, then by quality/size/seeds)
    // 6. Return combined results

    suspend fun resolveStream(stream: ResolvedStream): Result<String>  // Get direct URL
}

data class ResolvedStream(
    val infoHash: String,
    val quality: String,
    val source: String,
    val size: String,
    val sizeBytes: Long,
    val seeds: Int?,
    val isCached: Boolean,
    val fileName: String,
    val codec: String?,
    val audio: String?,
    val hdr: String?,
    val fileIdx: Int?
)
```

### 3.4 Caching Resolved Links
**Files to create:**
- `data/local/entity/CachedResolvedLink.kt`
- `data/local/dao/CachedResolvedLinkDao.kt`

**For Continue Watching feature:**
```kotlin
@Entity(tableName = "cached_resolved_links")
data class CachedResolvedLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: String,          // IMDB ID or internal ID
    val contentType: String,        // "movie" or "episode"
    val season: Int?,
    val episode: Int?,
    val infoHash: String,
    val fileIdx: Int?,
    val directUrl: String?,         // Resolved URL (may expire)
    val quality: String,
    val source: String,
    val fileName: String,
    val resolvedAt: Long,
    val expiresAt: Long?            // URL expiry
)
```

---

## Phase 4: Sources Selection UI

### 4.1 Sources Activity/Fragment
**Files to create:**
- `ui/sources/SourcesActivity.kt`
- `ui/sources/SourcesFragment.kt`
- `ui/sources/SourcesViewModel.kt`
- `ui/sources/adapter/SourcesAdapter.kt`
- `res/layout/activity_sources.xml`
- `res/layout/fragment_sources.xml`
- `res/layout/item_source.xml`

### 4.2 UI Layout Design (Based on Reference Screenshots)
```
┌────────────────────────────────────────────────────────────────────────────┐
│                        [MOVIE LOGO]                              8:22 AM   │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ┌──────────────────┐    ☁ 1) Movie.Name.2024.uhd.bluray.2160p.hdr...     │
│  │                  │       67.00 GB  |  TORRENT  |  PREMIUMIZE            │
│  │                  │                                                      │
│  │   MOVIE POSTER   │    ☁ 2) Movie.Name.2024.2160p.bluray.hevc.truehd... │
│  │                  │       61.50 GB  |  TORRENT  |  PREMIUMIZE            │
│  │                  │                                                      │
│  │                  │    ☁ 3) Movie.Name.2024.bluray.remux.hevc.dts-hd... │ ← SELECTED
│  │    [MOVIE LOGO]  │       17.21 GB  |  TORRENT  |  PREMIUMIZE            │
│  └──────────────────┘                                                      │
│                          ☁ 4) Movie.Name.2024.2160p.uhd.bluray.x265.hdr.. │
│  Movie Title             17.93 GB  |  TORRENT  |  PREMIUMIZE              │
│  PG-13  |  2017                                                           │
│                          ☁ 5) Movie.Name.2024.4K.UltraHD.BluRay.2160p... │
│  Movie description       11.21 GB  |  TORRENT  |  PREMIUMIZE              │
│  text goes here...                                                        │
│                          ☁ 6) Movie Name 2024 4K HDR 2160p BDRip...      │
│                             10.65 GB  |  TORRENT  |  PREMIUMIZE           │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### 4.3 Source Item Components (Matching Reference)
- **Cloud Icon**: Left side indicator (☁) - indicates cached on Premiumize
- **Numbered Entry**: Sequential number (1, 2, 3...)
- **Filename**: Full torrent filename with quality/codec info
- **File Size**: Displayed prominently (67.00 GB, 17.93 GB, etc.)
- **Type Badges**: `TORRENT | PREMIUMIZE` separated by pipes
- **Selected State**: Lighter background highlight on focused item
- **Cached sources sorted to top** of the list

### 4.4 ViewModel
```kotlin
@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val linkResolutionService: LinkResolutionService,
    private val premiumizeRepository: PremiumizeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SourcesUiState>(SourcesUiState.Loading)
    val uiState: StateFlow<SourcesUiState> = _uiState

    fun loadSources(contentId: String, type: String, season: Int?, episode: Int?)
    fun rescrape()
    fun selectSource(source: ResolvedStream)

    sealed class SourcesUiState {
        object Loading : SourcesUiState()
        data class Success(
            val cachedSources: List<ResolvedStream>,
            val uncachedSources: List<ResolvedStream>
        ) : SourcesUiState()
        data class Error(val message: String) : SourcesUiState()
        object NoPremiumize : SourcesUiState()  // Prompt to add account
    }
}
```

---

## Phase 5: Play Button Integration

### 5.1 Details Screen Integration
**Files to modify:**
- `ui/details/DetailsFragment.kt` or equivalent
- Context menu handler

**Play Button Flow:**
1. User clicks "Play" on movie or episode
2. Check if Premiumize account exists
   - If not: Show prompt to add account in Settings
3. Launch SourcesActivity with content info
4. User selects source
5. Resolve to direct URL via Premiumize
6. Launch PlaybackActivity with URL

### 5.2 Episode Card Integration
**Files to modify:**
- Episode list adapter
- Episode click handler

**For TV Shows:**
- Clicking episode card → opens Sources for that specific episode
- Pass: IMDB ID + Season + Episode number

### 5.3 Continue Watching Integration
**Files to modify:**
- `ContinueWatchingDao.kt`
- Continue watching card click handler

**Flow:**
1. Check cached resolved link exists and not expired
2. If valid: Direct play with cached URL
3. If expired: Rescrape and show sources
4. Option to rescrape even if cached (long-press or menu option)

---

## Phase 6: Error Handling & Edge Cases

### 6.1 Error States
- No Premiumize account configured
- Premiumize API key invalid/expired
- No sources found from Torrentio
- All sources filtered out by preferences
- Premiumize cache check failed
- Direct link resolution failed
- Network errors

### 6.2 Loading States
- Fetching sources from Torrentio
- Checking cache status on Premiumize
- Resolving direct link

### 6.3 UI Feedback
- Progress indicators during loading
- Toast/Snackbar for errors
- Empty state when no sources match filters

---

## Implementation Order

### Sprint 1: Foundation
1. [ ] PremiumizeAccount entity + DAO + migration
2. [ ] Premiumize API service + models
3. [ ] PremiumizeRepository
4. [ ] DI setup for Premiumize
5. [ ] AccountsFragment - Premiumize card UI

### Sprint 2: Filtering
6. [ ] LinkFilterPreferences
7. [ ] LinkFilteringFragment UI
8. [ ] Settings integration

### Sprint 3: Torrentio
9. [ ] TorrentioService + models
10. [ ] Stream title parser
11. [ ] TorrentioRepository
12. [ ] CachedResolvedLink entity + DAO

### Sprint 4: Resolution Service
13. [ ] LinkResolutionService
14. [ ] Filter application logic
15. [ ] Cache status integration

### Sprint 5: Sources UI
16. [ ] SourcesActivity/Fragment
17. [ ] SourcesAdapter
18. [ ] SourcesViewModel
19. [ ] Layout files

### Sprint 6: Integration
20. [ ] Details screen Play button
21. [ ] Episode card integration
22. [ ] Continue watching integration
23. [ ] Error handling polish

---

## API Reference

### Premiumize API
- Base URL: `https://www.premiumize.me/api/`
- Auth: API key as query parameter
- Docs: https://www.premiumize.me/api

### Torrentio (Stremio Addon)
- Base URL: `https://torrentio.strem.fun/`
- Config format: `providers=yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentgalaxy,magnetdl`
- No auth required
- Rate limiting: Be respectful, add delays between requests

---

## File Structure Summary

```
app/src/main/java/com/test1/tv/
├── data/
│   ├── local/
│   │   ├── entity/
│   │   │   ├── PremiumizeAccount.kt          [NEW]
│   │   │   └── CachedResolvedLink.kt         [NEW]
│   │   ├── dao/
│   │   │   ├── PremiumizeAccountDao.kt       [NEW]
│   │   │   └── CachedResolvedLinkDao.kt      [NEW]
│   │   └── AppDatabase.kt                     [MODIFY]
│   ├── remote/
│   │   ├── api/
│   │   │   ├── PremiumizeApiService.kt       [NEW]
│   │   │   └── TorrentioService.kt           [NEW]
│   │   └── model/
│   │       ├── premiumize/                    [NEW]
│   │       └── torrentio/                     [NEW]
│   ├── repository/
│   │   ├── PremiumizeRepository.kt           [NEW]
│   │   ├── TorrentioRepository.kt            [NEW]
│   │   └── LinkFilterPreferences.kt          [NEW]
│   └── service/
│       └── LinkResolutionService.kt          [NEW]
├── ui/
│   ├── settings/
│   │   └── fragments/
│   │       ├── AccountsFragment.kt           [MODIFY]
│   │       └── LinkFilteringFragment.kt      [NEW]
│   └── sources/
│       ├── SourcesActivity.kt                [NEW]
│       ├── SourcesFragment.kt                [NEW]
│       ├── SourcesViewModel.kt               [NEW]
│       └── adapter/
│           └── SourcesAdapter.kt             [NEW]
└── di/
    └── AppModule.kt                          [MODIFY]

res/layout/
├── activity_sources.xml                      [NEW]
├── fragment_sources.xml                      [NEW]
├── fragment_link_filtering.xml               [NEW]
└── item_source.xml                           [NEW]
```

---

## Questions Resolved
- Database: Using existing Room setup ✓
- Other debrids: Premiumize first, others later ✓
- API key display: Visible text ✓
- Torrentio providers: YTS, EZTV, RARBG, 1337x, PirateBay, KickAss, TorrentGalaxy, MagnetDL ✓
- Cache indicator: Show "isCached" from Premiumize, cached first ✓
- Link caching: For continue watching, with rescrape option ✓
- TV show play: Scrape currently selected episode ✓
- Separate sources button: No, Play goes directly to sources ✓
