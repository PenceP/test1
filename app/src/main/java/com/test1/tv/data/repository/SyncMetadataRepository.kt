package com.test1.tv.data.repository

import com.test1.tv.data.local.dao.SyncMetadataDao
import com.test1.tv.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncMetadataRepository @Inject constructor(
    private val syncMetadataDao: SyncMetadataDao
) {
    companion object {
        private const val STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    suspend fun isStale(key: String): Boolean = withContext(Dispatchers.IO) {
        val lastSync = syncMetadataDao.getLastSyncTime(key) ?: return@withContext true
        (System.currentTimeMillis() - lastSync) > STALE_THRESHOLD_MS
    }

    suspend fun markSynced(key: String, traktTimestamp: String? = null) = withContext(Dispatchers.IO) {
        syncMetadataDao.upsert(
            SyncMetadataEntity(
                key = key,
                lastSyncedAt = System.currentTimeMillis(),
                traktActivityTimestamp = traktTimestamp
            )
        )
    }

    suspend fun getTraktTimestamp(key: String): String? = withContext(Dispatchers.IO) {
        syncMetadataDao.get(key)?.traktActivityTimestamp
    }

    suspend fun clearSyncData(key: String) = withContext(Dispatchers.IO) {
        syncMetadataDao.delete(key)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        syncMetadataDao.clearAll()
    }
}
