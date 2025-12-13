package com.test1.tv.ui.contextmenu

import com.test1.tv.data.model.ContentItem
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentItemContextMenuTest {

    private fun createTestItem(
        id: Int = 1,
        tmdbId: Int = 12345,
        imdbId: String? = null,
        title: String = "Test Item",
        type: ContentItem.ContentType = ContentItem.ContentType.MOVIE,
        isPlaceholder: Boolean = false
    ) = ContentItem(
        id = id,
        tmdbId = tmdbId,
        imdbId = imdbId,
        title = title,
        overview = null,
        posterUrl = null,
        backdropUrl = null,
        logoUrl = null,
        year = null,
        rating = null,
        ratingPercentage = null,
        genres = null,
        type = type,
        runtime = null,
        cast = null,
        certification = null,
        imdbRating = null,
        rottenTomatoesRating = null,
        traktRating = null,
        isPlaceholder = isPlaceholder
    )

    @Test
    fun `shouldShowContextMenu returns false for tmdbId -1`() {
        val item = createTestItem(tmdbId = -1, title = "Collection Row")
        assertFalse(item.shouldShowContextMenu())
    }

    @Test
    fun `shouldShowContextMenu returns false for placeholder items`() {
        val item = createTestItem(title = "Loading...", isPlaceholder = true)
        assertFalse(item.shouldShowContextMenu())
    }

    @Test
    fun `shouldShowContextMenu returns false for Trakt list URLs`() {
        val item = createTestItem(
            imdbId = "https://trakt.tv/users/me/lists/my-list",
            title = "My Trakt List"
        )
        assertFalse(item.shouldShowContextMenu())
    }

    @Test
    fun `shouldShowContextMenu returns true for valid movie items`() {
        val item = createTestItem(title = "Valid Movie")
        assertTrue(item.shouldShowContextMenu())
    }

    @Test
    fun `shouldShowContextMenu returns true for valid TV show items`() {
        val item = createTestItem(
            id = 2,
            tmdbId = 67890,
            title = "Valid TV Show",
            type = ContentItem.ContentType.TV_SHOW
        )
        assertTrue(item.shouldShowContextMenu())
    }

    @Test
    fun `shouldShowContextMenu returns true with valid imdbId`() {
        val item = createTestItem(imdbId = "tt1234567", title = "Movie with IMDB")
        assertTrue(item.shouldShowContextMenu())
    }

    @Test
    fun `shouldShowContextMenu returns true with null imdbId`() {
        val item = createTestItem(imdbId = null, title = "Movie without IMDB")
        assertTrue(item.shouldShowContextMenu())
    }

    @Test
    fun `shouldShowContextMenu correctly identifies network collection rows`() {
        val networkItem = createTestItem(tmdbId = -1, title = "ABC Network")
        assertFalse(networkItem.shouldShowContextMenu())
    }

    @Test
    fun `shouldShowContextMenu correctly identifies director collection rows`() {
        val directorItem = createTestItem(tmdbId = -1, title = "Steven Spielberg")
        assertFalse(directorItem.shouldShowContextMenu())
    }
}
