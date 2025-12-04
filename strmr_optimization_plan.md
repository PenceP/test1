# Strmr App Optimization Plan

## Overview

This document outlines performance optimizations for the Strmr Android TV app. The core issues are:
1. Eager loading syndrome - fetching data regardless of whether it changed
2. Missing HTTP caching layer
3. Redundant TMDB API calls
4. Reactive (not predictive) hero section loading
5. Poor cold start UX

---

## Problem 1: Missing OkHttp Disk Cache

### Issue
No HTTP-level caching configured. Every image and API response is fetched fresh from network, even when TMDB returns proper cache headers.

### Solution
Add a 50MB disk cache to OkHttpClient in NetworkModule.

### Code

```kotlin
// NetworkModule.kt - Update provideOkHttpClient

@Provides
@Singleton
fun provideOkHttpClient(
    @ApplicationContext context: Context,
    loggingInterceptor: HttpLoggingInterceptor
): OkHttpClient {
    val cacheSize = 50L * 1024L * 1024L // 50 MB
    val cacheDir = File(context.cacheDir, "http_cache")
    val cache = Cache(cacheDir, cacheSize)

    return OkHttpClient.Builder()
        .cache(cache)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
```

---

## Problem 2: No Centralized Sync Metadata Tracking

### Issue
Cache freshness checks are scattered across repositories. No single source of truth for "when was this data last synced?"

### Solution
Create a dedicated `sync_metadata` table to track last sync timestamps for all data categories.

### Code

```kotlin
// data/local/entity/SyncMetadataEntity.kt

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey 
    val key: String,  // e.g., "trending_movies", "continue_watching", "network_netflix"
    val lastSyncedAt: Long,
    val traktActivityTimestamp: String? = null  // For Trakt last_activities comparison
)
```

```kotlin
// data/local/dao/SyncMetadataDao.kt

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE `key` = :key")
    suspend fun get(key: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: SyncMetadataEntity)

    @Query("SELECT lastSyncedAt FROM sync_metadata WHERE `key` = :key")
    suspend fun getLastSyncTime(key: String): Long?

    @Query("DELETE FROM sync_metadata WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM sync_metadata")
    suspend fun clearAll()
}
```

```kotlin
// data/repository/SyncMetadataRepository.kt

@Singleton
class SyncMetadataRepository @Inject constructor(
    private val syncMetadataDao: SyncMetadataDao
) {
    companion object {
        private const val STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    suspend fun isStale(key: String): Boolean {
        val lastSync = syncMetadataDao.getLastSyncTime(key) ?: return true
        return (System.currentTimeMillis() - lastSync) > STALE_THRESHOLD_MS
    }

    suspend fun markSynced(key: String, traktTimestamp: String? = null) {
        syncMetadataDao.upsert(
            SyncMetadataEntity(
                key = key,
                lastSyncedAt = System.currentTimeMillis(),
                traktActivityTimestamp = traktTimestamp
            )
        )
    }

    suspend fun getTraktTimestamp(key: String): String? {
        return syncMetadataDao.get(key)?.traktActivityTimestamp
    }
}
```

Add to AppDatabase:

```kotlin
@Database(
    entities = [
        // ... existing entities
        SyncMetadataEntity::class
    ],
    version = X // increment version
)
abstract class AppDatabase : RoomDatabase() {
    // ... existing DAOs
    abstract fun syncMetadataDao(): SyncMetadataDao
}
```

---

## Problem 3: Continue Watching Bypasses last_activities

### Issue
`ContinueWatchingRepository.load()` always hits Trakt API without checking if playback data actually changed via `/sync/last_activities`.

### Solution
Gate Continue Watching refresh behind Trakt's `paused_at` timestamp from last_activities.

### Code

```kotlin
// data/repository/ContinueWatchingRepository.kt - Add sync check

@Singleton
class ContinueWatchingRepository @Inject constructor(
    private val traktApiService: TraktApiService,
    private val tmdbApiService: TMDBApiService,
    private val accountRepository: TraktAccountRepository,
    private val syncMetadataRepository: SyncMetadataRepository, // NEW
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val SYNC_KEY_CONTINUE_WATCHING = "continue_watching"
    }

    suspend fun load(limit: Int = 20, forceRefresh: Boolean = false): List<ContentItem> =
        withContext(ioDispatcher) {
            val account = accountRepository.refreshTokenIfNeeded() 
                ?: return@withContext emptyList()
            val authHeader = accountRepository.buildAuthHeader(account.accessToken)

            // Check if we need to refresh
            if (!forceRefresh && !shouldRefresh(authHeader)) {
                // Return cached data from Room instead
                return@withContext getCachedContinueWatching()
            }

            // Existing fetch logic...
            val playbackEntries = fetchPlaybackEntries(authHeader)
            // ... rest of existing implementation

            // After successful fetch, update sync timestamp
            val activities = traktApiService.getLastActivities(authHeader, BuildConfig.TRAKT_CLIENT_ID)
            val pausedAt = activities.movies?.pausedAt ?: activities.episodes?.pausedAt
            syncMetadataRepository.markSynced(SYNC_KEY_CONTINUE_WATCHING, pausedAt)

            // Cache results to Room and return
            // ...
        }

    private suspend fun shouldRefresh(authHeader: String): Boolean {
        // First check 24-hour staleness
        if (syncMetadataRepository.isStale(SYNC_KEY_CONTINUE_WATCHING)) {
            return true
        }

        // Then check Trakt last_activities
        val activities = try {
            traktApiService.getLastActivities(authHeader, BuildConfig.TRAKT_CLIENT_ID)
        } catch (e: Exception) {
            return false // Network error, use cache
        }

        val remotePausedAt = activities.movies?.pausedAt ?: activities.episodes?.pausedAt
        val localPausedAt = syncMetadataRepository.getTraktTimestamp(SYNC_KEY_CONTINUE_WATCHING)

        // If remote timestamp is newer, we need to refresh
        return remotePausedAt != null && remotePausedAt != localPausedAt
    }

    private suspend fun getCachedContinueWatching(): List<ContentItem> {
        // Implement cache retrieval from Room
        // You may need a ContinueWatchingDao for this
        return emptyList() // TODO: implement
    }
}
```

Ensure TraktLastActivities model includes paused_at:

```kotlin
// data/model/trakt/TraktLastActivities.kt

data class TraktLastActivities(
    val all: String?,
    val movies: TraktActivityGroup?,
    val episodes: TraktActivityGroup?,
    val shows: TraktActivityGroup?,
    val seasons: TraktActivityGroup?,
    val lists: TraktActivityGroup?
)

data class TraktActivityGroup(
    @SerializedName("watched_at") val watchedAt: String? = null,
    @SerializedName("collected_at") val collectedAt: String? = null,
    @SerializedName("rated_at") val ratedAt: String? = null,
    @SerializedName("watchlisted_at") val watchlistedAt: String? = null,
    @SerializedName("commented_at") val commentedAt: String? = null,
    @SerializedName("paused_at") val pausedAt: String? = null,  // IMPORTANT for continue watching
    @SerializedName("hidden_at") val hiddenAt: String? = null
)
```

---

## Problem 4: Row Loading Too Aggressive (50ms Stagger)

### Issue
In `BaseContentViewModel.loadAllRows()`, rows are staggered by only 50ms. This causes a thundering herd of API calls on startup.

### Solution
Increase stagger to 200-300ms and defer non-essential rows.

### Code

```kotlin
// ui/base/BaseContentViewModel.kt - Update loadAllRows()

protected open suspend fun loadAllRows(forceRefresh: Boolean = false) {
    if (rowStates.isEmpty()) return

    // Load first row immediately for instant UI
    rowStates.firstOrNull()?.let { first ->
        loadRowContent(0, first, forceRefresh = forceRefresh)
    }

    _isLoading.value = false

    // Categorize rows by priority
    val (criticalRows, deferredRows) = rowStates.drop(1).withIndex().partition { (_, state) ->
        // Critical: Trending, Popular, Continue Watching
        state.rowType in listOf("trending", "popular", "continue_watching")
    }

    // Load critical rows with moderate stagger
    kotlinx.coroutines.coroutineScope {
        criticalRows.forEach { (idx, state) ->
            launch {
                delay(200L * (idx + 1))  // 200ms stagger instead of 50ms
                loadRowContent(idx + 1, state, forceRefresh = forceRefresh)
            }
        }
    }

    // Defer non-essential rows (Networks, Directors, Studios) by 1 second
    kotlinx.coroutines.coroutineScope {
        deferredRows.forEach { (idx, state) ->
            launch {
                delay(1000L + (150L * idx))  // Start after 1s, then 150ms stagger
                loadRowContent(idx + 1, state, forceRefresh = forceRefresh)
            }
        }
    }

    // Prefetch next pages after all rows loaded
    delay(500)
    prefetchNextPages()
}
```

---

## Problem 5: TMDB Enrichment Causes N API Calls per N Items

### Issue
Throughout the codebase, pattern like `items.map { async { tmdbApi.getMovieDetails(it.tmdbId) } }.awaitAll()` fires N parallel requests. For a 20-item list, that's 20 TMDB calls.

### Solution
Create a `TmdbEnrichmentService` that coalesces requests, deduplicates, checks cache first, and batch-fetches only missing items.

### Code

```kotlin
// data/service/TmdbEnrichmentService.kt

@Singleton
class TmdbEnrichmentService @Inject constructor(
    private val tmdbApi: TMDBApiService,
    private val mediaDao: MediaDao,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<MediaEnrichment?>>()
    private val requestBuffer = Channel<EnrichmentRequest>(Channel.BUFFERED)
    private val inFlightBatches = AtomicInteger(0)

    data class MediaEnrichment(
        val tmdbId: Int,
        val posterUrl: String?,
        val backdropUrl: String?,
        val logoUrl: String?,
        val genres: String?,
        val cast: String?,
        val runtime: String?,
        val certification: String?
    )

    private data class EnrichmentRequest(
        val tmdbId: Int,
        val type: ContentType,
        val deferred: CompletableDeferred<MediaEnrichment?>
    )

    enum class ContentType { MOVIE, TV_SHOW }

    init {
        // Process requests in batches
        scope.launch(Dispatchers.IO) {
            val buffer = mutableListOf<EnrichmentRequest>()
            
            while (true) {
                // Collect requests for 75ms or until we have 15
                val request = if (buffer.isEmpty()) {
                    requestBuffer.receive() // Block until first request
                } else {
                    withTimeoutOrNull(75) { requestBuffer.receive() }
                }

                if (request != null) {
                    buffer.add(request)
                }

                // Process batch when timeout expires or batch is full
                if (request == null || buffer.size >= 15) {
                    if (buffer.isNotEmpty()) {
                        processBatch(buffer.toList())
                        buffer.clear()
                    }
                }
            }
        }
    }

    private suspend fun processBatch(requests: List<EnrichmentRequest>) {
        // Deduplicate by tmdbId
        val uniqueRequests = requests.distinctBy { it.tmdbId }
        val tmdbIds = uniqueRequests.map { it.tmdbId }

        // Check cache first
        val cached = mediaDao.getEnrichmentsByTmdbIds(tmdbIds)
        val cachedMap = cached.associateBy { it.tmdbId }

        // Resolve cached immediately
        uniqueRequests.forEach { req ->
            cachedMap[req.tmdbId]?.let { enrichment ->
                req.deferred.complete(enrichment)
                pendingRequests.remove(req.tmdbId)
            }
        }

        // Fetch missing from API
        val missing = uniqueRequests.filter { it.tmdbId !in cachedMap }
        if (missing.isEmpty()) return

        // Batch fetch with rate limiting (5 concurrent)
        missing.chunked(5).forEach { batch ->
            coroutineScope {
                batch.map { req ->
                    async {
                        try {
                            val enrichment = when (req.type) {
                                ContentType.MOVIE -> fetchMovieEnrichment(req.tmdbId)
                                ContentType.TV_SHOW -> fetchShowEnrichment(req.tmdbId)
                            }
                            enrichment?.let { mediaDao.insertEnrichment(it) }
                            req.deferred.complete(enrichment)
                        } catch (e: Exception) {
                            req.deferred.complete(null)
                        } finally {
                            pendingRequests.remove(req.tmdbId)
                        }
                    }
                }.awaitAll()
            }
            delay(250) // Rate limit between batches
        }
    }

    private suspend fun fetchMovieEnrichment(tmdbId: Int): MediaEnrichment? {
        val details = tmdbApi.getMovieDetails(
            movieId = tmdbId,
            apiKey = BuildConfig.TMDB_API_KEY,
            appendToResponse = "images,credits"
        )
        return MediaEnrichment(
            tmdbId = tmdbId,
            posterUrl = details.getPosterUrl(),
            backdropUrl = details.getBackdropUrl(),
            logoUrl = details.getLogoUrl(),
            genres = details.genres?.joinToString(", ") { it.name },
            cast = details.getCastNames(5),
            runtime = details.runtime?.let { "${it / 60}h ${it % 60}m" },
            certification = details.getCertification()
        )
    }

    private suspend fun fetchShowEnrichment(tmdbId: Int): MediaEnrichment? {
        val details = tmdbApi.getShowDetails(
            showId = tmdbId,
            apiKey = BuildConfig.TMDB_API_KEY,
            appendToResponse = "images,credits,content_ratings"
        )
        return MediaEnrichment(
            tmdbId = tmdbId,
            posterUrl = details.getPosterUrl(),
            backdropUrl = details.getBackdropUrl(),
            logoUrl = details.getLogoUrl(),
            genres = details.genres?.joinToString(", ") { it.name },
            cast = details.getCastNames(5),
            runtime = details.episodeRunTime?.firstOrNull()?.let { "${it}m" },
            certification = details.getCertification()
        )
    }

    /**
     * Request enrichment for a TMDB ID. Returns cached data if available,
     * otherwise queues for batch fetch.
     */
    suspend fun enrich(tmdbId: Int, type: ContentType): MediaEnrichment? {
        // Check if already pending
        pendingRequests[tmdbId]?.let { return it.await() }

        // Check cache synchronously
        mediaDao.getEnrichmentByTmdbId(tmdbId)?.let { return it }

        // Queue for batch fetch
        val deferred = CompletableDeferred<MediaEnrichment?>()
        pendingRequests[tmdbId] = deferred
        requestBuffer.send(EnrichmentRequest(tmdbId, type, deferred))
        return deferred.await()
    }

    /**
     * Pre-warm cache for a list of IDs (fire-and-forget)
     */
    fun preload(tmdbIds: List<Int>, type: ContentType) {
        scope.launch {
            tmdbIds.forEach { tmdbId ->
                if (pendingRequests[tmdbId] == null && mediaDao.getEnrichmentByTmdbId(tmdbId) == null) {
                    val deferred = CompletableDeferred<MediaEnrichment?>()
                    pendingRequests[tmdbId] = deferred
                    requestBuffer.send(EnrichmentRequest(tmdbId, type, deferred))
                }
            }
        }
    }
}
```

Add enrichment entity and DAO methods:

```kotlin
// data/local/entity/MediaEnrichmentEntity.kt

@Entity(tableName = "media_enrichment")
data class MediaEnrichmentEntity(
    @PrimaryKey val tmdbId: Int,
    val posterUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val genres: String?,
    val cast: String?,
    val runtime: String?,
    val certification: String?,
    val lastUpdated: Long = System.currentTimeMillis()
)
```

```kotlin
// Add to MediaDao

@Query("SELECT * FROM media_enrichment WHERE tmdbId IN (:tmdbIds)")
suspend fun getEnrichmentsByTmdbIds(tmdbIds: List<Int>): List<MediaEnrichmentEntity>

@Query("SELECT * FROM media_enrichment WHERE tmdbId = :tmdbId")
suspend fun getEnrichmentByTmdbId(tmdbId: Int): MediaEnrichmentEntity?

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertEnrichment(enrichment: MediaEnrichmentEntity)
```

---

## Problem 6: Hero Section Fetches Data On Focus (Reactive)

### Issue
`HeroExtrasLoader` and `HeroBackgroundController` fetch backdrop/logo/cast when user focuses an item. With D-pad navigation, this causes visible lag as data loads after focus.

### Solution
Predictively pre-load hero data for items within ±3 positions of current focus.

### Code

```kotlin
// ui/HeroPrefetchManager.kt

@Singleton
class HeroPrefetchManager @Inject constructor(
    private val enrichmentService: TmdbEnrichmentService
) {
    private val prefetchedIds = LruCache<Int, Boolean>(100)
    private var lastFocusIndex = -1
    private var currentRowItems: List<ContentItem> = emptyList()

    fun onRowLoaded(items: List<ContentItem>) {
        currentRowItems = items
        // Pre-fetch first 6 items immediately
        prefetchRange(0, 5)
    }

    fun onFocusChanged(focusIndex: Int) {
        if (focusIndex == lastFocusIndex) return
        lastFocusIndex = focusIndex

        // Prefetch window: [focus-2, focus+4]
        val start = (focusIndex - 2).coerceAtLeast(0)
        val end = (focusIndex + 4).coerceAtMost(currentRowItems.lastIndex)
        prefetchRange(start, end)
    }

    private fun prefetchRange(start: Int, end: Int) {
        if (currentRowItems.isEmpty()) return

        val itemsToFetch = (start..end)
            .mapNotNull { currentRowItems.getOrNull(it) }
            .filter { it.tmdbId > 0 && prefetchedIds.get(it.tmdbId) == null }

        if (itemsToFetch.isEmpty()) return

        itemsToFetch.forEach { item ->
            prefetchedIds.put(item.tmdbId, true)
        }

        val type = when (itemsToFetch.first().type) {
            ContentItem.ContentType.MOVIE -> TmdbEnrichmentService.ContentType.MOVIE
            ContentItem.ContentType.TV_SHOW -> TmdbEnrichmentService.ContentType.TV_SHOW
        }

        enrichmentService.preload(itemsToFetch.map { it.tmdbId }, type)
    }

    fun clearForNewRow() {
        lastFocusIndex = -1
        currentRowItems = emptyList()
    }
}
```

Update `ContentRowAdapter` to use prefetch manager:

```kotlin
// ui/adapter/ContentRowAdapter.kt - Update onItemFocused callback

// In the adapter, when focus changes:
onItemFocused = { item, rowIndex, itemIndex ->
    heroPrefetchManager.onFocusChanged(itemIndex)
    heroSyncManager.onContentSelected(item)
    // ... existing logic
}

// When rows update:
fun updateRows(newRows: List<ContentRow>) {
    // ... existing logic
    newRows.firstOrNull()?.items?.let { items ->
        heroPrefetchManager.onRowLoaded(items)
    }
}
```

---

## Problem 7: Cold Start Shows Loading Spinner

### Issue
On first launch (empty DB), user sees a loading indicator for 2-5 seconds before any content appears.

### Solution
Show skeleton UI immediately, then progressively replace with real content.

### Code

```kotlin
// ui/adapter/SkeletonContentItem.kt

object SkeletonContentItem {
    fun createPlaceholders(count: Int): List<ContentItem> {
        return (0 until count).map { index ->
            ContentItem(
                id = -1000 - index,  // Negative IDs for skeletons
                tmdbId = -1,
                imdbId = null,
                title = "",
                overview = null,
                posterUrl = null,
                backdropUrl = null,
                logoUrl = null,
                year = null,
                rating = null,
                ratingPercentage = null,
                genres = null,
                type = ContentItem.ContentType.MOVIE,
                runtime = null,
                cast = null,
                certification = null,
                imdbRating = null,
                rottenTomatoesRating = null,
                traktRating = null,
                watchProgress = null,
                isPlaceholder = true  // Use existing field
            )
        }
    }
}
```

```kotlin
// ui/base/BaseContentViewModel.kt - Update to show skeletons first

protected open fun buildRowsFromConfig() {
    val configs = getRowConfigs()
    rowStates.clear()
    
    configs.forEach { config ->
        val state = ContentRowState(
            category = config.category,
            title = config.title,
            rowType = config.rowType,
            contentType = config.contentType,
            presentation = config.presentation,
            pageSize = config.pageSize,
            items = SkeletonContentItem.createPlaceholders(6).toMutableList(), // Pre-populate
            hasMore = true
        )
        rowStates.add(state)
    }
    
    // Publish skeleton rows immediately
    publishRows()
}
```

```kotlin
// ui/adapter/ContentItemViewHolder.kt - Handle skeleton rendering

fun bind(item: ContentItem) {
    if (item.isPlaceholder) {
        showShimmer()
        return
    }
    hideShimmer()
    // ... existing bind logic
}

private fun showShimmer() {
    // Apply shimmer drawable to poster
    posterImage.setImageDrawable(ShimmerDrawable().apply {
        setShimmer(Shimmer.AlphaHighlightBuilder()
            .setDuration(1000)
            .setBaseAlpha(0.7f)
            .setHighlightAlpha(0.9f)
            .setDirection(Shimmer.Direction.LEFT_TO_RIGHT)
            .build())
    })
    titleText.text = ""
    // Hide other views
}

private fun hideShimmer() {
    // Handled by normal bind
}
```

Add Shimmer dependency if not present:

```kotlin
// build.gradle.kts
implementation("com.facebook.shimmer:shimmer:0.5.0")
```

---

## Problem 8: IntermediateViewRepository May Bypass 24hr Cache

### Issue
When clicking Collections, Directors, Networks row items, ensure data isn't re-fetched if less than 24 hours old.

### Solution
Verify `IntermediateViewRepository` uses `SyncMetadataRepository` for all intermediate views.

### Code

```kotlin
// data/repository/IntermediateViewRepository.kt - Ensure proper caching

@Singleton
class IntermediateViewRepository @Inject constructor(
    private val traktApiService: TraktApiService,
    private val tmdbApiService: TMDBApiService,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val intermediateViewDao: IntermediateViewDao
) {
    suspend fun getIntermediateViewData(
        viewType: String,
        itemId: String,
        forceRefresh: Boolean = false
    ): List<ContentItem> {
        val cacheKey = "intermediate_${viewType}_${itemId}"

        // Always return cache first if available
        val cached = intermediateViewDao.getItems(cacheKey)
        
        // If not forcing refresh and cache is fresh, return cached
        if (!forceRefresh && cached.isNotEmpty() && !syncMetadataRepository.isStale(cacheKey)) {
            return cached.map { it.toContentItem() }
        }

        // Fetch from API
        return try {
            val items = fetchFromApi(viewType, itemId)
            
            // Update cache
            intermediateViewDao.replaceItems(cacheKey, items.map { it.toEntity(cacheKey) })
            syncMetadataRepository.markSynced(cacheKey)
            
            items
        } catch (e: Exception) {
            // On error, return stale cache if available
            if (cached.isNotEmpty()) {
                cached.map { it.toContentItem() }
            } else {
                throw e
            }
        }
    }

    private suspend fun fetchFromApi(viewType: String, itemId: String): List<ContentItem> {
        return when (viewType) {
            "collection" -> fetchCollection(itemId)
            "director" -> fetchDirectorWorks(itemId)
            "network" -> fetchNetworkShows(itemId)
            "studio" -> fetchStudioMovies(itemId)
            else -> emptyList()
        }
    }
    
    // ... existing fetch methods
}
```

---

## Implementation Checklist

### P0 - Quick Wins (Week 1)
- [x] Add OkHttp disk cache (50MB) in NetworkModule
- [x] Create SyncMetadataEntity, SyncMetadataDao, SyncMetadataRepository
- [x] Add SyncMetadataEntity to AppDatabase, increment version, add migration
- [x] Increase row loading stagger from 50ms to 200ms
- [x] Defer non-essential rows (Networks, Directors) by 1 second

### P1 - Core Optimizations (Week 2)
- [x] Gate ContinueWatchingRepository behind last_activities paused_at check
- [x] Create TmdbEnrichmentService with request coalescing
- [x] Add MediaEnrichmentEntity for caching enrichment data
- [x] Create HeroPrefetchManager for predictive loading
- [x] Wire prefetch manager into ContentRowAdapter

### P2 - UX Polish (Week 3)
- [ ] Create SkeletonContentItem helper
- [ ] Update BaseContentViewModel to show skeletons immediately
- [ ] Add shimmer effect to ContentItemViewHolder
- [ ] Update IntermediateViewRepository to use SyncMetadataRepository
- [ ] Audit all data loading paths for 24-hour cache compliance

---

## Testing Verification

After implementing, verify with these checks:

```bash
# 1. Check network calls on startup (should see fewer TMDB calls)
adb logcat | grep -E "(TMDB|Trakt)" | head -50

# 2. Check HTTP cache hits
adb logcat | grep -i "cache"

# 3. Verify cold start time
adb shell am force-stop com.test1.tv
adb shell am start -W com.test1.tv/.ui.splash.SyncSplashActivity
# Look for "TotalTime" - should be under 1500ms for skeleton display

# 4. Check sync_metadata table
adb shell run-as com.test1.tv sqlite3 databases/app_database.db "SELECT * FROM sync_metadata;"
```
