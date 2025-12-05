# Quick Implementation Checklist

## Priority Order (Recommended)

```
1. ⬜ Details Page Buttons (Option 3) - Foundation for all Trakt writes
2. ⬜  Global Search (Suggestion 4) - High user impact, standalone
3. ⬜ Long-Press Menus (Option 1) - Reuses API from #1
4. ⬜ Episode Checkmarks (Option 2) - Needs history data from #1
5. ⬜ Smart Recommendations (Suggestion 6) - Enhancement
```

---

## Files to Create (NEW)

| File | Purpose |
|------|---------|
| `data/model/trakt/TraktSyncModels.kt` | Request/response models for Trakt sync API |
| `data/repository/TraktActionsRepository.kt` | Handles all Trakt write operations |
| `ui/search/SearchViewModel.kt` | Search logic with TMDB multi-search |
| `res/layout/fragment_search.xml` | Search UI with voice button |
| `res/layout/item_search_person.xml` | Person result card layout |

---

## Files to Modify

| File | Changes |
|------|---------|
| `TraktApiService.kt` | Add 10 new endpoints (sync/history, collection, watchlist, ratings) |
| `DetailsFragment.kt` | Wire up Mark Watched, Collection, Rating buttons |
| `HomeFragment.kt` | Enhanced long-press context menu |
| `EpisodeAdapter.kt` | Add watched badge, long-press handler |
| `NetworkModule.kt` | Provide TraktActionsRepository |
| `strings.xml` | Add ~15 new strings |

---

## New API Endpoints to Add

```kotlin
// In TraktApiService.kt

// History (watched)
POST sync/history              // Mark watched
POST sync/history/remove       // Unmark watched

// Collection  
POST sync/collection           // Add to collection
POST sync/collection/remove    // Remove from collection

// Watchlist
POST sync/watchlist            // Add to watchlist
POST sync/watchlist/remove     // Remove from watchlist

// Ratings
POST sync/ratings              // Rate item (1-10)
POST sync/ratings/remove       // Remove rating
```

---

## New Icons Needed

| Icon | Use |
|------|-----|
| `ic_visibility_off` | Hollow eye (not watched) |
| `ic_bookmark_filled` | In collection |
| `ic_bookmark_outline` | Not in collection |
| `ic_thumb_up_filled` | Liked (rating 8) |
| `ic_thumb_up_outline` | Not rated up |
| `ic_thumb_down_filled` | Disliked (rating 4) |
| `ic_thumb_down_outline` | Not rated down |
| `ic_mic` | Voice search |
| `ic_check_badge` | Episode watched overlay |

---

## Testing Quick Reference

### Details Page Buttons
```
1. Open any movie details
2. Click Mark Watched → Should see filled eye icon
3. Click Collection → Should see filled bookmark
4. Click Thumbs Up → Should see filled thumb
5. Verify changes on trakt.tv website
```

### Search
```
1. Navigate to Search from nav bar
2. Type "Batman" → Should see results grid
3. Click voice button → Should open speech recognizer
4. Click result → Should open Details page
```

### Long-Press Menu
```
1. On Home screen, long-press any movie poster
2. Menu should show: Play, Mark Watched, Collection, Watchlist, Details
3. Select "Add to Collection" → Toast confirms
4. Long-press same item → Should now show "Remove from Collection"
```

---

## Key Code Patterns

### Making a Trakt API Write Call
```kotlin
suspend fun addToCollection(item: ContentItem): Result<Boolean> {
    val authHeader = getAuthHeader() ?: return Result.failure(...)
    
    val request = TraktSyncRequest(
        movies = listOf(TraktSyncMovie(ids = TraktSyncIds(tmdb = item.tmdbId)))
    )
    
    return runCatching {
        traktApiService.addToCollection(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            body = request
        ).added?.movies ?: 0 > 0
    }
}
```

### Checking Local Status
```kotlin
suspend fun isInCollection(tmdbId: Int, type: ContentType): Boolean {
    val itemType = if (type == MOVIE) "MOVIE" else "SHOW"
    return traktUserItemDao.getItems("COLLECTION", itemType)
        .any { it.tmdbId == tmdbId }
}
```

### Button State Toggle
```kotlin
private fun handleCollectionToggle() {
    lifecycleScope.launch {
        val result = if (isInCollection) {
            traktActionsRepository.removeFromCollection(item)
        } else {
            traktActionsRepository.addToCollection(item)
        }
        
        result.onSuccess {
            isInCollection = !isInCollection
            updateButtonStates()  // Update icon filled/hollow
        }
    }
}
```

---

## Common Gotchas

1. **Trakt ID vs TMDB ID**: Some Trakt endpoints need Trakt IDs, not TMDB. Use the `ids` object to pass both when available.

2. **Auth Header Format**: Must be `"Bearer {token}"` with space after Bearer.

3. **ISO 8601 Dates**: Trakt expects `2024-01-15T10:30:00.000Z` format for timestamps.

4. **Rate Limits**: Trakt has generous limits but batch operations when possible.

5. **Local Cache Sync**: After any write, trigger sync worker to update local DB.

---

## Estimated Time Per Feature

| Feature | Estimated Hours |
|---------|----------------|
| Trakt Sync Models | 1-2 hrs |
| TraktActionsRepository | 3-4 hrs |
| Details Page Buttons | 4-6 hrs |
| Search ViewModel | 2-3 hrs |
| Search Fragment + UI | 4-5 hrs |
| Long-Press Menu | 3-4 hrs |
| Episode Checkmarks | 4-6 hrs |

**Total: ~25-35 hours of development**

---

## Dependencies

```
Phase 1A (Details Buttons):
  └── TraktSyncModels ← TraktActionsRepository ← DetailsFragment

Phase 1B (Search):
  └── SearchViewModel ← SearchFragment (standalone)

Phase 2 (Long-Press):
  └── TraktActionsRepository (from 1A) ← HomeFragment updates

Phase 2 (Checkmarks):
  └── TraktActionsRepository (from 1A) ← EpisodeAdapter + DetailsFragment
```
