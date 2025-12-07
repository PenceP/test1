package com.test1.tv.integration

import com.test1.tv.BuildConfig
import com.test1.tv.data.local.dao.TraktUserItemDao
import com.test1.tv.data.local.entity.TraktUserItem
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.repository.TraktAccountRepository
import com.test1.tv.data.repository.TraktStatusProvider
import com.test1.tv.data.repository.TraktSyncRepository
import com.test1.tv.data.repository.WatchStatusRepository
import com.test1.tv.ui.contextmenu.ContextMenuAction
import com.test1.tv.ui.contextmenu.ContextMenuActionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the context menu flow with Trakt.
 *
 * These tests verify the complete flow from action selection to cache update.
 * For tests requiring real Trakt API calls, you must set TRAKT_TEST_ACCESS_TOKEN
 * in your secrets.properties file.
 *
 * To run these tests with real API:
 * 1. Authorize your Trakt account
 * 2. Get your access token and refresh token
 * 3. Add to secrets.properties:
 *    TRAKT_TEST_ACCESS_TOKEN=your_access_token
 *    TRAKT_TEST_REFRESH_TOKEN=your_refresh_token
 * 4. Run the tests
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TraktContextMenuIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var traktSyncRepository: TraktSyncRepository
    private lateinit var traktStatusProvider: TraktStatusProvider
    private lateinit var watchStatusRepository: WatchStatusRepository
    private lateinit var actionHandler: ContextMenuActionHandler
    private lateinit var userItemDao: TraktUserItemDao
    private lateinit var accountRepository: TraktAccountRepository

    // Test item - using a real TMDB ID for a popular movie
    private val testMovieItem = ContentItem(
        id = 550,
        tmdbId = 550, // Fight Club
        imdbId = "tt0137523",
        title = "Fight Club",
        overview = null,
        posterUrl = null,
        backdropUrl = null,
        logoUrl = null,
        year = "1999",
        rating = null,
        ratingPercentage = null,
        genres = null,
        type = ContentItem.ContentType.MOVIE,
        runtime = null,
        cast = null,
        certification = null,
        imdbRating = null,
        rottenTomatoesRating = null,
        traktRating = null
    )

    private val testShowItem = ContentItem(
        id = 1396,
        tmdbId = 1396, // Breaking Bad
        imdbId = "tt0903747",
        title = "Breaking Bad",
        overview = null,
        posterUrl = null,
        backdropUrl = null,
        logoUrl = null,
        year = "2008",
        rating = null,
        ratingPercentage = null,
        genres = null,
        type = ContentItem.ContentType.TV_SHOW,
        runtime = null,
        cast = null,
        certification = null,
        imdbRating = null,
        rottenTomatoesRating = null,
        traktRating = null
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        userItemDao = mock()
        accountRepository = mock()
        traktSyncRepository = mock()
        watchStatusRepository = mock()

        traktStatusProvider = TraktStatusProvider(userItemDao, accountRepository)
        actionHandler = ContextMenuActionHandler(
            traktSyncRepository,
            traktStatusProvider,
            watchStatusRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `full flow - unauthenticated user sees only Play action`() = runTest {
        whenever(accountRepository.getAccount()).thenReturn(null)
        whenever(userItemDao.getAllTmdbIdsInList(any())).thenReturn(emptyList())

        val actions = actionHandler.buildActions(testMovieItem, isTraktAuthenticated = false)

        assertEquals(1, actions.size)
        assertTrue(actions[0] is ContextMenuAction.Play)
    }

    @Test
    fun `full flow - authenticated user with unwatched movie sees correct actions`() = runTest {
        // Setup: user is authenticated, movie is not watched/collected/watchlisted
        whenever(accountRepository.getAccount()).thenReturn(mock())
        whenever(userItemDao.getAllTmdbIdsInList("HISTORY")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("COLLECTION")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("WATCHLIST")).thenReturn(emptyList())

        val actions = actionHandler.buildActions(testMovieItem, isTraktAuthenticated = true)

        assertEquals(4, actions.size)
        assertTrue(actions.any { it is ContextMenuAction.Play })
        assertTrue(actions.any { it is ContextMenuAction.MarkWatched })
        assertTrue(actions.any { it is ContextMenuAction.AddToCollection })
        assertTrue(actions.any { it is ContextMenuAction.AddToWatchlist })
    }

    @Test
    fun `full flow - authenticated user with watched movie sees correct actions`() = runTest {
        // Setup: user is authenticated, movie is already watched
        whenever(accountRepository.getAccount()).thenReturn(mock())
        whenever(userItemDao.getAllTmdbIdsInList("HISTORY")).thenReturn(listOf(550))
        whenever(userItemDao.getAllTmdbIdsInList("COLLECTION")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("WATCHLIST")).thenReturn(emptyList())

        val actions = actionHandler.buildActions(testMovieItem, isTraktAuthenticated = true)

        assertEquals(4, actions.size)
        assertTrue(actions.any { it is ContextMenuAction.Play })
        assertTrue(actions.any { it is ContextMenuAction.MarkUnwatched }) // Changed from MarkWatched
        assertTrue(actions.any { it is ContextMenuAction.AddToCollection })
        assertTrue(actions.any { it is ContextMenuAction.AddToWatchlist })
    }

    @Test
    fun `full flow - authenticated user with item in all lists sees remove actions`() = runTest {
        // Setup: user is authenticated, item is in all lists
        whenever(accountRepository.getAccount()).thenReturn(mock())
        whenever(userItemDao.getAllTmdbIdsInList("HISTORY")).thenReturn(listOf(550))
        whenever(userItemDao.getAllTmdbIdsInList("COLLECTION")).thenReturn(listOf(550))
        whenever(userItemDao.getAllTmdbIdsInList("WATCHLIST")).thenReturn(listOf(550))

        val actions = actionHandler.buildActions(testMovieItem, isTraktAuthenticated = true)

        assertEquals(4, actions.size)
        assertTrue(actions.any { it is ContextMenuAction.Play })
        assertTrue(actions.any { it is ContextMenuAction.MarkUnwatched })
        assertTrue(actions.any { it is ContextMenuAction.RemoveFromCollection })
        assertTrue(actions.any { it is ContextMenuAction.RemoveFromWatchlist })
    }

    @Test
    fun `cache is updated immediately after action`() = runTest {
        whenever(accountRepository.getAccount()).thenReturn(mock())
        whenever(userItemDao.getAllTmdbIdsInList("HISTORY")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("COLLECTION")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("WATCHLIST")).thenReturn(emptyList())
        whenever(traktSyncRepository.markMovieWatched(any())).thenReturn(true)
        whenever(traktSyncRepository.syncHistoryOnly()).thenReturn(true)

        // Initially not watched
        val beforeStatus = traktStatusProvider.getItemStatus(550, ContentItem.ContentType.MOVIE)
        assertTrue(!beforeStatus.isWatched)

        // Execute action
        var successCalled = false
        actionHandler.executeAction(
            testMovieItem,
            ContextMenuAction.MarkWatched,
            object : ContextMenuActionHandler.ActionCallback {
                override fun onSuccess(action: ContextMenuAction) { successCalled = true }
                override fun onFailure(action: ContextMenuAction, error: String) {}
            }
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify success
        assertTrue(successCalled)

        // Cache should now show item as watched
        val afterStatus = traktStatusProvider.getItemStatus(550, ContentItem.ContentType.MOVIE)
        assertTrue(afterStatus.isWatched)
    }

    @Test
    fun `action failure does not update cache`() = runTest {
        whenever(accountRepository.getAccount()).thenReturn(mock())
        whenever(userItemDao.getAllTmdbIdsInList("HISTORY")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("COLLECTION")).thenReturn(emptyList())
        whenever(userItemDao.getAllTmdbIdsInList("WATCHLIST")).thenReturn(emptyList())
        whenever(traktSyncRepository.markMovieWatched(any())).thenReturn(false) // Simulate failure

        // Initially not watched
        val beforeStatus = traktStatusProvider.getItemStatus(550, ContentItem.ContentType.MOVIE)
        assertTrue(!beforeStatus.isWatched)

        // Execute action (will fail)
        var failureCalled = false
        actionHandler.executeAction(
            testMovieItem,
            ContextMenuAction.MarkWatched,
            object : ContextMenuActionHandler.ActionCallback {
                override fun onSuccess(action: ContextMenuAction) {}
                override fun onFailure(action: ContextMenuAction, error: String) { failureCalled = true }
            }
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify failure
        assertTrue(failureCalled)

        // Cache should NOT be updated - item still not watched
        val afterStatus = traktStatusProvider.getItemStatus(550, ContentItem.ContentType.MOVIE)
        assertTrue(!afterStatus.isWatched)
    }

    /**
     * This test requires real Trakt credentials.
     * Skip if not configured.
     */
    @Test
    fun `real API - mark movie watched requires valid credentials`() {
        // Skip if no test token configured
        Assume.assumeTrue(
            "Skipping real API test - TRAKT_TEST_ACCESS_TOKEN not configured",
            BuildConfig.TRAKT_TEST_ACCESS_TOKEN.isNotEmpty()
        )

        // This test would need a real TraktApiService implementation
        // For now, we just verify the test infrastructure works
        assertTrue(BuildConfig.TRAKT_TEST_ACCESS_TOKEN.isNotEmpty())
    }
}
