package com.test1.tv.ui.contextmenu

import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.repository.TraktStatusProvider
import com.test1.tv.data.repository.TraktSyncRepository
import com.test1.tv.data.repository.WatchStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ContextMenuActionHandlerTest {

    private lateinit var traktSyncRepository: TraktSyncRepository
    private lateinit var traktStatusProvider: TraktStatusProvider
    private lateinit var watchStatusRepository: WatchStatusRepository
    private lateinit var handler: ContextMenuActionHandler

    private val testDispatcher = StandardTestDispatcher()

    private val testMovieItem = ContentItem(
        id = 1,
        tmdbId = 12345,
        imdbId = null,
        title = "Test Movie",
        overview = null,
        posterUrl = null,
        backdropUrl = null,
        logoUrl = null,
        year = "2024",
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
        id = 2,
        tmdbId = 67890,
        imdbId = null,
        title = "Test Show",
        overview = null,
        posterUrl = null,
        backdropUrl = null,
        logoUrl = null,
        year = "2024",
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
        traktSyncRepository = mock()
        traktStatusProvider = mock()
        watchStatusRepository = mock()
        handler = ContextMenuActionHandler(
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
    fun `buildActions returns only Play when not authenticated`() = runTest {
        val actions = handler.buildActions(testMovieItem, isTraktAuthenticated = false)

        assertEquals(1, actions.size)
        assertIs<ContextMenuAction.Play>(actions[0])
    }

    @Test
    fun `buildActions includes MarkWatched when not watched`() = runTest {
        whenever(traktStatusProvider.getItemStatus(any(), any())).thenReturn(
            TraktStatusProvider.ItemStatus(
                isWatched = false,
                isInCollection = false,
                isInWatchlist = false
            )
        )

        val actions = handler.buildActions(testMovieItem, isTraktAuthenticated = true)

        assertTrue(actions.any { it is ContextMenuAction.MarkWatched })
        assertTrue(actions.none { it is ContextMenuAction.MarkUnwatched })
    }

    @Test
    fun `buildActions includes MarkUnwatched when already watched`() = runTest {
        whenever(traktStatusProvider.getItemStatus(any(), any())).thenReturn(
            TraktStatusProvider.ItemStatus(
                isWatched = true,
                isInCollection = false,
                isInWatchlist = false
            )
        )

        val actions = handler.buildActions(testMovieItem, isTraktAuthenticated = true)

        assertTrue(actions.any { it is ContextMenuAction.MarkUnwatched })
        assertTrue(actions.none { it is ContextMenuAction.MarkWatched })
    }

    @Test
    fun `buildActions includes AddToCollection when not in collection`() = runTest {
        whenever(traktStatusProvider.getItemStatus(any(), any())).thenReturn(
            TraktStatusProvider.ItemStatus(
                isWatched = false,
                isInCollection = false,
                isInWatchlist = false
            )
        )

        val actions = handler.buildActions(testMovieItem, isTraktAuthenticated = true)

        assertTrue(actions.any { it is ContextMenuAction.AddToCollection })
        assertTrue(actions.none { it is ContextMenuAction.RemoveFromCollection })
    }

    @Test
    fun `buildActions includes RemoveFromCollection when already in collection`() = runTest {
        whenever(traktStatusProvider.getItemStatus(any(), any())).thenReturn(
            TraktStatusProvider.ItemStatus(
                isWatched = false,
                isInCollection = true,
                isInWatchlist = false
            )
        )

        val actions = handler.buildActions(testMovieItem, isTraktAuthenticated = true)

        assertTrue(actions.any { it is ContextMenuAction.RemoveFromCollection })
        assertTrue(actions.none { it is ContextMenuAction.AddToCollection })
    }

    @Test
    fun `buildActions includes AddToWatchlist when not in watchlist`() = runTest {
        whenever(traktStatusProvider.getItemStatus(any(), any())).thenReturn(
            TraktStatusProvider.ItemStatus(
                isWatched = false,
                isInCollection = false,
                isInWatchlist = false
            )
        )

        val actions = handler.buildActions(testMovieItem, isTraktAuthenticated = true)

        assertTrue(actions.any { it is ContextMenuAction.AddToWatchlist })
        assertTrue(actions.none { it is ContextMenuAction.RemoveFromWatchlist })
    }

    @Test
    fun `buildActions includes RemoveFromWatchlist when already in watchlist`() = runTest {
        whenever(traktStatusProvider.getItemStatus(any(), any())).thenReturn(
            TraktStatusProvider.ItemStatus(
                isWatched = false,
                isInCollection = false,
                isInWatchlist = true
            )
        )

        val actions = handler.buildActions(testMovieItem, isTraktAuthenticated = true)

        assertTrue(actions.any { it is ContextMenuAction.RemoveFromWatchlist })
        assertTrue(actions.none { it is ContextMenuAction.AddToWatchlist })
    }

    @Test
    fun `buildActions returns 4 actions when authenticated`() = runTest {
        whenever(traktStatusProvider.getItemStatus(any(), any())).thenReturn(
            TraktStatusProvider.ItemStatus(
                isWatched = false,
                isInCollection = false,
                isInWatchlist = false
            )
        )

        val actions = handler.buildActions(testMovieItem, isTraktAuthenticated = true)

        // Play + MarkWatched + AddToCollection + AddToWatchlist
        assertEquals(4, actions.size)
    }

    @Test
    fun `executeAction updates cache on successful mark watched`() = runTest {
        whenever(traktSyncRepository.markMovieWatched(any())).thenReturn(true)
        whenever(traktSyncRepository.syncHistoryOnly()).thenReturn(true)

        var successCalled = false
        val callback = object : ContextMenuActionHandler.ActionCallback {
            override fun onSuccess(action: ContextMenuAction) {
                successCalled = true
            }
            override fun onFailure(action: ContextMenuAction, error: String) {}
        }

        handler.executeAction(testMovieItem, ContextMenuAction.MarkWatched, callback)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(traktStatusProvider).markWatched(12345)
        assertTrue(successCalled)
    }

    @Test
    fun `executeAction calls onFailure when API fails`() = runTest {
        whenever(traktSyncRepository.markMovieWatched(any())).thenReturn(false)

        var failureCalled = false
        val callback = object : ContextMenuActionHandler.ActionCallback {
            override fun onSuccess(action: ContextMenuAction) {}
            override fun onFailure(action: ContextMenuAction, error: String) {
                failureCalled = true
            }
        }

        handler.executeAction(testMovieItem, ContextMenuAction.MarkWatched, callback)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(failureCalled)
    }

    @Test
    fun `executeAction uses correct API for TV shows`() = runTest {
        whenever(traktSyncRepository.markShowWatched(any())).thenReturn(true)
        whenever(traktSyncRepository.syncHistoryOnly()).thenReturn(true)

        val callback = object : ContextMenuActionHandler.ActionCallback {
            override fun onSuccess(action: ContextMenuAction) {}
            override fun onFailure(action: ContextMenuAction, error: String) {}
        }

        handler.executeAction(testShowItem, ContextMenuAction.MarkWatched, callback)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(traktSyncRepository).markShowWatched(67890)
    }

    @Test
    fun `executeAction adds to collection and updates cache`() = runTest {
        whenever(traktSyncRepository.addToCollection(any(), any())).thenReturn(true)
        whenever(traktSyncRepository.syncCollectionOnly()).thenReturn(true)

        val callback = object : ContextMenuActionHandler.ActionCallback {
            override fun onSuccess(action: ContextMenuAction) {}
            override fun onFailure(action: ContextMenuAction, error: String) {}
        }

        handler.executeAction(testMovieItem, ContextMenuAction.AddToCollection, callback)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(traktStatusProvider).addToCollection(12345)
        verify(traktSyncRepository).addToCollection(12345, true)
    }

    @Test
    fun `executeAction removes from watchlist and updates cache`() = runTest {
        whenever(traktSyncRepository.removeFromWatchlist(any(), any())).thenReturn(true)
        whenever(traktSyncRepository.syncWatchlistOnly()).thenReturn(true)

        val callback = object : ContextMenuActionHandler.ActionCallback {
            override fun onSuccess(action: ContextMenuAction) {}
            override fun onFailure(action: ContextMenuAction, error: String) {}
        }

        handler.executeAction(testMovieItem, ContextMenuAction.RemoveFromWatchlist, callback)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(traktStatusProvider).removeFromWatchlist(12345)
        verify(traktSyncRepository).removeFromWatchlist(12345, true)
    }
}
