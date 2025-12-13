package com.test1.tv.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.test1.tv.data.model.ContentItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class HeroSyncManagerTest {

    @get:Rule
    val instantRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private fun item(id: Int) = ContentItem(
        id = id,
        tmdbId = id,
        imdbId = null,
        title = "Item $id",
        overview = null,
        posterUrl = null,
        backdropUrl = null,
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

    private lateinit var lifecycleOwner: SimpleOwner
    private lateinit var registry: LifecycleRegistry

    private class SimpleOwner : LifecycleOwner {
        val reg = LifecycleRegistry(this)
        override val lifecycle: Lifecycle
            get() = reg
    }

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        lifecycleOwner = SimpleOwner()
        registry = lifecycleOwner.reg
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `debounces rapid selections and applies latest`() = runTest(testDispatcher) {
        val updates = mutableListOf<Int>()
        val manager = HeroSyncManager(lifecycleOwner) { content ->
            updates.add(content.tmdbId)
        }

        launch {
            manager.onContentSelected(item(1))
            manager.onContentSelected(item(2))
            manager.onContentSelected(item(3))
        }

        // Before debounce window, no updates
        advanceTimeBy(100)
        assertEquals(emptyList<Int>(), updates)

        // After debounce (150ms), only latest should emit
        advanceTimeBy(100)
        assertEquals(listOf(3), updates)
    }

}
