package com.test1.tv.data.repository

import com.test1.tv.data.local.dao.CachedContentDao
import com.test1.tv.data.local.entity.CachedContent
import com.test1.tv.data.model.ContentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class CacheRepository(private val cachedContentDao: CachedContentDao) {

    companion object {
        // Cache TTL for different categories (in milliseconds)
        private val TRENDING_TTL = TimeUnit.HOURS.toMillis(6) // 6 hours for trending
        private val POPULAR_TTL = TimeUnit.HOURS.toMillis(24) // 24 hours for popular
        private val DETAILS_TTL = TimeUnit.DAYS.toMillis(7) // 7 days for details
        private val MAX_CACHE_AGE = TimeUnit.DAYS.toMillis(30) // Clean up after 30 days
    }

    /**
     * Check if cached data for a category is still fresh
     */
    suspend fun isCacheFresh(category: String): Boolean = withContext(Dispatchers.IO) {
        val timestamp = cachedContentDao.getCategoryTimestamp(category)
        if (timestamp == null) return@withContext false

        val age = System.currentTimeMillis() - timestamp
        val ttl = getTTLForCategory(category)

        age < ttl
    }

    /**
     * Get cached content for a category
     */
    suspend fun getCachedContent(category: String): List<ContentItem>? = withContext(Dispatchers.IO) {
        val cached = cachedContentDao.getContentByCategory(category)
        if (cached.isEmpty()) return@withContext null

        cached.map { it.toContentItem() }
    }

    /**
     * Cache content for a category
     */
    suspend fun cacheContent(
        category: String,
        content: List<ContentItem>
    ) = withContext(Dispatchers.IO) {
        val cachedEntities = content.mapIndexed { index, item ->
            CachedContent.fromContentItem(item, category, index)
        }
        cachedContentDao.replaceCategoryContent(category, cachedEntities)
    }

    /**
     * Clean up old cached content based on MAX_CACHE_AGE
     */
    suspend fun cleanupOldCache() = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - MAX_CACHE_AGE
        cachedContentDao.deleteOldContent(cutoffTime)
    }

    /**
     * Clear cache for a specific category
     */
    suspend fun clearCategoryCache(category: String) = withContext(Dispatchers.IO) {
        cachedContentDao.deleteCategoryContent(category)
    }

    /**
     * Get TTL based on category type
     */
    private fun getTTLForCategory(category: String): Long {
        return when {
            category.startsWith("TRENDING") -> TRENDING_TTL
            category.startsWith("POPULAR") -> POPULAR_TTL
            else -> DETAILS_TTL
        }
    }

    /**
     * Get cache age for a category (in milliseconds)
     */
    suspend fun getCacheAge(category: String): Long? = withContext(Dispatchers.IO) {
        val timestamp = cachedContentDao.getCategoryTimestamp(category)
        timestamp?.let { System.currentTimeMillis() - it }
    }
}
