package com.test1.tv.ui.adapter

import com.test1.tv.data.model.ContentItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentDiffCallbackTest {

    private val diff = ContentDiffCallback()

    private fun item(id: Int, title: String = "Title$id", poster: String? = "p$id", backdrop: String? = "b$id") =
        ContentItem(
            id = id,
            tmdbId = id,
            imdbId = null,
            title = title,
            overview = null,
            posterUrl = poster,
            backdropUrl = backdrop,
            logoUrl = null,
            year = null,
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

    @Test
    fun `same tmdbId considered same item`() {
        val a = item(1)
        val b = item(1)
        assertTrue(diff.areItemsTheSame(a, b))
    }

    @Test
    fun `different tmdbId considered different item`() {
        val a = item(1)
        val b = item(2)
        assertFalse(diff.areItemsTheSame(a, b))
    }

    @Test
    fun `contents differ when title changes`() {
        val a = item(1, title = "Old")
        val b = item(1, title = "New")
        assertFalse(diff.areContentsTheSame(a, b))
    }

    @Test
    fun `contents differ when artwork changes`() {
        val a = item(1, poster = "posterA")
        val b = item(1, poster = "posterB")
        assertFalse(diff.areContentsTheSame(a, b))
    }

    @Test
    fun `contents equal when fields match`() {
        val a = item(1, title = "Same", poster = "p", backdrop = "b")
        val b = item(1, title = "Same", poster = "p", backdrop = "b")
        assertTrue(diff.areContentsTheSame(a, b))
    }
}
