package com.strmr.tv.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.strmr.tv.data.local.AppDatabase
import com.strmr.tv.data.local.dao.SyncMetadataDao
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncMetadataRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var syncMetadataDao: SyncMetadataDao
    private lateinit var repository: SyncMetadataRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncMetadataDao = database.syncMetadataDao()
        repository = SyncMetadataRepository(syncMetadataDao)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `isStale returns true when key has never been synced`() = runTest {
        val isStale = repository.isStale("new_key")
        assertTrue(isStale, "Should be stale when never synced")
    }

    @Test
    fun `isStale returns false when sync is recent`() = runTest {
        // Arrange: Mark as synced just now
        repository.markSynced("recent_key")

        // Act
        val isStale = repository.isStale("recent_key")

        // Assert
        assertFalse(isStale, "Should not be stale when recently synced")
    }

    @Test
    fun `isStale returns true when sync is older than 24 hours`() = runTest {
        // Arrange: Mark as synced 25 hours ago
        val twentyFiveHoursAgo = System.currentTimeMillis() - (25 * 60 * 60 * 1000L)
        syncMetadataDao.upsert(
            com.strmr.tv.data.local.entity.SyncMetadataEntity(
                key = "old_key",
                lastSyncedAt = twentyFiveHoursAgo
            )
        )

        // Act
        val isStale = repository.isStale("old_key")

        // Assert
        assertTrue(isStale, "Should be stale when older than 24 hours")
    }

    @Test
    fun `markSynced updates sync timestamp`() = runTest {
        // Arrange
        val key = "test_key"

        // Act
        repository.markSynced(key)

        // Assert
        val timestamp = syncMetadataDao.getLastSyncTime(key)
        assertTrue(timestamp != null && timestamp > 0, "Timestamp should be set")
    }

    @Test
    fun `markSynced stores Trakt timestamp`() = runTest {
        // Arrange
        val key = "test_key"
        val traktTimestamp = "2024-01-01T12:00:00.000Z"

        // Act
        repository.markSynced(key, traktTimestamp)

        // Assert
        val stored = repository.getTraktTimestamp(key)
        assertTrue(stored == traktTimestamp, "Trakt timestamp should be stored correctly")
    }

    @Test
    fun `getTraktTimestamp returns null for non-existent key`() = runTest {
        val timestamp = repository.getTraktTimestamp("non_existent")
        assertNull(timestamp, "Should return null for non-existent key")
    }

    @Test
    fun `clearSyncData removes specific key`() = runTest {
        // Arrange
        repository.markSynced("key1")
        repository.markSynced("key2")

        // Act
        repository.clearSyncData("key1")

        // Assert
        assertTrue(repository.isStale("key1"), "Cleared key should be stale")
        assertFalse(repository.isStale("key2"), "Other key should not be affected")
    }

    @Test
    fun `clearAll removes all sync data`() = runTest {
        // Arrange
        repository.markSynced("key1")
        repository.markSynced("key2")
        repository.markSynced("key3")

        // Act
        repository.clearAll()

        // Assert
        assertTrue(repository.isStale("key1"), "All keys should be stale after clearAll")
        assertTrue(repository.isStale("key2"), "All keys should be stale after clearAll")
        assertTrue(repository.isStale("key3"), "All keys should be stale after clearAll")
    }
}
