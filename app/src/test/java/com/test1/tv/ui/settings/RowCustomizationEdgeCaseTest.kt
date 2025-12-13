package com.test1.tv.ui.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.test1.tv.data.local.entity.RowConfigEntity
import com.test1.tv.data.repository.ScreenConfigRepository
import com.test1.tv.ui.settings.viewmodel.RowCustomizationViewModel
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
import org.mockito.kotlin.never
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Edge Case Tests - Phase 5.6
 *
 * Tests edge cases and boundary conditions:
 * - No authenticated user (requiresAuth rows)
 * - Empty row lists
 * - All rows disabled
 * - Single row scenarios
 * - Invalid operations (move first row up, move last row down)
 * - Null/empty data handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RowCustomizationEdgeCaseTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var repository: ScreenConfigRepository
    private lateinit var viewModel: RowCustomizationViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val authRequiredRow = RowConfigEntity(
        id = "home_watchlist",
        screenType = "home",
        title = "My Watchlist",
        rowType = "watchlist",
        contentType = "mixed",
        presentation = "portrait",
        dataSourceUrl = null,
        defaultPosition = 0,
        position = 0,
        enabled = true,
        requiresAuth = true,  // Requires authentication
        pageSize = 20,
        isSystemRow = false
    )

    private val publicRow = RowConfigEntity(
        id = "home_trending",
        screenType = "home",
        title = "Trending",
        rowType = "trending",
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
    fun testEmptyRowList() {
        runBlocking {
            // Given: Repository returns empty list
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(emptyList()))

            // When: Load screen
            val observer = mock<Observer<List<RowConfigEntity>>>()
            viewModel.rows.observeForever(observer)

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Empty list should be emitted
            val captor = argumentCaptor<List<RowConfigEntity>>()
            verify(observer).onChanged(captor.capture())

            val emittedRows = captor.firstValue
            assertNotNull(emittedRows)
            assertEquals(0, emittedRows.size)

            viewModel.rows.removeObserver(observer)
        }
    }

    @Test
    fun testAllRowsDisabled() {
        runBlocking {
            // Given: All rows are disabled
            val disabledRows = listOf(
                publicRow.copy(enabled = false),
                authRequiredRow.copy(enabled = false)
            )
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(disabledRows))

            // When: Load screen
            val observer = mock<Observer<List<RowConfigEntity>>>()
            viewModel.rows.observeForever(observer)

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: All rows should be marked as disabled
            val captor = argumentCaptor<List<RowConfigEntity>>()
            verify(observer).onChanged(captor.capture())

            val emittedRows = captor.firstValue
            assertNotNull(emittedRows)
            assertEquals(2, emittedRows.size)
            assertTrue(emittedRows.all { !it.enabled })

            viewModel.rows.removeObserver(observer)
        }
    }

    @Test
    fun testSingleRowScenario() {
        runBlocking {
            // Given: Only one row exists
            val singleRow = listOf(publicRow)
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(singleRow))

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: Try to move the only row up
            viewModel.moveRowUp(publicRow)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: No swap should occur (can't move position 0 up)
            verify(repository, never()).swapRowPositions(any(), any())

            // When: Try to move the only row down
            viewModel.moveRowDown(publicRow)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: No swap should occur (can't move last row down)
            verify(repository, never()).swapRowPositions(any(), any())
        }
    }

    @Test
    fun testAuthRequiredRowsAreIncludedInSettings() {
        runBlocking {
            // Given: Mix of auth-required and public rows
            val mixedRows = listOf(authRequiredRow, publicRow)
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(mixedRows))

            // When: Load settings screen
            val observer = mock<Observer<List<RowConfigEntity>>>()
            viewModel.rows.observeForever(observer)

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Both auth-required and public rows should be shown in settings
            val captor = argumentCaptor<List<RowConfigEntity>>()
            verify(observer).onChanged(captor.capture())

            val emittedRows = captor.firstValue
            assertNotNull(emittedRows)
            assertEquals(2, emittedRows.size)
            assertTrue(emittedRows.any { it.requiresAuth })
            assertTrue(emittedRows.any { !it.requiresAuth })

            viewModel.rows.removeObserver(observer)
        }
    }

    @Test
    fun testCanDisableAuthRequiredRow() {
        runBlocking {
            // Given: Auth-required row is enabled
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(listOf(authRequiredRow)))

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: User disables auth-required row
            viewModel.toggleRowVisibility(authRequiredRow)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Repository should be called to disable it
            verify(repository).toggleRowVisibility(authRequiredRow.id, false)
        }
    }

    @Test
    fun testCanReorderAuthRequiredRows() {
        runBlocking {
            // Given: Auth-required row is second in list
            val rows = listOf(publicRow, authRequiredRow)
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(rows))

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: User moves auth-required row up
            viewModel.moveRowUp(authRequiredRow)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Repository should swap positions
            verify(repository).swapRowPositions(authRequiredRow.id, publicRow.id)
        }
    }

    @Test
    fun testCannotMoveFirstRowUp() {
        runBlocking {
            // Given: Row at position 0
            val rows = listOf(publicRow.copy(position = 0), authRequiredRow.copy(position = 1))
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(rows))

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: Try to move first row up
            viewModel.moveRowUp(rows[0])
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: No swap should occur
            verify(repository, never()).swapRowPositions(any(), any())
        }
    }

    @Test
    fun testCannotMoveLastRowDown() {
        runBlocking {
            // Given: Two rows, second is last
            val rows = listOf(publicRow.copy(position = 0), authRequiredRow.copy(position = 1))
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(rows))

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: Try to move last row down
            viewModel.moveRowDown(rows[1])
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: No swap should occur
            verify(repository, never()).swapRowPositions(any(), any())
        }
    }

    @Test
    fun testToggleAlreadyDisabledRowEnablesIt() {
        runBlocking {
            // Given: Row is already disabled
            val disabledRow = publicRow.copy(enabled = false)
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(listOf(disabledRow)))

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: User toggles the disabled row
            viewModel.toggleRowVisibility(disabledRow)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Repository should be called to enable it (true)
            verify(repository).toggleRowVisibility(disabledRow.id, true)
        }
    }

    @Test
    fun testResetToDefaultsWithNoRows() {
        runBlocking {
            // Given: No rows exist
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(emptyList()))

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: User tries to reset to defaults
            viewModel.resetToDefaults()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Reset should still be called (will restore default rows)
            verify(repository).resetScreenToDefaults(ScreenConfigRepository.ScreenType.HOME)
        }
    }

    @Test
    fun testSwitchingScreensWithDifferentRowCounts() {
        runBlocking {
            // Given: Home has 2 rows, Movies has 5 rows
            val homeRows = listOf(publicRow, authRequiredRow)
            val moviesRows = List(5) { index ->
                publicRow.copy(
                    id = "movies_$index",
                    screenType = "movies",
                    title = "Movie Row $index",
                    position = index
                )
            }

            `when`(repository.getAllRowsForSettings(ScreenConfigRepository.ScreenType.HOME))
                .thenReturn(flowOf(homeRows))
            `when`(repository.getAllRowsForSettings(ScreenConfigRepository.ScreenType.MOVIES))
                .thenReturn(flowOf(moviesRows))

            val observer = mock<Observer<List<RowConfigEntity>>>()
            viewModel.rows.observeForever(observer)

            // When: Load Home screen
            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Should show 2 rows
            val captor = argumentCaptor<List<RowConfigEntity>>()
            verify(observer, times(1)).onChanged(captor.capture())
            assertEquals(2, captor.firstValue.size)

            // When: Switch to Movies screen
            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.MOVIES)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Should show 5 rows (observer called 2 times total now)
            verify(observer, times(2)).onChanged(captor.capture())
            assertEquals(5, captor.lastValue.size)

            viewModel.rows.removeObserver(observer)
        }
    }

    @Test
    fun testMixOfEnabledAndDisabledRows() {
        runBlocking {
            // Given: Mix of enabled and disabled rows
            val rows = listOf(
                publicRow.copy(id = "row1", enabled = true),
                publicRow.copy(id = "row2", enabled = false),
                publicRow.copy(id = "row3", enabled = true),
                publicRow.copy(id = "row4", enabled = false)
            )
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(rows))

            // When: Load screen
            val observer = mock<Observer<List<RowConfigEntity>>>()
            viewModel.rows.observeForever(observer)

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: All rows should be shown (both enabled and disabled)
            val captor = argumentCaptor<List<RowConfigEntity>>()
            verify(observer).onChanged(captor.capture())

            val emittedRows = captor.firstValue
            assertEquals(4, emittedRows.size)
            assertEquals(2, emittedRows.count { it.enabled })
            assertEquals(2, emittedRows.count { !it.enabled })

            viewModel.rows.removeObserver(observer)
        }
    }

    @Test
    fun testRowsWithSamePosition() {
        runBlocking {
            // Given: Two rows with the same position (edge case/data corruption)
            val rows = listOf(
                publicRow.copy(id = "row1", position = 0),
                authRequiredRow.copy(id = "row2", position = 0)
            )
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(rows))

            // When: Load screen
            val observer = mock<Observer<List<RowConfigEntity>>>()
            viewModel.rows.observeForever(observer)

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Both rows should still be displayed
            val captor = argumentCaptor<List<RowConfigEntity>>()
            verify(observer).onChanged(captor.capture())

            val emittedRows = captor.firstValue
            assertEquals(2, emittedRows.size)

            viewModel.rows.removeObserver(observer)
        }
    }

    @Test
    fun testNullContentTypeRow() {
        runBlocking {
            // Given: Row with null contentType (valid edge case)
            val nullContentRow = publicRow.copy(contentType = null)
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(listOf(nullContentRow)))

            // When: Load screen and toggle visibility
            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.toggleRowVisibility(nullContentRow)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Should handle gracefully
            verify(repository).toggleRowVisibility(nullContentRow.id, false)
        }
    }

    @Test
    fun testSystemRowsCanBeReordered() {
        runBlocking {
            // Given: System rows that can be reordered
            val systemRow1 = publicRow.copy(id = "sys1", isSystemRow = true, position = 0)
            val systemRow2 = authRequiredRow.copy(id = "sys2", isSystemRow = true, position = 1)
            val rows = listOf(systemRow1, systemRow2)

            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(rows))

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: Move second system row up
            viewModel.moveRowUp(systemRow2)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: Should allow reordering
            verify(repository).swapRowPositions(systemRow2.id, systemRow1.id)
        }
    }
}
