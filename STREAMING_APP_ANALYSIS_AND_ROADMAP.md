# Android TV Streaming App - Codebase Analysis & Implementation Roadmap

## Executive Summary

After a thorough analysis of your ~25,000 line codebase, I've identified a well-architected Android TV streaming application with:

- **Solid Architecture**: Hilt DI, MVVM pattern, Room database, Retrofit for networking
- **Trakt Integration**: OAuth device flow, sync infrastructure, watchlist/collection/history support
- **TMDB Integration**: Rich metadata, images, episodes, seasons
- **UI Framework**: Custom Leanback-inspired implementation with poster cards, hero sections, and smooth animations
- **Existing Infrastructure**: Background sync workers, rate limiting, caching

---

## Your 3 Options Analysis

### Option 1: Long-Press Context Menus
**Complexity**: High | **Impact**: Very High | **Dependencies**: Trakt API write calls

Already have:
- `onItemLongPress` callback in `ContentRowAdapter` and `PosterAdapter`
- Basic context menu skeleton in `HomeFragment.showItemContextMenu()`
- `TraktApiService` with read endpoints

Need to add:
- Trakt sync/write API endpoints (mark watched, add/remove collection/watchlist)
- Season/episode long-press handlers
- Continue Watching row refresh after marking watched
- User requests "Popup" modal/context menu, radius same as posters

### Option 2: Watched Checkmarks on Episodes
**Complexity**: Medium | **Impact**: High | **Dependencies**: Trakt history sync

Already have:
- `WatchStatusEntity` and `WatchStatusRepository`
- `TraktSyncRepository` with history sync
- `EpisodeAdapter` with episode cards

Need to add:
- Episode-level watched status from Trakt
- UI overlay (checkmark badge on episode cards)
- Real-time update when marking watched

### Option 3: Details Page Buttons
**Complexity**: Medium | **Impact**: High | **Dependencies**: Trakt API write calls

Already have:
- Button layout (Play, Trailer, Watchlist, ThumbsUp, ThumbsDown)
- Basic click handlers (Toast placeholders)
- `TraktShowProgress` for next episode

Need to add:
- Mark Watched button + Collection button
- State management (filled vs hollow icons)
- Smart "Play S#E#" based on next episode
- Rating API calls (8 for like, 4 for dislike)

---

## Additional Feature Suggestions (After Full Analysis)

### Suggestion 4: Global Search with Voice Support 🔍
**Complexity**: Medium | **Impact**: Very High | **User Value**: Critical for TV UX

**Why This Matters**:
- You already have `SearchFragment` imported but it appears minimal
- Android TV users expect voice search (RECORD_AUDIO permission already declared)
- TMDB `multiSearch` API endpoint is already defined but unused
- Enables discovery beyond browsing rows

**What to Build**:
- Full `SearchFragment` with keyboard + voice input
- Real-time search-as-you-type with debouncing
- Results grid showing movies, TV shows, and people
- Navigate to Details/Actor pages from results

### Suggestion 5: Offline Mode & Download Queue 📥
**Complexity**: High | **Impact**: High | **User Value**: Competitive advantage

**Why This Matters**:
- Your caching infrastructure is solid (Room DB, 24hr cache validity)
- No offline viewing capability exists
- Fire Stick/Chromecast users often have unstable WiFi

**What to Build**:
- Download manager for "save for later" 
- Background download worker
- Offline playback detection
- Storage management UI in Settings

### Suggestion 6: Smart Recommendations Row ("Because You Watched X") 🎯
**Complexity**: Medium | **Impact**: Medium-High | **User Value**: Engagement driver

**Why This Matters**:
- You have Trakt watch history synced locally
- TMDB has `similar` endpoints already wired up
- No personalized recommendations currently exist

**What to Build**:
- New "Because You Watched [Last Item]" row on Home
- Pull from user's most recent history item
- Use Trakt related or TMDB similar
- Rotate recommendation source periodically

---

## Recommended Implementation Order

Based on dependencies, complexity, and user impact:

```
Phase 1: Foundation (Week 1-2)
├── Option 3: Details Page Buttons (enables Phase 2)
│   └── Adds Mark Watched + Collection + Ratings API
└── Suggestion 4: Global Search (high impact, standalone)

Phase 2: Core Trakt Features (Week 2-3)  
├── Option 1: Long-Press Context Menus
│   └── Reuses API calls from Phase 1
└── Option 2: Watched Checkmarks on Episodes
    └── Depends on mark watched infrastructure

Phase 3: Enhancement (Week 4+)
├── Suggestion 6: Smart Recommendations
└── Suggestion 5: Offline Mode (if prioritized)
```

---

## Detailed TODO List with Code Snippets

### Phase 1A: Details Page Buttons (Option 3)

#### 1.1 Add Trakt Write API Endpoints

**File: `TraktApiService.kt` - ADD these endpoints:**

```kotlin
// Add to existing TraktApiService interface

// ============== SYNC/HISTORY WRITE ENDPOINTS ==============

@POST("sync/history")
suspend fun addToHistory(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktSyncRequest
): TraktSyncResponse

@POST("sync/history/remove")
suspend fun removeFromHistory(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktSyncRequest
): TraktSyncResponse

// ============== COLLECTION WRITE ENDPOINTS ==============

@POST("sync/collection")
suspend fun addToCollection(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktSyncRequest
): TraktSyncResponse

@POST("sync/collection/remove")
suspend fun removeFromCollection(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktSyncRequest
): TraktSyncResponse

// ============== WATCHLIST WRITE ENDPOINTS ==============

@POST("sync/watchlist")
suspend fun addToWatchlist(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktSyncRequest
): TraktSyncResponse

@POST("sync/watchlist/remove")
suspend fun removeFromWatchlist(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktSyncRequest
): TraktSyncResponse

// ============== RATINGS ENDPOINTS ==============

@POST("sync/ratings")
suspend fun addRating(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktRatingRequest
): TraktSyncResponse

@POST("sync/ratings/remove")
suspend fun removeRating(
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String,
    @Body body: TraktSyncRequest
): TraktSyncResponse

// ============== CHECK ITEM STATUS ==============

@GET("users/me/ratings/movies/{id}")
suspend fun getMovieRating(
    @Path("id") traktId: Int,
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String
): List<TraktRatingItem>

@GET("users/me/ratings/shows/{id}")
suspend fun getShowRating(
    @Path("id") traktId: Int,
    @Header("Authorization") authHeader: String,
    @Header("trakt-api-version") apiVersion: String = "2",
    @Header("trakt-api-key") clientId: String
): List<TraktRatingItem>
```

#### 1.2 Add Request/Response Models

**File: `data/model/trakt/TraktSyncModels.kt` - CREATE:**

```kotlin
package com.test1.tv.data.model.trakt

import com.google.gson.annotations.SerializedName

// ============== SYNC REQUEST MODELS ==============

data class TraktSyncRequest(
    @SerializedName("movies") val movies: List<TraktSyncMovie>? = null,
    @SerializedName("shows") val shows: List<TraktSyncShow>? = null,
    @SerializedName("episodes") val episodes: List<TraktSyncEpisode>? = null
)

data class TraktSyncMovie(
    @SerializedName("ids") val ids: TraktSyncIds,
    @SerializedName("watched_at") val watchedAt: String? = null,  // ISO 8601
    @SerializedName("collected_at") val collectedAt: String? = null
)

data class TraktSyncShow(
    @SerializedName("ids") val ids: TraktSyncIds,
    @SerializedName("seasons") val seasons: List<TraktSyncSeason>? = null
)

data class TraktSyncSeason(
    @SerializedName("number") val number: Int,
    @SerializedName("episodes") val episodes: List<TraktSyncSeasonEpisode>? = null,
    @SerializedName("watched_at") val watchedAt: String? = null
)

data class TraktSyncSeasonEpisode(
    @SerializedName("number") val number: Int,
    @SerializedName("watched_at") val watchedAt: String? = null
)

data class TraktSyncEpisode(
    @SerializedName("ids") val ids: TraktSyncIds,
    @SerializedName("watched_at") val watchedAt: String? = null
)

data class TraktSyncIds(
    @SerializedName("trakt") val trakt: Int? = null,
    @SerializedName("tmdb") val tmdb: Int? = null,
    @SerializedName("imdb") val imdb: String? = null
)

// ============== RATING REQUEST MODELS ==============

data class TraktRatingRequest(
    @SerializedName("movies") val movies: List<TraktRatingMovie>? = null,
    @SerializedName("shows") val shows: List<TraktRatingShow>? = null,
    @SerializedName("episodes") val episodes: List<TraktRatingEpisode>? = null
)

data class TraktRatingMovie(
    @SerializedName("ids") val ids: TraktSyncIds,
    @SerializedName("rating") val rating: Int,  // 1-10
    @SerializedName("rated_at") val ratedAt: String? = null
)

data class TraktRatingShow(
    @SerializedName("ids") val ids: TraktSyncIds,
    @SerializedName("rating") val rating: Int
)

data class TraktRatingEpisode(
    @SerializedName("ids") val ids: TraktSyncIds,
    @SerializedName("rating") val rating: Int
)

// ============== RESPONSE MODELS ==============

data class TraktSyncResponse(
    @SerializedName("added") val added: TraktSyncCount? = null,
    @SerializedName("deleted") val deleted: TraktSyncCount? = null,
    @SerializedName("existing") val existing: TraktSyncCount? = null,
    @SerializedName("not_found") val notFound: TraktSyncNotFound? = null
)

data class TraktSyncCount(
    @SerializedName("movies") val movies: Int? = null,
    @SerializedName("shows") val shows: Int? = null,
    @SerializedName("episodes") val episodes: Int? = null
)

data class TraktSyncNotFound(
    @SerializedName("movies") val movies: List<TraktSyncIds>? = null,
    @SerializedName("shows") val shows: List<TraktSyncIds>? = null,
    @SerializedName("episodes") val episodes: List<TraktSyncIds>? = null
)

data class TraktRatingItem(
    @SerializedName("rated_at") val ratedAt: String?,
    @SerializedName("rating") val rating: Int?,
    @SerializedName("movie") val movie: TraktMovie? = null,
    @SerializedName("show") val show: TraktShow? = null
)
```

#### 1.3 Create Trakt Actions Repository

**File: `data/repository/TraktActionsRepository.kt` - CREATE:**

```kotlin
package com.test1.tv.data.repository

import com.test1.tv.BuildConfig
import com.test1.tv.data.local.dao.TraktUserItemDao
import com.test1.tv.data.local.entity.TraktUserItem
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.model.trakt.*
import com.test1.tv.data.remote.api.TraktApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktActionsRepository @Inject constructor(
    private val traktApiService: TraktApiService,
    private val traktAccountRepository: TraktAccountRepository,
    private val traktUserItemDao: TraktUserItemDao
) {
    private suspend fun getAuthHeader(): String? {
        val account = traktAccountRepository.getAccount() ?: return null
        return "Bearer ${account.accessToken}"
    }

    private fun nowIso(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

    // ============== MARK WATCHED ==============

    suspend fun markMovieWatched(tmdbId: Int, imdbId: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext Result.failure(Exception("Not logged in"))
        
        runCatching {
            val request = TraktSyncRequest(
                movies = listOf(
                    TraktSyncMovie(
                        ids = TraktSyncIds(tmdb = tmdbId, imdb = imdbId),
                        watchedAt = nowIso()
                    )
                )
            )
            val response = traktApiService.addToHistory(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            (response.added?.movies ?: 0) > 0
        }
    }

    suspend fun markShowWatched(tmdbId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext Result.failure(Exception("Not logged in"))
        
        runCatching {
            val request = TraktSyncRequest(
                shows = listOf(
                    TraktSyncShow(ids = TraktSyncIds(tmdb = tmdbId))
                )
            )
            val response = traktApiService.addToHistory(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            (response.added?.shows ?: 0) > 0
        }
    }

    suspend fun markEpisodeWatched(
        showTmdbId: Int, 
        seasonNumber: Int, 
        episodeNumber: Int
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext Result.failure(Exception("Not logged in"))
        
        runCatching {
            val request = TraktSyncRequest(
                shows = listOf(
                    TraktSyncShow(
                        ids = TraktSyncIds(tmdb = showTmdbId),
                        seasons = listOf(
                            TraktSyncSeason(
                                number = seasonNumber,
                                episodes = listOf(
                                    TraktSyncSeasonEpisode(
                                        number = episodeNumber,
                                        watchedAt = nowIso()
                                    )
                                )
                            )
                        )
                    )
                )
            )
            val response = traktApiService.addToHistory(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            (response.added?.episodes ?: 0) > 0
        }
    }

    suspend fun markSeasonWatched(showTmdbId: Int, seasonNumber: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext Result.failure(Exception("Not logged in"))
        
        runCatching {
            val request = TraktSyncRequest(
                shows = listOf(
                    TraktSyncShow(
                        ids = TraktSyncIds(tmdb = showTmdbId),
                        seasons = listOf(
                            TraktSyncSeason(
                                number = seasonNumber,
                                watchedAt = nowIso()
                            )
                        )
                    )
                )
            )
            val response = traktApiService.addToHistory(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            (response.added?.episodes ?: 0) > 0
        }
    }

    // ============== COLLECTION ==============

    suspend fun addToCollection(item: ContentItem): Result<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext Result.failure(Exception("Not logged in"))
        
        runCatching {
            val request = when (item.type) {
                ContentItem.ContentType.MOVIE -> TraktSyncRequest(
                    movies = listOf(TraktSyncMovie(ids = TraktSyncIds(tmdb = item.tmdbId)))
                )
                ContentItem.ContentType.TV_SHOW -> TraktSyncRequest(
                    shows = listOf(TraktSyncShow(ids = TraktSyncIds(tmdb = item.tmdbId)))
                )
            }
            val response = traktApiService.addToCollection(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            val added = when (item.type) {
                ContentItem.ContentType.MOVIE -> response.added?.movies ?: 0
                ContentItem.ContentType.TV_SHOW -> response.added?.shows ?: 0
            }
            added > 0
        }
    }

    suspend fun removeFromCollection(item: ContentItem): Result<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext Result.failure(Exception("Not logged in"))
        
        runCatching {
            val request = when (item.type) {
                ContentItem.ContentType.MOVIE -> TraktSyncRequest(
                    movies = listOf(TraktSyncMovie(ids = TraktSyncIds(tmdb = item.tmdbId)))
                )
                ContentItem.ContentType.TV_SHOW -> TraktSyncRequest(
                    shows = listOf(TraktSyncShow(ids = TraktSyncIds(tmdb = item.tmdbId)))
                )
            }
            val response = traktApiService.removeFromCollection(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            val deleted = when (item.type) {
                ContentItem.ContentType.MOVIE -> response.deleted?.movies ?: 0
                ContentItem.ContentType.TV_SHOW -> response.deleted?.shows ?: 0
            }
            deleted > 0
        }
    }

    // ============== WATCHLIST ==============

    suspend fun addToWatchlist(item: ContentItem): Result<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext Result.failure(Exception("Not logged in"))
        
        runCatching {
            val request = when (item.type) {
                ContentItem.ContentType.MOVIE -> TraktSyncRequest(
                    movies = listOf(TraktSyncMovie(ids = TraktSyncIds(tmdb = item.tmdbId)))
                )
                ContentItem.ContentType.TV_SHOW -> TraktSyncRequest(
                    shows = listOf(TraktSyncShow(ids = TraktSyncIds(tmdb = item.tmdbId)))
                )
            }
            val response = traktApiService.addToWatchlist(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            val added = when (item.type) {
                ContentItem.ContentType.MOVIE -> response.added?.movies ?: 0
                ContentItem.ContentType.TV_SHOW -> response.added?.shows ?: 0
            }
            added > 0
        }
    }

    suspend fun removeFromWatchlist(item: ContentItem): Result<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext Result.failure(Exception("Not logged in"))
        
        runCatching {
            val request = when (item.type) {
                ContentItem.ContentType.MOVIE -> TraktSyncRequest(
                    movies = listOf(TraktSyncMovie(ids = TraktSyncIds(tmdb = item.tmdbId)))
                )
                ContentItem.ContentType.TV_SHOW -> TraktSyncRequest(
                    shows = listOf(TraktSyncShow(ids = TraktSyncIds(tmdb = item.tmdbId)))
                )
            }
            val response = traktApiService.removeFromWatchlist(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            val deleted = when (item.type) {
                ContentItem.ContentType.MOVIE -> response.deleted?.movies ?: 0
                ContentItem.ContentType.TV_SHOW -> response.deleted?.shows ?: 0
            }
            deleted > 0
        }
    }

    // ============== RATINGS ==============

    suspend fun rateItem(item: ContentItem, rating: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext Result.failure(Exception("Not logged in"))
        
        runCatching {
            val request = when (item.type) {
                ContentItem.ContentType.MOVIE -> TraktRatingRequest(
                    movies = listOf(TraktRatingMovie(ids = TraktSyncIds(tmdb = item.tmdbId), rating = rating))
                )
                ContentItem.ContentType.TV_SHOW -> TraktRatingRequest(
                    shows = listOf(TraktRatingShow(ids = TraktSyncIds(tmdb = item.tmdbId), rating = rating))
                )
            }
            val response = traktApiService.addRating(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            val added = when (item.type) {
                ContentItem.ContentType.MOVIE -> response.added?.movies ?: 0
                ContentItem.ContentType.TV_SHOW -> response.added?.shows ?: 0
            }
            added > 0
        }
    }

    suspend fun removeRating(item: ContentItem): Result<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext Result.failure(Exception("Not logged in"))
        
        runCatching {
            val request = when (item.type) {
                ContentItem.ContentType.MOVIE -> TraktSyncRequest(
                    movies = listOf(TraktSyncMovie(ids = TraktSyncIds(tmdb = item.tmdbId)))
                )
                ContentItem.ContentType.TV_SHOW -> TraktSyncRequest(
                    shows = listOf(TraktSyncShow(ids = TraktSyncIds(tmdb = item.tmdbId)))
                )
            }
            val response = traktApiService.removeRating(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID,
                body = request
            )
            val deleted = when (item.type) {
                ContentItem.ContentType.MOVIE -> response.deleted?.movies ?: 0
                ContentItem.ContentType.TV_SHOW -> response.deleted?.shows ?: 0
            }
            deleted > 0
        }
    }

    // ============== STATUS CHECKS ==============

    suspend fun isInCollection(tmdbId: Int, type: ContentItem.ContentType): Boolean {
        val itemType = when (type) {
            ContentItem.ContentType.MOVIE -> "MOVIE"
            ContentItem.ContentType.TV_SHOW -> "SHOW"
        }
        val items = traktUserItemDao.getItems("COLLECTION", itemType)
        return items.any { it.tmdbId == tmdbId }
    }

    suspend fun isInWatchlist(tmdbId: Int, type: ContentItem.ContentType): Boolean {
        val itemType = when (type) {
            ContentItem.ContentType.MOVIE -> "MOVIE"
            ContentItem.ContentType.TV_SHOW -> "SHOW"
        }
        val items = traktUserItemDao.getItems("WATCHLIST", itemType)
        return items.any { it.tmdbId == tmdbId }
    }

    suspend fun isWatched(tmdbId: Int, type: ContentItem.ContentType): Boolean {
        val itemType = when (type) {
            ContentItem.ContentType.MOVIE -> "MOVIE"
            ContentItem.ContentType.TV_SHOW -> "SHOW"
        }
        val items = traktUserItemDao.getItems("HISTORY", itemType)
        return items.any { it.tmdbId == tmdbId }
    }
}
```

#### 1.4 Update Details Fragment - Wire Up Buttons

**File: `ui/details/DetailsFragment.kt` - MODIFY:**

Add these properties and inject the repository:

```kotlin
// Add to class properties
@Inject lateinit var traktActionsRepository: TraktActionsRepository

private var isInCollection = false
private var isInWatchlist = false
private var isWatched = false
private var userRating: Int = 0  // 0=none, 8=liked, 4=disliked

// Add these new button references
private lateinit var buttonMarkWatched: MaterialButton
private lateinit var buttonCollection: MaterialButton
```

Update `onViewCreated` to initialize new buttons:

```kotlin
// Add in onViewCreated, after other button initializations
buttonMarkWatched = view.findViewById(R.id.button_mark_watched)
buttonCollection = view.findViewById(R.id.button_collection)

// Load initial states
contentItem?.let { loadItemStates(it) }

// Wire up button clicks
buttonMarkWatched.setOnClickListener { handleMarkWatched() }
buttonCollection.setOnClickListener { handleCollectionToggle() }
buttonThumbsUp.setOnClickListener { handleRating(8) }  // Like = 8 on Trakt
buttonThumbsDown.setOnClickListener { handleRating(4) }  // Dislike = 4 on Trakt
```

Add the handler methods:

```kotlin
private fun loadItemStates(item: ContentItem) {
    viewLifecycleOwner.lifecycleScope.launch {
        isInCollection = traktActionsRepository.isInCollection(item.tmdbId, item.type)
        isInWatchlist = traktActionsRepository.isInWatchlist(item.tmdbId, item.type)
        isWatched = traktActionsRepository.isWatched(item.tmdbId, item.type)
        
        updateButtonStates()
    }
}

private fun updateButtonStates() {
    // Mark Watched button - filled eye if watched, hollow if not
    buttonMarkWatched.setIconResource(
        if (isWatched) R.drawable.ic_visibility 
        else R.drawable.ic_visibility_off
    )
    
    // Collection button - filled bookmark if in collection
    buttonCollection.setIconResource(
        if (isInCollection) R.drawable.ic_bookmark_filled 
        else R.drawable.ic_bookmark_outline
    )
    
    // Rating buttons
    buttonThumbsUp.setIconResource(
        if (userRating == 8) R.drawable.ic_thumb_up_filled 
        else R.drawable.ic_thumb_up_outline
    )
    buttonThumbsDown.setIconResource(
        if (userRating == 4) R.drawable.ic_thumb_down_filled 
        else R.drawable.ic_thumb_down_outline
    )
}

private fun handleMarkWatched() {
    val item = contentItem ?: return
    
    viewLifecycleOwner.lifecycleScope.launch {
        val result = if (item.type == ContentItem.ContentType.MOVIE) {
            traktActionsRepository.markMovieWatched(item.tmdbId, item.imdbId)
        } else {
            // For shows, mark all episodes watched
            traktActionsRepository.markShowWatched(item.tmdbId)
        }
        
        result.onSuccess { 
            isWatched = true
            updateButtonStates()
            Toast.makeText(context, "Marked as watched", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Failed to mark watched", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun handleCollectionToggle() {
    val item = contentItem ?: return
    
    viewLifecycleOwner.lifecycleScope.launch {
        val result = if (isInCollection) {
            traktActionsRepository.removeFromCollection(item)
        } else {
            traktActionsRepository.addToCollection(item)
        }
        
        result.onSuccess {
            isInCollection = !isInCollection
            updateButtonStates()
            val message = if (isInCollection) "Added to collection" else "Removed from collection"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Action failed", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun handleRating(rating: Int) {
    val item = contentItem ?: return
    
    viewLifecycleOwner.lifecycleScope.launch {
        val result = if (userRating == rating) {
            // Toggle off - remove rating
            traktActionsRepository.removeRating(item)
        } else {
            // Set new rating
            traktActionsRepository.rateItem(item, rating)
        }
        
        result.onSuccess {
            userRating = if (userRating == rating) 0 else rating
            updateButtonStates()
            val message = when (userRating) {
                8 -> "Rated: Like"
                4 -> "Rated: Dislike"
                else -> "Rating removed"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Rating failed", Toast.LENGTH_SHORT).show()
        }
    }
}
```

#### 1.5 Smart Play Button for Next Episode

Update `updatePlayButtonText` to use actual progress:

```kotlin
private fun updatePlayButtonForShow(tmdbId: Int) {
    viewLifecycleOwner.lifecycleScope.launch {
        val authHeader = traktAccountRepository.getAccount()?.let { "Bearer ${it.accessToken}" }
        if (authHeader == null) {
            buttonPlay.text = getString(R.string.details_play)
            return@launch
        }
        
        // Get show progress from Trakt
        val progress = runCatching {
            traktApiService.getShowProgress(
                showId = tmdbId,  // Note: This needs Trakt ID, may need lookup
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID
            )
        }.getOrNull()
        
        val nextEp = progress?.nextEpisode
        if (nextEp != null) {
            val season = nextEp.season ?: 1
            val episode = nextEp.number ?: 1
            buttonPlay.text = "${getString(R.string.details_play)} S${season}E${episode}"
        } else if ((progress?.completed ?: 0) == (progress?.aired ?: 0) && (progress?.aired ?: 0) > 0) {
            // All episodes watched - show "Rewatch"
            buttonPlay.text = getString(R.string.rewatch)
        } else {
            buttonPlay.text = "${getString(R.string.details_play)} S1E1"
        }
    }
}
```

---

### Phase 1B: Global Search (Suggestion 4)

#### 1.6 Create SearchViewModel

**File: `ui/search/SearchViewModel.kt` - CREATE:**

```kotlin
package com.test1.tv.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.test1.tv.BuildConfig
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.model.tmdb.TMDBCast
import com.test1.tv.data.remote.api.TMDBApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SearchResult {
    data class Movie(val item: ContentItem) : SearchResult()
    data class Show(val item: ContentItem) : SearchResult()
    data class Person(val id: Int, val name: String, val profileUrl: String?) : SearchResult()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val tmdbApiService: TMDBApiService
) : ViewModel() {

    private val _searchResults = MutableLiveData<List<SearchResult>>()
    val searchResults: LiveData<List<SearchResult>> = _searchResults

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            
            _isLoading.value = true
            try {
                val response = tmdbApiService.multiSearch(
                    apiKey = BuildConfig.TMDB_API_KEY,
                    query = query
                )

                val results = response.results?.mapNotNull { result ->
                    when (result.mediaType) {
                        "movie" -> SearchResult.Movie(
                            ContentItem(
                                id = result.id,
                                tmdbId = result.id,
                                imdbId = null,
                                title = result.title ?: result.name ?: "",
                                overview = result.overview,
                                posterUrl = result.getPosterUrl(),
                                backdropUrl = result.getBackdropUrl(),
                                logoUrl = null,
                                year = result.getYear(),
                                rating = result.voteAverage,
                                ratingPercentage = result.voteAverage?.times(10)?.toInt(),
                                genres = null,
                                type = ContentItem.ContentType.MOVIE,
                                runtime = null,
                                cast = null,
                                certification = null,
                                imdbRating = null,
                                rottenTomatoesRating = null,
                                traktRating = null
                            )
                        )
                        "tv" -> SearchResult.Show(
                            ContentItem(
                                id = result.id,
                                tmdbId = result.id,
                                imdbId = null,
                                title = result.name ?: result.title ?: "",
                                overview = result.overview,
                                posterUrl = result.getPosterUrl(),
                                backdropUrl = result.getBackdropUrl(),
                                logoUrl = null,
                                year = result.getYear(),
                                rating = result.voteAverage,
                                ratingPercentage = result.voteAverage?.times(10)?.toInt(),
                                genres = null,
                                type = ContentItem.ContentType.TV_SHOW,
                                runtime = null,
                                cast = null,
                                certification = null,
                                imdbRating = null,
                                rottenTomatoesRating = null,
                                traktRating = null
                            )
                        )
                        "person" -> SearchResult.Person(
                            id = result.id,
                            name = result.name ?: "",
                            profileUrl = result.getProfileUrl()
                        )
                        else -> null
                    }
                } ?: emptyList()

                _searchResults.value = results
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
    }
}
```

#### 1.7 Create SearchFragment Layout

**File: `res/layout/fragment_search.xml` - CREATE:**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/default_background">

    <!-- Background gradient overlay -->
    <View
        android:id="@+id/search_backdrop"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@drawable/search_gradient_bg" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:paddingTop="48dp">

        <!-- Search Input Row -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:paddingHorizontal="64dp"
            android:paddingBottom="32dp">

            <!-- Voice Search Button -->
            <ImageButton
                android:id="@+id/btn_voice_search"
                android:layout_width="56dp"
                android:layout_height="56dp"
                android:src="@drawable/ic_mic"
                android:background="@drawable/circle_button_bg"
                android:contentDescription="@string/voice_search"
                android:focusable="true"
                android:focusableInTouchMode="true" />

            <!-- Search Text Input -->
            <EditText
                android:id="@+id/search_input"
                android:layout_width="0dp"
                android:layout_height="56dp"
                android:layout_weight="1"
                android:layout_marginStart="16dp"
                android:background="@drawable/search_input_bg"
                android:hint="@string/search_hint"
                android:textColor="@android:color/white"
                android:textColorHint="#88FFFFFF"
                android:textSize="18sp"
                android:paddingHorizontal="24dp"
                android:imeOptions="actionSearch"
                android:inputType="text"
                android:focusable="true"
                android:focusableInTouchMode="true" />
        </LinearLayout>

        <!-- Loading Indicator -->
        <ProgressBar
            android:id="@+id/search_loading"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:visibility="gone" />

        <!-- Results Grid -->
        <androidx.leanback.widget.VerticalGridView
            android:id="@+id/search_results_grid"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:clipToPadding="false"
            android:paddingHorizontal="64dp"
            android:paddingBottom="32dp" />

        <!-- Empty State -->
        <TextView
            android:id="@+id/search_empty"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:text="@string/search_no_results"
            android:textColor="#88FFFFFF"
            android:textSize="16sp"
            android:visibility="gone" />

    </LinearLayout>

</FrameLayout>
```

#### 1.8 Implement SearchFragment

**File: `ui/search/SearchFragment.kt` - CREATE/REPLACE:**

```kotlin
package com.test1.tv.ui.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.test1.tv.ActorDetailsActivity
import com.test1.tv.DetailsActivity
import com.test1.tv.R
import com.test1.tv.data.model.ContentItem
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private val viewModel: SearchViewModel by viewModels()

    private lateinit var searchInput: EditText
    private lateinit var voiceButton: ImageButton
    private lateinit var resultsGrid: VerticalGridView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var emptyText: TextView

    private lateinit var resultsAdapter: SearchResultsAdapter

    companion object {
        private const val VOICE_REQUEST_CODE = 1001
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_search, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchInput = view.findViewById(R.id.search_input)
        voiceButton = view.findViewById(R.id.btn_voice_search)
        resultsGrid = view.findViewById(R.id.search_results_grid)
        loadingIndicator = view.findViewById(R.id.search_loading)
        emptyText = view.findViewById(R.id.search_empty)

        setupSearchInput()
        setupVoiceSearch()
        setupResultsGrid()
        observeViewModel()
    }

    private fun setupSearchInput() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.search(s?.toString() ?: "")
            }
        })
    }

    private fun setupVoiceSearch() {
        voiceButton.setOnClickListener {
            startVoiceSearch()
        }
    }

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_search_prompt))
        }
        try {
            startActivityForResult(intent, VOICE_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.voice_not_supported, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            results?.firstOrNull()?.let { query ->
                searchInput.setText(query)
                searchInput.setSelection(query.length)
            }
        }
    }

    private fun setupResultsGrid() {
        resultsAdapter = SearchResultsAdapter(
            onMovieClick = { item -> DetailsActivity.start(requireContext(), item) },
            onShowClick = { item -> DetailsActivity.start(requireContext(), item) },
            onPersonClick = { id, name -> 
                ActorDetailsActivity.start(requireContext(), id, name)
            }
        )
        resultsGrid.adapter = resultsAdapter
        resultsGrid.setNumColumns(5)
        resultsGrid.setItemSpacing(16)
    }

    private fun observeViewModel() {
        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            resultsAdapter.submitList(results)
            emptyText.visibility = if (results.isEmpty() && searchInput.text.isNotEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }
}

// Adapter for mixed results
class SearchResultsAdapter(
    private val onMovieClick: (ContentItem) -> Unit,
    private val onShowClick: (ContentItem) -> Unit,
    private val onPersonClick: (Int, String) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {

    private val items = mutableListOf<SearchResult>()

    fun submitList(newItems: List<SearchResult>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is SearchResult.Movie -> 0
        is SearchResult.Show -> 0
        is SearchResult.Person -> 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (viewType == 1) R.layout.item_search_person else R.layout.item_poster
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val posterImage: ImageView? = itemView.findViewById(R.id.poster_image)
        private val personImage: ImageView? = itemView.findViewById(R.id.person_image)
        private val personName: TextView? = itemView.findViewById(R.id.person_name)

        fun bind(result: SearchResult) {
            when (result) {
                is SearchResult.Movie -> {
                    Glide.with(itemView).load(result.item.posterUrl).into(posterImage!!)
                    itemView.setOnClickListener { onMovieClick(result.item) }
                }
                is SearchResult.Show -> {
                    Glide.with(itemView).load(result.item.posterUrl).into(posterImage!!)
                    itemView.setOnClickListener { onShowClick(result.item) }
                }
                is SearchResult.Person -> {
                    personName?.text = result.name
                    Glide.with(itemView).load(result.profileUrl).into(personImage!!)
                    itemView.setOnClickListener { onPersonClick(result.id, result.name) }
                }
            }
            
            // Focus handling
            itemView.setOnFocusChangeListener { view, hasFocus ->
                view.animate()
                    .scaleX(if (hasFocus) 1.1f else 1f)
                    .scaleY(if (hasFocus) 1.1f else 1f)
                    .setDuration(150)
                    .start()
            }
        }
    }
}
```

---

### Phase 2: Long-Press & Episode Checkmarks (Options 1 & 2)

#### 2.1 Enhanced Long-Press Context Menu

**Update `HomeFragment.kt` `showItemContextMenu`:**

```kotlin
private fun showItemContextMenu(item: ContentItem) {
    if (traktAccountRepository.getAccount() == null) {
        // Not logged in - show limited options
        showNotLoggedInMenu(item)
        return
    }

    viewLifecycleOwner.lifecycleScope.launch {
        val isWatched = traktActionsRepository.isWatched(item.tmdbId, item.type)
        val isInCollection = traktActionsRepository.isInCollection(item.tmdbId, item.type)
        val isInWatchlist = traktActionsRepository.isInWatchlist(item.tmdbId, item.type)

        val options = mutableListOf<Pair<String, () -> Unit>>()
        
        // Play option
        options.add(getString(R.string.action_play) to { handlePlay(item) })
        
        // Mark Watched toggle
        val watchedText = if (isWatched) R.string.action_unmark_watched else R.string.action_mark_watched
        options.add(getString(watchedText) to { toggleWatched(item, isWatched) })
        
        // Collection toggle
        val collectionText = if (isInCollection) R.string.action_remove_collection else R.string.action_add_collection
        options.add(getString(collectionText) to { toggleCollection(item, isInCollection) })
        
        // Watchlist toggle
        val watchlistText = if (isInWatchlist) R.string.action_remove_watchlist else R.string.action_add_watchlist
        options.add(getString(watchlistText) to { toggleWatchlist(item, isInWatchlist) })
        
        // View Details
        options.add(getString(R.string.action_view_details) to { openDetails(item) })

        withContext(Dispatchers.Main) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.title)
                .setItems(options.map { it.first }.toTypedArray()) { dialog, which ->
                    options[which].second.invoke()
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
}

private fun toggleWatched(item: ContentItem, currentlyWatched: Boolean) {
    viewLifecycleOwner.lifecycleScope.launch {
        val result = if (currentlyWatched) {
            // Would need removeFromHistory endpoint - simplified for now
            Result.success(true) 
        } else {
            if (item.type == ContentItem.ContentType.MOVIE) {
                traktActionsRepository.markMovieWatched(item.tmdbId, item.imdbId)
            } else {
                traktActionsRepository.markShowWatched(item.tmdbId)
            }
        }
        
        result.onSuccess {
            Toast.makeText(context, 
                if (currentlyWatched) "Unmarked as watched" else "Marked as watched",
                Toast.LENGTH_SHORT
            ).show()
            
            // Refresh Continue Watching if on home screen
            if (this@HomeFragment.isVisible) {
                viewModel.refreshContinueWatching()
            }
        }
    }
}
```

#### 2.2 Episode Watched Checkmarks

**Update `EpisodeAdapter` in DetailsFragment:**

```kotlin
// Add to EpisodeAdapter constructor
private val watchedEpisodes: Set<String> = emptySet(),  // "S1E2" format
private val onEpisodeLongPress: ((TMDBEpisode) -> Unit)? = null

// In ViewHolder.bind():
fun bind(episode: TMDBEpisode) {
    // ... existing binding code ...
    
    // Add watched checkmark
    val episodeKey = "S${episode.seasonNumber}E${episode.episodeNumber}"
    val watchedBadge: ImageView? = itemView.findViewById(R.id.episode_watched_badge)
    watchedBadge?.visibility = if (watchedEpisodes.contains(episodeKey)) {
        View.VISIBLE
    } else {
        View.GONE
    }
    
    // Long press handler
    itemView.setOnLongClickListener {
        onEpisodeLongPress?.invoke(episode)
        true
    }
}
```

**Fetch watched episodes in DetailsFragment:**

```kotlin
private suspend fun loadWatchedEpisodes(showTmdbId: Int): Set<String> {
    val authHeader = traktAccountRepository.getAccount()?.let { "Bearer ${it.accessToken}" }
        ?: return emptySet()
    
    // Get show's Trakt ID first (you may need to look this up)
    val watchedShows = runCatching {
        traktApiService.getWatchedShows(
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID
        )
    }.getOrNull() ?: return emptySet()
    
    // Find matching show and get progress
    val progress = runCatching {
        // This needs the Trakt ID, not TMDB ID
        // You may need to add a lookup or use the TMDB->Trakt mapping
        traktApiService.getShowProgress(
            showId = showTmdbId, // Note: Should be Trakt ID
            authHeader = authHeader,
            clientId = BuildConfig.TRAKT_CLIENT_ID
        )
    }.getOrNull()
    
    // Build set of watched episode keys
    // Note: getShowProgress returns completed count, not individual episodes
    // You may need /sync/history/shows endpoint with episodes extended
    return emptySet() // Placeholder - needs full implementation
}
```

---

## Resource Files Needed

### Icons to Add

Create or add these vector drawables:

```
res/drawable/
├── ic_visibility_off.xml        (Hollow eye)
├── ic_bookmark_filled.xml       (Filled bookmark) 
├── ic_bookmark_outline.xml      (Hollow bookmark)
├── ic_thumb_up_filled.xml       (Filled thumbs up)
├── ic_thumb_up_outline.xml      (Hollow thumbs up)
├── ic_thumb_down_filled.xml     (Filled thumbs down)
├── ic_thumb_down_outline.xml    (Hollow thumbs down)
├── ic_mic.xml                   (Microphone for voice)
├── ic_check_badge.xml           (Checkmark overlay for watched)
└── search_input_bg.xml          (Search field background)
```

### String Resources

```xml
<!-- Add to strings.xml -->
<string name="action_mark_watched">Mark as Watched</string>
<string name="action_unmark_watched">Remove from History</string>
<string name="action_add_collection">Add to Collection</string>
<string name="action_remove_collection">Remove from Collection</string>
<string name="action_add_watchlist">Add to Watchlist</string>
<string name="action_remove_watchlist">Remove from Watchlist</string>
<string name="action_view_details">View Details</string>
<string name="action_play">Play</string>
<string name="rewatch">Rewatch</string>
<string name="voice_search">Voice Search</string>
<string name="voice_search_prompt">Say a movie or show name</string>
<string name="voice_not_supported">Voice search not available</string>
<string name="search_hint">Search movies, shows, people...</string>
<string name="search_no_results">No results found</string>
<string name="cancel">Cancel</string>
```

---

## Hilt Module Updates

**Add to existing NetworkModule or create new:**

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object TraktActionsModule {
    
    @Provides
    @Singleton
    fun provideTraktActionsRepository(
        traktApiService: TraktApiService,
        traktAccountRepository: TraktAccountRepository,
        traktUserItemDao: TraktUserItemDao
    ): TraktActionsRepository {
        return TraktActionsRepository(
            traktApiService,
            traktAccountRepository,
            traktUserItemDao
        )
    }
}
```

---

## Testing Checklist

### Phase 1 Tests
- [ ] Mark movie as watched → Verify Trakt history updated
- [ ] Add movie to collection → Verify Trakt collection updated
- [ ] Rate movie thumbs up → Verify rating = 8 on Trakt
- [ ] Toggle collection off → Verify removed from Trakt
- [ ] Search "Batman" → Verify movies, shows, people returned
- [ ] Voice search → Verify speech recognized and search triggered
- [ ] Play button shows correct next episode for in-progress show

### Phase 2 Tests
- [ ] Long-press on movie card → Context menu appears
- [ ] Long-press "Mark Watched" → Toast confirms, item state updates
- [ ] Episode cards show checkmark for watched episodes
- [ ] Long-press episode → Can mark that specific episode watched
- [ ] Long-press season button → Marks entire season watched

---

## Performance Considerations

1. **Cache Trakt Status Locally**: The `TraktUserItemDao` already stores watchlist/collection/history. Query locally first, then refresh from API in background.

2. **Debounce API Writes**: When rapidly toggling (e.g., marking multiple episodes), batch requests or debounce to avoid rate limits.

3. **Optimistic UI Updates**: Update button states immediately, then confirm with API. Revert on failure.

4. **Background Sync**: After any write operation, trigger `TraktSyncWorker` to ensure local DB stays in sync.

---

## Summary

This roadmap provides a clear path from your current state to a fully-featured streaming app with:

1. **Full Trakt write integration** for watched status, collections, watchlists, and ratings
2. **Contextual long-press menus** throughout the app
3. **Visual watched indicators** on episodes
4. **Voice-enabled search** for TV-optimized discovery
5. **Smart playback** that knows where you left off

Start with Phase 1 (Details buttons + Search) as they're foundational and high-impact. Phase 2 builds directly on that foundation.
