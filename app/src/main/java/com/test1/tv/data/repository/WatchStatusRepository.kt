package com.test1.tv.data.repository

import com.test1.tv.data.local.dao.WatchStatusDao
import com.test1.tv.data.local.entity.WatchStatusEntity
import com.test1.tv.data.model.ContentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchStatusRepository @Inject constructor(
    private val watchStatusDao: WatchStatusDao
) {
    private val cache = ConcurrentHashMap<String, WatchStatusEntity>()

    private fun key(tmdbId: Int, type: ContentItem.ContentType): String =
        "${type.name}_$tmdbId"

    suspend fun preload() = withContext(Dispatchers.IO) {
        val all = watchStatusDao.getAll()
        cache.clear()
        all.forEach { cache[it.key] = it }
    }

    fun getProgress(tmdbId: Int, type: ContentItem.ContentType): Double? {
        return cache[key(tmdbId, type)]?.progress
    }

    suspend fun upsert(
        tmdbId: Int,
        type: ContentItem.ContentType,
        progress: Double?,
        updatedAt: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val entity = WatchStatusEntity(
            key = key(tmdbId, type),
            tmdbId = tmdbId,
            type = type.name,
            progress = progress,
            updatedAt = updatedAt
        )
        cache[entity.key] = entity
        watchStatusDao.upsertAll(listOf(entity))
    }

    suspend fun upsertAll(items: List<WatchStatusEntity>) = withContext(Dispatchers.IO) {
        items.forEach { cache[it.key] = it }
        watchStatusDao.upsertAll(items)
        android.util.Log.d("WatchStatus", "upsertAll: saved=${items.size}")
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        cache.clear()
        watchStatusDao.clear()
    }

    /**
     * Synchronously update the cache for immediate UI feedback.
     * The database is updated asynchronously in the background.
     */
    fun setProgressSync(tmdbId: Int, type: ContentItem.ContentType, progress: Double) {
        val entity = WatchStatusEntity(
            key = key(tmdbId, type),
            tmdbId = tmdbId,
            type = type.name,
            progress = progress,
            updatedAt = System.currentTimeMillis()
        )
        cache[entity.key] = entity
        // Database update will happen during next sync
    }

    /**
     * Synchronously remove from cache for immediate UI feedback.
     */
    fun removeProgressSync(tmdbId: Int, type: ContentItem.ContentType) {
        cache.remove(key(tmdbId, type))
        // Database update will happen during next sync
    }
}
