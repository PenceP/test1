package com.strmr.tv.data.repository

import com.strmr.tv.data.local.dao.TraktUserItemDao
import com.strmr.tv.data.model.ContentItem
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TraktStatusProviderTest {

    private lateinit var userItemDao: TraktUserItemDao
    private lateinit var accountRepository: TraktAccountRepository
    private lateinit var provider: TraktStatusProvider

    @Before
    fun setup() {
        userItemDao = mock()
        accountRepository = mock()
        provider = TraktStatusProvider(userItemDao, accountRepository)
    }

    @Test
    fun `isAuthenticated returns true when account exists`() = runTest {
        whenever(accountRepository.getAccount()).thenReturn(mock())
        assertTrue(provider.isAuthenticated())
    }

    @Test
    fun `isAuthenticated returns false when account is null`() = runTest {
        whenever(accountRepository.getAccount()).thenReturn(null)
        assertFalse(provider.isAuthenticated())
    }

    @Test
    fun `isWatched returns false when item not in history`() = runTest {
        whenever(userItemDao.getAllTmdbIdsInList("HISTORY")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("COLLECTION")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("WATCHLIST")).thenReturn(emptyList())

        assertFalse(provider.isWatched(12345, ContentItem.ContentType.MOVIE))
    }

    @Test
    fun `isWatched returns true when item is in history`() = runTest {
        whenever(userItemDao.getAllTmdbIdsInList("HISTORY")).thenReturn(listOf(12345))
        whenever(userItemDao.getAllTmdbIdsInList("COLLECTION")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("WATCHLIST")).thenReturn(emptyList())

        assertTrue(provider.isWatched(12345, ContentItem.ContentType.MOVIE))
    }

    @Test
    fun `isInCollection returns true when item is in collection`() = runTest {
        whenever(userItemDao.getAllTmdbIdsInList("HISTORY")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("COLLECTION")).thenReturn(listOf(12345))
        whenever(userItemDao.getAllTmdbIdsInList("WATCHLIST")).thenReturn(emptyList())

        assertTrue(provider.isInCollection(12345, ContentItem.ContentType.MOVIE))
    }

    @Test
    fun `isInWatchlist returns true when item is in watchlist`() = runTest {
        whenever(userItemDao.getAllTmdbIdsInList("HISTORY")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("COLLECTION")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("WATCHLIST")).thenReturn(listOf(12345))

        assertTrue(provider.isInWatchlist(12345, ContentItem.ContentType.MOVIE))
    }

    @Test
    fun `markWatched updates cache immediately`() = runTest {
        whenever(userItemDao.getAllTmdbIdsInList("HISTORY")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("COLLECTION")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("WATCHLIST")).thenReturn(emptyList())

        // Initially not watched
        assertFalse(provider.isWatched(99999, ContentItem.ContentType.MOVIE))

        // Mark watched
        provider.markWatched(99999)

        // Now should be watched
        assertTrue(provider.isWatched(99999, ContentItem.ContentType.MOVIE))
    }

    @Test
    fun `markUnwatched removes from cache immediately`() = runTest {
        whenever(userItemDao.getAllTmdbIdsInList("HISTORY")).thenReturn(listOf(99999))
        whenever(userItemDao.getAllTmdbIdsInList("COLLECTION")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("WATCHLIST")).thenReturn(emptyList())

        // Initially watched
        assertTrue(provider.isWatched(99999, ContentItem.ContentType.MOVIE))

        // Mark unwatched
        provider.markUnwatched(99999)

        // Now should not be watched
        assertFalse(provider.isWatched(99999, ContentItem.ContentType.MOVIE))
    }

    @Test
    fun `addToCollection updates cache immediately`() = runTest {
        whenever(userItemDao.getAllTmdbIdsInList("HISTORY")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("COLLECTION")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("WATCHLIST")).thenReturn(emptyList())

        assertFalse(provider.isInCollection(88888, ContentItem.ContentType.TV_SHOW))

        provider.addToCollection(88888)

        assertTrue(provider.isInCollection(88888, ContentItem.ContentType.TV_SHOW))
    }

    @Test
    fun `removeFromCollection removes from cache immediately`() = runTest {
        whenever(userItemDao.getAllTmdbIdsInList("HISTORY")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("COLLECTION")).thenReturn(listOf(88888))
        whenever(userItemDao.getAllTmdbIdsInList("WATCHLIST")).thenReturn(emptyList())

        assertTrue(provider.isInCollection(88888, ContentItem.ContentType.TV_SHOW))

        provider.removeFromCollection(88888)

        assertFalse(provider.isInCollection(88888, ContentItem.ContentType.TV_SHOW))
    }

    @Test
    fun `clearCache resets all caches`() = runTest {
        whenever(userItemDao.getAllTmdbIdsInList("HISTORY")).thenReturn(listOf(11111))
        whenever(userItemDao.getAllTmdbIdsInList("COLLECTION")).thenReturn(listOf(22222))
        whenever(userItemDao.getAllTmdbIdsInList("WATCHLIST")).thenReturn(listOf(33333))

        // Load cache
        assertTrue(provider.isWatched(11111, ContentItem.ContentType.MOVIE))

        // Clear cache
        provider.clearCache()

        // After clear, should reload from DB on next access
        // Since we mocked the DAO, it will reload the same values
        assertTrue(provider.isWatched(11111, ContentItem.ContentType.MOVIE))
    }

    @Test
    fun `getItemStatus returns combined status`() = runTest {
        whenever(userItemDao.getAllTmdbIdsInList("HISTORY")).thenReturn(listOf(55555))
        whenever(userItemDao.getAllTmdbIdsInList("COLLECTION")).thenReturn(listOf(55555))
        whenever(userItemDao.getAllTmdbIdsInList("WATCHLIST")).thenReturn(emptyList())

        val status = provider.getItemStatus(55555, ContentItem.ContentType.MOVIE)

        assertTrue(status.isWatched)
        assertTrue(status.isInCollection)
        assertFalse(status.isInWatchlist)
    }
}
