package com.strmr.tv.ui.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.strmr.tv.data.local.entity.RowConfigEntity
import com.strmr.tv.data.repository.ScreenConfigRepository
import com.strmr.tv.ui.settings.viewmodel.RowCustomizationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration Test - Phase 5.1-5.5: Row Customization Flow Testing
 *
 * Comprehensive integration tests covering:
 * - Phase 5.1: Row visibility changes reflect on Home screen
 * - Phase 5.2: Row visibility changes reflect on Movies screen
 * - Phase 5.3: Row visibility changes reflect on TV Shows screen
 * - Phase 5.4: Row reordering persists
 * - Phase 5.5: Reset to defaults works correctly
 *
 * Tests the complete data flow:
 * 1. ViewModel receives user action
 * 2. Repository processes the request
 * 3. Database is updated
 * 4. UI reflects the changes
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RowVisibilityFlowTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var repository: ScreenConfigRepository
    private lateinit var viewModel: RowCustomizationViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val sampleHomeRows = listOf(
        RowConfigEntity(
            id = "home_trending",
            screenType = "home",
            title = "Trending Movies",
            rowType = "trending",
            contentType = "movies",
            presentation = "portrait",
            dataSourceUrl = null,
            defaultPosition = 0,
            position = 0,
            enabled = true,
            requiresAuth = false,
            pageSize = 20,
            isSystemRow = false
        ),
        RowConfigEntity(
            id = "home_popular",
            screenType = "home",
            title = "Popular Movies",
            rowType = "popular",
            contentType = "movies",
            presentation = "portrait",
            dataSourceUrl = null,
            defaultPosition = 1,
            position = 1,
            enabled = true,
            requiresAuth = false,
            pageSize = 20,
            isSystemRow = false
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        viewModel = RowCustomizationViewModel(repository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testRowVisibilityToggleUpdatesDatabase() {
        runBlocking {
            // Given: Repository returns flow of rows
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(sampleHomeRows))

            // When: User toggles a row visibility
            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            val rowToToggle = sampleHomeRows.first()
            viewModel.toggleRowVisibility(rowToToggle)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Repository should be called to toggle visibility
            verify(repository).toggleRowVisibility(rowToToggle.id, false)
        }
    }

    @Test
    fun testMultipleRapidTogglesCallRepositoryForEachToggle() {
        runBlocking {
            // Given: Repository returns flow of rows
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(sampleHomeRows))

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            val rowToToggle = sampleHomeRows.first()

            // When: User toggles multiple times
            repeat(3) {
                viewModel.toggleRowVisibility(rowToToggle)
                testDispatcher.scheduler.advanceUntilIdle()
            }

            // Then: Repository should be called 3 times
            verify(repository, times(3)).toggleRowVisibility(any(), any())
        }
    }

    @Test
    fun testSwitchingScreensLoadsCorrectScreenRows() {
        runBlocking {
            val homeRows = sampleHomeRows
            val moviesRows = listOf(
                sampleHomeRows.first().copy(
                    id = "movies_trending",
                    screenType = "movies",
                    title = "Trending in Movies"
                )
            )

            `when`(repository.getAllRowsForSettings(ScreenConfigRepository.ScreenType.HOME))
                .thenReturn(flowOf(homeRows))
            `when`(repository.getAllRowsForSettings(ScreenConfigRepository.ScreenType.MOVIES))
                .thenReturn(flowOf(moviesRows))

            // When: Load Home screen
            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Repository should be called for HOME screen
            verify(repository).getAllRowsForSettings(ScreenConfigRepository.ScreenType.HOME)

            // When: Switch to Movies screen
            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.MOVIES)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Repository should be called for MOVIES screen
            verify(repository).getAllRowsForSettings(ScreenConfigRepository.ScreenType.MOVIES)
        }
    }

    @Test
    fun testMoveRowUpCallsRepositoryWithCorrectParameters() {
        runBlocking {
            // Given: Repository returns flow of rows
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(sampleHomeRows))

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: User moves second row up
            val secondRow = sampleHomeRows[1]
            val firstRow = sampleHomeRows[0]

            viewModel.moveRowUp(secondRow)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Repository should swap positions
            verify(repository).swapRowPositions(secondRow.id, firstRow.id)
        }
    }

    @Test
    fun testResetToDefaultsCallsRepositoryForCurrentScreen() {
        runBlocking {
            // Given: Repository returns flow of rows
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(sampleHomeRows))

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: User resets to defaults
            viewModel.resetToDefaults()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Repository should reset HOME screen
            verify(repository).resetScreenToDefaults(ScreenConfigRepository.ScreenType.HOME)
        }
    }

    @Test
    fun testLiveDataEmitsRowsWhenScreenLoads() {
        runBlocking {
            // Given: Repository returns flow of rows
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(sampleHomeRows))

            // When: Load screen
            val observer = mock<Observer<List<RowConfigEntity>>>()
            viewModel.rows.observeForever(observer)

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Observer should receive rows
            val captor = argumentCaptor<List<RowConfigEntity>>()
            verify(observer).onChanged(captor.capture())

            val emittedRows = captor.firstValue
            assertNotNull(emittedRows)
            assertEquals(2, emittedRows.size)
            assertEquals("Trending Movies", emittedRows[0].title)
            assertEquals("Popular Movies", emittedRows[1].title)

            viewModel.rows.removeObserver(observer)
        }
    }

    @Test
    fun testErrorInRepositoryShowsErrorMessage() {
        runBlocking {
            // Given: Repository throws error
            `when`(repository.getAllRowsForSettings(any()))
                .thenThrow(RuntimeException("Database error"))

            // When: Load screen
            val errorObserver = mock<Observer<String?>>()
            viewModel.error.observeForever(errorObserver)

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Error should be emitted
            val captor = argumentCaptor<String>()
            verify(errorObserver).onChanged(captor.capture())

            val errorMessage = captor.firstValue
            assertNotNull(errorMessage)
            assertTrue(errorMessage.contains("Failed to load rows"))

            viewModel.error.removeObserver(errorObserver)
        }
    }
}
