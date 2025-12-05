# STRMR Android TV App - Feature Implementation Guide

## Table of Contents
1. [Overview](#overview)
2. [Feature 1: Context Menus (Long-Press Actions)](#feature-1-context-menus-long-press-actions)
3. [Feature 2: Visual Watched Indicators](#feature-2-visual-watched-indicators)
4. [Feature 3: Details Page Actions & UI Update](#feature-3-details-page-actions--ui-update)
5. [Database Schema Changes](#database-schema-changes)
6. [Implementation Priority Order](#implementation-priority-order)

---

## Overview

This document provides detailed implementation instructions for three major features in the STRMR Android TV streaming app. All implementations extend existing files without creating new architecture.

**Key Files to Modify:**
- `TraktApiService.kt` - Add new API endpoints
- `TraktSyncRepository.kt` - Add sync/action methods
- `TraktUserItemDao.kt` - Add query methods
- `WatchStatusRepository.kt` - Extend watch status logic
- `HomeFragment.kt` - Extend context menu logic
- `MoviesFragment.kt` / `TvShowsFragment.kt` - Add context menu
- `DetailsFragment.kt` - Add new buttons and logic
- `PosterAdapter.kt` - Already has long-press support
- `EpisodeAdapter` (inner class in DetailsFragment) - Add watched indicator
- `SeasonAdapter` (inner class in DetailsFragment) - Add long-press
- `AppDatabase.kt` - Increment version if schema changes
- `DatabaseMigrations.kt` - Add migration if needed

---

## Feature 1: Context Menus (Long-Press Actions)

### 1.1 Overview

Implement a long-press context menu for content items with a TMDB ID. The menu must be **state-aware** and update the local database immediately.

### 1.2 Scope & Exclusions

**Enable long-press for:**
- Movies (all rows)
- TV Shows (all rows)
- Season buttons (on Details page)
- Episode cards (on Details page)

**Exclude from long-press:**
- "My Trakt" rows (tmdbId == -1 with Trakt URL pattern)
- "Franchises" rows (collections with drawable:// URLs)
- "Directors" rows
- "Networks" rows

### 1.3 New API Endpoints

#### File: `TraktApiService.kt`

Add after line ~4783 (after `getRelatedShows`):

```kotlin
import retrofit2.Response
import retrofit2.http.DELETE

// ==================== SYNC ACTIONS ====================

/**
 * Add items to watched history (mark as watched)
 */
@POST("sync/history")
suspend fun addToHistory(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktSyncRequest
): TraktSyncResponse

/**
 * Remove items from watched history (mark as unwatched)
 */
@POST("sync/history/remove")
suspend fun removeFromHistory(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktSyncRequest
): TraktSyncResponse

/**
 * Add items to collection
 */
@POST("sync/collection")
suspend fun addToCollection(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktSyncRequest
): TraktSyncResponse

/**
 * Remove items from collection
 */
@POST("sync/collection/remove")
suspend fun removeFromCollection(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktSyncRequest
): TraktSyncResponse

/**
 * Add items to watchlist
 */
@POST("sync/watchlist")
suspend fun addToWatchlist(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktSyncRequest
): TraktSyncResponse

/**
 * Remove items from watchlist
 */
@POST("sync/watchlist/remove")
suspend fun removeFromWatchlist(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktSyncRequest
): TraktSyncResponse

/**
 * Rate an item
 */
@POST("sync/ratings")
suspend fun addRating(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktRatingRequest
): TraktSyncResponse

/**
 * Remove rating from item
 */
@POST("sync/ratings/remove")
suspend fun removeRating(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktSyncRequest
): TraktSyncResponse
```

### 1.4 New Data Models

#### File: `TraktModels.kt` (add to existing trakt model file around line ~4200)

```kotlin
// ==================== SYNC REQUEST/RESPONSE MODELS ====================

data class TraktSyncRequest(
    val movies: List<TraktSyncMovie>? = null,
    val shows: List<TraktSyncShow>? = null,
    val episodes: List<TraktSyncEpisode>? = null
)

data class TraktSyncMovie(
    val ids: TraktIds
)

data class TraktSyncShow(
    val ids: TraktIds,
    val seasons: List<TraktSyncSeason>? = null
)

data class TraktSyncSeason(
    val number: Int,
    val episodes: List<TraktSyncSeasonEpisode>? = null
)

data class TraktSyncSeasonEpisode(
    val number: Int
)

data class TraktSyncEpisode(
    val ids: TraktIds
)

data class TraktSyncResponse(
    val added: TraktSyncCounts? = null,
    val deleted: TraktSyncCounts? = null,
    val existing: TraktSyncCounts? = null,
    @SerializedName("not_found")
    val notFound: TraktSyncNotFound? = null
)

data class TraktSyncCounts(
    val movies: Int? = null,
    val shows: Int? = null,
    val seasons: Int? = null,
    val episodes: Int? = null
)

data class TraktSyncNotFound(
    val movies: List<TraktSyncMovie>? = null,
    val shows: List<TraktSyncShow>? = null,
    val episodes: List<TraktSyncEpisode>? = null
)

data class TraktRatingRequest(
    val movies: List<TraktRatingItem>? = null,
    val shows: List<TraktRatingItem>? = null,
    val episodes: List<TraktRatingItem>? = null
)

data class TraktRatingItem(
    val ids: TraktIds,
    val rating: Int,  // 1-10
    @SerializedName("rated_at")
    val ratedAt: String? = null  // ISO 8601 format
)
```

### 1.5 Repository Actions

#### File: `TraktSyncRepository.kt`

Add these methods after `syncAll()` function (around line ~7304):

```kotlin
// ==================== CONTEXT MENU ACTIONS ====================

/**
 * Mark a movie as watched
 */
suspend fun markMovieWatched(tmdbId: Int): Boolean {
    val account = accountRepository.refreshTokenIfNeeded() ?: return false
    val authHeader = accountRepository.buildAuthHeader(account.accessToken)
    
    return try {
        val request = TraktSyncRequest(
            movies = listOf(TraktSyncMovie(ids = TraktIds(tmdb = tmdbId)))
        )
        traktApiService.addToHistory(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            body = request
        )
        // Update local DB
        val item = TraktUserItem(
            id = TraktUserItem.key(LIST_HISTORY, ITEM_MOVIE, null),
            listType = LIST_HISTORY,
            itemType = ITEM_MOVIE,
            traktId = null,
            tmdbId = tmdbId,
            slug = null,
            title = null,
            year = null,
            updatedAt = System.currentTimeMillis(),
            listedAt = null,
            collectedAt = null,
            watchedAt = System.currentTimeMillis()
        )
        userItemDao.insertAll(listOf(item))
        true
    } catch (e: Exception) {
        android.util.Log.e("TraktSync", "Failed to mark movie watched", e)
        false
    }
}

/**
 * Mark a movie as unwatched
 */
suspend fun markMovieUnwatched(tmdbId: Int): Boolean {
    val account = accountRepository.refreshTokenIfNeeded() ?: return false
    val authHeader = accountRepository.buildAuthHeader(account.accessToken)
    
    return try {
        val request = TraktSyncRequest(
            movies = listOf(TraktSyncMovie(ids = TraktIds(tmdb = tmdbId)))
        )
        traktApiService.removeFromHistory(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            body = request
        )
        // Remove from local DB (by tmdbId pattern)
        // Note: This requires a new DAO method
        true
    } catch (e: Exception) {
        android.util.Log.e("TraktSync", "Failed to mark movie unwatched", e)
        false
    }
}

/**
 * Mark a show as watched (all episodes)
 */
suspend fun markShowWatched(tmdbId: Int): Boolean {
    val account = accountRepository.refreshTokenIfNeeded() ?: return false
    val authHeader = accountRepository.buildAuthHeader(account.accessToken)
    
    return try {
        val request = TraktSyncRequest(
            shows = listOf(TraktSyncShow(ids = TraktIds(tmdb = tmdbId)))
        )
        traktApiService.addToHistory(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            body = request
        )
        // Update local DB
        val item = TraktUserItem(
            id = TraktUserItem.key(LIST_HISTORY, ITEM_SHOW, null),
            listType = LIST_HISTORY,
            itemType = ITEM_SHOW,
            traktId = null,
            tmdbId = tmdbId,
            slug = null,
            title = null,
            year = null,
            updatedAt = System.currentTimeMillis(),
            listedAt = null,
            collectedAt = null,
            watchedAt = System.currentTimeMillis()
        )
        userItemDao.insertAll(listOf(item))
        true
    } catch (e: Exception) {
        android.util.Log.e("TraktSync", "Failed to mark show watched", e)
        false
    }
}

/**
 * Mark a specific season as watched
 */
suspend fun markSeasonWatched(showTmdbId: Int, seasonNumber: Int): Boolean {
    val account = accountRepository.refreshTokenIfNeeded() ?: return false
    val authHeader = accountRepository.buildAuthHeader(account.accessToken)
    
    return try {
        val request = TraktSyncRequest(
            shows = listOf(
                TraktSyncShow(
                    ids = TraktIds(tmdb = showTmdbId),
                    seasons = listOf(TraktSyncSeason(number = seasonNumber))
                )
            )
        )
        traktApiService.addToHistory(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            body = request
        )
        true
    } catch (e: Exception) {
        android.util.Log.e("TraktSync", "Failed to mark season watched", e)
        false
    }
}

/**
 * Mark a specific episode as watched
 */
suspend fun markEpisodeWatched(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int): Boolean {
    val account = accountRepository.refreshTokenIfNeeded() ?: return false
    val authHeader = accountRepository.buildAuthHeader(account.accessToken)
    
    return try {
        val request = TraktSyncRequest(
            shows = listOf(
                TraktSyncShow(
                    ids = TraktIds(tmdb = showTmdbId),
                    seasons = listOf(
                        TraktSyncSeason(
                            number = seasonNumber,
                            episodes = listOf(TraktSyncSeasonEpisode(number = episodeNumber))
                        )
                    )
                )
            )
        )
        traktApiService.addToHistory(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            body = request
        )
        true
    } catch (e: Exception) {
        android.util.Log.e("TraktSync", "Failed to mark episode watched", e)
        false
    }
}

/**
 * Mark a specific episode as unwatched
 */
suspend fun markEpisodeUnwatched(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int): Boolean {
    val account = accountRepository.refreshTokenIfNeeded() ?: return false
    val authHeader = accountRepository.buildAuthHeader(account.accessToken)
    
    return try {
        val request = TraktSyncRequest(
            shows = listOf(
                TraktSyncShow(
                    ids = TraktIds(tmdb = showTmdbId),
                    seasons = listOf(
                        TraktSyncSeason(
                            number = seasonNumber,
                            episodes = listOf(TraktSyncSeasonEpisode(number = episodeNumber))
                        )
                    )
                )
            )
        )
        traktApiService.removeFromHistory(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            body = request
        )
        true
    } catch (e: Exception) {
        android.util.Log.e("TraktSync", "Failed to mark episode unwatched", e)
        false
    }
}

/**
 * Add item to collection
 */
suspend fun addToCollection(tmdbId: Int, isMovie: Boolean): Boolean {
    val account = accountRepository.refreshTokenIfNeeded() ?: return false
    val authHeader = accountRepository.buildAuthHeader(account.accessToken)
    
    return try {
        val request = if (isMovie) {
            TraktSyncRequest(movies = listOf(TraktSyncMovie(ids = TraktIds(tmdb = tmdbId))))
        } else {
            TraktSyncRequest(shows = listOf(TraktSyncShow(ids = TraktIds(tmdb = tmdbId))))
        }
        traktApiService.addToCollection(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            body = request
        )
        // Update local DB
        val itemType = if (isMovie) ITEM_MOVIE else ITEM_SHOW
        val item = TraktUserItem(
            id = "${LIST_COLLECTION}_${itemType}_tmdb_$tmdbId",
            listType = LIST_COLLECTION,
            itemType = itemType,
            traktId = null,
            tmdbId = tmdbId,
            slug = null,
            title = null,
            year = null,
            updatedAt = System.currentTimeMillis(),
            listedAt = null,
            collectedAt = System.currentTimeMillis(),
            watchedAt = null
        )
        userItemDao.insertAll(listOf(item))
        true
    } catch (e: Exception) {
        android.util.Log.e("TraktSync", "Failed to add to collection", e)
        false
    }
}

/**
 * Remove item from collection
 */
suspend fun removeFromCollection(tmdbId: Int, isMovie: Boolean): Boolean {
    val account = accountRepository.refreshTokenIfNeeded() ?: return false
    val authHeader = accountRepository.buildAuthHeader(account.accessToken)
    
    return try {
        val request = if (isMovie) {
            TraktSyncRequest(movies = listOf(TraktSyncMovie(ids = TraktIds(tmdb = tmdbId))))
        } else {
            TraktSyncRequest(shows = listOf(TraktSyncShow(ids = TraktIds(tmdb = tmdbId))))
        }
        traktApiService.removeFromCollection(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            body = request
        )
        // Note: Requires DAO method to delete by tmdbId
        true
    } catch (e: Exception) {
        android.util.Log.e("TraktSync", "Failed to remove from collection", e)
        false
    }
}

/**
 * Add item to watchlist
 */
suspend fun addToWatchlist(tmdbId: Int, isMovie: Boolean): Boolean {
    val account = accountRepository.refreshTokenIfNeeded() ?: return false
    val authHeader = accountRepository.buildAuthHeader(account.accessToken)
    
    return try {
        val request = if (isMovie) {
            TraktSyncRequest(movies = listOf(TraktSyncMovie(ids = TraktIds(tmdb = tmdbId))))
        } else {
            TraktSyncRequest(shows = listOf(TraktSyncShow(ids = TraktIds(tmdb = tmdbId))))
        }
        traktApiService.addToWatchlist(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            body = request
        )
        // Update local DB
        val itemType = if (isMovie) ITEM_MOVIE else ITEM_SHOW
        val item = TraktUserItem(
            id = "${LIST_WATCHLIST}_${itemType}_tmdb_$tmdbId",
            listType = LIST_WATCHLIST,
            itemType = itemType,
            traktId = null,
            tmdbId = tmdbId,
            slug = null,
            title = null,
            year = null,
            updatedAt = System.currentTimeMillis(),
            listedAt = System.currentTimeMillis(),
            collectedAt = null,
            watchedAt = null
        )
        userItemDao.insertAll(listOf(item))
        true
    } catch (e: Exception) {
        android.util.Log.e("TraktSync", "Failed to add to watchlist", e)
        false
    }
}

/**
 * Remove item from watchlist
 */
suspend fun removeFromWatchlist(tmdbId: Int, isMovie: Boolean): Boolean {
    val account = accountRepository.refreshTokenIfNeeded() ?: return false
    val authHeader = accountRepository.buildAuthHeader(account.accessToken)
    
    return try {
        val request = if (isMovie) {
            TraktSyncRequest(movies = listOf(TraktSyncMovie(ids = TraktIds(tmdb = tmdbId))))
        } else {
            TraktSyncRequest(shows = listOf(TraktSyncShow(ids = TraktIds(tmdb = tmdbId))))
        }
        traktApiService.removeFromWatchlist(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            body = request
        )
        true
    } catch (e: Exception) {
        android.util.Log.e("TraktSync", "Failed to remove from watchlist", e)
        false
    }
}

/**
 * Check if item is in a specific list
 */
suspend fun isInList(tmdbId: Int, listType: String, itemType: String): Boolean {
    val items = userItemDao.getItems(listType, itemType)
    return items.any { it.tmdbId == tmdbId }
}
```

### 1.6 DAO Extensions

#### File: `TraktUserItemDao.kt`

Add these queries after existing methods (around line ~2842):

```kotlin
@Query("""
    SELECT * FROM trakt_user_items 
    WHERE tmdbId = :tmdbId AND listType = :listType AND itemType = :itemType 
    LIMIT 1
""")
suspend fun findByTmdbId(tmdbId: Int, listType: String, itemType: String): TraktUserItem?

@Query("DELETE FROM trakt_user_items WHERE tmdbId = :tmdbId AND listType = :listType")
suspend fun deleteByTmdbId(tmdbId: Int, listType: String)

@Query("""
    SELECT EXISTS(
        SELECT 1 FROM trakt_user_items 
        WHERE tmdbId = :tmdbId AND listType = :listType
    )
""")
suspend fun existsByTmdbId(tmdbId: Int, listType: String): Boolean
```

### 1.7 Context Menu UI Implementation

#### File: `HomeFragment.kt`

Replace the existing `showItemContextMenu` function (around line ~13401):

```kotlin
private fun showItemContextMenu(item: ContentItem) {
    // Exclude non-TMDB items (collections, directors, networks, My Trakt placeholders)
    if (item.tmdbId == -1) {
        Toast.makeText(requireContext(), "Actions not available for this item", Toast.LENGTH_SHORT).show()
        return
    }
    
    viewLifecycleOwner.lifecycleScope.launch {
        // Fetch current state from local DB
        val isMovie = item.type == ContentItem.ContentType.MOVIE
        val itemType = if (isMovie) "MOVIE" else "SHOW"
        
        val isWatched = traktSyncRepository.isInList(item.tmdbId, "HISTORY", itemType)
        val isInCollection = traktSyncRepository.isInList(item.tmdbId, "COLLECTION", itemType)
        val isInWatchlist = traktSyncRepository.isInList(item.tmdbId, "WATCHLIST", itemType)
        
        // Build state-aware options
        val options = mutableListOf<String>()
        val actions = mutableListOf<suspend () -> Boolean>()
        
        // Play option
        options.add(getString(R.string.action_play_immediately))
        actions.add { 
            // Placeholder for play action
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Play: ${item.title}", Toast.LENGTH_SHORT).show()
            }
            true 
        }
        
        // Watched toggle
        if (isWatched) {
            options.add(getString(R.string.action_mark_unwatched))
            actions.add {
                val result = if (isMovie) {
                    traktSyncRepository.markMovieUnwatched(item.tmdbId)
                } else {
                    traktSyncRepository.markShowUnwatched(item.tmdbId)
                }
                if (result) refreshContinueWatching()
                result
            }
        } else {
            options.add(getString(R.string.action_mark_watched))
            actions.add {
                val result = if (isMovie) {
                    traktSyncRepository.markMovieWatched(item.tmdbId)
                } else {
                    traktSyncRepository.markShowWatched(item.tmdbId)
                }
                if (result) refreshContinueWatching()
                result
            }
        }
        
        // Collection toggle
        if (isInCollection) {
            options.add(getString(R.string.action_remove_from_collection))
            actions.add { traktSyncRepository.removeFromCollection(item.tmdbId, isMovie) }
        } else {
            options.add(getString(R.string.action_add_to_collection))
            actions.add { traktSyncRepository.addToCollection(item.tmdbId, isMovie) }
        }
        
        // Watchlist toggle
        if (isInWatchlist) {
            options.add(getString(R.string.action_remove_from_watchlist))
            actions.add { traktSyncRepository.removeFromWatchlist(item.tmdbId, isMovie) }
        } else {
            options.add(getString(R.string.action_add_to_watchlist))
            actions.add { traktSyncRepository.addToWatchlist(item.tmdbId, isMovie) }
        }
        
        withContext(Dispatchers.Main) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.title)
                .setItems(options.toTypedArray()) { dialog, which ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val success = actions[which].invoke()
                        withContext(Dispatchers.Main) {
                            if (success) {
                                val actionName = options[which]
                                Toast.makeText(
                                    requireContext(),
                                    "$actionName: ${item.title}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    "Action failed. Please try again.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.dismiss_error, null)
                .show()
        }
    }
}

private fun refreshContinueWatching() {
    // Trigger a refresh of the Continue Watching row
    viewModel.refreshContinueWatching()
}
```

#### Add Dependency Injection

In `HomeFragment.kt`, add this injection (around line ~13008):

```kotlin
@Inject lateinit var traktSyncRepository: TraktSyncRepository
```

### 1.8 String Resources

#### File: `strings.xml`

Add these strings:

```xml
<!-- Context Menu Actions -->
<string name="action_play_immediately">Play</string>
<string name="action_mark_watched">Mark Watched</string>
<string name="action_mark_unwatched">Mark Unwatched</string>
<string name="action_add_to_collection">Add to Collection</string>
<string name="action_remove_from_collection">Remove from Collection</string>
<string name="action_add_to_watchlist">Add to Watchlist</string>
<string name="action_remove_from_watchlist">Remove from Watchlist</string>
<string name="action_mark_season_watched">Mark Season Watched</string>
<string name="action_mark_episode_watched">Mark Episode Watched</string>
<string name="action_mark_episode_unwatched">Mark Episode Unwatched</string>
```

### 1.9 Season Long-Press Context Menu

#### File: `DetailsFragment.kt`

Modify the `SeasonAdapter` inner class (around line ~12521) to add long-press:

```kotlin
private inner class SeasonAdapter(
    private val seasons: List<TMDBSeason>,
    private val showTmdbId: Int,  // ADD THIS PARAMETER
    private val onSeasonClick: (TMDBSeason, Int) -> Unit,
    private val onSeasonLongPress: (TMDBSeason, Int) -> Unit  // ADD THIS CALLBACK
) : RecyclerView.Adapter<SeasonAdapter.SeasonViewHolder>() {

    // ... existing code ...

    inner class SeasonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.season_title)

        fun bind(season: TMDBSeason, position: Int) {
            // ... existing binding code ...
            
            // ADD LONG PRESS HANDLER
            itemView.isLongClickable = true
            itemView.setOnLongClickListener {
                onSeasonLongPress(season, position)
                true
            }
        }
    }
}
```

Update the adapter instantiation in `populateSeasonsAndEpisodes` (around line ~12192):

```kotlin
seasonAdapter = SeasonAdapter(
    seasons = seasons,
    showTmdbId = tmdbShowId,
    onSeasonClick = { season, position ->
        season.seasonNumber?.let {
            loadEpisodesForSeason(tmdbShowId, it, position)
            seasonAdapter?.setSelectedPosition(position)
        }
    },
    onSeasonLongPress = { season, position ->
        showSeasonContextMenu(tmdbShowId, season)
    }
)
```

Add the season context menu function:

```kotlin
private fun showSeasonContextMenu(showTmdbId: Int, season: TMDBSeason) {
    val seasonNumber = season.seasonNumber ?: return
    
    MaterialAlertDialogBuilder(requireContext())
        .setTitle("Season $seasonNumber")
        .setItems(arrayOf(
            getString(R.string.action_mark_season_watched)
        )) { dialog, which ->
            when (which) {
                0 -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val success = traktSyncRepository.markSeasonWatched(showTmdbId, seasonNumber)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                Toast.makeText(
                                    requireContext(),
                                    "Season $seasonNumber marked as watched",
                                    Toast.LENGTH_SHORT
                                ).show()
                                // Refresh episode adapter to show watched badges
                                episodeAdapter?.notifyDataSetChanged()
                            }
                        }
                    }
                }
            }
            dialog.dismiss()
        }
        .setNegativeButton(R.string.dismiss_error, null)
        .show()
}
```

### 1.10 Episode Long-Press Context Menu

Modify the `EpisodeAdapter` inner class (around line ~12581):

```kotlin
private inner class EpisodeAdapter(
    private val episodes: List<TMDBEpisode>,
    private val showTmdbId: Int,  // ADD THIS PARAMETER
    private val onEpisodeFocused: (TMDBEpisode?) -> Unit,
    private val onEpisodeLongPress: (TMDBEpisode) -> Unit  // ADD THIS CALLBACK
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

    inner class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val episodeImage: ImageView = itemView.findViewById(R.id.episode_image)
        private val episodeTitle: TextView = itemView.findViewById(R.id.episode_title)
        private val focusOverlay: View = itemView.findViewById(R.id.episode_focus_overlay)
        private val watchedBadge: ImageView? = itemView.findViewById(R.id.watched_badge)  // ADD THIS

        fun bind(episode: TMDBEpisode) {
            // ... existing binding code ...
            
            // ADD LONG PRESS HANDLER
            itemView.isLongClickable = true
            itemView.setOnLongClickListener {
                onEpisodeLongPress(episode)
                true
            }
            
            // ADD WATCHED BADGE LOGIC (see Feature 2)
            updateWatchedBadge(episode)
        }
        
        private fun updateWatchedBadge(episode: TMDBEpisode) {
            // Implementation in Feature 2
        }
    }
}
```

---

## Feature 2: Visual Watched Indicators

### 2.1 Overview

Display a visual checkmark badge on episode cards when they have been watched. The state is derived from the local database, which syncs with Trakt history.

### 2.2 Episode Card Layout Update

#### File: `item_episode_card.xml`

Add the watched badge inside the FrameLayout (before the focus overlay):

```xml
<!-- Add after the CardView, before episode_focus_overlay -->
<ImageView
    android:id="@+id/watched_badge"
    android:layout_width="28dp"
    android:layout_height="28dp"
    android:layout_gravity="top|end"
    android:layout_marginTop="8dp"
    android:layout_marginEnd="8dp"
    android:elevation="8dp"
    android:visibility="gone"
    android:src="@drawable/ic_check_badge"
    android:contentDescription="@string/watched_indicator" />
```

### 2.3 Episode Watched State Logic

#### File: `DetailsFragment.kt`

Add a watched episodes cache and lookup function:

```kotlin
// Add as class property (around line ~11559)
private var watchedEpisodesCache: Set<String> = emptySet()

// Add method to load watched episodes for a show
private fun loadWatchedEpisodesForShow(showTmdbId: Int) {
    viewLifecycleOwner.lifecycleScope.launch {
        watchedEpisodesCache = withContext(Dispatchers.IO) {
            // Query local DB for all watched episodes of this show
            // Format: "S{season}E{episode}"
            traktSyncRepository.getWatchedEpisodesForShow(showTmdbId)
        }
    }
}

private fun isEpisodeWatched(seasonNumber: Int?, episodeNumber: Int?): Boolean {
    if (seasonNumber == null || episodeNumber == null) return false
    return watchedEpisodesCache.contains("S${seasonNumber}E${episodeNumber}")
}
```

Update the `EpisodeViewHolder.bind()` method:

```kotlin
fun bind(episode: TMDBEpisode) {
    // ... existing binding code ...
    
    // Update watched badge
    val isWatched = isEpisodeWatched(episode.seasonNumber, episode.episodeNumber)
    watchedBadge?.visibility = if (isWatched) View.VISIBLE else View.GONE
}
```

### 2.4 Repository Method for Watched Episodes

#### File: `TraktSyncRepository.kt`

Add this method:

```kotlin
/**
 * Get all watched episodes for a show as a set of "S{season}E{episode}" strings
 */
suspend fun getWatchedEpisodesForShow(showTmdbId: Int): Set<String> {
    // Note: This requires storing episode-level watch data
    // For now, return empty set - full implementation requires schema changes
    return emptySet()
}
```

### 2.5 Enhanced Watch Status Tracking

For full episode-level tracking, extend `WatchStatusEntity` or create a new entity:

#### File: `WatchStatusEntity.kt` (extend existing)

Add fields for episode tracking:

```kotlin
@Entity(tableName = "watch_status")
data class WatchStatusEntity(
    @PrimaryKey val key: String,
    val tmdbId: Int,
    val type: String,
    val progress: Double?,
    val updatedAt: Long,
    val lastWatched: Long? = null,
    val nextEpisodeTitle: String? = null,
    val nextEpisodeSeason: Int? = null,
    val nextEpisodeNumber: Int? = null,
    val nextEpisodeTmdbId: Int? = null,
    // NEW FIELDS
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val isWatched: Boolean = false
)
```

---

## Feature 3: Details Page Actions & UI Update

### 3.1 Overview

Update the Movie/TV Show Details page with new action buttons and dynamic "Next Up" play logic.

### 3.2 New Button Icons

Create or ensure these drawable resources exist:

#### File: `res/drawable/ic_eye.xml` (Watched - Hollow)
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z"/>
</vector>
```

#### File: `res/drawable/ic_eye_filled.xml` (Watched - Filled)
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z"/>
    <circle
        android:fillColor="#FFFFFF"
        android:cx="12"
        android:cy="12"
        android:r="2.5"/>
</vector>
```

#### File: `res/drawable/ic_library.xml` (Collection - Hollow)
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zM20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM20,16H8V4h12v12z"/>
</vector>
```

#### File: `res/drawable/ic_library_filled.xml` (Collection - Filled)
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zM20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z"/>
</vector>
```

### 3.3 Layout Updates

#### File: `fragment_details.xml`

Add new buttons to the button container. Find the existing button row and add:

```xml
<!-- After buttonWatchlist, add these buttons -->
<com.google.android.material.button.MaterialButton
    android:id="@+id/button_watched"
    style="@style/Widget.MaterialComponents.Button.Icon"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:layout_marginStart="8dp"
    android:backgroundTint="@color/button_secondary_background"
    android:insetTop="0dp"
    android:insetBottom="0dp"
    app:cornerRadius="24dp"
    app:icon="@drawable/ic_eye"
    app:iconGravity="textStart"
    app:iconPadding="0dp"
    app:iconSize="24dp"
    app:iconTint="@android:color/white" />

<com.google.android.material.button.MaterialButton
    android:id="@+id/button_collection"
    style="@style/Widget.MaterialComponents.Button.Icon"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:layout_marginStart="8dp"
    android:backgroundTint="@color/button_secondary_background"
    android:insetTop="0dp"
    android:insetBottom="0dp"
    app:cornerRadius="24dp"
    app:icon="@drawable/ic_library"
    app:iconGravity="textStart"
    app:iconPadding="0dp"
    app:iconSize="24dp"
    app:iconTint="@android:color/white" />
```

### 3.4 DetailsFragment Button Logic

#### File: `DetailsFragment.kt`

Add new view references (around line ~11533):

```kotlin
private lateinit var buttonWatched: MaterialButton
private lateinit var buttonCollection: MaterialButton
```

Add injection for repository (around line ~11564):

```kotlin
@Inject lateinit var traktSyncRepository: TraktSyncRepository
```

In `initViews()`, add button initialization:

```kotlin
buttonWatched = view.findViewById(R.id.button_watched)
buttonCollection = view.findViewById(R.id.button_collection)

// Setup new buttons with expanding pill animation
setupExpandingPillButton(buttonWatched, "Watched")
setupExpandingPillButton(buttonCollection, "Collection")
```

Add button state management:

```kotlin
private var isItemWatched = false
private var isItemInCollection = false

private fun loadItemState(item: ContentItem) {
    viewLifecycleOwner.lifecycleScope.launch {
        val isMovie = item.type == ContentItem.ContentType.MOVIE
        val itemType = if (isMovie) "MOVIE" else "SHOW"
        
        isItemWatched = withContext(Dispatchers.IO) {
            traktSyncRepository.isInList(item.tmdbId, "HISTORY", itemType)
        }
        isItemInCollection = withContext(Dispatchers.IO) {
            traktSyncRepository.isInList(item.tmdbId, "COLLECTION", itemType)
        }
        
        updateButtonStates()
    }
}

private fun updateButtonStates() {
    // Update Watched button icon
    buttonWatched.icon = if (isItemWatched) {
        resources.getDrawable(R.drawable.ic_eye_filled, null)
    } else {
        resources.getDrawable(R.drawable.ic_eye, null)
    }
    
    // Update Collection button icon
    buttonCollection.icon = if (isItemInCollection) {
        resources.getDrawable(R.drawable.ic_library_filled, null)
    } else {
        resources.getDrawable(R.drawable.ic_library, null)
    }
}
```

Add click handlers in `initViews()`:

```kotlin
buttonWatched.setOnClickListener {
    contentItem?.let { item ->
        viewLifecycleOwner.lifecycleScope.launch {
            val isMovie = item.type == ContentItem.ContentType.MOVIE
            val success = if (isItemWatched) {
                if (isMovie) traktSyncRepository.markMovieUnwatched(item.tmdbId)
                else traktSyncRepository.markShowUnwatched(item.tmdbId)
            } else {
                if (isMovie) traktSyncRepository.markMovieWatched(item.tmdbId)
                else traktSyncRepository.markShowWatched(item.tmdbId)
            }
            
            if (success) {
                isItemWatched = !isItemWatched
                withContext(Dispatchers.Main) {
                    updateButtonStates()
                    val message = if (isItemWatched) "Marked as watched" else "Marked as unwatched"
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

buttonCollection.setOnClickListener {
    contentItem?.let { item ->
        viewLifecycleOwner.lifecycleScope.launch {
            val isMovie = item.type == ContentItem.ContentType.MOVIE
            val success = if (isItemInCollection) {
                traktSyncRepository.removeFromCollection(item.tmdbId, isMovie)
            } else {
                traktSyncRepository.addToCollection(item.tmdbId, isMovie)
            }
            
            if (success) {
                isItemInCollection = !isItemInCollection
                withContext(Dispatchers.Main) {
                    updateButtonStates()
                    val message = if (isItemInCollection) "Added to collection" else "Removed from collection"
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
```

### 3.5 Rating Button Logic (Like/Dislike)

Update the existing thumbs up/down handlers:

```kotlin
buttonThumbsUp.setOnClickListener {
    contentItem?.let { item ->
        viewLifecycleOwner.lifecycleScope.launch {
            if (currentRating == 1) {
                // Remove rating
                val success = traktSyncRepository.removeRating(item.tmdbId, item.type == ContentItem.ContentType.MOVIE)
                if (success) {
                    currentRating = 0
                    withContext(Dispatchers.Main) {
                        updateRatingButtons()
                        Toast.makeText(requireContext(), "Rating removed", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // Add "Like" rating (8 out of 10)
                val success = traktSyncRepository.addRating(item.tmdbId, item.type == ContentItem.ContentType.MOVIE, 8)
                if (success) {
                    currentRating = 1
                    withContext(Dispatchers.Main) {
                        updateRatingButtons()
                        Toast.makeText(requireContext(), "Rated thumbs up", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

buttonThumbsDown.setOnClickListener {
    contentItem?.let { item ->
        viewLifecycleOwner.lifecycleScope.launch {
            if (currentRating == 2) {
                // Remove rating
                val success = traktSyncRepository.removeRating(item.tmdbId, item.type == ContentItem.ContentType.MOVIE)
                if (success) {
                    currentRating = 0
                    withContext(Dispatchers.Main) {
                        updateRatingButtons()
                        Toast.makeText(requireContext(), "Rating removed", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // Add "Dislike" rating (4 out of 10)
                val success = traktSyncRepository.addRating(item.tmdbId, item.type == ContentItem.ContentType.MOVIE, 4)
                if (success) {
                    currentRating = 2
                    withContext(Dispatchers.Main) {
                        updateRatingButtons()
                        Toast.makeText(requireContext(), "Rated thumbs down", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
```

### 3.6 Rating Repository Methods

#### File: `TraktSyncRepository.kt`

Add these methods:

```kotlin
/**
 * Add rating to an item
 * @param rating 1-10, where 8 = Like, 4 = Dislike
 */
suspend fun addRating(tmdbId: Int, isMovie: Boolean, rating: Int): Boolean {
    val account = accountRepository.refreshTokenIfNeeded() ?: return false
    val authHeader = accountRepository.buildAuthHeader(account.accessToken)
    
    return try {
        val ratingItem = TraktRatingItem(
            ids = TraktIds(tmdb = tmdbId),
            rating = rating.coerceIn(1, 10)
        )
        val request = if (isMovie) {
            TraktRatingRequest(movies = listOf(ratingItem))
        } else {
            TraktRatingRequest(shows = listOf(ratingItem))
        }
        traktApiService.addRating(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            body = request
        )
        true
    } catch (e: Exception) {
        android.util.Log.e("TraktSync", "Failed to add rating", e)
        false
    }
}

/**
 * Remove rating from an item
 */
suspend fun removeRating(tmdbId: Int, isMovie: Boolean): Boolean {
    val account = accountRepository.refreshTokenIfNeeded() ?: return false
    val authHeader = accountRepository.buildAuthHeader(account.accessToken)
    
    return try {
        val request = if (isMovie) {
            TraktSyncRequest(movies = listOf(TraktSyncMovie(ids = TraktIds(tmdb = tmdbId))))
        } else {
            TraktSyncRequest(shows = listOf(TraktSyncShow(ids = TraktIds(tmdb = tmdbId))))
        }
        traktApiService.removeRating(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID,
            body = request
        )
        true
    } catch (e: Exception) {
        android.util.Log.e("TraktSync", "Failed to remove rating", e)
        false
    }
}
```

### 3.7 Dynamic "Next Up" Play Button

#### File: `DetailsFragment.kt`

Add state tracking for next episode:

```kotlin
private var nextUpSeason: Int? = null
private var nextUpEpisode: Int? = null
```

Add method to load next up info for TV shows:

```kotlin
private fun loadNextUpForShow(item: ContentItem) {
    if (item.type != ContentItem.ContentType.TV_SHOW) {
        buttonPlay.text = getString(R.string.details_play)
        return
    }
    
    viewLifecycleOwner.lifecycleScope.launch {
        try {
            val progress = withContext(Dispatchers.IO) {
                val account = traktAccountRepository.getAccount() ?: return@withContext null
                val authHeader = traktAccountRepository.buildAuthHeader(account.accessToken)
                
                traktApiService.getShowProgress(
                    showId = item.tmdbId,
                    authHeader = authHeader,
                    clientId = BuildConfig.TRAKT_CLIENT_ID
                )
            }
            
            val nextEp = progress?.nextEpisode
            if (nextEp != null) {
                nextUpSeason = nextEp.season
                nextUpEpisode = nextEp.number
                withContext(Dispatchers.Main) {
                    buttonPlay.text = "Play S${nextEp.season} E${nextEp.number}"
                }
            } else {
                // No next episode - show is complete or not started
                withContext(Dispatchers.Main) {
                    buttonPlay.text = getString(R.string.details_play)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load show progress", e)
            buttonPlay.text = getString(R.string.details_play)
        }
    }
}
```

Call `loadNextUpForShow()` in `bindContent()`:

```kotlin
private fun bindContent(item: ContentItem) {
    // ... existing code ...
    
    // Load item state (watched, collection status)
    loadItemState(item)
    
    // Load next up for TV shows
    loadNextUpForShow(item)
}
```

### 3.8 Trailer Button Implementation

Add trailer loading logic:

```kotlin
private var trailerKey: String? = null

private fun loadTrailer(item: ContentItem) {
    viewLifecycleOwner.lifecycleScope.launch {
        try {
            val videos = withContext(Dispatchers.IO) {
                rateLimiter.acquire()
                if (item.type == ContentItem.ContentType.MOVIE) {
                    tmdbApiService.getMovieVideos(item.tmdbId, BuildConfig.TMDB_API_KEY)
                } else {
                    tmdbApiService.getShowVideos(item.tmdbId, BuildConfig.TMDB_API_KEY)
                }
            }
            
            // Find YouTube trailer
            trailerKey = videos.results
                ?.filter { it.site == "YouTube" && it.type == "Trailer" }
                ?.firstOrNull()
                ?.key
            
            withContext(Dispatchers.Main) {
                buttonTrailer.isEnabled = trailerKey != null
                buttonTrailer.alpha = if (trailerKey != null) 1.0f else 0.5f
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load trailer", e)
        }
    }
}

// Update the trailer button click handler
buttonTrailer.setOnClickListener {
    trailerKey?.let { key ->
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key"))
        startActivity(intent)
    } ?: run {
        Toast.makeText(requireContext(), "No trailer available", Toast.LENGTH_SHORT).show()
    }
}
```

---

## Database Schema Changes

### 4.1 No Major Schema Changes Required

The existing schema supports all features through the `TraktUserItem` entity with these list types:
- `WATCHLIST`
- `COLLECTION`  
- `HISTORY`

### 4.2 Optional Enhancement: Episode-Level Tracking

For granular episode watched status, add a new entity:

```kotlin
@Entity(
    tableName = "episode_watch_status",
    primaryKeys = ["showTmdbId", "seasonNumber", "episodeNumber"]
)
data class EpisodeWatchStatusEntity(
    val showTmdbId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val isWatched: Boolean,
    val watchedAt: Long?
)
```

This requires:
1. Adding entity to `AppDatabase`
2. Creating `EpisodeWatchStatusDao`
3. Database migration (version 14 → 15)

---

## Implementation Priority Order

### Phase 1: Core Infrastructure (1-2 days)
1. Add new API endpoints to `TraktApiService.kt`
2. Add data models for sync requests/responses
3. Add repository action methods to `TraktSyncRepository.kt`
4. Add new DAO query methods

### Phase 2: Context Menu - Poster Rows (1 day)
1. Update `HomeFragment.showItemContextMenu()` with state-aware logic
2. Add dependency injection for `TraktSyncRepository`
3. Copy implementation to `MoviesFragment` and `TvShowsFragment`

### Phase 3: Details Page Buttons (1 day)
1. Add new button views to `fragment_details.xml`
2. Add button initialization and state management in `DetailsFragment`
3. Implement Watched and Collection button logic
4. Update rating button logic to send actual ratings

### Phase 4: Episode/Season Context Menus (1 day)
1. Add long-press to `SeasonAdapter`
2. Add long-press to `EpisodeAdapter`
3. Add context menu functions for seasons and episodes

### Phase 5: Visual Indicators & Next Up (1 day)
1. Update `item_episode_card.xml` with watched badge
2. Implement watched badge visibility logic
3. Implement dynamic "Next Up" play button for TV shows
4. Add trailer loading and playback

### Phase 6: Testing & Polish
1. Test all API calls with Trakt
2. Verify local DB updates
3. Test Continue Watching refresh
4. Edge case handling (no auth, API failures)

---

## Testing Checklist

- [ ] Long-press context menu appears for movies
- [ ] Long-press context menu appears for TV shows
- [ ] Long-press is disabled for My Trakt placeholder items
- [ ] Long-press is disabled for Franchise/Director/Network rows
- [ ] Mark Watched toggles to "Mark Unwatched" after action
- [ ] Collection toggle works correctly
- [ ] Watchlist toggle works correctly
- [ ] Season long-press shows "Mark Season Watched"
- [ ] Episode long-press shows watched/unwatched toggle
- [ ] Episode cards show checkmark when watched
- [ ] Details page Watched button icon updates on toggle
- [ ] Details page Collection button icon updates on toggle
- [ ] Like button sends rating of 8 to Trakt
- [ ] Dislike button sends rating of 4 to Trakt
- [ ] Play button shows "Play S3E5" format for TV shows with progress
- [ ] Trailer button opens YouTube
- [ ] Continue Watching row refreshes after marking watched
- [ ] All actions work when offline (graceful failure)
- [ ] All actions require Trakt authentication (prompt if not logged in)
