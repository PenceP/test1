# STRMR - Master Engineering & Development Plan

This document serves as the single source of truth for all development work. It combines critical bug fixes, architectural repairs, UI/UX refinements, and feature implementations identified during code audit and user requirements gathering.

---

## 🚨 Phase 1: Critical Stability & Security (IMMEDIATE)

These items address crashes, memory leaks, security vulnerabilities, and build issues. **Must be resolved before any new features.**

---

### 1.1 Fix Hardcoded API Credentials

**File:** `app/build.gradle.kts`

**Issue:** The build script contains a hardcoded fallback for `OMDB_API_KEY` (`"a8787305"`). This is a security vulnerability that could expose API keys in version control.

**Fix:**
```kotlin
// REMOVE this pattern:
buildConfigField("String", "OMDB_API_KEY", "\"${properties["OMDB_API_KEY"] ?: "a8787305"}\"")

// REPLACE with:
buildConfigField("String", "OMDB_API_KEY", "\"${properties["OMDB_API_KEY"] 
    ?: throw GradleException("OMDB_API_KEY not found in secrets.properties")}\"")

// Or for debug builds only:
buildTypes {
    debug {
        buildConfigField("String", "OMDB_API_KEY", "\"${properties["OMDB_API_KEY"] ?: ""}\"")
    }
    release {
        buildConfigField("String", "OMDB_API_KEY", "\"${properties["OMDB_API_KEY"] 
            ?: throw GradleException("OMDB_API_KEY required for release builds")}\"")
    }
}
```

**Priority:** 🔴 CRITICAL - Security vulnerability

---

### 1.2 Fix Memory Leak: Timer in MainFragment

**File:** `MainFragment.kt`

**Issue:** Usage of `java.util.Timer` and `TimerTask` for background rotation creates memory leak and crash risk if Fragment is destroyed while timer is active (threading violation).

**Fix:**
```kotlin
// REMOVE:
private var backgroundTimer: Timer? = null
private fun startBackgroundTimer() {
    backgroundTimer = Timer()
    backgroundTimer?.schedule(object : TimerTask() {
        override fun run() {
            handler.post { updateBackground() }
        }
    }, BACKGROUND_UPDATE_DELAY, BACKGROUND_UPDATE_DELAY)
}

// REPLACE with:
private var backgroundJob: Job? = null

private fun startBackgroundRotation() {
    backgroundJob?.cancel()
    backgroundJob = viewLifecycleOwner.lifecycleScope.launch {
        while (isActive) {
            delay(BACKGROUND_UPDATE_DELAY)
            updateBackground()
        }
    }
}

override fun onDestroyView() {
    backgroundJob?.cancel()
    super.onDestroyView()
}
```

**Priority:** 🔴 CRITICAL - Memory leak / potential crash

---

### 1.3 Fix Memory Leak: Handler in BrowseErrorActivity

**File:** `BrowseErrorActivity.kt`

**Issue:** `Handler().postDelayed` holds implicit reference to Activity, preventing garbage collection if Activity is destroyed before delay completes.

**Fix:**
```kotlin
// REMOVE:
Handler().postDelayed({
    finish()
}, 3000)

// REPLACE with:
lifecycleScope.launch {
    delay(3000)
    finish()
}
```

**Priority:** 🔴 CRITICAL - Memory leak

---

### 1.4 Fix "Wrong Icon" Recycling Bug

**File:** `CardPresenter.kt`

**Issue:** Disney+ showing HBO Max icon (and vice versa). The `onBindViewHolder` checks `if (movie.cardImageUrl != null)` but lacks an `else` block. When a view is recycled, if the new item has no image, it keeps displaying the old image from the previous item.

**Root Cause:** This is the PRIMARY cause of wrong icons - not just Glide caching.

**Fix:**
```kotlin
override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
    val movie = item as Movie
    val cardView = viewHolder.view as ImageCardView
    
    cardView.titleText = movie.title
    cardView.contentText = movie.studio
    
    if (movie.cardImageUrl != null) {
        cardView.mainImageView.visibility = View.VISIBLE
        Glide.with(viewHolder.view.context)
            .load(movie.cardImageUrl)
            .centerCrop()
            .error(R.drawable.default_background)
            .into(cardView.mainImageView)
    } else {
        // CRITICAL: Clear the image when recycling!
        cardView.mainImageView.setImageDrawable(null)
        // Or set a placeholder:
        // cardView.mainImageView.setImageResource(R.drawable.default_card)
    }
}

override fun onUnbindViewHolder(viewHolder: ViewHolder) {
    val cardView = viewHolder.view as ImageCardView
    // Clear image to prevent memory leaks and recycling issues
    Glide.with(cardView.context).clear(cardView.mainImageView)
    cardView.mainImageView.setImageDrawable(null)
    cardView.badgeImage = null
}
```

**Priority:** 🔴 CRITICAL - Visual bug affecting user trust

---

### 1.5 Fix Glide VectorDrawable Crash

**File:** `PosterAdapter.kt` and any code loading `drawable://` URIs

**Issue (from Appendix A):** `NoResultEncoderAvailableException` when loading drawable:// resources (like `trakt2` or `ic_watchlist`) that are VectorDrawables. Glide cannot encode vectors for disk caching.

**Error:**
```
Failed to find result encoder for resource class: class android.graphics.drawable.VectorDrawable
```

**Fix Option 1 - Don't use Glide for local drawables (Recommended):**
```kotlin
private fun loadDrawableResource(imageView: ImageView, drawableResId: Int) {
    // Don't use Glide for local vector drawables
    imageView.setImageResource(drawableResId)
}
```

**Fix Option 2 - Disable disk caching for vectors:**
```kotlin
Glide.with(context)
    .load(drawableResId)
    .diskCacheStrategy(DiskCacheStrategy.NONE)  // Critical for VectorDrawables
    .into(imageView)
```

**Priority:** 🔴 CRITICAL - Causes crashes and wrong icons

---

### 1.6 Fix HWUI Image Decoder Errors

**Issue (from Appendix A):** Repeated `Failed to create image decoder with message 'unimplemented'` in logs.

**Root Cause:** HWUI cannot decode certain compressed XML drawables or vectors.

**Fix:**
```kotlin
// In build.gradle.kts - disable PNG crunching
android {
    aaptOptions {
        cruncherEnabled = false
    }
}
```

**Alternative:** Convert problematic VectorDrawables to WebP for frequently-loaded items.

**Priority:** 🟠 HIGH - Performance and visual glitches

---

### 1.7 Migrate from kapt to KSP

**File:** `app/build.gradle.kts`

**Issue:** Using `kapt` for Room, Glide, and Hilt annotation processing is slow.

**Fix:**
```kotlin
plugins {
    // REMOVE: id("kotlin-kapt")
    id("com.google.devtools.ksp") version "1.9.20-1.0.14"
}

dependencies {
    // REPLACE kapt with ksp:
    // kapt("androidx.room:room-compiler:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // kapt("com.google.dagger:hilt-compiler:2.48")
    ksp("com.google.dagger:hilt-compiler:2.48")
    
    // kapt("com.github.bumptech.glide:compiler:4.16.0")
    ksp("com.github.bumptech.glide:ksp:4.16.0")
}
```

**Impact:** Build times improve by up to 2x.

**Priority:** 🟠 HIGH - Developer experience

---

## 🎨 Phase 2: UI/UX Refinements

Addressing visual polish, focus issues, and layout problems.

---

### 2.1 Fix Settings Submenu Spacing

**Issue:** `VerticalGridView` defaults to centering items, causing large gaps at the top of settings lists.

**Fix:** In Settings Fragments where `VerticalGridView` is used:
```kotlin
verticalGridView.apply {
    windowAlignment = BaseGridView.WINDOW_ALIGN_LOW_EDGE
    windowAlignmentOffset = 0  // Or specific dp for top margin
    windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
    itemAlignmentOffset = 0
    itemAlignmentOffsetPercent = BaseGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED
}
```

**Priority:** 🟠 HIGH - Layout issue

---

### 2.2 Fix Long Press Context Menu Jitter

**Issue:** Menu appears and immediately selects first item before user can read it, then disappears. User is still holding "OK" from the long press.

**Fix:**
```kotlin
class ContextMenuDialog(context: Context) : Dialog(context) {
    
    private var keyUpReceived = false
    
    override fun show() {
        super.show()
        keyUpReceived = false
    }
    
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // Wait for key UP before allowing any selection
                if (event.action == KeyEvent.ACTION_UP) {
                    if (!keyUpReceived) {
                        keyUpReceived = true
                        return true  // Consume this first key up
                    }
                }
                
                // Only process DOWN after we've seen UP
                if (event.action == KeyEvent.ACTION_DOWN && !keyUpReceived) {
                    return true  // Consume - user still holding from long press
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
```

**Priority:** 🔴 CRITICAL - Core functionality broken

---

### 2.3 Horizontal Scroll for Link Titles

**File:** `res/layout/item_source.xml`, `SourcesAdapter.kt`

**Issue:** Link/source titles are always too long to fully display.

**Fix:**
```xml
<!-- In item_source.xml -->
<TextView
    android:id="@+id/source_title"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:singleLine="true"
    android:ellipsize="marquee"
    android:marqueeRepeatLimit="marquee_forever"
    android:focusable="true"
    android:focusableInTouchMode="true"
    android:scrollHorizontally="true" />
```

```kotlin
// In SourcesAdapter.kt - set isSelected when focused
holder.itemView.setOnFocusChangeListener { _, hasFocus ->
    holder.titleView.isSelected = hasFocus  // Triggers marquee
}
```

**Priority:** 🟠 HIGH - Usability issue

---

### 2.4 Remove Colored Backdrop Overlay on Focus

**Issue:** Palette extraction on poster focus is resource-intensive for minimal visual gain.

**Fix:** Replace with standard transparent dark overlay:
```kotlin
// REMOVE expensive palette extraction:
// Palette.from(bitmap).generate { palette ->
//     val color = palette?.vibrantSwatch?.rgb ?: defaultColor
//     applyColoredOverlay(color)
// }

// REPLACE with simple overlay:
private fun applyFocusEffect(view: View, hasFocus: Boolean) {
    val overlayView = view.findViewById<View>(R.id.overlay)
    if (hasFocus) {
        overlayView?.setBackgroundColor(Color.parseColor("#80000000"))
        view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
    } else {
        overlayView?.setBackgroundColor(Color.TRANSPARENT)
        view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
    }
}
```

**Priority:** 🟡 MEDIUM - Performance improvement

---

### 2.5 Fix Networks/Directors/Franchises Backdrop Updates

**Issue:** These special rows do not update the main background when items are selected.

**Fix:** Ensure `OnItemViewSelectedListener` is hooked up to `BackgroundManager`:
```kotlin
// In RowsScreenDelegate or HomeFragment
override fun onItemSelected(
    itemViewHolder: Presenter.ViewHolder?,
    item: Any?,
    rowViewHolder: RowPresenter.ViewHolder?,
    row: Row?
) {
    when (item) {
        is NetworkItem, is DirectorItem, is FranchiseItem -> {
            // Use card image as backdrop, stretched
            val imageUrl = when (item) {
                is NetworkItem -> item.logoUrl
                is DirectorItem -> item.imageUrl
                is FranchiseItem -> item.posterUrl
                else -> null
            }
            imageUrl?.let {
                backgroundManager.loadBackdrop(it, applyBlur = true)
            }
        }
        is ContentItem -> {
            backgroundManager.loadBackdrop(item.backdropUrl)
        }
    }
}
```

**Priority:** 🟡 MEDIUM - Visual polish

---

### 2.6 Add Clickable Functionality to Posters

**Issue:** Posters on Movies/TV Shows pages currently show a Toast instead of navigating.

**Fix:**
```kotlin
// In MoviesFragment.kt / TvShowsFragment.kt
posterAdapter.setOnItemClickListener { item, view ->
    val contentItem = item as ContentItem
    val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
        putExtra(DetailsActivity.EXTRA_CONTENT_ID, contentItem.tmdbId)
        putExtra(DetailsActivity.EXTRA_CONTENT_TYPE, contentItem.type.name)
    }
    startActivity(intent)
}
```

**Priority:** 🟠 HIGH - Core functionality broken

---

## 🚀 Phase 3: Details Page & Navigation

Implementing the core functionality requests for content details.

---

### 3.1 TV Show Details: Smart "Next Up" Focus

**Requirements:**
1. If multiple episodes half-watched → focus most recently watched
2. If all watched → focus next unwatched episode
3. If fully caught up → focus last episode
4. Update Play button to show correct S#E#

**Implementation:**
```kotlin
// In TvShowDetailsFragment.kt
private suspend fun determineNextUpEpisode(showId: Int): EpisodeInfo {
    val allEpisodes = tmdbRepository.getAllEpisodes(showId)
    val watchProgress = continueWatchingRepository.getShowProgress(showId)
    
    // 1. Find in-progress episodes (0 < progress < 90%)
    val inProgress = allEpisodes.filter { ep ->
        val progress = watchProgress[ep.id]
        progress != null && progress.percent > 0 && progress.percent < 0.9f
    }.sortedByDescending { watchProgress[it.id]?.lastWatchedAt }
    
    if (inProgress.isNotEmpty()) {
        return inProgress.first()
    }
    
    // 2. Find next unwatched
    val nextUnwatched = allEpisodes.firstOrNull { ep ->
        val progress = watchProgress[ep.id]
        progress == null || progress.percent == 0f
    }
    
    if (nextUnwatched != null) {
        return nextUnwatched
    }
    
    // 3. Fully caught up - return last episode
    return allEpisodes.last()
}

private fun focusEpisode(episode: EpisodeInfo) {
    seasonSelector.selectSeason(episode.seasonNumber)
    
    episodeGrid.post {
        val position = episodeAdapter.getPositionForEpisode(episode)
        episodeGrid.scrollToPosition(position)
        episodeGrid.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
    }
    
    // Update Play button
    playButton.text = "Play S${episode.seasonNumber}E${episode.episodeNumber}"
}
```

**Priority:** 🟠 HIGH - Major UX improvement

---

### 3.2 Red Progress Bar on Episode Cards

**Requirement:** Port progress bar logic from Home to Details episode cards.

**Rule:** Only display if `watched_percent` is between 5% and 90%. If >90%, show as fully watched (checkmark or dim).

**Implementation:**
```kotlin
// In EpisodeAdapter.kt
private fun bindEpisode(holder: EpisodeViewHolder, episode: EpisodeInfo, progress: WatchProgress?) {
    holder.apply {
        titleText.text = episode.title
        episodeText.text = "E${episode.episodeNumber}"
        
        when {
            progress == null || progress.percent <= 0.05f -> {
                // Unwatched
                progressBar.visibility = View.GONE
                watchedIcon.visibility = View.GONE
                itemView.alpha = 1.0f
            }
            progress.percent >= 0.90f -> {
                // Fully watched
                progressBar.visibility = View.GONE
                watchedIcon.visibility = View.VISIBLE
                itemView.alpha = 0.7f  // Slightly dim
            }
            else -> {
                // In progress (5% - 90%)
                progressBar.visibility = View.VISIBLE
                progressBar.progress = (progress.percent * 100).toInt()
                watchedIcon.visibility = View.GONE
                itemView.alpha = 1.0f
            }
        }
    }
}
```

**Priority:** 🟠 HIGH - Visual consistency

---

### 3.3 Redesigned Details Page Buttons

**Current:** Play, Trailer, Add to Watchlist, Thumbs Up, Thumbs Down

**New Design:** Play, Trailer, Mark Watched/Unwatched, More (⋮)

**Implementation:**
```kotlin
// In VideoDetailsFragment.kt
private fun setupActionButtons(item: ContentItem) {
    val isWatched = watchStatusRepository.isWatched(item.tmdbId, item.type)
    
    // Button 1: Play
    playButton.setOnClickListener { startPlayback(item) }
    
    // Button 2: Trailer
    trailerButton.setOnClickListener { playTrailer(item) }
    
    // Button 3: Mark Watched/Unwatched (dynamic)
    watchedButton.apply {
        text = if (isWatched) "Mark Unwatched" else "Mark Watched"
        setOnClickListener { 
            if (traktAccountRepository.isAuthenticated()) {
                toggleWatchedStatus(item)
            } else {
                showTraktAuthPrompt("Mark Watched")
            }
        }
    }
    
    // Button 4: More (⋮)
    moreButton.apply {
        setImageResource(R.drawable.ic_more_vert)
        contentDescription = "More options"
        setOnClickListener { showMoreOptionsModal(item) }
    }
}

private fun showMoreOptionsModal(item: ContentItem) {
    if (!traktAccountRepository.isAuthenticated()) {
        showTraktAuthPrompt("access Trakt features")
        return
    }
    
    val isInCollection = traktSyncRepository.isInCollection(item)
    val isInWatchlist = traktSyncRepository.isInWatchlist(item)
    
    MoreOptionsDialog(requireContext()).apply {
        addOption(
            if (isInCollection) "Remove from Collection" else "Add to Collection",
            R.drawable.ic_collection
        ) { toggleCollection(item) }
        
        addOption(
            if (isInWatchlist) "Remove from Watchlist" else "Add to Watchlist",
            R.drawable.ic_watchlist
        ) { toggleWatchlist(item) }
        
        addOption("Thumbs Up", R.drawable.ic_thumb_up) { rateItem(item, 10) }
        addOption("Thumbs Down", R.drawable.ic_thumb_down) { rateItem(item, 1) }
        
        show(anchorView = moreButton)
    }
}

private fun showTraktAuthPrompt(action: String) {
    AlertDialog.Builder(requireContext())
        .setTitle("Trakt Authorization Required")
        .setMessage("Authorize Trakt to $action")
        .setPositiveButton("Authorize") { _, _ -> startTraktAuth() }
        .setNegativeButton("Cancel", null)
        .show()
}
```

**Priority:** 🟠 HIGH - Core UX improvement

---

## ⚙️ Phase 4: Settings Enhancements

---

### 4.1 "Add From Liked Lists" Button

**Location:** Settings → Layout & Rows, next to "Reset To Defaults"

**Implementation:**
```kotlin
// In RowCustomizationFragment.kt
private fun showAddFromLikedListsDialog() {
    if (!traktAccountRepository.isAuthenticated()) {
        showTraktAuthPrompt("view your Liked Lists")
        return
    }
    
    lifecycleScope.launch {
        try {
            val likedLists = traktApiService.getLikedLists(
                authHeader = traktAccountRepository.getAuthHeader(),
                clientId = BuildConfig.TRAKT_CLIENT_ID
            )
            
            LikedListsDialog(requireContext(), likedLists) { selectedList ->
                addListAsRow(selectedList)
            }.show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to load lists", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun addListAsRow(list: TraktUserList) {
    val newRow = RowConfigEntity(
        id = UUID.randomUUID().toString(),
        screenType = currentScreen.name,
        title = list.name,
        rowType = RowType.TRAKT_LIST.name,
        dataSource = "trakt_list:${list.ids.slug}",
        orientation = RowConfigEntity.ORIENTATION_LANDSCAPE,
        isEnabled = true,
        position = getNextPosition()
    )
    viewModel.addRow(newRow)
}
```

**Priority:** 🟡 MEDIUM - Power user feature

---

### 4.2 Row Orientation Toggle (Landscape/Portrait/Square)

**Location:** Settings → Layout & Rows, button on each row card

**Implementation:**
```kotlin
// In RowConfigEntity.kt
@Entity(tableName = "row_config")
data class RowConfigEntity(
    @PrimaryKey val id: String,
    val screenType: String,
    val title: String,
    val rowType: String,
    val dataSource: String,
    val orientation: String = ORIENTATION_LANDSCAPE,
    val isEnabled: Boolean = true,
    val position: Int
) {
    companion object {
        const val ORIENTATION_LANDSCAPE = "landscape"
        const val ORIENTATION_PORTRAIT = "portrait"
        const val ORIENTATION_SQUARE = "square"
    }
    
    fun cycleOrientation(): RowConfigEntity {
        val nextOrientation = when (orientation) {
            ORIENTATION_LANDSCAPE -> ORIENTATION_PORTRAIT
            ORIENTATION_PORTRAIT -> ORIENTATION_SQUARE
            ORIENTATION_SQUARE -> ORIENTATION_LANDSCAPE
            else -> ORIENTATION_LANDSCAPE
        }
        return copy(orientation = nextOrientation)
    }
}

// In RowConfigAdapter.kt
orientationButton.setOnClickListener {
    val updatedRow = row.cycleOrientation()
    
    orientationButton.setImageResource(when (updatedRow.orientation) {
        RowConfigEntity.ORIENTATION_PORTRAIT -> R.drawable.ic_orientation_portrait
        RowConfigEntity.ORIENTATION_SQUARE -> R.drawable.ic_orientation_square
        else -> R.drawable.ic_orientation_landscape
    })
    
    onRowUpdated(updatedRow)
}
```

**Priority:** 🟡 MEDIUM - Customization feature

---

### 4.3 Playback Settings: Incremental vs Traditional Steps

**Location:** Settings → Playback

**Implementation:**
```kotlin
// In PlayerSettings.kt
data class PlayerSettings(
    // ... existing fields
    val skipMode: String = SKIP_MODE_INCREMENTAL,
    val traditionalStepSeconds: Int = 30,
    val incrementalSteps: List<Int> = listOf(5, 10, 30, 60, 180, 300, 600),
    val skipAccumulationDelayMs: Long = 500L
) {
    companion object {
        const val SKIP_MODE_TRADITIONAL = "traditional"
        const val SKIP_MODE_INCREMENTAL = "incremental"
    }
}
```

```xml
<!-- In fragment_playback_settings.xml -->
<RadioGroup android:id="@+id/step_mode_group">
    <RadioButton
        android:id="@+id/radio_traditional"
        android:text="Traditional (30s fixed)" />
    <RadioButton
        android:id="@+id/radio_incremental"
        android:text="Incremental (5s → 10s → 30s → 60s → 3m → 5m → 10m)" />
</RadioGroup>
```

**Priority:** 🟡 MEDIUM - Playback enhancement

---

### 4.4 About Page

**Implementation:**
```kotlin
// In AboutFragment.kt
class AboutFragment : Fragment(R.layout.fragment_about) {
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        view.findViewById<TextView>(R.id.app_name).text = "STRMR"
        view.findViewById<TextView>(R.id.version_name).text = 
            "Version ${BuildConfig.VERSION_NAME}"
        view.findViewById<TextView>(R.id.version_code).text = 
            "Build ${BuildConfig.VERSION_CODE}"
        
        // Optional: Load from version.txt in assets
        try {
            val versionFromFile = requireContext().assets
                .open("version.txt")
                .bufferedReader()
                .readLine()
            view.findViewById<TextView>(R.id.version_name).text = "Version $versionFromFile"
        } catch (e: Exception) {
            // Use BuildConfig version as fallback
        }
    }
}
```

**Priority:** 🟡 MEDIUM - Branding

---

## 🎥 Phase 5: ExoPlayer Modernization

---

### 5.1 Modern Player UI

**Requirements:**
- Transport controls at bottom of screen
- Subtitle/Audio/Quality buttons
- Skip indicators (+30s, -10s) displayed during seek

**Implementation:**
```xml
<!-- res/layout/custom_player_controls.xml -->
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    
    <!-- Skip indicator (center, shown during seek) -->
    <TextView
        android:id="@+id/skip_indicator"
        android:layout_gravity="center"
        android:textSize="56sp"
        android:textColor="@color/white"
        android:textStyle="bold"
        android:shadowColor="#000000"
        android:shadowRadius="8"
        android:visibility="gone"
        tools:text="+1m 30s" />
    
    <!-- Bottom controls -->
    <LinearLayout
        android:id="@+id/bottom_controls"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:orientation="vertical"
        android:padding="24dp"
        android:background="@drawable/gradient_bottom_fade">
        
        <!-- Title -->
        <TextView
            android:id="@+id/video_title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="20sp"
            android:textColor="@color/white"
            android:maxLines="1"
            android:ellipsize="end" />
        
        <!-- Progress bar -->
        <SeekBar
            android:id="@+id/progress_bar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:layout_marginBottom="8dp" />
        
        <!-- Time and controls row -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">
            
            <TextView android:id="@+id/time_current" android:textColor="@color/white" />
            <TextView android:text=" / " android:textColor="@color/white_70" />
            <TextView android:id="@+id/time_total" android:textColor="@color/white_70" />
            
            <Space android:layout_width="0dp" android:layout_weight="1" />
            
            <!-- Quick settings buttons -->
            <ImageButton
                android:id="@+id/btn_subtitles"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:src="@drawable/ic_subtitles"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="Subtitles" />
            
            <ImageButton
                android:id="@+id/btn_audio"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:src="@drawable/ic_audio_track"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="Audio Track" />
            
            <ImageButton
                android:id="@+id/btn_quality"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:src="@drawable/ic_settings"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="Quality" />
        </LinearLayout>
    </LinearLayout>
</FrameLayout>
```

**Priority:** 🟠 HIGH - Major UX improvement

---

### 5.2 Progressive Seek Handler (Kodi-Style)

**Implementation:**
```kotlin
// ui/player/ProgressiveSeekHandler.kt
class ProgressiveSeekHandler(
    private val player: Player,
    private val settings: PlayerSettings,
    private val onSeekPreview: (totalMs: Long, direction: Direction) -> Unit,
    private val onSeekExecuted: () -> Unit
) {
    enum class Direction { FORWARD, BACKWARD }
    
    private var accumulatedMs = 0L
    private var stepIndex = 0
    private var currentDirection: Direction? = null
    private var executeJob: Job? = null
    private var resetJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val steps: List<Int>
        get() = if (settings.skipMode == PlayerSettings.SKIP_MODE_INCREMENTAL) {
            settings.incrementalSteps  // [5, 10, 30, 60, 180, 300, 600]
        } else {
            listOf(settings.traditionalStepSeconds)  // [30]
        }
    
    fun onDpadPress(direction: Direction) {
        executeJob?.cancel()
        resetJob?.cancel()
        
        // Reset if direction changed
        if (currentDirection != direction) {
            resetState()
            currentDirection = direction
        }
        
        // Get step value (cap at last value for incremental)
        val stepSeconds = steps.getOrElse(stepIndex) { steps.last() }
        accumulatedMs += stepSeconds * 1000L
        
        if (settings.skipMode == PlayerSettings.SKIP_MODE_INCREMENTAL) {
            stepIndex = (stepIndex + 1).coerceAtMost(steps.lastIndex)
        }
        
        // Show preview
        onSeekPreview(accumulatedMs, direction)
        
        // Schedule execution after delay
        val delay = if (settings.skipMode == PlayerSettings.SKIP_MODE_INCREMENTAL) {
            settings.skipAccumulationDelayMs
        } else {
            0L
        }
        
        executeJob = scope.launch {
            delay(delay)
            executeSeek()
        }
        
        // Schedule reset after 1 second of inactivity
        resetJob = scope.launch {
            delay(1000L)
            stepIndex = 0  // Reset step progression
        }
    }
    
    private fun executeSeek() {
        val delta = when (currentDirection) {
            Direction.FORWARD -> accumulatedMs
            Direction.BACKWARD -> -accumulatedMs
            null -> return
        }
        
        val newPosition = (player.currentPosition + delta).coerceIn(0, player.duration)
        player.seekTo(newPosition)
        
        onSeekExecuted()
        resetState()
    }
    
    private fun resetState() {
        accumulatedMs = 0L
        currentDirection = null
    }
    
    fun cancel() {
        executeJob?.cancel()
        resetJob?.cancel()
        resetState()
        stepIndex = 0
    }
    
    fun release() {
        scope.cancel()
    }
}

// Format helper
fun formatSeekTime(totalMs: Long): String {
    val totalSeconds = totalMs / 1000
    return when {
        totalSeconds >= 60 -> {
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            if (seconds > 0) "${minutes}m ${seconds}s" else "${minutes}m"
        }
        else -> "${totalSeconds}s"
    }
}
```

**Usage in VideoPlayerActivity:**
```kotlin
private lateinit var seekHandler: ProgressiveSeekHandler

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    seekHandler = ProgressiveSeekHandler(
        player = player,
        settings = playerSettings,
        onSeekPreview = { totalMs, direction ->
            val sign = if (direction == Direction.FORWARD) "+" else "-"
            skipIndicator.text = "$sign${formatSeekTime(totalMs)}"
            skipIndicator.visibility = View.VISIBLE
        },
        onSeekExecuted = {
            skipIndicator.visibility = View.GONE
        }
    )
}

override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            seekHandler.onDpadPress(ProgressiveSeekHandler.Direction.FORWARD)
            true
        }
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            seekHandler.onDpadPress(ProgressiveSeekHandler.Direction.BACKWARD)
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }
}
```

**Priority:** 🟠 HIGH - Core playback feature

---

### 5.3 Hardware/Software Decoder Settings

**Implementation:**
```kotlin
// In PlayerSettings.kt
val decoderMode: String = DECODER_MODE_AUTO

companion object {
    const val DECODER_MODE_AUTO = "auto"
    const val DECODER_MODE_HW_ONLY = "hw_only"
    const val DECODER_MODE_SW_PREFER = "sw_prefer"
}

// In VideoPlayerActivity.kt
private fun createRenderersFactory(): RenderersFactory {
    val baseFactory = DefaultRenderersFactory(this).apply {
        setExtensionRendererMode(
            when (playerSettings.decoderMode) {
                PlayerSettings.DECODER_MODE_SW_PREFER -> 
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else -> 
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            }
        )
        setEnableDecoderFallback(true)
    }
    
    if (playerSettings.decoderMode == PlayerSettings.DECODER_MODE_SW_PREFER) {
        baseFactory.setMediaCodecSelector(SoftwarePreferringCodecSelector())
    }
    
    return baseFactory
}

class SoftwarePreferringCodecSelector : MediaCodecSelector {
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(
            mimeType, requiresSecureDecoder, requiresTunnelingDecoder
        )
        // Software decoders have "c2.android." or "OMX.google." prefix
        return decoders.sortedByDescending { 
            it.name.startsWith("c2.android.") || it.name.startsWith("OMX.google.")
        }
    }
}
```

**Priority:** 🟡 MEDIUM - Power user feature

---

### 5.4 Tunneled Playback for 4K/HDR

**Implementation:**
```kotlin
// In PlayerSettings.kt
val tunnelingEnabled: Boolean = true  // Default on for TV

// In VideoPlayerActivity.kt
val trackSelector = DefaultTrackSelector(this).apply {
    parameters = buildUponParameters()
        .setTunnelingEnabled(playerSettings.tunnelingEnabled)
        .setMaxVideoSize(3840, 2160)
        .setMaxVideoFrameRate(60)
        .build()
}
```

**Caveats:**
- Tunneling doesn't support playback speed changes
- Seek-while-paused may not update displayed frame on some devices
- Some devices have buggy implementations - provide toggle in settings

**Priority:** 🟡 MEDIUM - Performance for 4K/HDR

---

### 5.5 ExoPlayer Buffer Configuration

**Implementation:**
```kotlin
private fun createLoadControl(): LoadControl {
    return DefaultLoadControl.Builder()
        .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
        .setBufferDurationsMs(
            30_000,  // minBufferMs
            30_000,  // maxBufferMs (equal = "drip-style" buffering)
            1_500,   // bufferForPlaybackMs - fast startup
            3_000    // bufferForPlaybackAfterRebufferMs
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
}
```

**Priority:** 🟡 MEDIUM - Reliability improvement

---

### 5.6 Media Cache Singleton

**Implementation:**
```kotlin
// In StrmrApp.kt (or Test1App.kt until renamed)
class StrmrApp : Application() {
    
    companion object {
        private const val MEDIA_CACHE_SIZE = 200L * 1024 * 1024  // 200MB
        lateinit var mediaCache: SimpleCache
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize media cache as singleton (CRITICAL - only one instance allowed)
        val cacheDir = File(cacheDir, "exoplayer_media_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(MEDIA_CACHE_SIZE)
        val databaseProvider = StandaloneDatabaseProvider(this)
        
        mediaCache = SimpleCache(cacheDir, evictor, databaseProvider)
    }
    
    override fun onTerminate() {
        mediaCache.release()
        super.onTerminate()
    }
}

// Usage in VideoPlayerActivity.kt
private fun createDataSourceFactory(): DataSource.Factory {
    val httpFactory = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(15_000)
        .setAllowCrossProtocolRedirects(true)
    
    return CacheDataSource.Factory()
        .setCache(StrmrApp.mediaCache)
        .setUpstreamDataSourceFactory(httpFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
```

**Priority:** 🟡 MEDIUM - Improves seek performance

---

## 🔗 Phase 6: Trakt Player Integration

---

### 6.1 Trakt Scrobbling

**Requirements:**
- Report "now watching" when playback starts
- Update progress periodically
- Auto-scrobble (mark watched) at 80% completion
- Handle pause/resume

**Implementation:**
```kotlin
// data/repository/TraktScrobbleRepository.kt
@Singleton
class TraktScrobbleRepository @Inject constructor(
    private val traktApiService: TraktApiService,
    private val accountRepository: TraktAccountRepository
) {
    companion object {
        private const val SCROBBLE_THRESHOLD = 0.80  // 80% = mark watched
        private const val UPDATE_INTERVAL_MS = 60_000L  // Update every 60s
        private const val TAG = "TraktScrobble"
    }
    
    private var lastUpdateTime = 0L
    private var hasScrobbled = false
    
    suspend fun startWatching(
        item: ContentItem,
        season: Int? = null,
        episode: Int? = null
    ) {
        val account = accountRepository.getAuthenticatedAccount() ?: return
        hasScrobbled = false
        
        val request = buildScrobbleRequest(item, season, episode, progress = 0.0)
        
        runCatching {
            traktApiService.startScrobble(
                authHeader = "Bearer ${account.accessToken}",
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            Log.d(TAG, "Started watching: ${item.title}")
        }.onFailure { 
            Log.e(TAG, "Failed to start scrobble", it) 
        }
    }
    
    suspend fun updateProgress(
        item: ContentItem,
        currentPositionMs: Long,
        durationMs: Long,
        isPaused: Boolean,
        season: Int? = null,
        episode: Int? = null
    ) {
        // Rate limit updates
        val now = System.currentTimeMillis()
        if (!isPaused && now - lastUpdateTime < UPDATE_INTERVAL_MS) {
            return
        }
        lastUpdateTime = now
        
        val progress = if (durationMs > 0) {
            (currentPositionMs.toDouble() / durationMs) * 100
        } else 0.0
        
        val account = accountRepository.getAuthenticatedAccount() ?: return
        val request = buildScrobbleRequest(item, season, episode, progress)
        
        runCatching {
            when {
                isPaused -> {
                    traktApiService.pauseScrobble(
                        authHeader = "Bearer ${account.accessToken}",
                        clientId = BuildConfig.TRAKT_CLIENT_ID,
                        body = request
                    )
                    Log.d(TAG, "Paused at ${progress.toInt()}%")
                }
                !hasScrobbled && progress >= SCROBBLE_THRESHOLD * 100 -> {
                    traktApiService.stopScrobble(
                        authHeader = "Bearer ${account.accessToken}",
                        clientId = BuildConfig.TRAKT_CLIENT_ID,
                        body = request
                    )
                    hasScrobbled = true
                    Log.d(TAG, "Auto-scrobbled at ${progress.toInt()}%")
                }
            }
        }.onFailure { 
            Log.e(TAG, "Failed to update progress", it) 
        }
    }
    
    suspend fun stopWatching(
        item: ContentItem,
        currentPositionMs: Long,
        durationMs: Long,
        season: Int? = null,
        episode: Int? = null
    ) {
        val progress = if (durationMs > 0) {
            (currentPositionMs.toDouble() / durationMs) * 100
        } else 0.0
        
        val account = accountRepository.getAuthenticatedAccount() ?: return
        val request = buildScrobbleRequest(item, season, episode, progress)
        
        runCatching {
            traktApiService.stopScrobble(
                authHeader = "Bearer ${account.accessToken}",
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            Log.d(TAG, "Stopped watching at ${progress.toInt()}%")
        }
    }
    
    private fun buildScrobbleRequest(
        item: ContentItem,
        season: Int?,
        episode: Int?,
        progress: Double
    ): TraktScrobbleRequest {
        return when (item.type) {
            ContentItem.ContentType.MOVIE -> TraktScrobbleRequest(
                movie = TraktScrobbleMovie(ids = TraktIds(tmdb = item.tmdbId)),
                progress = progress
            )
            ContentItem.ContentType.TV_SHOW -> TraktScrobbleRequest(
                show = TraktScrobbleShow(ids = TraktIds(tmdb = item.tmdbId)),
                episode = TraktScrobbleEpisode(
                    season = season ?: 1,
                    number = episode ?: 1
                ),
                progress = progress
            )
        }
    }
}
```

**Add to TraktApiService:**
```kotlin
@POST("scrobble/start")
suspend fun startScrobble(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktScrobbleRequest
): TraktScrobbleResponse

@POST("scrobble/pause")
suspend fun pauseScrobble(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktScrobbleRequest
): TraktScrobbleResponse

@POST("scrobble/stop")
suspend fun stopScrobble(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktScrobbleRequest
): TraktScrobbleResponse
```

**Integrate with VideoPlayerActivity:**
```kotlin
@Inject lateinit var scrobbleRepository: TraktScrobbleRepository

private var scrobbleJob: Job? = null

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Start scrobbling when playback begins
    player.addListener(object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY && player.playWhenReady) {
                startScrobbling()
            }
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying && player.playbackState == Player.STATE_READY) {
                // Paused
                updateScrobbleProgress(isPaused = true)
            }
        }
    })
}

private fun startScrobbling() {
    lifecycleScope.launch {
        scrobbleRepository.startWatching(
            item = contentItem!!,
            season = seasonNumber.takeIf { it > 0 },
            episode = episodeNumber.takeIf { it > 0 }
        )
    }
    
    // Periodic updates every 30 seconds
    scrobbleJob?.cancel()
    scrobbleJob = lifecycleScope.launch {
        while (isActive) {
            delay(30_000)
            updateScrobbleProgress(isPaused = false)
        }
    }
}

private fun updateScrobbleProgress(isPaused: Boolean) {
    player?.let { p ->
        lifecycleScope.launch {
            scrobbleRepository.updateProgress(
                item = contentItem!!,
                currentPositionMs = p.currentPosition,
                durationMs = p.duration,
                isPaused = isPaused,
                season = seasonNumber.takeIf { it > 0 },
                episode = episodeNumber.takeIf { it > 0 }
            )
        }
    }
}

override fun onDestroy() {
    scrobbleJob?.cancel()
    
    // Stop scrobbling (fire and forget)
    player?.let { p ->
        CoroutineScope(Dispatchers.IO).launch {
            scrobbleRepository.stopWatching(
                item = contentItem!!,
                currentPositionMs = p.currentPosition,
                durationMs = p.duration,
                season = seasonNumber.takeIf { it > 0 },
                episode = episodeNumber.takeIf { it > 0 }
            )
        }
    }
    
    super.onDestroy()
}
```

**Priority:** 🟠 HIGH - Core Trakt integration

---

### 6.2 Local Playback Progress Database

**Implementation:**
```kotlin
// data/local/entity/PlaybackProgress.kt
@Entity(tableName = "playback_progress")
data class PlaybackProgress(
    @PrimaryKey val id: String,  // "movie_{tmdbId}" or "show_{tmdbId}_{s}_{e}"
    val tmdbId: Int,
    val type: String,  // "movie" or "episode"
    val title: String,
    val posterUrl: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long,
    val durationMs: Long,
    val percent: Float,
    val lastWatchedAt: Long = System.currentTimeMillis(),
    val syncedToTrakt: Boolean = false
) {
    companion object {
        fun createId(tmdbId: Int, type: String, season: Int?, episode: Int?): String {
            return if (type == "episode" && season != null && episode != null) {
                "show_${tmdbId}_${season}_${episode}"
            } else {
                "movie_${tmdbId}"
            }
        }
    }
}

// data/local/dao/PlaybackProgressDao.kt
@Dao
interface PlaybackProgressDao {
    
    @Query("SELECT * FROM playback_progress WHERE id = :id")
    suspend fun getProgress(id: String): PlaybackProgress?
    
    @Query("SELECT * FROM playback_progress WHERE percent > 0.05 AND percent < 0.90 ORDER BY lastWatchedAt DESC LIMIT :limit")
    fun getContinueWatchingFlow(limit: Int = 20): Flow<List<PlaybackProgress>>
    
    @Query("SELECT * FROM playback_progress WHERE tmdbId = :showId AND type = 'episode'")
    suspend fun getShowProgress(showId: Int): List<PlaybackProgress>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlaybackProgress)
    
    @Query("DELETE FROM playback_progress WHERE id = :id")
    suspend fun deleteProgress(id: String)
    
    @Query("UPDATE playback_progress SET percent = 1.0, syncedToTrakt = :synced WHERE id = :id")
    suspend fun markWatched(id: String, synced: Boolean = false)
    
    @Query("SELECT * FROM playback_progress WHERE syncedToTrakt = 0")
    suspend fun getUnsyncedProgress(): List<PlaybackProgress>
    
    @Query("UPDATE playback_progress SET syncedToTrakt = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
```

**Priority:** 🟡 MEDIUM - Offline resilience

---

## 🎬 Phase 7: Subtitle System

---

### 7.1 a4kSubtitles Integration

**Implementation:**
```kotlin
// data/subtitle/SubtitleManager.kt
@Singleton
class SubtitleManager @Inject constructor(
    private val openSubtitlesProvider: OpenSubtitlesProvider,
    private val preferences: SubtitlePreferences
) {
    suspend fun findSubtitles(
        videoInfo: VideoInfo,
        embeddedTracks: List<Format>
    ): List<SubtitleOption> {
        val results = mutableListOf<SubtitleOption>()
        
        // 1. Add embedded subtitles FIRST
        embeddedTracks.forEachIndexed { index, format ->
            results.add(SubtitleOption.Embedded(
                trackIndex = index,
                language = format.language ?: "Unknown",
                label = format.label ?: "Track ${index + 1}",
                isForced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
            ))
        }
        
        // 2. Search external providers
        val query = SubtitleQuery(
            hash = videoInfo.movieHash,
            fileSize = videoInfo.fileSize,
            imdbId = videoInfo.imdbId,
            tmdbId = videoInfo.tmdbId,
            title = videoInfo.title,
            year = videoInfo.year,
            season = videoInfo.season,
            episode = videoInfo.episode,
            languages = preferences.preferredLanguages
        )
        
        val externalResults = openSubtitlesProvider.search(query)
            .getOrElse { emptyList() }
            .sortedByDescending { if (it.hashMatched) 100 else 0 + (it.rating ?: 0f).toInt() }
        
        externalResults.forEach { sub ->
            results.add(SubtitleOption.External(sub))
        }
        
        return results
    }
}

sealed class SubtitleOption {
    data class Embedded(
        val trackIndex: Int,
        val language: String,
        val label: String,
        val isForced: Boolean = false
    ) : SubtitleOption()
    
    data class External(val subtitle: SubtitleResult) : SubtitleOption()
    object Off : SubtitleOption()
}
```

**Priority:** 🟡 MEDIUM-HIGH - Major feature

---

## 🏷️ Phase 8: Rebranding & Final Polish

---

### 8.1 App Rename: test1 → STRMR

**Files to update:**
```kotlin
// res/values/strings.xml
<string name="app_name">STRMR</string>

// settings.gradle.kts
rootProject.name = "STRMR"

// app/build.gradle.kts (applicationId can stay for Play Store continuity)
android {
    namespace = "com.strmr.tv"  // Optional: refactor if fresh start
    defaultConfig {
        applicationId = "com.strmr.tv"  // Or keep "com.test1.tv" for updates
    }
}
```

**Class renames (optional but recommended):**
- `Test1App.kt` → `StrmrApp.kt`
- Update all `com.test1.tv` imports if changing package

**Priority:** 🟡 MEDIUM - Branding

---

### 8.2 Additional Debrid Providers

**Real-Debrid API:** https://api.real-debrid.com/rest/1.0/

**AllDebrid API:** https://docs.alldebrid.com/

**Priority:** 🟢 LOW - Expansion

---

## 📋 Summary Checklist

Use this checklist to track progress across all phases.

---

### Phase 1: Critical Stability & Security
- [x] **1.1** Fix hardcoded OMDB_API_KEY in build.gradle.kts
- [x] **1.2** Replace Timer with coroutines in MainFragment
- [x] **1.3** Replace Handler.postDelayed in BrowseErrorActivity
- [x] **1.4** Fix CardPresenter missing else block (wrong icons)
- [x] **1.5** Fix Glide VectorDrawable crash
- [x] **1.6** Fix HWUI image decoder errors
- [x] **1.7** Migrate kapt to KSP

---

### Phase 2: UI/UX Refinements
- [x] **2.1** Fix Settings submenu spacing (VerticalGridView alignment)
- [x] **2.2** Fix long press context menu jitter
- [x] **2.3** Add horizontal scroll (marquee) for link titles
- [x] **2.4** Remove colored backdrop overlay on focus
- [x] **2.5** Fix Networks/Directors/Franchises backdrop updates
- [x] **2.6** Add clickable functionality to posters

---

### Phase 3: Details Page & Navigation
- [x] **3.1** Implement smart "Next Up" episode focus
- [x] **3.2** Add red progress bar to episode cards
- [x] **3.3** Redesign Details page buttons (Play, Trailer, Mark Watched, More)

---

### Phase 4: Settings Enhancements
- [x] **4.1** Add "Add From Liked Lists" button (Layout & Rows)
- [x] **4.2** Add row orientation toggle (Landscape/Portrait/Square) (Layout & Rows)
- [x] **4.3** Add Incremental vs Traditional skip settings (Playback Submenu)
- [x] **4.4** Create About page

---

### Phase 5: ExoPlayer Modernization
- [x] **5.1** Implement modern player UI (bottom controls)
- [x] **5.2** Implement progressive seek handler (Kodi-style)
- [ ] **5.3** Add hardware/software decoder settings
- [ ] **5.4** Add tunneled playback toggle
- [ ] **5.5** Configure ExoPlayer buffering
- [ ] **5.6** Implement media cache singleton

---

### Phase 6: Trakt Player Integration
- [ ] **6.1** Implement Trakt scrobbling (start/pause/stop/auto-scrobble)
- [ ] **6.2** Create local PlaybackProgress database

---

### Phase 7: Subtitle System
- [ ] **7.1** Implement a4kSubtitles integration
- [ ] **7.2** Show embedded subtitles at top of list
- [ ] **7.3** Create subtitle selection dialog

---

### Phase 8: Rebranding & Polish
- [ ] **8.1** Rename app to STRMR
- [ ] **8.2** Add Real-Debrid support
- [ ] **8.3** Add AllDebrid support

---

## 🔗 External Resources

### Trakt API
- Scrobble: https://trakt.docs.apiary.io/#reference/scrobble
- Sync: https://trakt.docs.apiary.io/#reference/sync

### Subtitle APIs
- OpenSubtitles: https://opensubtitles.stoplight.io/
- a4kSubtitles: https://github.com/a4k-openproject/a4kSubtitles

### ExoPlayer / Media3
- Documentation: https://developer.android.com/media/media3
- Tunneling: https://developer.amazon.com/docs/fire-tv/4k-tunnel-mode-playback.html

### Debrid APIs
- Real-Debrid: https://api.real-debrid.com/
- AllDebrid: https://docs.alldebrid.com/

---

## Appendix A: Original Error Logs

### Glide VectorDrawable Error
```
NoResultEncoderAvailableException: Failed to find result encoder for resource class: 
class android.graphics.drawable.VectorDrawable
```

### HWUI Image Decoder Error
```
Failed to create image decoder with message 'unimplemented'
```

### Resource Not Found
```
Resources$NotFoundException: File res/Uh.xml from resource ID #0x7f080168
```

---

*Last updated: December 2024*
