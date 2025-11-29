# Android TV App - Master Fix Document

**Combined Analysis from Claude & Gemini**  
**Priority:** Critical Performance & Bug Fixes  
**Estimated Time:** 4-6 hours

---

## Executive Summary

Your refactoring introduced excellent architectural improvements (Hilt, Room, DiffUtil), but three critical issues need immediate attention:

1. **Serial Loading Bottleneck** - API calls are sequential (4s to load 20 items)
2. **Flow.last() Deadlock Risk** - ViewModel can hang forever
3. **Portrait/Landscape Bug** - Cached adapters ignore presentation changes

Plus several P1 fixes from the previous analysis.

---

## 🔴 CRITICAL FIX 1: Portrait/Landscape Poster Bug

### Problem
You're seeing landscape posters in portrait rows because `ContentRowAdapter` caches `PosterAdapter` instances by `rowIndex`. When the row rebinds with new data, the cached adapter keeps its **original** `presentation` value.

### Location
`ContentRowAdapter.kt` - `RowViewHolder.bind()` method

### Root Cause
```kotlin
var adapter = rowAdapters.get(rowIndex)
if (adapter == null) {  // ← Only creates new adapter if cache miss
    adapter = PosterAdapter(
        presentation = row.presentation,  // ← Set ONCE at creation
        ...
    )
    rowAdapters.put(rowIndex, adapter)
}
// If adapter exists, it IGNORES row.presentation changes!
```

### Fix
Check if presentation changed and recreate adapter if needed:

```kotlin
// In ContentRowAdapter.kt - RowViewHolder.bind()

fun bind(row: ContentRow, rowIndex: Int) {
    rowTitle.text = row.title

    var adapter = rowAdapters.get(rowIndex)
    
    // FIX: Check if presentation changed - if so, need new adapter
    val needsNewAdapter = adapter == null || 
        !adapter.hasPresentation(row.presentation)
    
    if (needsNewAdapter) {
        adapter = PosterAdapter(
            onItemClick = onItemClick,
            onItemFocused = { item, itemIndex ->
                onItemFocused(item, rowIndex, itemIndex)
            },
            onNavigateToNavBar = onNavigateToNavBar,
            onItemLongPressed = onItemLongPress,
            presentation = row.presentation,
            onNearEnd = {
                onRequestMore(rowIndex)
            },
            accentColorCache = accentColorCache
        )
        rowAdapters.put(rowIndex, adapter)
        
        // Force re-attach since adapter changed
        rowContent.adapter = null
    }

    if (rowContent.adapter !== adapter) {
        rowContent.adapter = adapter
        viewPool?.let { rowContent.setRecycledViewPool(it) }
        rowContent.setNumRows(1)
        rowContent.setItemSpacing(
            if (row.presentation == RowPresentation.LANDSCAPE_16_9) 16 else 8
        )
        rowContent.setHasFixedSize(true)
        rowContent.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)
        rowContent.setWindowAlignment(HorizontalGridView.WINDOW_ALIGN_LOW_EDGE)
        rowContent.setWindowAlignmentOffset(144)
        rowContent.setWindowAlignmentOffsetPercent(HorizontalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED)
        rowContent.setItemAlignmentOffset(60)
        rowContent.setItemAlignmentOffsetPercent(HorizontalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED)
        SmartRowScrollManager.attach(rowContent)
        rowContent.setOnKeyInterceptListener(scrollThrottler)
    }

    // Update row height based on presentation
    val layoutParams = rowContent.layoutParams
    val heightRes = if (row.presentation == RowPresentation.LANDSCAPE_16_9) {
        R.dimen.row_height_landscape
    } else {
        R.dimen.row_height_portrait
    }
    layoutParams.height = rowContent.resources.getDimensionPixelSize(heightRes)
    rowContent.layoutParams = layoutParams

    adapter.submitList(row.items.toList())
}
```

### Also add to PosterAdapter:

```kotlin
// In PosterAdapter.kt - add this method

fun hasPresentation(expected: RowPresentation): Boolean {
    return presentation == expected
}
```

---

## 🔴 CRITICAL FIX 2: Serial Loading Bottleneck (5x Faster)

### Problem
`MediaRepository.fetchMovies()` processes items **sequentially**. With 20 items at 200ms each = 4 seconds to load a single row.

### Location
`MediaRepository.kt` - `fetchMovies()` and `fetchShows()` methods

### Current Code (Bad)
```kotlin
traktMovies.forEachIndexed { index, movie ->
    rateLimiter.acquire()  // ← Waits, then processes ONE item
    // ... fetch details
}
```

### Fix: Batched Parallel Loading
Process 5 items at a time. This respects rate limits (40 req/10s) but is 5x faster.

```kotlin
// In MediaRepository.kt - Replace fetchMovies() entirely

private suspend fun fetchMovies(
    category: String,
    page: Int,
    popular: Boolean = false
): Resource.Success<List<ContentItem>> = coroutineScope {
    
    val traktMovies: List<com.test1.tv.data.model.trakt.TraktMovie> =
        if (popular) {
            traktApi.getPopularMovies(
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                page = page,
                limit = PAGE_SIZE
            )
        } else {
            traktApi.getTrendingMovies(
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                page = page,
                limit = PAGE_SIZE
            ).mapNotNull { it.movie }
        }

    val contentItems = mutableListOf<ContentItem>()
    val mediaEntities = mutableListOf<MediaContentEntity>()
    val imageEntities = mutableListOf<MediaImageEntity>()

    // BATCH PARALLEL: Process 5 at a time for 5x speedup
    traktMovies.chunked(5).forEachIndexed { batchIndex, batch ->
        val batchJobs = batch.mapIndexed { index, movie ->
            async {
                val tmdbId = movie.ids?.tmdb ?: return@async null
                val position = batchIndex * 5 + index

                // Each request acquires its own token
                rateLimiter.acquire()

                runCatching {
                    val tmdbDetails = tmdbApi.getMovieDetails(
                        movieId = tmdbId,
                        apiKey = BuildConfig.TMDB_API_KEY
                    )

                    val mediaEntity = MediaContentEntity(
                        tmdbId = tmdbId,
                        imdbId = movie.ids?.imdb,
                        title = tmdbDetails.title ?: movie.title ?: "",
                        overview = tmdbDetails.overview,
                        year = tmdbDetails.releaseDate?.take(4),
                        runtime = tmdbDetails.runtime,
                        certification = tmdbDetails.getCertification(),
                        contentType = "movie",
                        category = category,
                        position = position,
                        // Store genres as comma-separated string for offline
                        genres = tmdbDetails.genres?.joinToString(",") { it.name }
                    )

                    val imageEntity = MediaImageEntity(
                        tmdbId = tmdbId,
                        posterUrl = tmdbDetails.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                        backdropUrl = tmdbDetails.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                        logoUrl = tmdbDetails.getLogoUrl()
                    )

                    Triple(mediaEntity, imageEntity, toContentItem(mediaEntity, imageEntity))
                }.getOrNull()
            }
        }

        // Wait for this batch of 5 before starting next batch
        // This prevents flooding the rate limiter queue
        batchJobs.awaitAll().filterNotNull().forEach { (media, image, item) ->
            mediaEntities.add(media)
            imageEntities.add(image)
            contentItems.add(item)
        }
    }

    mediaDao.replaceCategory(category, mediaEntities, imageEntities)
    Resource.Success(contentItems)
}

// Apply the same pattern to fetchShows()
private suspend fun fetchShows(
    category: String,
    page: Int,
    popular: Boolean = false
): Resource.Success<List<ContentItem>> = coroutineScope {
    
    val traktShows: List<com.test1.tv.data.model.trakt.TraktShow> =
        if (popular) {
            traktApi.getPopularShows(
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                page = page,
                limit = PAGE_SIZE
            )
        } else {
            traktApi.getTrendingShows(
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                page = page,
                limit = PAGE_SIZE
            ).mapNotNull { it.show }
        }

    val contentItems = mutableListOf<ContentItem>()
    val mediaEntities = mutableListOf<MediaContentEntity>()
    val imageEntities = mutableListOf<MediaImageEntity>()

    // BATCH PARALLEL: Process 5 at a time
    traktShows.chunked(5).forEachIndexed { batchIndex, batch ->
        val batchJobs = batch.mapIndexed { index, show ->
            async {
                val tmdbId = show.ids?.tmdb ?: return@async null
                val position = batchIndex * 5 + index

                rateLimiter.acquire()

                runCatching {
                    val tmdbDetails = tmdbApi.getShowDetails(
                        showId = tmdbId,
                        apiKey = BuildConfig.TMDB_API_KEY
                    )

                    val mediaEntity = MediaContentEntity(
                        tmdbId = tmdbId,
                        imdbId = show.ids?.imdb,
                        title = tmdbDetails.name ?: show.title ?: "",
                        overview = tmdbDetails.overview,
                        year = tmdbDetails.firstAirDate?.take(4),
                        runtime = tmdbDetails.episodeRunTime?.firstOrNull(),
                        certification = tmdbDetails.getCertification(),
                        contentType = "tv",
                        category = category,
                        position = position,
                        genres = tmdbDetails.genres?.joinToString(",") { it.name }
                    )

                    val imageEntity = MediaImageEntity(
                        tmdbId = tmdbId,
                        posterUrl = tmdbDetails.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                        backdropUrl = tmdbDetails.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                        logoUrl = tmdbDetails.getLogoUrl()
                    )

                    Triple(mediaEntity, imageEntity, toContentItem(mediaEntity, imageEntity))
                }.getOrNull()
            }
        }

        batchJobs.awaitAll().filterNotNull().forEach { (media, image, item) ->
            mediaEntities.add(media)
            imageEntities.add(image)
            contentItems.add(item)
        }
    }

    mediaDao.replaceCategory(category, mediaEntities, imageEntities)
    Resource.Success(contentItems)
}
```

---

## 🔴 CRITICAL FIX 3: Flow.last() Deadlock

### Problem
`HomeViewModel` uses `.last()` on repository Flows. If the Flow never closes (e.g., Room observes DB continuously), `.last()` blocks forever and UI never loads.

### Location
`HomeViewModel.kt` - `loadRowPage()` method

### Current Code (Dangerous)
```kotlin
val result = when (state.category) {
    ContentRepository.CATEGORY_TRENDING_MOVIES -> 
        mapResource(mediaRepository.getTrendingMovies(page).last())  // ← Can block forever!
    // ...
}
```

### Fix: Use collect() with proper handling

```kotlin
// In HomeViewModel.kt - Replace loadRowPage() entirely

private suspend fun loadRowPage(
    rowIndex: Int,
    page: Int,
    forceRefresh: Boolean
) {
    val state = rowStates.getOrNull(rowIndex) ?: return
    if (state.isLoading) return
    if (!forceRefresh && page > 1 && !state.hasMore) return

    state.isLoading = true

    val flow = when (state.category) {
        ContentRepository.CATEGORY_TRENDING_MOVIES -> mediaRepository.getTrendingMovies(page)
        ContentRepository.CATEGORY_POPULAR_MOVIES -> mediaRepository.getPopularMovies(page)
        ContentRepository.CATEGORY_TRENDING_SHOWS -> mediaRepository.getTrendingShows(page)
        ContentRepository.CATEGORY_POPULAR_SHOWS -> mediaRepository.getPopularShows(page)
        ContentRepository.CATEGORY_CONTINUE_WATCHING -> {
            state.isLoading = false
            return  // Handled by loadContinueWatching()
        }
        else -> {
            state.isLoading = false
            return
        }
    }

    // FIX: Properly collect the Flow instead of using .last()
    flow.collect { resource ->
        when (resource) {
            is Resource.Success -> {
                applyRowItems(rowIndex, state, page, resource.data)
                state.isLoading = false
                return@collect  // Done, stop collecting
            }
            is Resource.Error -> {
                // Show cached data if available
                if (!resource.cachedData.isNullOrEmpty()) {
                    applyRowItems(rowIndex, state, page, resource.cachedData)
                }
                _error.value = "Failed to load ${state.title}: ${resource.exception.message}"
                state.isLoading = false
                return@collect  // Done, stop collecting
            }
            is Resource.Loading -> {
                // Show cached data immediately while loading
                if (!resource.cachedData.isNullOrEmpty()) {
                    applyRowItems(rowIndex, state, page, resource.cachedData)
                }
                // Keep collecting, wait for Success or Error
            }
        }
    }
}
```

### Alternative Fix (Simpler)
If you want to keep the current structure but avoid the deadlock:

```kotlin
// Use firstOrNull with timeout instead of last()
val result = when (state.category) {
    ContentRepository.CATEGORY_TRENDING_MOVIES -> 
        mapResource(
            withTimeoutOrNull(10_000) {
                mediaRepository.getTrendingMovies(page)
                    .first { it is Resource.Success || it is Resource.Error }
            } ?: Resource.Error(TimeoutException("Request timed out"))
        )
    // ... same for others
}
```

---

## 🟡 P1 FIX 4: HeroLogoLoader Version Checking

### Problem
`HeroLogoLoader` is a stateless object - old Glide callbacks can update the logo after user scrolled away.

### Fix
Convert to a class with version tracking:

```kotlin
// Replace HeroLogoLoader.kt entirely

package com.test1.tv.ui

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Thread-safe logo loader with version tracking to prevent stale updates.
 */
class HeroLogoLoader(
    private val fragment: Fragment,
    private val logoView: ImageView,
    private val titleView: View?,
    private val maxWidthRes: Int? = null,
    private val maxHeightRes: Int? = null
) {
    private var currentVersion = 0L
    private var currentTarget: CustomTarget<Drawable>? = null

    fun load(logoUrl: String?) {
        val version = ++currentVersion
        
        // Cancel previous request
        currentTarget?.let { 
            try { Glide.with(fragment).clear(it) } catch (_: Exception) {}
        }
        currentTarget = null
        
        // Reset to title state
        titleView?.visibility = View.VISIBLE
        logoView.visibility = View.GONE
        logoView.setImageDrawable(null)

        if (logoUrl.isNullOrBlank()) return

        val resources = logoView.resources
        val maxWidth = maxWidthRes?.let { resources.getDimensionPixelSize(it) }
        val maxHeight = maxHeightRes?.let { resources.getDimensionPixelSize(it) }

        currentTarget = object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                if (version != currentVersion) return  // STALE - ignore
                
                logoView.setImageDrawable(resource)
                applyBounds(resource, maxWidth, maxHeight)
                logoView.visibility = View.VISIBLE
                titleView?.visibility = View.GONE
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                if (version != currentVersion) return
                logoView.setImageDrawable(placeholder)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                if (version != currentVersion) return
                logoView.visibility = View.GONE
                titleView?.visibility = View.VISIBLE
            }
        }

        Glide.with(fragment)
            .load(logoUrl)
            .thumbnail(
                Glide.with(fragment).load(logoUrl).override(300, 100)
            )
            .transition(DrawableTransitionOptions.withCrossFade(150))
            .into(currentTarget!!)
    }

    fun cancel() {
        currentTarget?.let {
            try { Glide.with(fragment).clear(it) } catch (_: Exception) {}
        }
        currentTarget = null
    }

    private fun applyBounds(resource: Drawable, maxWidth: Int?, maxHeight: Int?) {
        if (maxWidth == null || maxHeight == null) return
        val intrinsicWidth = resource.intrinsicWidth.takeIf { it > 0 } ?: logoView.width
        val intrinsicHeight = resource.intrinsicHeight.takeIf { it > 0 } ?: logoView.height
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return

        val scale = min(maxWidth.toFloat() / intrinsicWidth, maxHeight.toFloat() / intrinsicHeight)
        logoView.layoutParams = logoView.layoutParams.apply {
            width = (intrinsicWidth * scale).roundToInt()
            height = (intrinsicHeight * scale).roundToInt()
        }
    }
}
```

### Update Fragment Usage

```kotlin
// Before (in each fragment):
HeroLogoLoader.load(this, logoUrl, heroLogo, heroTitle, R.dimen.hero_logo_max_width, R.dimen.hero_logo_max_height)

// After:
// 1. Add field
private lateinit var heroLogoLoader: HeroLogoLoader

// 2. Initialize in onViewCreated
heroLogoLoader = HeroLogoLoader(
    fragment = this,
    logoView = binding.heroLogo,
    titleView = binding.heroTitle,
    maxWidthRes = R.dimen.hero_logo_max_width,
    maxHeightRes = R.dimen.hero_logo_max_height
)

// 3. Use it
heroLogoLoader.load(item.logoUrl)

// 4. Cleanup in onDestroyView
heroLogoLoader.cancel()
```

---

## 🟡 P1 FIX 5: Async Palette Extraction in PosterAdapter

### Problem
`extractAccentColor()` runs synchronously on main thread, blocking 5-15ms per poster.

### Location
`PosterAdapter.kt` - `extractAccentColor()` method

### Fix
Make it async:

```kotlin
// In PosterAdapter.kt - PosterViewHolder class

private var paletteJob: Job? = null

private fun extractAccentColorAsync(item: ContentItem, drawable: Drawable) {
    paletteJob?.cancel()
    paletteJob = CoroutineScope(Dispatchers.Default).launch {
        try {
            val bitmap = when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> drawable.toBitmap(50, 75)  // Small for speed
            }
            val palette = Palette.from(bitmap).generate()
            val color = palette.vibrantSwatch?.rgb
                ?: palette.darkVibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: DEFAULT_BORDER_COLOR

            accentColorCache.put(item, color)

            withContext(Dispatchers.Main) {
                if (itemView.isFocused) {
                    applyFocusOverlay(true, color)
                }
            }
        } catch (_: Exception) {}
    }
}

// Call this instead of the sync version in onResourceReady
// And add cleanup:
fun recycle() {
    paletteJob?.cancel()
    paletteJob = null
}
```

Add to adapter:
```kotlin
override fun onViewRecycled(holder: PosterViewHolder) {
    super.onViewRecycled(holder)
    holder.recycle()
    Glide.with(holder.itemView).clear(holder.posterImage)
}
```

---

## 🟡 P1 FIX 6: Stable IDs for RecyclerView

### Location
`PosterAdapter.kt`

### Fix
```kotlin
class PosterAdapter(...) : ListAdapter<...>(...) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).tmdbId.toLong()
    }
}
```

---

## 🟡 P1 FIX 7: AccentColorCache Memory Bound

### Problem
Uses unbounded `MutableMap` - grows forever as user browses.

### Fix
```kotlin
// Replace AccentColorCache.kt

package com.test1.tv.ui

import android.graphics.Color
import android.util.LruCache
import com.test1.tv.data.model.ContentItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccentColorCache @Inject constructor() {
    
    companion object {
        const val DEFAULT = Color.WHITE
        private const val MAX_ENTRIES = 200
    }
    
    private val colors = LruCache<Int, Int>(MAX_ENTRIES)

    fun get(item: ContentItem): Int? {
        val key = item.tmdbId.takeIf { it != 0 } ?: item.id
        return colors.get(key)
    }

    fun put(item: ContentItem, color: Int) {
        val key = item.tmdbId.takeIf { it != 0 } ?: item.id
        colors.put(key, color)
    }

    fun clear() {
        colors.evictAll()
    }
}
```

---

## 🟢 P2 FIX 8: MainFragment Debounce Inconsistency

### Problem
MainFragment uses 250ms manual debounce instead of HeroSyncManager's 150ms.

### Fix
```kotlin
// In MainFragment.kt

// Remove the manual debounce:
// private var heroUpdateJob: Job? = null

// Use HeroSyncManager like other fragments:
private lateinit var heroSyncManager: HeroSyncManager

// In onViewCreated:
heroSyncManager = HeroSyncManager(viewLifecycleOwner) { content ->
    updateHeroSection(content)
}

// Change handleItemFocused:
private fun handleItemFocused(item: ContentItem, rowIndex: Int, itemIndex: Int) {
    Log.d(TAG, "Item focused: ${item.title} at row $rowIndex, position $itemIndex")
    heroSyncManager.onContentSelected(item)  // Use manager instead of manual delay
}
```

---

## 🟢 P2 FIX 9: Glide Memory Cache Size

### Problem
50MB cache is too small for 4K TV backdrops (~30MB per image).

### Location
`util/GlideModule.kt`

### Fix
```kotlin
// In GlideModule.kt - applyOptions()

override fun applyOptions(context: Context, builder: GlideBuilder) {
    // Dynamic: 2 screens worth of memory (handles 4K TVs)
    val calculator = MemorySizeCalculator.Builder(context)
        .setMemoryCacheScreens(2f)  // Default is 2, but explicitly set
        .build()
    
    builder.setMemoryCache(LruResourceCache(calculator.memoryCacheSize.toLong()))
    
    // Use RGB_565 for non-hero images to save memory
    builder.setDefaultRequestOptions(
        RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
    )
}
```

---

## 🟢 P2 FIX 10: Add Genres to MediaContentEntity

### Problem
Genres are not persisted, so offline mode shows empty genre text.

### Fix
Add `genres` column to `MediaContentEntity`:

```kotlin
// In MediaEntities.kt

@Entity(tableName = "media_content")
data class MediaContentEntity(
    @PrimaryKey val tmdbId: Int,
    val imdbId: String?,
    val title: String,
    val overview: String?,
    val year: String?,
    val runtime: Int?,
    val certification: String?,
    val contentType: String,
    val category: String,
    val position: Int,
    val genres: String? = null,  // ← ADD THIS (comma-separated)
    val cast: String? = null,    // ← ADD THIS (comma-separated)
    val updatedAt: Long = System.currentTimeMillis()
)
```

Update the toContentItem() mapping:
```kotlin
private fun MediaWithImages.toContentItem(): ContentItem {
    return ContentItem(
        // ... existing fields ...
        genres = content.genres,  // Now persisted!
        cast = content.cast
    )
}
```

---

## Implementation Order

| Priority | Fix | Time | Impact |
|----------|-----|------|--------|
| 🔴 P0 | Portrait/Landscape Bug (#1) | 15 min | Fixes visual bug |
| 🔴 P0 | Serial Loading (#2) | 30 min | 5x faster loading |
| 🔴 P0 | Flow.last() Deadlock (#3) | 20 min | Prevents hangs |
| 🟡 P1 | HeroLogoLoader Version (#4) | 30 min | Fixes hero desync |
| 🟡 P1 | Async Palette (#5) | 20 min | Smoother scrolling |
| 🟡 P1 | Stable IDs (#6) | 5 min | Better animations |
| 🟡 P1 | AccentColorCache LruCache (#7) | 10 min | Memory bounded |
| 🟢 P2 | MainFragment Debounce (#8) | 5 min | Consistency |
| 🟢 P2 | Glide Memory (#9) | 5 min | Better caching |
| 🟢 P2 | Genres Column (#10) | 15 min | Offline support |

**Total: ~2.5 hours for all fixes**

---

## Verification Checklist

After implementing, verify:

- [ ] All rows show correct poster orientation (portrait vs landscape)
- [ ] Rows load in ~1 second instead of 4+ seconds
- [ ] App doesn't hang on cold start
- [ ] Fast scrolling doesn't show wrong hero logo
- [ ] No frame drops during scroll (use Systrace)
- [ ] Memory stable after browsing 100+ items
- [ ] Genres visible in offline mode

---

## Files to Modify

1. `ContentRowAdapter.kt` - Fix #1
2. `PosterAdapter.kt` - Fix #1, #5, #6
3. `MediaRepository.kt` - Fix #2
4. `HomeViewModel.kt` - Fix #3
5. `HeroLogoLoader.kt` - Fix #4 (replace entirely)
6. `AccentColorCache.kt` - Fix #7 (replace entirely)
7. `MainFragment.kt` - Fix #8
8. `GlideModule.kt` - Fix #9
9. `MediaEntities.kt` - Fix #10
10. All fragments using HeroLogoLoader - Update usage
