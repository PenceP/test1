# Android TV Refactoring Master Plan

## Project: Media Streaming App → Netflix-Grade Performance

**Target Metrics:**
- 60fps scrolling (no dropped frames during D-pad navigation)
- <150ms hero section response time
- Zero hero/poster desync during fast scrolling
- Resilient networking with offline-first architecture
- Memory-efficient image loading

**Architecture:** MVVM + Clean Architecture + Offline-First (Repository Pattern)  
**Tech Stack:** Hilt, Coroutines, Flow, Room, Retrofit, Glide

---

## Table of Contents

1. [Phase 1: Foundation & Dependency Injection](#phase-1-foundation--dependency-injection)
2. [Phase 2: Data Layer & API Resilience](#phase-2-data-layer--api-resilience)
3. [Phase 3: Navigation Smoothness & Hero Sync](#phase-3-navigation-smoothness--hero-sync)
4. [Phase 4: UI Runtime Performance](#phase-4-ui-runtime-performance)
5. [Phase 5: Hardware Acceleration](#phase-5-hardware-acceleration)
6. [Phase 6: Cleanup & Polish](#phase-6-cleanup--polish)
7. [Phase 7: Testing & Validation](#phase-7-testing--validation)
8. [Appendix: Code Templates](#appendix-code-templates)

---

## Phase 1: Foundation & Dependency Injection

**Goal:** Decouple the monolithic `ContentRepositoryProvider` and ensure testable, singleton access to core components.

**Estimated Time:** 2-3 hours

### Tasks

#### 1.1 Add Hilt Dependencies

**File:** `app/build.gradle.kts`

```kotlin
plugins {
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

dependencies {
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    
    // Testing
    testImplementation("com.google.dagger:hilt-android-testing:2.48")
    kaptTest("com.google.dagger:hilt-compiler:2.48")
}
```

- [ ] Add plugins block entries
- [ ] Add dependencies
- [ ] Sync gradle

---

#### 1.2 Annotate Application Class

**File:** `Test1App.kt`

```kotlin
@HiltAndroidApp
class Test1App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Existing initialization code...
    }
}
```

- [ ] Add `@HiltAndroidApp` annotation
- [ ] Verify app still launches

---

#### 1.3 Create Core DI Module

**File:** `di/AppModule.kt` (create new)

```kotlin
package com.test1.tv.di

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.test1.tv.R
import com.test1.tv.data.local.AppDatabase
import com.test1.tv.data.remote.TMDBApiService
import com.test1.tv.data.remote.TraktApiService
import com.test1.tv.data.remote.OMDbApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TmdbRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TraktRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OmdbRetrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ==================== Database ====================
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "tv_app_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideCachedContentDao(database: AppDatabase) = database.cachedContentDao()

    @Provides
    fun provideWatchStatusDao(database: AppDatabase) = database.watchStatusDao()

    @Provides
    fun provideContinueWatchingDao(database: AppDatabase) = database.continueWatchingDao()

    // ==================== Network ====================
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (com.test1.tv.BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            })
            .build()
    }

    @Provides
    @Singleton
    @TmdbRetrofit
    fun provideTmdbRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @TraktRetrofit
    fun provideTraktRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.trakt.tv/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @OmdbRetrofit
    fun provideOmdbRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://www.omdbapi.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideTmdbApiService(@TmdbRetrofit retrofit: Retrofit): TMDBApiService {
        return retrofit.create(TMDBApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideTraktApiService(@TraktRetrofit retrofit: Retrofit): TraktApiService {
        return retrofit.create(TraktApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideOmdbApiService(@OmdbRetrofit retrofit: Retrofit): OMDbApiService {
        return retrofit.create(OMDbApiService::class.java)
    }

    // ==================== UI Performance ====================
    
    /**
     * CRITICAL PERFORMANCE FIX: Shared ViewPool for nested RecyclerViews
     * This allows poster views to be recycled across different rows,
     * dramatically reducing view inflation during scrolling.
     */
    @Provides
    @Singleton
    fun provideSharedViewPool(): RecyclerView.RecycledViewPool {
        return RecyclerView.RecycledViewPool().apply {
            setMaxRecycledViews(R.layout.item_poster, 40)
            setMaxRecycledViews(R.layout.item_poster_landscape, 25)
        }
    }
}
```

- [ ] Create `di/` package
- [ ] Create `AppModule.kt`
- [ ] Verify all API service interfaces exist

---

#### 1.4 Create Dispatchers Module

**File:** `di/DispatchersModule.kt` (create new)

```kotlin
package com.test1.tv.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
```

- [ ] Create `DispatchersModule.kt`

---

#### 1.5 Annotate Entry Points

Add `@AndroidEntryPoint` to all Activities and Fragments:

**Files to modify:**

- [ ] `MainActivity.kt`
- [ ] `DetailsActivity.kt`
- [ ] `ActorDetailsActivity.kt`
- [ ] `ui/home/HomeFragment.kt` (MainFragment)
- [ ] `ui/movies/MoviesFragment.kt`
- [ ] `ui/tvshows/TvShowsFragment.kt`
- [ ] `ui/search/SearchFragment.kt`
- [ ] `ui/details/DetailsFragment.kt`
- [ ] `ui/actor/ActorDetailsFragment.kt`
- [ ] `ui/settings/SettingsActivity.kt`

```kotlin
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    // ...
}
```

---

#### 1.6 Verification Checkpoint

- [ ] App compiles without errors
- [ ] App launches successfully
- [ ] No crashes on navigation between screens

---

## Phase 2: Data Layer & API Resilience

**Goal:** Fix the N+1 network calls causing rate limits and implement offline-first data flow.

**Estimated Time:** 4-5 hours

### Tasks

#### 2.1 Implement Token Bucket Rate Limiter

**File:** `data/remote/RateLimiter.kt` (create new)

```kotlin
package com.test1.tv.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token bucket rate limiter for API calls.
 * 
 * TMDB allows 40 requests per 10 seconds.
 * This implementation ensures we never exceed that limit.
 */
@Singleton
class RateLimiter @Inject constructor() {
    
    companion object {
        private const val MAX_TOKENS = 40
        private const val REFILL_PERIOD_MS = 10_000L  // 10 seconds
        private const val WAIT_INTERVAL_MS = 250L
    }

    private val mutex = Mutex()
    private var availableTokens = MAX_TOKENS
    private var lastRefillTime = System.currentTimeMillis()

    /**
     * Acquires a token before making an API call.
     * Suspends if no tokens are available.
     */
    suspend fun acquire() {
        mutex.withLock {
            refillTokens()
            while (availableTokens <= 0) {
                // Release lock while waiting to allow other coroutines to proceed
                mutex.unlock()
                delay(WAIT_INTERVAL_MS)
                mutex.lock()
                refillTokens()
            }
            availableTokens--
        }
    }

    /**
     * Tries to acquire a token without waiting.
     * @return true if token was acquired, false otherwise
     */
    suspend fun tryAcquire(): Boolean {
        return mutex.withLock {
            refillTokens()
            if (availableTokens > 0) {
                availableTokens--
                true
            } else {
                false
            }
        }
    }

    private fun refillTokens() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRefillTime
        if (elapsed >= REFILL_PERIOD_MS) {
            availableTokens = MAX_TOKENS
            lastRefillTime = now
        }
    }

    /**
     * Returns current available tokens (for debugging/monitoring)
     */
    suspend fun availableTokens(): Int = mutex.withLock {
        refillTokens()
        availableTokens
    }
}
```

- [ ] Create `RateLimiter.kt`
- [ ] Add unit tests for rate limiter

---

#### 2.2 Create Resource Wrapper

**File:** `data/Resource.kt` (create new)

```kotlin
package com.test1.tv.data

/**
 * A generic class that holds a value with its loading status.
 * Used to wrap data that comes from the repository layer.
 */
sealed class Resource<out T> {
    
    /**
     * Represents successful data retrieval.
     * @param data The retrieved data
     */
    data class Success<T>(val data: T) : Resource<T>()
    
    /**
     * Represents an error during data retrieval.
     * @param exception The exception that occurred
     * @param cachedData Optional cached data to show while error is displayed
     */
    data class Error<T>(
        val exception: Throwable,
        val cachedData: T? = null
    ) : Resource<T>()
    
    /**
     * Represents ongoing data retrieval.
     * @param cachedData Optional cached data to show while loading
     */
    data class Loading<T>(val cachedData: T? = null) : Resource<T>()

    /**
     * Returns the data if available (from Success, or cached from Error/Loading)
     */
    fun dataOrNull(): T? = when (this) {
        is Success -> data
        is Error -> cachedData
        is Loading -> cachedData
    }

    /**
     * Returns true if this is a Success
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * Returns true if this is Loading
     */
    fun isLoading(): Boolean = this is Loading

    /**
     * Returns true if this is Error
     */
    fun isError(): Boolean = this is Error
}
```

- [ ] Create `Resource.kt`

---

#### 2.3 Create Normalized Database Entities

**File:** `data/local/MediaEntities.kt` (create new)

```kotlin
package com.test1.tv.data.local

import androidx.room.*

/**
 * Core media entity - stores basic metadata.
 * Split from images/ratings for efficient partial updates.
 */
@Entity(
    tableName = "media_content",
    indices = [
        Index(value = ["category", "position"]),
        Index(value = ["tmdbId"], unique = true),
        Index(value = ["cachedAt"]),
        Index(value = ["contentType", "category"])
    ]
)
data class MediaContentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tmdbId: Int,
    val imdbId: String?,
    val title: String,
    val overview: String?,
    val year: String?,
    val runtime: Int?,
    val certification: String?,
    val contentType: String,  // "movie" or "tv"
    val category: String,     // "trending_movies", "popular_shows", etc.
    val position: Int,        // Position within category for ordering
    val cachedAt: Long = System.currentTimeMillis()
)

/**
 * Separate entity for images - allows updating images without touching metadata.
 */
@Entity(
    tableName = "media_images",
    foreignKeys = [
        ForeignKey(
            entity = MediaContentEntity::class,
            parentColumns = ["tmdbId"],
            childColumns = ["tmdbId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tmdbId"])]
)
data class MediaImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tmdbId: Int,
    val posterUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?
)

/**
 * Separate entity for ratings from multiple sources.
 */
@Entity(
    tableName = "media_ratings",
    foreignKeys = [
        ForeignKey(
            entity = MediaContentEntity::class,
            parentColumns = ["tmdbId"],
            childColumns = ["tmdbId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tmdbId"])]
)
data class MediaRatingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tmdbId: Int,
    val tmdbRating: Float?,
    val imdbRating: Float?,
    val traktRating: Float?,
    val rottenTomatoesRating: Int?,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Watch progress - separate for frequent updates.
 */
@Entity(
    tableName = "watch_progress",
    indices = [Index(value = ["tmdbId"], unique = true)]
)
data class WatchProgressEntity(
    @PrimaryKey
    val tmdbId: Int,
    val progress: Float,          // 0.0 to 1.0
    val lastWatchedAt: Long,
    val episodeId: Int? = null,   // For TV shows
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
)

/**
 * Aggregate object for full media data with relations.
 */
data class MediaWithDetails(
    @Embedded
    val content: MediaContentEntity,
    
    @Relation(
        parentColumn = "tmdbId",
        entityColumn = "tmdbId"
    )
    val images: MediaImageEntity?,
    
    @Relation(
        parentColumn = "tmdbId",
        entityColumn = "tmdbId"
    )
    val ratings: MediaRatingEntity?,
    
    @Relation(
        parentColumn = "tmdbId",
        entityColumn = "tmdbId"
    )
    val progress: WatchProgressEntity?
)

/**
 * Lightweight version for list display (no ratings).
 */
data class MediaWithImages(
    @Embedded
    val content: MediaContentEntity,
    
    @Relation(
        parentColumn = "tmdbId",
        entityColumn = "tmdbId"
    )
    val images: MediaImageEntity?,
    
    @Relation(
        parentColumn = "tmdbId",
        entityColumn = "tmdbId"
    )
    val progress: WatchProgressEntity?
)
```

- [ ] Create `MediaEntities.kt`
- [ ] Update `AppDatabase.kt` to include new entities

---

#### 2.4 Create Media DAO

**File:** `data/local/MediaDao.kt` (create new)

```kotlin
package com.test1.tv.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    // ==================== Queries ====================
    
    /**
     * Get all media for a category with images and progress.
     * Uses Room's automatic JOIN for relations.
     */
    @Transaction
    @Query("""
        SELECT * FROM media_content 
        WHERE category = :category 
        ORDER BY position ASC
    """)
    fun getMediaByCategory(category: String): Flow<List<MediaWithImages>>

    /**
     * Get single media with full details.
     */
    @Transaction
    @Query("SELECT * FROM media_content WHERE tmdbId = :tmdbId")
    suspend fun getMediaDetails(tmdbId: Int): MediaWithDetails?

    /**
     * Get single media with full details as Flow.
     */
    @Transaction
    @Query("SELECT * FROM media_content WHERE tmdbId = :tmdbId")
    fun observeMediaDetails(tmdbId: Int): Flow<MediaWithDetails?>

    /**
     * Check if category cache is still valid.
     */
    @Query("""
        SELECT COUNT(*) FROM media_content 
        WHERE category = :category 
        AND cachedAt > :minCacheTime
    """)
    suspend fun getCachedCount(category: String, minCacheTime: Long): Int

    // ==================== Inserts ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContent(content: MediaContentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContents(contents: List<MediaContentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: MediaImageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImagesBatch(images: List<MediaImageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRatings(ratings: MediaRatingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: WatchProgressEntity)

    // ==================== Updates ====================
    
    @Query("UPDATE watch_progress SET progress = :progress, lastWatchedAt = :timestamp WHERE tmdbId = :tmdbId")
    suspend fun updateProgress(tmdbId: Int, progress: Float, timestamp: Long)

    // ==================== Deletes ====================
    
    @Query("DELETE FROM media_content WHERE category = :category")
    suspend fun clearCategory(category: String)

    @Query("DELETE FROM media_content WHERE cachedAt < :maxAge")
    suspend fun clearOldCache(maxAge: Long)

    // ==================== Transactions ====================
    
    /**
     * Atomically replace all content for a category.
     */
    @Transaction
    suspend fun replaceCategory(
        category: String,
        contents: List<MediaContentEntity>,
        images: List<MediaImageEntity>
    ) {
        clearCategory(category)
        insertContents(contents)
        insertImagesBatch(images)
    }
}
```

- [ ] Create `MediaDao.kt`
- [ ] Add DAO to AppDatabase

---

#### 2.5 Implement Offline-First Repository

**File:** `data/repository/MediaRepository.kt` (create new)

```kotlin
package com.test1.tv.data.repository

import android.util.Log
import com.test1.tv.BuildConfig
import com.test1.tv.data.Resource
import com.test1.tv.data.local.MediaDao
import com.test1.tv.data.local.MediaContentEntity
import com.test1.tv.data.local.MediaImageEntity
import com.test1.tv.data.local.MediaWithImages
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.remote.RateLimiter
import com.test1.tv.data.remote.TMDBApiService
import com.test1.tv.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val mediaDao: MediaDao,
    private val tmdbApi: TMDBApiService,
    private val rateLimiter: RateLimiter,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "MediaRepository"
        private const val CACHE_VALIDITY_MS = 30 * 60 * 1000L  // 30 minutes
        private const val BATCH_SIZE = 10
        private const val BATCH_DELAY_MS = 300L
        
        // Category constants
        const val CATEGORY_TRENDING_MOVIES = "trending_movies"
        const val CATEGORY_POPULAR_MOVIES = "popular_movies"
        const val CATEGORY_TRENDING_SHOWS = "trending_shows"
        const val CATEGORY_POPULAR_SHOWS = "popular_shows"
    }

    /**
     * Gets trending movies with offline-first strategy.
     * 
     * Flow emissions:
     * 1. Loading(cachedData) - Immediate UI update from cache
     * 2. Success(freshData) or Error(exception, cachedData)
     */
    fun getTrendingMovies(forceRefresh: Boolean = false): Flow<Resource<List<ContentItem>>> = flow {
        val category = CATEGORY_TRENDING_MOVIES
        val minCacheTime = System.currentTimeMillis() - CACHE_VALIDITY_MS

        // 1. Emit cached data immediately
        val cachedData = mediaDao.getMediaByCategory(category).first()
        val cachedItems = cachedData.map { it.toContentItem() }
        
        if (cachedItems.isNotEmpty()) {
            emit(Resource.Loading(cachedItems))
        } else {
            emit(Resource.Loading(null))
        }

        // 2. Check if refresh needed
        val cacheCount = mediaDao.getCachedCount(category, minCacheTime)
        val needsRefresh = forceRefresh || cacheCount == 0

        if (!needsRefresh && cachedItems.isNotEmpty()) {
            emit(Resource.Success(cachedItems))
            return@flow
        }

        // 3. Fetch from network
        try {
            val freshItems = fetchTrendingMoviesFromApi()
            
            // 4. Save to database (this triggers Flow update)
            saveToDatabase(category, freshItems)
            
            emit(Resource.Success(freshItems))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch trending movies", e)
            emit(Resource.Error(e, cachedItems.ifEmpty { null }))
        }
    }.flowOn(ioDispatcher)

    /**
     * Fetches trending movies from TMDB with rate limiting and batched enrichment.
     */
    private suspend fun fetchTrendingMoviesFromApi(): List<ContentItem> {
        // Acquire rate limit token
        rateLimiter.acquire()
        
        val response = tmdbApi.getTrendingMovies(
            apiKey = BuildConfig.TMDB_API_KEY,
            timeWindow = "week",
            page = 1
        )

        val movies = response.results ?: return emptyList()

        // Batch enrichment requests to avoid rate limit
        return enrichMoviesInBatches(movies)
    }

    /**
     * Enriches movies with additional details in batches.
     * TMDB allows 40 requests/10s, so we process 10 at a time with delays.
     */
    private suspend fun enrichMoviesInBatches(
        movies: List<com.test1.tv.data.model.tmdb.TMDBMovie>
    ): List<ContentItem> {
        val enrichedItems = mutableListOf<ContentItem>()
        
        movies.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            // Add delay between batches (except first)
            if (batchIndex > 0) {
                delay(BATCH_DELAY_MS)
            }
            
            batch.forEach { movie ->
                try {
                    rateLimiter.acquire()
                    
                    val details = tmdbApi.getMovieDetails(
                        movieId = movie.id,
                        apiKey = BuildConfig.TMDB_API_KEY,
                        appendToResponse = "images"
                    )
                    
                    enrichedItems.add(details.toContentItem())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to enrich movie ${movie.id}", e)
                    // Use basic data if enrichment fails
                    enrichedItems.add(movie.toBasicContentItem())
                }
            }
        }
        
        return enrichedItems
    }

    /**
     * Saves items to database with proper entity mapping.
     */
    private suspend fun saveToDatabase(category: String, items: List<ContentItem>) {
        val contents = items.mapIndexed { index, item ->
            MediaContentEntity(
                tmdbId = item.tmdbId,
                imdbId = item.imdbId,
                title = item.title,
                overview = item.overview,
                year = item.year,
                runtime = item.runtime,
                certification = item.certification,
                contentType = item.type.name.lowercase(),
                category = category,
                position = index
            )
        }
        
        val images = items.map { item ->
            MediaImageEntity(
                tmdbId = item.tmdbId,
                posterUrl = item.posterUrl,
                backdropUrl = item.backdropUrl,
                logoUrl = item.logoUrl
            )
        }
        
        mediaDao.replaceCategory(category, contents, images)
    }

    /**
     * Clears cache older than specified age.
     */
    suspend fun cleanupCache(maxAgeMs: Long = CACHE_VALIDITY_MS * 2) {
        withContext(ioDispatcher) {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            mediaDao.clearOldCache(cutoff)
        }
    }
}

// ==================== Extension Functions ====================

private fun MediaWithImages.toContentItem(): ContentItem {
    return ContentItem(
        id = content.id.toInt(),
        tmdbId = content.tmdbId,
        imdbId = content.imdbId,
        title = content.title,
        overview = content.overview,
        posterUrl = images?.posterUrl,
        backdropUrl = images?.backdropUrl,
        logoUrl = images?.logoUrl,
        year = content.year,
        runtime = content.runtime,
        certification = content.certification,
        type = if (content.contentType == "movie") {
            ContentItem.ContentType.MOVIE
        } else {
            ContentItem.ContentType.TV_SHOW
        },
        rating = null,
        ratingPercentage = null,
        genres = null,
        cast = null,
        imdbRating = null,
        rottenTomatoesRating = null,
        traktRating = null,
        watchProgress = progress?.progress?.toDouble()
    )
}
```

- [ ] Create `MediaRepository.kt`
- [ ] Add extension functions for model conversion
- [ ] Wire repository in ViewModels

---

#### 2.6 Verification Checkpoint

- [ ] Rate limiter prevents 429 errors
- [ ] App shows cached data immediately on launch
- [ ] Network errors don't crash the app (graceful degradation)
- [ ] Database contains expected data after first load

---

## Phase 3: Navigation Smoothness & Hero Sync

**Goal:** Fix the race condition where the Hero section shows the wrong image after fast scrolling.

**Estimated Time:** 3-4 hours

### Tasks

#### 3.1 Create Thread-Safe Hero Controller

**File:** `ui/home/HeroSectionController.kt` (create new)

```kotlin
package com.test1.tv.ui.home

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.test1.tv.R
import com.test1.tv.data.model.ContentItem
import com.test1.tv.ui.HeroSectionHelper
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe hero section controller with proper request versioning.
 * 
 * GUARANTEES:
 * - The hero section ALWAYS matches the currently focused item
 * - No stale Glide callbacks can update the UI after user scrolls away
 * - Smooth crossfade transitions between content
 * 
 * USAGE:
 * 1. Create instance in onViewCreated()
 * 2. Call onItemFocused() from your adapter's focus listener
 * 3. Call cleanup() in onDestroyView()
 */
class HeroSectionController(
    private val fragment: Fragment,
    private val backdropView: ImageView,
    private val logoView: ImageView,
    private val titleView: TextView,
    private val ambientOverlay: View,
    private val overviewView: TextView? = null,
    private val metadataView: TextView? = null,
    private val genresView: TextView? = null,
    private val castView: TextView? = null
) {
    companion object {
        private const val TAG = "HeroController"
        private const val DEBOUNCE_MS = 150L  // Netflix uses ~100-150ms
        private const val BACKDROP_CROSSFADE_MS = 200
        private const val LOGO_FADE_MS = 150
        private const val AMBIENT_ANIMATION_MS = 200L
        private const val DEFAULT_AMBIENT_COLOR = 0xFF0A0F1F.toInt()
    }

    // Atomic version counter - increments on every focus change
    private val requestVersion = AtomicLong(0)
    
    // Track all in-flight Glide targets for cancellation
    private var backdropTarget: CustomTarget<Drawable>? = null
    private var logoTarget: CustomTarget<Drawable>? = null
    private var paletteTarget: CustomTarget<Bitmap>? = null
    
    // Coroutine jobs
    private var updateJob: Job? = null
    private var paletteJob: Job? = null
    
    // Animation state
    private var currentAmbientColor: Int = DEFAULT_AMBIENT_COLOR
    private val argbEvaluator = ArgbEvaluator()
    private var ambientAnimator: ValueAnimator? = null

    /**
     * Call this on EVERY focus change from your adapter.
     * Handles debouncing internally to prevent jank during fast scrolling.
     */
    fun onItemFocused(item: ContentItem) {
        val version = requestVersion.incrementAndGet()
        
        Log.d(TAG, "Focus changed to: ${item.title} (v$version)")
        
        // IMMEDIATELY cancel all in-flight requests
        cancelAllRequests()
        
        // Cancel pending debounce
        updateJob?.cancel()
        
        // Debounce the actual load
        updateJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
            delay(DEBOUNCE_MS)
            
            // Double-check we're still the current request
            if (requestVersion.get() != version) {
                Log.d(TAG, "Debounce expired but version changed (v$version → v${requestVersion.get()})")
                return@launch
            }
            
            loadHeroContent(item, version)
        }
    }

    /**
     * Immediately updates hero without debounce.
     * Use for initial load or when user explicitly selects an item.
     */
    fun updateImmediate(item: ContentItem) {
        val version = requestVersion.incrementAndGet()
        cancelAllRequests()
        updateJob?.cancel()
        
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            loadHeroContent(item, version)
        }
    }

    /**
     * Clears all views and cancels pending operations.
     * Call in onDestroyView().
     */
    fun cleanup() {
        Log.d(TAG, "Cleanup called")
        cancelAllRequests()
        updateJob?.cancel()
        paletteJob?.cancel()
        ambientAnimator?.cancel()
    }

    private fun cancelAllRequests() {
        backdropTarget?.let { 
            try { Glide.with(fragment).clear(it) } catch (_: Exception) {}
        }
        logoTarget?.let { 
            try { Glide.with(fragment).clear(it) } catch (_: Exception) {}
        }
        paletteTarget?.let { 
            try { Glide.with(fragment).clear(it) } catch (_: Exception) {}
        }
        backdropTarget = null
        logoTarget = null
        paletteTarget = null
        paletteJob?.cancel()
    }

    private suspend fun loadHeroContent(item: ContentItem, version: Long) {
        // Update text immediately (no async needed)
        withContext(Dispatchers.Main.immediate) {
            if (requestVersion.get() != version) return@withContext
            
            titleView.text = item.title
            overviewView?.text = item.overview ?: ""
            metadataView?.let { HeroSectionHelper.updateHeroMetadata(it, item) }
            genresView?.let { HeroSectionHelper.updateGenres(it, item.genres) }
            castView?.let { HeroSectionHelper.updateCast(it, item.cast) }
            
            // Show title initially, hide logo until loaded
            titleView.visibility = View.VISIBLE
            logoView.visibility = View.GONE
            logoView.setImageDrawable(null)
        }

        // Load backdrop with version check
        loadBackdrop(item.backdropUrl ?: item.posterUrl, version)
        
        // Load logo with version check  
        loadLogo(item.logoUrl, version)
    }

    private fun loadBackdrop(url: String?, version: Long) {
        if (!fragment.isAdded) return
        
        if (url.isNullOrBlank()) {
            backdropView.setImageResource(R.drawable.default_background)
            animateAmbientToColor(DEFAULT_AMBIENT_COLOR)
            return
        }

        backdropTarget = object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                // VERSION CHECK: Ignore if we've moved on
                if (requestVersion.get() != version) {
                    Log.d(TAG, "DROPPED backdrop v$version (current=${requestVersion.get()})")
                    return
                }
                
                Log.d(TAG, "Applied backdrop v$version")
                crossfadeBackdrop(resource)
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                if (requestVersion.get() != version) return
                backdropView.setImageDrawable(placeholder)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                if (requestVersion.get() != version) return
                backdropView.setImageResource(R.drawable.default_background)
                animateAmbientToColor(DEFAULT_AMBIENT_COLOR)
            }
        }

        Glide.with(fragment)
            .load(url)
            .thumbnail(0.1f)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .override(1920, 1080)
            .into(backdropTarget!!)

        // Extract palette for ambient lighting
        loadPalette(url, version)
    }

    private fun loadPalette(url: String, version: Long) {
        if (!fragment.isAdded) return
        
        paletteTarget = object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                if (requestVersion.get() != version) return
                
                // Extract palette on background thread
                paletteJob?.cancel()
                paletteJob = fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                    val palette = Palette.from(resource).generate()
                    
                    // Check version again after palette generation
                    if (requestVersion.get() != version) return@launch
                    
                    val color = palette.vibrantSwatch?.rgb
                        ?: palette.darkVibrantSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                        ?: DEFAULT_AMBIENT_COLOR
                    
                    val deepColor = ColorUtils.blendARGB(color, Color.BLACK, 0.55f)
                    
                    withContext(Dispatchers.Main) {
                        if (requestVersion.get() == version) {
                            animateAmbientToColor(deepColor)
                        }
                    }
                }
            }

            override fun onLoadCleared(placeholder: Drawable?) = Unit
            
            override fun onLoadFailed(errorDrawable: Drawable?) {
                if (requestVersion.get() == version) {
                    animateAmbientToColor(DEFAULT_AMBIENT_COLOR)
                }
            }
        }

        Glide.with(fragment)
            .asBitmap()
            .load(url)
            .override(100, 100)  // Small for fast palette extraction
            .into(paletteTarget!!)
    }

    private fun loadLogo(url: String?, version: Long) {
        if (!fragment.isAdded) return
        
        if (url.isNullOrBlank()) {
            logoView.visibility = View.GONE
            titleView.visibility = View.VISIBLE
            return
        }

        logoTarget = object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                // VERSION CHECK
                if (requestVersion.get() != version) {
                    Log.d(TAG, "DROPPED logo v$version (current=${requestVersion.get()})")
                    return
                }
                
                Log.d(TAG, "Applied logo v$version")
                
                logoView.alpha = 0f
                logoView.setImageDrawable(resource)
                applyLogoBounds(resource)
                logoView.visibility = View.VISIBLE
                logoView.animate()
                    .alpha(1f)
                    .setDuration(LOGO_FADE_MS.toLong())
                    .start()
                titleView.visibility = View.GONE
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                if (requestVersion.get() != version) return
                logoView.visibility = View.GONE
                titleView.visibility = View.VISIBLE
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                if (requestVersion.get() != version) return
                logoView.visibility = View.GONE
                titleView.visibility = View.VISIBLE
            }
        }

        Glide.with(fragment)
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .override(600, 200)
            .into(logoTarget!!)
    }

    private fun crossfadeBackdrop(newDrawable: Drawable) {
        val current = backdropView.drawable
        if (current != null && current !== newDrawable) {
            val transition = TransitionDrawable(arrayOf(current, newDrawable))
            transition.isCrossFadeEnabled = true
            backdropView.setImageDrawable(transition)
            transition.startTransition(BACKDROP_CROSSFADE_MS)
        } else {
            backdropView.setImageDrawable(newDrawable)
        }
    }

    private fun applyLogoBounds(resource: Drawable) {
        val intrinsicWidth = resource.intrinsicWidth.takeIf { it > 0 } ?: return
        val intrinsicHeight = resource.intrinsicHeight.takeIf { it > 0 } ?: return

        val maxWidth = fragment.resources.getDimensionPixelSize(R.dimen.hero_logo_max_width)
        val maxHeight = fragment.resources.getDimensionPixelSize(R.dimen.hero_logo_max_height)
        val scale = minOf(
            maxWidth.toFloat() / intrinsicWidth,
            maxHeight.toFloat() / intrinsicHeight
        )

        logoView.layoutParams = logoView.layoutParams.apply {
            width = (intrinsicWidth * scale).toInt()
            height = (intrinsicHeight * scale).toInt()
        }
    }

    private fun animateAmbientToColor(targetColor: Int) {
        if (!fragment.isAdded) return
        if (currentAmbientColor == targetColor) return
        
        ambientAnimator?.cancel()
        
        val startColor = currentAmbientColor
        ambientAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = AMBIENT_ANIMATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val blended = argbEvaluator.evaluate(
                    animator.animatedFraction,
                    startColor,
                    targetColor
                ) as Int
                updateAmbientGradient(blended)
            }
            doOnEnd { currentAmbientColor = targetColor }
            start()
        }
    }

    private fun updateAmbientGradient(color: Int) {
        val width = fragment.resources.displayMetrics.widthPixels
        val height = fragment.resources.displayMetrics.heightPixels
        val radius = maxOf(width, height) * 0.95f

        val gradient = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = radius
            setGradientCenter(0.32f, 0.28f)
            colors = intArrayOf(
                ColorUtils.setAlphaComponent(color, 220),
                ColorUtils.setAlphaComponent(color, 120),
                ColorUtils.setAlphaComponent(color, 10)
            )
        }
        ambientOverlay.background = gradient
    }
}
```

- [ ] Create `HeroSectionController.kt`
- [ ] Add dimension resources for logo sizing

---

#### 3.2 Create Accent Color Cache

**File:** `ui/AccentColorCache.kt` (create new)

```kotlin
package com.test1.tv.ui

import android.graphics.Color
import android.util.LruCache
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches extracted accent colors to avoid repeated Palette extraction.
 * Shared across all adapters for efficiency.
 */
@Singleton
class AccentColorCache @Inject constructor() {
    
    companion object {
        private const val CACHE_SIZE = 200
        val DEFAULT_COLOR: Int = Color.WHITE
    }
    
    private val cache = LruCache<Int, Int>(CACHE_SIZE)
    
    /**
     * Gets cached color for a tmdbId.
     * @return Cached color or null if not cached
     */
    fun get(tmdbId: Int): Int? = cache.get(tmdbId)
    
    /**
     * Caches a color for a tmdbId.
     */
    fun put(tmdbId: Int, color: Int) {
        cache.put(tmdbId, color)
    }
    
    /**
     * Gets cached color or returns default.
     */
    fun getOrDefault(tmdbId: Int): Int = cache.get(tmdbId) ?: DEFAULT_COLOR
    
    /**
     * Clears the cache.
     */
    fun clear() {
        cache.evictAll()
    }
    
    /**
     * Returns cache statistics for debugging.
     */
    fun stats(): String = "AccentColorCache: ${cache.size()}/$CACHE_SIZE entries"
}
```

- [ ] Create `AccentColorCache.kt`

---

#### 3.3 Reduce Hero Debounce Timing

**File:** `ui/home/MainFragment.kt` (modify)

```kotlin
companion object {
    private const val TAG = "MainFragment"
    // CHANGED: 500ms → 150ms for snappier response
    private const val HERO_UPDATE_DEBOUNCE_MS = 150L
}
```

- [ ] Update `HERO_UPDATE_DEBOUNCE_MS` from 500 to 150

---

#### 3.4 Integrate Hero Controller

**File:** `ui/home/MainFragment.kt` (modify)

```kotlin
@AndroidEntryPoint
class MainFragment : Fragment() {
    
    // Add this field
    private lateinit var heroController: HeroSectionController
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize hero controller
        heroController = HeroSectionController(
            fragment = this,
            backdropView = binding.heroBackdrop,
            logoView = binding.heroLogo,
            titleView = binding.heroTitle,
            ambientOverlay = binding.ambientBackgroundOverlay,
            overviewView = binding.heroOverview,
            metadataView = binding.heroMetadata,
            genresView = binding.heroGenreText,
            castView = binding.heroCast
        )
        
        // ... rest of setup
    }
    
    private fun handleItemFocused(item: ContentItem, rowIndex: Int, itemIndex: Int) {
        // Replace existing implementation with:
        heroController.onItemFocused(item)
    }
    
    override fun onDestroyView() {
        heroController.cleanup()
        super.onDestroyView()
    }
}
```

- [ ] Add `heroController` field
- [ ] Initialize in `onViewCreated()`
- [ ] Update `handleItemFocused()` to use controller
- [ ] Add cleanup in `onDestroyView()`

---

#### 3.5 Verification Checkpoint

- [ ] Fast scroll through items - hero never shows wrong content
- [ ] Check logcat for "DROPPED" messages during fast scroll (confirms fix works)
- [ ] Hero updates feel responsive (~150ms after stopping)
- [ ] No crashes when rapidly navigating

---

## Phase 4: UI Runtime Performance

**Goal:** Eliminate frame drops by removing `notifyDataSetChanged` and reducing allocations.

**Estimated Time:** 4-5 hours

### Tasks

#### 4.1 Create DiffUtil Callbacks

**File:** `ui/adapter/DiffCallbacks.kt` (create new)

```kotlin
package com.test1.tv.ui.adapter

import androidx.recyclerview.widget.DiffUtil
import com.test1.tv.data.model.ContentItem

/**
 * DiffUtil callback for ContentItem.
 * Supports partial updates via payload mechanism.
 */
object ContentDiffCallback : DiffUtil.ItemCallback<ContentItem>() {
    
    override fun areItemsTheSame(oldItem: ContentItem, newItem: ContentItem): Boolean {
        return oldItem.tmdbId == newItem.tmdbId && oldItem.type == newItem.type
    }
    
    override fun areContentsTheSame(oldItem: ContentItem, newItem: ContentItem): Boolean {
        return oldItem == newItem
    }
    
    override fun getChangePayload(oldItem: ContentItem, newItem: ContentItem): Any? {
        val changes = mutableMapOf<String, Any?>()
        
        if (oldItem.posterUrl != newItem.posterUrl) {
            changes["posterUrl"] = newItem.posterUrl
        }
        if (oldItem.backdropUrl != newItem.backdropUrl) {
            changes["backdropUrl"] = newItem.backdropUrl
        }
        if (oldItem.watchProgress != newItem.watchProgress) {
            changes["watchProgress"] = newItem.watchProgress
        }
        if (oldItem.ratingPercentage != newItem.ratingPercentage) {
            changes["rating"] = newItem.ratingPercentage
        }
        
        return if (changes.isNotEmpty()) changes else null
    }
}

/**
 * DiffUtil callback for ContentRow.
 */
object RowDiffCallback : DiffUtil.ItemCallback<ContentRow>() {
    
    override fun areItemsTheSame(oldItem: ContentRow, newItem: ContentRow): Boolean {
        return oldItem.title == newItem.title
    }
    
    override fun areContentsTheSame(oldItem: ContentRow, newItem: ContentRow): Boolean {
        // Check if items list has same content
        return oldItem.items.size == newItem.items.size &&
               oldItem.items.zip(newItem.items).all { (old, new) ->
                   old.tmdbId == new.tmdbId
               }
    }
}
```

- [ ] Create `DiffCallbacks.kt`

---

#### 4.2 Refactor PosterAdapter to ListAdapter

**File:** `ui/adapter/PosterAdapter.kt` (major refactor)

```kotlin
package com.test1.tv.ui.adapter

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.test1.tv.R
import com.test1.tv.data.model.ContentItem
import com.test1.tv.ui.AccentColorCache
import kotlinx.coroutines.*
import kotlin.math.max

/**
 * High-performance poster adapter using ListAdapter with DiffUtil.
 * 
 * Performance features:
 * - Stable IDs for smooth item animations
 * - DiffUtil for minimal rebinds
 * - Async palette extraction with caching
 * - Proper Glide lifecycle management
 */
class PosterAdapter(
    private val presentation: RowPresentation = RowPresentation.PORTRAIT,
    private val accentColorCache: AccentColorCache,
    private val onItemClick: (ContentItem, ImageView) -> Unit,
    private val onItemFocused: (ContentItem, Int) -> Unit,
    private val onNavigateToNavBar: () -> Unit,
    private val onNearEnd: () -> Unit = {},
    private val onItemLongPressed: ((ContentItem) -> Unit)? = null
) : ListAdapter<ContentItem, PosterAdapter.ViewHolder>(ContentDiffCallback) {

    companion object {
        private const val NEAR_END_THRESHOLD = 10
        private const val FOCUS_SCALE = 1.08f
        private const val FOCUS_ELEVATION = 12f
        private const val ANIMATION_DURATION = 100L
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).tmdbId.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutRes = if (presentation == RowPresentation.LANDSCAPE_16_9) {
            R.layout.item_poster_landscape
        } else {
            R.layout.item_poster
        }
        val view = LayoutInflater.from(parent.context)
            .inflate(layoutRes, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            // Partial bind - only update changed fields
            @Suppress("UNCHECKED_CAST")
            val changes = payloads.firstOrNull() as? Map<String, Any?> ?: return
            holder.bindPartial(changes)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.recycle()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val posterImage: ImageView = itemView.findViewById(R.id.poster_image)
        private val focusOverlay: View = itemView.findViewById(R.id.focus_overlay)
        private val titleOverlay: TextView = itemView.findViewById(R.id.poster_title_overlay)
        private val cardContainer: CardView? = itemView.findViewById(R.id.poster_card)
        private val watchedBadge: ImageView? = itemView.findViewById(R.id.watched_badge)
        
        private var currentItem: ContentItem? = null
        private var glideTarget: CustomTarget<Drawable>? = null
        private var paletteJob: Job? = null

        init {
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true
            setupFocusListener()
            setupClickListeners()
        }

        fun bind(item: ContentItem, position: Int) {
            currentItem = item
            titleOverlay.text = item.title
            
            // Reset state
            focusOverlay.visibility = View.INVISIBLE
            itemView.scaleX = 1f
            itemView.scaleY = 1f
            itemView.translationZ = 0f
            
            // Watch progress badge
            val isWatched = (item.watchProgress ?: 0.0) >= 0.9
            watchedBadge?.visibility = if (isWatched) View.VISIBLE else View.GONE
            
            loadImage(item)
            
            // Prefetch trigger
            if (position >= itemCount - NEAR_END_THRESHOLD) {
                onNearEnd()
            }
        }

        fun bindPartial(changes: Map<String, Any?>) {
            changes["posterUrl"]?.let { 
                currentItem?.let { item -> loadImage(item) }
            }
            changes["watchProgress"]?.let { progress ->
                val isWatched = ((progress as? Double) ?: 0.0) >= 0.9
                watchedBadge?.visibility = if (isWatched) View.VISIBLE else View.GONE
            }
        }

        fun recycle() {
            // CRITICAL: Cancel in-flight requests
            glideTarget?.let { 
                try { Glide.with(itemView).clear(it) } catch (_: Exception) {}
            }
            glideTarget = null
            paletteJob?.cancel()
            paletteJob = null
            posterImage.setImageDrawable(null)
            currentItem = null
        }

        private fun loadImage(item: ContentItem) {
            val artworkUrl = if (presentation == RowPresentation.LANDSCAPE_16_9) {
                item.backdropUrl ?: item.posterUrl
            } else {
                item.posterUrl
            }

            if (artworkUrl.isNullOrBlank()) {
                posterImage.setImageResource(R.drawable.default_background)
                titleOverlay.visibility = View.VISIBLE
                return
            }

            // Cancel previous request
            glideTarget?.let { 
                try { Glide.with(itemView).clear(it) } catch (_: Exception) {}
            }

            glideTarget = object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    posterImage.setImageDrawable(resource)
                    titleOverlay.visibility = View.GONE
                    
                    // Extract accent color ASYNC if not cached
                    if (accentColorCache.get(item.tmdbId) == null) {
                        extractAccentColorAsync(item.tmdbId, resource)
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    posterImage.setImageDrawable(placeholder)
                    titleOverlay.visibility = View.VISIBLE
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    posterImage.setImageDrawable(errorDrawable)
                    titleOverlay.visibility = View.VISIBLE
                }
            }

            Glide.with(itemView)
                .load(artworkUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(
                    if (presentation == RowPresentation.LANDSCAPE_16_9) 400 else 240,
                    if (presentation == RowPresentation.LANDSCAPE_16_9) 225 else 360
                )
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .placeholder(R.drawable.default_background)
                .error(R.drawable.default_background)
                .into(glideTarget!!)
        }

        private fun extractAccentColorAsync(tmdbId: Int, drawable: Drawable) {
            paletteJob?.cancel()
            paletteJob = CoroutineScope(Dispatchers.Default).launch {
                try {
                    val bitmap = drawable.toBitmap(50, 75)
                    val palette = Palette.from(bitmap).generate()
                    val color = palette.vibrantSwatch?.rgb
                        ?: palette.darkVibrantSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                        ?: Color.WHITE
                    
                    accentColorCache.put(tmdbId, color)
                    
                    // Update focus overlay if currently focused
                    if (itemView.isFocused) {
                        withContext(Dispatchers.Main) {
                            applyFocusOverlay(true, color)
                        }
                    }
                } catch (_: Exception) {
                    // Ignore extraction failures
                }
            }
        }

        private fun setupFocusListener() {
            itemView.setOnFocusChangeListener { _, hasFocus ->
                val item = currentItem ?: return@setOnFocusChangeListener
                
                // Smooth scale animation
                itemView.animate()
                    .scaleX(if (hasFocus) FOCUS_SCALE else 1f)
                    .scaleY(if (hasFocus) FOCUS_SCALE else 1f)
                    .translationZ(if (hasFocus) FOCUS_ELEVATION else 0f)
                    .setDuration(ANIMATION_DURATION)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()

                // Focus overlay with cached accent color
                val accentColor = accentColorCache.getOrDefault(item.tmdbId)
                applyFocusOverlay(hasFocus, accentColor)

                if (hasFocus) {
                    itemView.playSoundEffect(SoundEffectConstants.CLICK)
                    onItemFocused(item, bindingAdapterPosition)
                }
            }
        }

        private fun applyFocusOverlay(hasFocus: Boolean, accentColor: Int) {
            if (hasFocus) {
                val strokeColor = ColorUtils.blendARGB(accentColor, Color.WHITE, 0.65f)
                val radius = itemView.resources.getDimension(
                    if (presentation == RowPresentation.LANDSCAPE_16_9) 
                        R.dimen.radius_card_landscape 
                    else 
                        R.dimen.radius_card
                )
                
                focusOverlay.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = radius
                    setStroke(
                        itemView.resources.getDimensionPixelSize(R.dimen.focus_border_width),
                        strokeColor
                    )
                    setColor(Color.TRANSPARENT)
                }
                focusOverlay.visibility = View.VISIBLE
            } else {
                focusOverlay.visibility = View.INVISIBLE
            }
        }

        private fun setupClickListeners() {
            itemView.setOnClickListener {
                currentItem?.let { onItemClick(it, posterImage) }
            }

            itemView.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    bindingAdapterPosition == 0
                ) {
                    onNavigateToNavBar()
                    true
                } else {
                    false
                }
            }

            onItemLongPressed?.let { handler ->
                itemView.isLongClickable = true
                itemView.setOnLongClickListener {
                    currentItem?.let { handler(it) }
                    true
                }
            }
        }
    }
}
```

- [ ] Refactor `PosterAdapter` to extend `ListAdapter`
- [ ] Add `setHasStableIds(true)`
- [ ] Implement `getItemId()`
- [ ] Add `onViewRecycled()` with Glide cleanup
- [ ] Inject `AccentColorCache`

---

#### 4.3 Refactor ContentRowAdapter with Shared Pool

**File:** `ui/adapter/ContentRowAdapter.kt` (modify)

```kotlin
class ContentRowAdapter(
    initialRows: List<ContentRow>,
    private val viewPool: RecyclerView.RecycledViewPool,  // ADD THIS
    private val accentColorCache: AccentColorCache,        // ADD THIS
    private val onItemClick: (ContentItem, ImageView) -> Unit,
    private val onItemFocused: (ContentItem, Int, Int) -> Unit,
    private val onNavigateToNavBar: () -> Unit,
    private val onItemLongPress: (ContentItem) -> Unit,
    private val onRequestMore: (Int) -> Unit
) : ListAdapter<ContentRow, ContentRowAdapter.RowViewHolder>(RowDiffCallback) {

    // ... existing code ...

    inner class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rowTitle: TextView = itemView.findViewById(R.id.row_title)
        private val rowContent: HorizontalGridView = itemView.findViewById(R.id.row_content)

        fun bind(row: ContentRow, rowIndex: Int) {
            rowTitle.text = row.title
            
            // CRITICAL: Use shared view pool
            rowContent.setRecycledViewPool(viewPool)
            
            // CRITICAL: Disable item animator for instant focus
            rowContent.itemAnimator = null

            var adapter = rowAdapters.get(rowIndex)
            if (adapter == null) {
                adapter = PosterAdapter(
                    presentation = row.presentation,
                    accentColorCache = accentColorCache,
                    onItemClick = onItemClick,
                    onItemFocused = { item, itemIndex ->
                        onItemFocused(item, rowIndex, itemIndex)
                    },
                    onNavigateToNavBar = onNavigateToNavBar,
                    onItemLongPressed = onItemLongPress,
                    onNearEnd = { onRequestMore(rowIndex) }
                )
                rowAdapters.put(rowIndex, adapter)
            }

            if (rowContent.adapter !== adapter) {
                rowContent.adapter = adapter
                // ... rest of setup
            }

            // Use submitList instead of replaceAll
            adapter.submitList(row.items.toList())
        }
    }
}
```

- [ ] Add `viewPool` parameter to constructor
- [ ] Add `accentColorCache` parameter
- [ ] Set recycled view pool on each row
- [ ] Set `itemAnimator = null`
- [ ] Change `replaceAll()` to `submitList()`

---

#### 4.4 Disable Item Animator on Main Grid

**File:** `ui/home/MainFragment.kt` (modify)

```kotlin
private fun setupContentRows() {
    binding.contentRows.apply {
        adapter = contentRowAdapter
        itemAnimator = null  // ADD THIS LINE
        // ... rest of setup
    }
}
```

- [ ] Add `itemAnimator = null` to VerticalGridView

---

#### 4.5 Implement Row Prefetching

**File:** `ui/home/RowPrefetchManager.kt` (create new)

```kotlin
package com.test1.tv.ui.home

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.test1.tv.ui.adapter.ContentRow
import com.test1.tv.ui.adapter.RowPresentation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prefetches images for adjacent rows to eliminate loading delays.
 * When user focuses a row, we preload images for rows above and below.
 */
@Singleton
class RowPrefetchManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefetchedRows = mutableSetOf<Int>()
    
    /**
     * Call when a row receives focus.
     * Prefetches images for adjacent rows.
     */
    fun onRowFocused(currentRowIndex: Int, allRows: List<ContentRow>) {
        // Prefetch next row
        prefetchRowIfNeeded(currentRowIndex + 1, allRows)
        
        // Prefetch previous row
        prefetchRowIfNeeded(currentRowIndex - 1, allRows)
    }
    
    private fun prefetchRowIfNeeded(rowIndex: Int, allRows: List<ContentRow>) {
        if (rowIndex !in allRows.indices) return
        if (rowIndex in prefetchedRows) return
        
        prefetchedRows.add(rowIndex)
        val row = allRows[rowIndex]
        
        // Prefetch first 8 items in the row
        row.items.take(8).forEach { item ->
            val url = if (row.presentation == RowPresentation.LANDSCAPE_16_9) {
                item.backdropUrl ?: item.posterUrl
            } else {
                item.posterUrl
            }
            
            url?.let {
                Glide.with(context)
                    .load(it)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .preload(
                        if (row.presentation == RowPresentation.LANDSCAPE_16_9) 400 else 240,
                        if (row.presentation == RowPresentation.LANDSCAPE_16_9) 225 else 360
                    )
            }
        }
    }
    
    /**
     * Clears prefetch state. Call when content changes.
     */
    fun clearPrefetchState() {
        prefetchedRows.clear()
    }
}
```

- [ ] Create `RowPrefetchManager.kt`
- [ ] Inject into MainFragment
- [ ] Call `onRowFocused()` when row focus changes

---

#### 4.6 Verification Checkpoint

- [ ] Systrace shows no dropped frames during scrolling
- [ ] `notifyDataSetChanged()` is no longer called
- [ ] View pool is being shared (check with debugger)
- [ ] Images load instantly when scrolling to prefetched rows

---

## Phase 5: Hardware Acceleration

**Goal:** Move animations off the UI thread to prevent "stuck" focus states.

**Estimated Time:** 1-2 hours

### Tasks

#### 5.1 Create StateListAnimator for Focus

**File:** `res/animator/scale_on_focus.xml` (create new)

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- Focused state -->
    <item android:state_focused="true">
        <set android:ordering="together">
            <objectAnimator
                android:propertyName="scaleX"
                android:valueTo="1.08"
                android:duration="100"
                android:interpolator="@android:interpolator/fast_out_slow_in" />
            <objectAnimator
                android:propertyName="scaleY"
                android:valueTo="1.08"
                android:duration="100"
                android:interpolator="@android:interpolator/fast_out_slow_in" />
            <objectAnimator
                android:propertyName="translationZ"
                android:valueTo="12dp"
                android:duration="100"
                android:interpolator="@android:interpolator/fast_out_slow_in" />
        </set>
    </item>
    
    <!-- Unfocused state -->
    <item android:state_focused="false">
        <set android:ordering="together">
            <objectAnimator
                android:propertyName="scaleX"
                android:valueTo="1.0"
                android:duration="80"
                android:interpolator="@android:interpolator/fast_out_slow_in" />
            <objectAnimator
                android:propertyName="scaleY"
                android:valueTo="1.0"
                android:duration="80"
                android:interpolator="@android:interpolator/fast_out_slow_in" />
            <objectAnimator
                android:propertyName="translationZ"
                android:valueTo="0dp"
                android:duration="80"
                android:interpolator="@android:interpolator/fast_out_slow_in" />
        </set>
    </item>
    
</selector>
```

- [ ] Create `res/animator/` directory
- [ ] Create `scale_on_focus.xml`

---

#### 5.2 Apply StateListAnimator to Poster Layouts

**File:** `res/layout/item_poster.xml` (modify)

```xml
<androidx.cardview.widget.CardView
    android:id="@+id/poster_card"
    android:layout_width="@dimen/poster_width_portrait"
    android:layout_height="@dimen/poster_height_portrait"
    android:focusable="true"
    android:focusableInTouchMode="true"
    android:stateListAnimator="@animator/scale_on_focus"
    app:cardCornerRadius="@dimen/radius_card"
    app:cardElevation="4dp">
    
    <!-- ... rest of layout ... -->
    
</androidx.cardview.widget.CardView>
```

**File:** `res/layout/item_poster_landscape.xml` (modify)

```xml
<androidx.cardview.widget.CardView
    android:id="@+id/poster_card"
    android:layout_width="@dimen/poster_width_landscape"
    android:layout_height="@dimen/poster_height_landscape"
    android:focusable="true"
    android:focusableInTouchMode="true"
    android:stateListAnimator="@animator/scale_on_focus"
    app:cardCornerRadius="@dimen/radius_card_landscape"
    app:cardElevation="4dp">
    
    <!-- ... rest of layout ... -->
    
</androidx.cardview.widget.CardView>
```

- [ ] Add `stateListAnimator` to `item_poster.xml`
- [ ] Add `stateListAnimator` to `item_poster_landscape.xml`

---

#### 5.3 Remove Kotlin-Based Scale Animations

**File:** `ui/adapter/PosterAdapter.kt` (modify)

Remove or comment out the programmatic animation code in `setupFocusListener()`:

```kotlin
private fun setupFocusListener() {
    itemView.setOnFocusChangeListener { _, hasFocus ->
        val item = currentItem ?: return@setOnFocusChangeListener
        
        // REMOVE THESE LINES - now handled by StateListAnimator
        // itemView.animate()
        //     .scaleX(if (hasFocus) FOCUS_SCALE else 1f)
        //     .scaleY(if (hasFocus) FOCUS_SCALE else 1f)
        //     .translationZ(if (hasFocus) FOCUS_ELEVATION else 0f)
        //     .setDuration(ANIMATION_DURATION)
        //     .setInterpolator(FastOutSlowInInterpolator())
        //     .start()

        // Keep focus overlay logic
        val accentColor = accentColorCache.getOrDefault(item.tmdbId)
        applyFocusOverlay(hasFocus, accentColor)

        if (hasFocus) {
            itemView.playSoundEffect(SoundEffectConstants.CLICK)
            onItemFocused(item, bindingAdapterPosition)
        }
    }
}
```

- [ ] Remove `itemView.animate()` calls from focus listener
- [ ] Keep `applyFocusOverlay()` logic (for border color)

---

#### 5.4 Verification Checkpoint

- [ ] Focus animations are smooth (GPU-accelerated)
- [ ] No animation conflicts between XML and Kotlin
- [ ] Animations don't "stick" during fast navigation

---

## Phase 6: Cleanup & Polish

**Goal:** Remove deprecated code and finalize optimizations.

**Estimated Time:** 1-2 hours

### Tasks

#### 6.1 Replace ScrollThrottler with SmartScrollThrottler

**File:** `ui/SmartScrollThrottler.kt` (create new, replaces `ScrollThrottler.kt`)

```kotlin
package com.test1.tv.ui

import android.os.SystemClock
import android.view.KeyEvent
import androidx.leanback.widget.BaseGridView

/**
 * Intelligent scroll throttler that:
 * - Allows initial keypresses through immediately
 * - Only throttles repeated rapid presses
 * - Respects TV remote's natural repeat rate
 */
class SmartScrollThrottler(
    private val initialDelayMs: Long = 0L,
    private val repeatDelayMs: Long = 80L
) : BaseGridView.OnKeyInterceptListener {

    private var lastEventTime: Long = 0L
    private var isHolding: Boolean = false

    override fun onInterceptKeyEvent(event: KeyEvent?): Boolean {
        event ?: return false
        
        val isNavigationKey = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> true
            else -> false
        }
        
        if (!isNavigationKey) return false

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                val now = SystemClock.uptimeMillis()
                val elapsed = now - lastEventTime
                
                // First press or after pause - let it through immediately
                if (!isHolding || elapsed > 300) {
                    isHolding = true
                    lastEventTime = now
                    false  // Don't intercept
                } else if (elapsed >= repeatDelayMs) {
                    // Holding and enough time passed
                    lastEventTime = now
                    false
                } else {
                    // Too fast - intercept (block)
                    true
                }
            }
            KeyEvent.ACTION_UP -> {
                isHolding = false
                false
            }
            else -> false
        }
    }
}
```

- [ ] Create `SmartScrollThrottler.kt`
- [ ] Replace all uses of `ScrollThrottler` with `SmartScrollThrottler`
- [ ] Delete old `ScrollThrottler.kt`

---

#### 6.2 Update RowScrollPauser

**File:** `ui/SmartRowScrollManager.kt` (create new, replaces `RowScrollPauser.kt`)

```kotlin
package com.test1.tv.ui

import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.test1.tv.R
import kotlin.math.abs

/**
 * Smarter scroll listener that only pauses Glide during very fast scrolling.
 * Normal D-pad navigation doesn't trigger pause.
 */
object SmartRowScrollManager {
    
    fun attach(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var wasFastScrolling = false
            
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (wasFastScrolling) {
                            try {
                                Glide.with(recyclerView).resumeRequests()
                            } catch (_: Exception) {}
                            wasFastScrolling = false
                        }
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Only pause if scrolling very fast (3+ items per frame)
                val threshold = recyclerView.resources
                    .getDimensionPixelSize(R.dimen.poster_width_portrait) * 3
                    
                if (abs(dx) > threshold || abs(dy) > threshold) {
                    if (!wasFastScrolling) {
                        try {
                            Glide.with(recyclerView).pauseRequests()
                        } catch (_: Exception) {}
                        wasFastScrolling = true
                    }
                }
            }
        })

        // Reduce fling velocity (safety for TV)
        if (recyclerView.onFlingListener == null) {
            recyclerView.onFlingListener = object : RecyclerView.OnFlingListener() {
                override fun onFling(velocityX: Int, velocityY: Int): Boolean {
                    return recyclerView.fling(
                        (velocityX * 0.2f).toInt(),
                        (velocityY * 0.2f).toInt()
                    )
                }
            }
        }
    }
}
```

- [ ] Create `SmartRowScrollManager.kt`
- [ ] Replace `RowScrollPauser.attach()` with `SmartRowScrollManager.attach()`
- [ ] Delete old `RowScrollPauser.kt`

---

#### 6.3 Delete ContentRepositoryProvider

**File:** `data/repository/ContentRepositoryProvider.kt` (delete)

- [ ] Delete `ContentRepositoryProvider.kt`
- [ ] Remove all references (should be replaced by Hilt injection)

---

#### 6.4 Remove Manual notifyDataSetChanged Calls

Search codebase for `notifyDataSetChanged` and replace with proper DiffUtil usage:

- [ ] Search: `notifyDataSetChanged`
- [ ] Replace with `submitList()` or `notifyItemRangeChanged()` as appropriate

---

#### 6.5 Final Cleanup

- [ ] Remove unused imports
- [ ] Run Android Lint
- [ ] Format code with ktlint
- [ ] Update ProGuard rules if needed

---

## Phase 7: Testing & Validation

**Goal:** Verify all fixes work correctly and performance targets are met.

**Estimated Time:** 2-3 hours

### Tasks

#### 7.1 Manual Testing Checklist

**Navigation Smoothness:**
- [ ] Fast scroll through 5+ rows - no hero desync
- [ ] Hold D-pad right for 10 seconds - no jank
- [ ] Navigate up/down between rows - instant focus
- [ ] Scroll to end of row and back - smooth throughout

**Hero Section:**
- [ ] Logo always matches focused poster
- [ ] Backdrop always matches focused poster
- [ ] Ambient color changes smoothly
- [ ] Text updates before images (perceived speed)

**Performance:**
- [ ] Launch time < 2 seconds to first content
- [ ] No visible loading spinners after cache warm
- [ ] Memory usage stable over time (no leaks)

**Offline Behavior:**
- [ ] App shows cached content when offline
- [ ] Error message when network unavailable
- [ ] App doesn't crash on network error

---

#### 7.2 Automated Tests

**Unit Tests to Add:**

- [ ] `RateLimiterTest.kt` - Token bucket behavior
- [ ] `MediaRepositoryTest.kt` - Offline-first flow
- [ ] `ContentDiffCallbackTest.kt` - DiffUtil correctness
- [ ] `HeroSectionControllerTest.kt` - Version checking

**UI Tests to Add:**

- [ ] `NavigationTest.kt` - D-pad navigation flow
- [ ] `HeroSyncTest.kt` - Fast scroll doesn't desync

---

#### 7.3 Performance Profiling

**Tools:**
- [ ] Android Studio Profiler - CPU/Memory
- [ ] Systrace - Frame timing
- [ ] LeakCanary - Memory leaks

**Metrics to Capture:**

| Metric | Target | Actual |
|--------|--------|--------|
| Scroll FPS | 60fps | |
| Hero update time | <150ms | |
| Cold launch | <2s | |
| Memory (steady state) | <200MB | |
| Dropped frames (30s scroll) | 0 | |

---

#### 7.4 Debug Logging

Add this to verify hero sync fix is working:

```kotlin
// In HeroSectionController
private fun loadBackdrop(url: String?, version: Long) {
    Log.d(TAG, "Loading backdrop v$version: $url")
    
    // ... in onResourceReady:
    if (requestVersion.get() != version) {
        Log.w(TAG, "DROPPED backdrop v$version (current=${requestVersion.get()})")
        return
    }
    Log.d(TAG, "APPLIED backdrop v$version")
}
```

- [ ] Add debug logging
- [ ] Verify "DROPPED" messages appear during fast scroll
- [ ] Remove debug logging before release

---

## Appendix: Code Templates

### Dimension Resources

**File:** `res/values/dimens.xml` (add if missing)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Poster dimensions -->
    <dimen name="poster_width_portrait">160dp</dimen>
    <dimen name="poster_height_portrait">240dp</dimen>
    <dimen name="poster_width_landscape">280dp</dimen>
    <dimen name="poster_height_landscape">158dp</dimen>
    
    <!-- Card radii -->
    <dimen name="radius_card">12dp</dimen>
    <dimen name="radius_card_landscape">8dp</dimen>
    
    <!-- Focus -->
    <dimen name="focus_border_width">3dp</dimen>
    <dimen name="focus_elevation">12dp</dimen>
    
    <!-- Hero -->
    <dimen name="hero_logo_max_width">400dp</dimen>
    <dimen name="hero_logo_max_height">150dp</dimen>
    
    <!-- Row heights -->
    <dimen name="row_height_portrait">280dp</dimen>
    <dimen name="row_height_landscape">200dp</dimen>
</resources>
```

---

### Gradle Dependencies (Complete)

```kotlin
dependencies {
    // Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    
    // Palette
    implementation("androidx.palette:palette-ktx:1.0.0")
    
    // Leanback (TV)
    implementation("androidx.leanback:leanback:1.0.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    
    // Debug
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")
}
```

---

## Quick Reference: File Changes Summary

| File | Action | Phase |
|------|--------|-------|
| `app/build.gradle.kts` | Modify | 1.1 |
| `Test1App.kt` | Modify | 1.2 |
| `di/AppModule.kt` | Create | 1.3 |
| `di/DispatchersModule.kt` | Create | 1.4 |
| `data/remote/RateLimiter.kt` | Create | 2.1 |
| `data/Resource.kt` | Create | 2.2 |
| `data/local/MediaEntities.kt` | Create | 2.3 |
| `data/local/MediaDao.kt` | Create | 2.4 |
| `data/repository/MediaRepository.kt` | Create | 2.5 |
| `ui/home/HeroSectionController.kt` | Create | 3.1 |
| `ui/AccentColorCache.kt` | Create | 3.2 |
| `ui/home/MainFragment.kt` | Modify | 3.3, 3.4 |
| `ui/adapter/DiffCallbacks.kt` | Create | 4.1 |
| `ui/adapter/PosterAdapter.kt` | Refactor | 4.2, 5.3 |
| `ui/adapter/ContentRowAdapter.kt` | Modify | 4.3 |
| `ui/home/RowPrefetchManager.kt` | Create | 4.5 |
| `res/animator/scale_on_focus.xml` | Create | 5.1 |
| `res/layout/item_poster.xml` | Modify | 5.2 |
| `res/layout/item_poster_landscape.xml` | Modify | 5.2 |
| `ui/SmartScrollThrottler.kt` | Create | 6.1 |
| `ui/SmartRowScrollManager.kt` | Create | 6.2 |
| `ScrollThrottler.kt` | Delete | 6.1 |
| `RowScrollPauser.kt` | Delete | 6.2 |
| `ContentRepositoryProvider.kt` | Delete | 6.3 |

---

## Progress Tracker

| Phase | Status | Notes |
|-------|--------|-------|
| Phase 1: DI Setup | ⬜ Not Started | |
| Phase 2: Data Layer | ⬜ Not Started | |
| Phase 3: Hero Sync | ⬜ Not Started | |
| Phase 4: UI Performance | ⬜ Not Started | |
| Phase 5: Hardware Accel | ⬜ Not Started | |
| Phase 6: Cleanup | ⬜ Not Started | |
| Phase 7: Testing | ⬜ Not Started | |

**Legend:** ⬜ Not Started | 🔄 In Progress | ✅ Complete | ❌ Blocked

---

*Document Version: 1.0*  
*Last Updated: November 2025*  
*Target Completion: 2-3 weeks*
