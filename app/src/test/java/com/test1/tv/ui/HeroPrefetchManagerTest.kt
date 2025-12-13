package com.test1.tv.ui

import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.service.TmdbEnrichmentService
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HeroPrefetchManagerTest {

    private lateinit var mockEnrichmentService: TmdbEnrichmentService
    private lateinit var prefetchManager: HeroPrefetchManager

    private fun createTestItems(count: Int): List<ContentItem> {
        return (1..count).map { index ->
            ContentItem(
                id = index,
                tmdbId = index,
                imdbId = "tt000000$index",
                title = "Test Item $index",
                overview = "Test overview",
                posterUrl = null,
                backdropUrl = null,
                logoUrl = null,
                year = "2024",
                rating = 7.5,
                ratingPercentage = 75,
                genres = "Action",
                type = ContentItem.ContentType.MOVIE,
                runtime = "120",
                cast = "Test Cast",
                certification = "PG-13",
                imdbRating = null,
                rottenTomatoesRating = null,
                traktRating = null
            )
        }
    }

    @Before
    fun setup() {
        mockEnrichmentService = mock(TmdbEnrichmentService::class.java)
        prefetchManager = HeroPrefetchManager(mockEnrichmentService)
    }

    @Test
    fun `onRowLoaded prefetches first 6 items`() = runTest {
        // Arrange
        val items = createTestItems(20)
        val idsCaptor = argumentCaptor<List<Int>>()
        val typeCaptor = argumentCaptor<TmdbEnrichmentService.ContentType>()

        // Act
        prefetchManager.onRowLoaded(items)

        // Assert
        verify(mockEnrichmentService).preload(idsCaptor.capture(), typeCaptor.capture())
        assertEquals(listOf(1, 2, 3, 4, 5, 6), idsCaptor.firstValue)
        assertEquals(TmdbEnrichmentService.ContentType.MOVIE, typeCaptor.firstValue)
    }

    @Test
    fun `onRowLoaded handles lists smaller than 6 items`() = runTest {
        // Arrange
        val items = createTestItems(3)
        val idsCaptor = argumentCaptor<List<Int>>()

        // Act
        prefetchManager.onRowLoaded(items)

        // Assert
        verify(mockEnrichmentService).preload(idsCaptor.capture(), any())
        assertEquals(listOf(1, 2, 3), idsCaptor.firstValue)
    }

    @Test
    fun `onRowLoaded with empty list does not prefetch`() = runTest {
        // Act
        prefetchManager.onRowLoaded(emptyList())

        // Assert
        verify(mockEnrichmentService, never()).preload(any(), any())
    }

    @Test
    fun `onFocusChanged prefetches window around focus`() = runTest {
        // Arrange
        val items = createTestItems(20)
        prefetchManager.onRowLoaded(items)

        // Act - Focus on item 5
        prefetchManager.onFocusChanged(5)

        // Assert - Should prefetch twice: initial load + focus change
        verify(mockEnrichmentService, times(2)).preload(any(), any())
    }

    @Test
    fun `onFocusChanged at start prefetches from index 0`() = runTest {
        // Arrange
        val items = createTestItems(20)
        prefetchManager.onRowLoaded(items)

        // Act - Focus on first item (index 0)
        prefetchManager.onFocusChanged(0)

        // Assert - Only initial load, no additional prefetch needed
        // (focus window [0,4] is already covered by initial load [0,5])
        verify(mockEnrichmentService, times(1)).preload(any(), any())
    }

    @Test
    fun `onFocusChanged at end prefetches to last index`() = runTest {
        // Arrange
        val items = createTestItems(20)
        prefetchManager.onRowLoaded(items)

        // Act - Focus on last item (index 19)
        prefetchManager.onFocusChanged(19)

        // Assert - Should prefetch twice
        verify(mockEnrichmentService, times(2)).preload(any(), any())
    }

    @Test
    fun `onFocusChanged with same index does not duplicate prefetch`() = runTest {
        // Arrange
        val items = createTestItems(20)
        prefetchManager.onRowLoaded(items)
        prefetchManager.onFocusChanged(5)

        // Act - Focus on same item again
        prefetchManager.onFocusChanged(5)

        // Assert - Should only have initial prefetch, no duplicate
        verify(mockEnrichmentService, times(2)).preload(any(), any())
    }

    @Test
    fun `clearForNewRow resets state`() = runTest {
        // Arrange
        val items = createTestItems(20)
        prefetchManager.onRowLoaded(items)
        prefetchManager.onFocusChanged(5)

        // Act
        prefetchManager.clearForNewRow()

        // Then load new row and focus
        val newItems = createTestItems(10)
        prefetchManager.onRowLoaded(newItems)
        prefetchManager.onFocusChanged(0)

        // Assert - Should prefetch again after reset
        verify(mockEnrichmentService, times(3)).preload(any(), any())
    }

    @Test
    fun `prefetch uses TV_SHOW type for TV content`() = runTest {
        // Arrange
        val items = createTestItems(6).map {
            it.copy(type = ContentItem.ContentType.TV_SHOW)
        }
        val typeCaptor = argumentCaptor<TmdbEnrichmentService.ContentType>()

        // Act
        prefetchManager.onRowLoaded(items)

        // Assert
        verify(mockEnrichmentService).preload(any(), typeCaptor.capture())
        assertEquals(TmdbEnrichmentService.ContentType.TV_SHOW, typeCaptor.firstValue)
    }
}
