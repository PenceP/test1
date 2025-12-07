package com.test1.tv.data.repository

import com.test1.tv.data.local.dao.TraktUserItemDao
import com.test1.tv.data.model.ContentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides fast, cached lookups for Trakt item status (watched, collection, watchlist).
 * Uses in-memory caching for instant UI responses.
 */
@Singleton
class TraktStatusProvider @Inject constructor(
    private val userItemDao: TraktUserItemDao,
    private val accountRepository: TraktAccountRepository,
    private val watchStatusRepository: WatchStatusRepository
) {
    companion object {
        // LIST_HISTORY removed - watched status now uses WatchStatusRepository as single source of truth
        private const val LIST_COLLECTION = "COLLECTION"
        private const val LIST_WATCHLIST = "WATCHLIST"
    }

    // In-memory caches for instant lookups (collection and watchlist only)
    // Watched status is handled by WatchStatusRepository as the single source of truth
    private val collectionCache = ConcurrentHashMap<Int, Boolean>()
    private val watchlistCache = ConcurrentHashMap<Int, Boolean>()

    private val cacheMutex = Mutex()
    private var cacheInitialized = false

    /**
     * Check if user is authenticated with Trakt
     */
    suspend fun isAuthenticated(): Boolean {
        return accountRepository.getAccount() != null
    }

    /**
     * Check if an item is in the watched history.
     * Uses WatchStatusRepository as the single source of truth for watched status.
     */
    suspend fun isWatched(tmdbId: Int, type: ContentItem.ContentType): Boolean {
        // WatchStatusRepository is the single source of truth (progress >= 90% = watched)
        val progress = watchStatusRepository.getProgress(tmdbId, type)
        return progress != null && progress >= 0.9
    }

    /**
     * Check if an item is in the user's collection
     */
    suspend fun isInCollection(tmdbId: Int, type: ContentItem.ContentType): Boolean {
        ensureCacheLoaded()
        return collectionCache[tmdbId] ?: false
    }

    /**
     * Check if an item is in the user's watchlist
     */
    suspend fun isInWatchlist(tmdbId: Int, type: ContentItem.ContentType): Boolean {
        ensureCacheLoaded()
        return watchlistCache[tmdbId] ?: false
    }

    /**
     * Mark an item as watched in the cache (call after successful API)
     * @deprecated WatchStatusRepository is now the single source of truth.
     * TraktSyncRepository updates it directly. This method is kept for backward compatibility.
     */
    fun markWatched(tmdbId: Int) {
        // No-op: WatchStatusRepository is updated by TraktSyncRepository and WatchedBadgeManager
    }

    /**
     * Mark an item as unwatched in the cache (call after successful API)
     * @deprecated WatchStatusRepository is now the single source of truth.
     * TraktSyncRepository updates it directly. This method is kept for backward compatibility.
     */
    fun markUnwatched(tmdbId: Int) {
        // No-op: WatchStatusRepository is updated by TraktSyncRepository and WatchedBadgeManager
    }

    /**
     * Add an item to collection in the cache (call after successful API)
     */
    fun addToCollection(tmdbId: Int) {
        collectionCache[tmdbId] = true
    }

    /**
     * Remove an item from collection in the cache (call after successful API)
     */
    fun removeFromCollection(tmdbId: Int) {
        collectionCache.remove(tmdbId)
    }

    /**
     * Add an item to watchlist in the cache (call after successful API)
     */
    fun addToWatchlist(tmdbId: Int) {
        watchlistCache[tmdbId] = true
    }

    /**
     * Remove an item from watchlist in the cache (call after successful API)
     */
    fun removeFromWatchlist(tmdbId: Int) {
        watchlistCache.remove(tmdbId)
    }

    /**
     * Get all status for an item at once (more efficient for context menu)
     */
    suspend fun getItemStatus(tmdbId: Int, type: ContentItem.ContentType): ItemStatus {
        ensureCacheLoaded()
        // WatchStatusRepository is the single source of truth for watched status
        val progress = watchStatusRepository.getProgress(tmdbId, type)
        val isWatched = progress != null && progress >= 0.9

        return ItemStatus(
            isWatched = isWatched,
            isInCollection = collectionCache[tmdbId] ?: false,
            isInWatchlist = watchlistCache[tmdbId] ?: false
        )
    }

    /**
     * Refresh the cache from database
     */
    suspend fun refreshCache() = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            loadCacheFromDatabase()
        }
    }

    /**
     * Clear all caches (call on logout)
     */
    fun clearCache() {
        collectionCache.clear()
        watchlistCache.clear()
        cacheInitialized = false
        // Note: WatchStatusRepository should be cleared separately if needed
    }

    private suspend fun ensureCacheLoaded() {
        if (!cacheInitialized) {
            cacheMutex.withLock {
                if (!cacheInitialized) {
                    loadCacheFromDatabase()
                }
            }
        }
    }

    private suspend fun loadCacheFromDatabase() = withContext(Dispatchers.IO) {
        // Load all tmdbIds for collection and watchlist only
        // Watched status is handled by WatchStatusRepository (single source of truth)
        val collectionIds = userItemDao.getAllTmdbIdsInList(LIST_COLLECTION)
        val watchlistIds = userItemDao.getAllTmdbIdsInList(LIST_WATCHLIST)

        collectionCache.clear()
        collectionIds.forEach { collectionCache[it] = true }

        watchlistCache.clear()
        watchlistIds.forEach { watchlistCache[it] = true }

        cacheInitialized = true
    }

    data class ItemStatus(
        val isWatched: Boolean,
        val isInCollection: Boolean,
        val isInWatchlist: Boolean
    )
}
