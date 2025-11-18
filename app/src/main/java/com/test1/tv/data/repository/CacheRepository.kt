package com.test1.tv.data.repository

import com.test1.tv.data.local.dao.CachedContentDao
import com.test1.tv.data.local.entity.CachedContent
import com.test1.tv.data.model.ContentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class CacheRepository(private val cachedContentDao: CachedContentDao) {

    companion object {
        private val LIST_TTL = TimeUnit.HOURS.toMillis(24)
        private val DETAILS_TTL = TimeUnit.DAYS.toMillis(7)
        private val MAX_CACHE_AGE = TimeUnit.DAYS.toMillis(30)
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
            category.startsWith("TRENDING") -> LIST_TTL
            category.startsWith("POPULAR") -> LIST_TTL
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

    suspend fun getCachedContent(category: String): List<ContentItem>? = withContext(Dispatchers.IO) {
        val cached = cachedContentDao.getContentByCategory(category)
        if (cached.isEmpty()) return@withContext null

        cached.map { it.toContentItem() }
    }

    suspend fun getCachedPage(
        category: String,
        page: Int,
        pageSize: Int
    ): List<ContentItem> = withContext(Dispatchers.IO) {
        val offset = (page - 1) * pageSize
        cachedContentDao.getContentPage(category, pageSize, offset).map { it.toContentItem() }
    }

    suspend fun cacheContentPage(
        category: String,
        content: List<ContentItem>,
        page: Int,
        pageSize: Int
    ) = withContext(Dispatchers.IO) {
        if (content.isEmpty()) return@withContext

        val start = (page - 1) * pageSize
        val end = start + content.size - 1
        cachedContentDao.deleteRange(category, start, end)

        val cachedEntities = content.mapIndexed { index, item ->
            CachedContent.fromContentItem(
                item = item,
                category = category,
                position = start + index
            )
        }
        cachedContentDao.insertContent(cachedEntities)
    }

    suspend fun cacheContent(
        category: String,
        content: List<ContentItem>
    ) = withContext(Dispatchers.IO) {
        val cachedEntities = content.mapIndexed { index, item ->
            CachedContent.fromContentItem(item, category, index)
        }
        cachedContentDao.replaceCategoryContent(category, cachedEntities)
    }
}
