# Context Menu Implementation Plan

## Overview

Implement a polished, TV-remote-friendly long-press context menu for poster items with Trakt integration. The menu will be styled to match the app's nav bar aesthetic (semi-transparent modal popup) and provide instant UI feedback through confirmed database queries.

---

## Current State Analysis

### Already Implemented
- `PosterAdapter` with `onItemLongPressed` callback
- `showItemContextMenu()` in `HomeFragment` using `MaterialAlertDialogBuilder`
- `TraktSyncRepository` with all CRUD operations (history, collection, watchlist)
- `TraktUserItem` entity storing list membership (WATCHLIST/COLLECTION/HISTORY)
- Watched badge (`watchedBadge` ImageView) on poster items
- `WatchStatusProvider` for fast local lookups

### Gaps to Address
1. Context menu uses generic `MaterialAlertDialogBuilder` instead of custom styled popup
2. No context menu in MoviesFragment, TvShowsFragment (need verification)
3. UI updates happen after API sync (slight delay)
4. Missing integration tests with real Trakt authorization
5. No separation of Trakt-required vs non-Trakt actions
6. Need to ensure collection rows are excluded from context menu

---

## Implementation Tasks

### Phase 1: Custom Context Menu UI Component

#### Task 1.1: Create Context Menu Dialog Layout
**File**: `app/src/main/res/layout/dialog_context_menu.xml`

Create a modal popup styled like the navigation dock:
- Semi-transparent background (#0A0F1F with 92% alpha)
- Rounded corners (16dp)
- Elevation/shadow (8dp)
- Vertical list of action items
- Each item: icon + label, focusable for D-pad navigation
- Item focus state: highlight background, scale animation

#### Task 1.2: Create Context Menu Item Layout
**File**: `app/src/main/res/layout/item_context_menu_action.xml`

- Icon (24dp) + Label (TextView)
- Focusable with ripple effect
- Focus state: white border, slight scale (1.05x)
- Pressed state: darker background

#### Task 1.3: Create ContextMenuDialog Class
**File**: `app/src/main/java/com/test1/tv/ui/contextmenu/ContextMenuDialog.kt`

```kotlin
class ContextMenuDialog(
    context: Context,
    private val item: ContentItem,
    private val actions: List<ContextMenuAction>,
    private val onActionSelected: (ContextMenuAction) -> Unit
) : Dialog(context, R.style.ContextMenuDialogTheme)
```

Features:
- Custom theme with transparent background
- RecyclerView for action items
- D-pad navigation support (up/down to navigate, select to choose)
- Dismiss on back press or clicking outside
- Animate in/out (fade + scale)

#### Task 1.4: Create ContextMenuAction Model
**File**: `app/src/main/java/com/test1/tv/ui/contextmenu/ContextMenuAction.kt`

```kotlin
sealed class ContextMenuAction(
    val labelRes: Int,
    val iconRes: Int,
    val requiresTrakt: Boolean = false
) {
    object Play : ContextMenuAction(R.string.action_play, R.drawable.ic_play, false)
    object MarkWatched : ContextMenuAction(R.string.action_mark_watched, R.drawable.ic_check, true)
    object MarkUnwatched : ContextMenuAction(R.string.action_mark_unwatched, R.drawable.ic_uncheck, true)
    object AddToCollection : ContextMenuAction(R.string.action_add_to_collection, R.drawable.ic_collection_add, true)
    object RemoveFromCollection : ContextMenuAction(R.string.action_remove_from_collection, R.drawable.ic_collection_remove, true)
    object AddToWatchlist : ContextMenuAction(R.string.action_add_to_watchlist, R.drawable.ic_watchlist_add, true)
    object RemoveFromWatchlist : ContextMenuAction(R.string.action_remove_from_watchlist, R.drawable.ic_watchlist_remove, true)
}
```

#### Task 1.5: Create ContextMenuTheme Style
**File**: `app/src/main/res/values/styles.xml` (add to existing)

```xml
<style name="ContextMenuDialogTheme" parent="Theme.MaterialComponents.Dialog">
    <item name="android:windowBackground">@android:color/transparent</item>
    <item name="android:windowIsFloating">true</item>
    <item name="android:windowAnimationStyle">@style/ContextMenuAnimation</item>
</style>
```

---

### Phase 2: Context Menu Handler Service

#### Task 2.1: Create TraktStatusProvider
**File**: `app/src/main/java/com/test1/tv/data/repository/TraktStatusProvider.kt`

Fast, cached lookups for item status:
```kotlin
@Singleton
class TraktStatusProvider @Inject constructor(
    private val userItemDao: TraktUserItemDao,
    private val accountRepository: TraktAccountRepository
) {
    // In-memory cache for instant lookups
    private val historyCache = ConcurrentHashMap<Int, Boolean>()
    private val collectionCache = ConcurrentHashMap<Int, Boolean>()
    private val watchlistCache = ConcurrentHashMap<Int, Boolean>()

    suspend fun isAuthenticated(): Boolean
    suspend fun isWatched(tmdbId: Int, type: ContentType): Boolean
    suspend fun isInCollection(tmdbId: Int, type: ContentType): Boolean
    suspend fun isInWatchlist(tmdbId: Int, type: ContentType): Boolean

    // Update cache after actions
    fun markWatched(tmdbId: Int)
    fun markUnwatched(tmdbId: Int)
    fun addToCollection(tmdbId: Int)
    fun removeFromCollection(tmdbId: Int)
    fun addToWatchlist(tmdbId: Int)
    fun removeFromWatchlist(tmdbId: Int)

    // Refresh cache from DB
    suspend fun refreshCache()
}
```

#### Task 2.2: Create ContextMenuActionHandler
**File**: `app/src/main/java/com/test1/tv/ui/contextmenu/ContextMenuActionHandler.kt`

```kotlin
@Singleton
class ContextMenuActionHandler @Inject constructor(
    private val traktSyncRepository: TraktSyncRepository,
    private val traktStatusProvider: TraktStatusProvider,
    private val watchStatusRepository: WatchStatusRepository
) {
    suspend fun buildActions(
        item: ContentItem,
        isTraktAuthenticated: Boolean
    ): List<ContextMenuAction>

    suspend fun executeAction(
        item: ContentItem,
        action: ContextMenuAction,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    )
}
```

Features:
- Query DB for current status (instant)
- Build appropriate action list based on auth and current state
- Execute actions with confirmed update pattern:
  1. Make API call
  2. On success: update local DB + cache + trigger UI refresh
  3. On failure: show error toast

---

### Phase 3: Badge & UI Update System

#### Task 3.1: Create WatchedBadgeManager
**File**: `app/src/main/java/com/test1/tv/ui/WatchedBadgeManager.kt`

Centralized badge state management:
```kotlin
class WatchedBadgeManager {
    // LiveData or StateFlow for reactive updates
    private val _badgeUpdates = MutableSharedFlow<BadgeUpdate>()
    val badgeUpdates: SharedFlow<BadgeUpdate> = _badgeUpdates

    data class BadgeUpdate(
        val tmdbId: Int,
        val type: ContentType,
        val isWatched: Boolean
    )

    suspend fun notifyWatchedStateChanged(tmdbId: Int, type: ContentType, isWatched: Boolean)
}
```

#### Task 3.2: Update PosterAdapter for Reactive Badge Updates
**File**: `app/src/main/java/com/test1/tv/ui/adapter/PosterAdapter.kt`

- Subscribe to `WatchedBadgeManager.badgeUpdates`
- On update: find ViewHolder by tmdbId, animate badge visibility change
- Smooth animation: fade in/out with slight scale

#### Task 3.3: Update WatchStatusProvider Integration
**File**: `app/src/main/java/com/test1/tv/data/repository/WatchStatusProvider.kt`

- Add method to update single item progress
- Integrate with `WatchedBadgeManager` for UI notifications

---

### Phase 4: Fragment Integration

#### Task 4.1: Create ContextMenuHelper
**File**: `app/src/main/java/com/test1/tv/ui/contextmenu/ContextMenuHelper.kt`

Shared helper for all fragments:
```kotlin
class ContextMenuHelper(
    private val fragment: Fragment,
    private val actionHandler: ContextMenuActionHandler,
    private val traktStatusProvider: TraktStatusProvider,
    private val badgeManager: WatchedBadgeManager
) {
    fun showContextMenu(item: ContentItem) {
        // Skip non-TMDB items (collections, directors, networks)
        if (!item.shouldShowContextMenu()) {
            Toast.makeText(context, "Actions not available for this item", LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val isAuthenticated = traktStatusProvider.isAuthenticated()
            val actions = actionHandler.buildActions(item, isAuthenticated)

            ContextMenuDialog(context, item, actions) { action ->
                handleAction(item, action)
            }.show()
        }
    }

    private fun handleAction(item: ContentItem, action: ContextMenuAction)
}
```

#### Task 4.2: Add ContentItem Extension
**File**: `app/src/main/java/com/test1/tv/data/model/ContentItem.kt` (extension)

```kotlin
fun ContentItem.shouldShowContextMenu(): Boolean {
    // Has valid TMDB ID
    if (tmdbId == -1) return false
    // Not a placeholder
    if (isPlaceholder) return false
    // Not a special collection item (Trakt list URLs, networks, etc.)
    if (imdbId?.startsWith("https://trakt.tv/") == true) return false
    return true
}
```

#### Task 4.3: Update HomeFragment
**File**: `app/src/main/java/com/test1/tv/ui/home/HomeFragment.kt`

- Inject `ContextMenuHelper`
- Replace `showItemContextMenu()` with `contextMenuHelper.showContextMenu(item)`

#### Task 4.4: Update MoviesFragment
**File**: `app/src/main/java/com/test1/tv/ui/movies/MoviesFragment.kt`

- Add `onItemLongPress` callback to RowsScreenDelegate
- Inject and use `ContextMenuHelper`

#### Task 4.5: Update TvShowsFragment
**File**: `app/src/main/java/com/test1/tv/ui/tvshows/TvShowsFragment.kt`

- Add `onItemLongPress` callback to RowsScreenDelegate
- Inject and use `ContextMenuHelper`

#### Task 4.6: Update Other Fragments
- SearchFragment
- TraktMediaFragment
- TraktListFragment
- ActorDetailsFragment

---

### Phase 5: Database Schema Updates

#### Task 5.1: Add Indexes for Fast Lookups
**File**: `app/src/main/java/com/test1/tv/data/local/entity/TraktUserItem.kt`

```kotlin
@Entity(
    tableName = "trakt_user_items",
    indices = [
        Index(value = ["listType", "tmdbId"]),  // Fast lookup by list type and tmdbId
        Index(value = ["tmdbId"])                // Fast lookup by tmdbId across all lists
    ]
)
```

#### Task 5.2: Add DAO Methods for Fast Lookups
**File**: `app/src/main/java/com/test1/tv/data/local/dao/TraktUserItemDao.kt`

```kotlin
@Query("SELECT EXISTS(SELECT 1 FROM trakt_user_items WHERE listType = :listType AND tmdbId = :tmdbId LIMIT 1)")
suspend fun existsInList(listType: String, tmdbId: Int): Boolean

@Query("SELECT tmdbId FROM trakt_user_items WHERE listType = :listType")
suspend fun getAllTmdbIdsInList(listType: String): List<Int>
```

---

### Phase 6: Icon Assets

#### Task 6.1: Create Context Menu Icons
**Directory**: `app/src/main/res/drawable/`

Create vector drawables:
- `ic_play_24.xml` - Play action
- `ic_check_circle_24.xml` - Mark watched
- `ic_check_circle_outline_24.xml` - Mark unwatched (remove watched)
- `ic_folder_add_24.xml` - Add to collection
- `ic_folder_remove_24.xml` - Remove from collection
- `ic_bookmark_add_24.xml` - Add to watchlist
- `ic_bookmark_remove_24.xml` - Remove from watchlist

Style: Material Design icons, white fill, 24dp

---

### Phase 7: Testing

#### Task 7.1: Unit Tests for TraktStatusProvider
**File**: `app/src/test/java/com/test1/tv/data/repository/TraktStatusProviderTest.kt`

Tests:
- `returns false when not authenticated`
- `returns cached value when available`
- `queries database when cache miss`
- `updates cache after action`
- `clears cache on refresh`

#### Task 7.2: Unit Tests for ContextMenuActionHandler
**File**: `app/src/test/java/com/test1/tv/ui/contextmenu/ContextMenuActionHandlerTest.kt`

Tests:
- `builds Play only when not authenticated`
- `builds watched toggle based on current state`
- `builds collection toggle based on current state`
- `builds watchlist toggle based on current state`
- `executes action and updates cache on success`
- `calls onFailure on API error`

#### Task 7.3: Unit Tests for ContentItem.shouldShowContextMenu
**File**: `app/src/test/java/com/test1/tv/data/model/ContentItemTest.kt`

Tests:
- `returns false for tmdbId -1`
- `returns false for placeholders`
- `returns false for Trakt list URLs`
- `returns true for valid movie items`
- `returns true for valid TV show items`

#### Task 7.4: Integration Tests with Trakt Authorization
**File**: `app/src/test/java/com/test1/tv/integration/TraktContextMenuIntegrationTest.kt`

Prerequisites:
- Add `TRAKT_TEST_ACCESS_TOKEN` to `BuildConfig` (debug only)
- Add `TRAKT_TEST_REFRESH_TOKEN` to `BuildConfig` (debug only)

Tests (require real Trakt account):
- `marks movie as watched and verifies on Trakt`
- `marks movie as unwatched and verifies on Trakt`
- `adds movie to collection and verifies on Trakt`
- `removes movie from collection and verifies on Trakt`
- `adds movie to watchlist and verifies on Trakt`
- `removes movie from watchlist and verifies on Trakt`
- `syncs full history and verifies local cache matches`

#### Task 7.5: UI Tests for Context Menu
**File**: `app/src/androidTest/java/com/test1/tv/ui/contextmenu/ContextMenuDialogTest.kt`

Tests:
- `shows dialog on long press`
- `displays correct actions based on auth state`
- `navigates with D-pad`
- `dismisses on back press`
- `executes action on select`

---

### Phase 8: BuildConfig for Test Tokens

#### Task 8.1: Update build.gradle.kts
**File**: `app/build.gradle.kts`

```kotlin
android {
    buildTypes {
        debug {
            buildConfigField("String", "TRAKT_TEST_ACCESS_TOKEN",
                "\"${project.findProperty("TRAKT_TEST_ACCESS_TOKEN") ?: ""}\"")
            buildConfigField("String", "TRAKT_TEST_REFRESH_TOKEN",
                "\"${project.findProperty("TRAKT_TEST_REFRESH_TOKEN") ?: ""}\"")
        }
    }
}
```

#### Task 8.2: Document Test Setup
**File**: `app/src/test/README.md`

Document how to:
1. Generate Trakt test tokens
2. Add tokens to `local.properties`
3. Run integration tests

---

## File Summary

### New Files
| File | Description |
|------|-------------|
| `dialog_context_menu.xml` | Context menu dialog layout |
| `item_context_menu_action.xml` | Action item layout |
| `ContextMenuDialog.kt` | Custom dialog class |
| `ContextMenuAction.kt` | Action sealed class |
| `ContextMenuActionHandler.kt` | Action execution handler |
| `ContextMenuHelper.kt` | Fragment integration helper |
| `TraktStatusProvider.kt` | Fast status lookups |
| `WatchedBadgeManager.kt` | Badge update manager |
| `ic_play_24.xml` | Play icon |
| `ic_check_circle_24.xml` | Watched icon |
| `ic_check_circle_outline_24.xml` | Unwatched icon |
| `ic_folder_add_24.xml` | Collection add icon |
| `ic_folder_remove_24.xml` | Collection remove icon |
| `ic_bookmark_add_24.xml` | Watchlist add icon |
| `ic_bookmark_remove_24.xml` | Watchlist remove icon |
| `TraktStatusProviderTest.kt` | Unit tests |
| `ContextMenuActionHandlerTest.kt` | Unit tests |
| `ContentItemTest.kt` | Unit tests |
| `TraktContextMenuIntegrationTest.kt` | Integration tests |
| `ContextMenuDialogTest.kt` | UI tests |

### Modified Files
| File | Changes |
|------|---------|
| `PosterAdapter.kt` | Subscribe to badge updates |
| `HomeFragment.kt` | Use ContextMenuHelper |
| `MoviesFragment.kt` | Add context menu support |
| `TvShowsFragment.kt` | Add context menu support |
| `SearchFragment.kt` | Add context menu support |
| `TraktUserItem.kt` | Add indexes |
| `TraktUserItemDao.kt` | Add fast lookup queries |
| `ContentItem.kt` | Add shouldShowContextMenu extension |
| `styles.xml` | Add ContextMenuDialogTheme |
| `build.gradle.kts` | Add test token config |

---

## Implementation Order

1. **Phase 5**: Database schema updates (indexes, DAO methods)
2. **Phase 2**: TraktStatusProvider and ContextMenuActionHandler
3. **Phase 6**: Icon assets
4. **Phase 1**: Custom context menu UI component
5. **Phase 3**: Badge update system
6. **Phase 4**: Fragment integration
7. **Phase 8**: BuildConfig for test tokens
8. **Phase 7**: Testing

---

## Success Criteria

- [ ] Long-press on poster shows styled context menu
- [ ] Menu shows "Play" only when Trakt not authorized
- [ ] Menu shows appropriate toggle actions based on current DB state
- [ ] Actions complete without visible lag (< 200ms to show result)
- [ ] Watched checkmark appears/disappears immediately after action
- [ ] Collection rows (networks, directors, My Trakt) show toast instead of menu
- [ ] All fragments (Home, Movies, TV Shows, Search, etc.) have context menu
- [ ] Menu dismisses correctly on back press
- [ ] D-pad navigation works smoothly
- [ ] All unit tests pass
- [ ] Integration tests verify Trakt API operations
- [ ] UI tests verify dialog behavior
