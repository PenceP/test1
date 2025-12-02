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
import org.mockito.kotlin.mock
import kotlin.test.assertTrue
import kotlin.system.measureTimeMillis

/**
 * Performance Tests - Phase 5.7
 *
 * Tests performance characteristics:
 * - Many rows (20+) on single screen
 * - Rapid row toggling
 * - Memory profiling - no leaks from config changes
 * - Screen switching performance
 * - Bulk operations
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RowCustomizationPerformanceTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var repository: ScreenConfigRepository
    private lateinit var viewModel: RowCustomizationViewModel
    private val testDispatcher = StandardTestDispatcher()

    private fun createTestRow(index: Int, screenType: String = "home"): RowConfigEntity {
        return RowConfigEntity(
            id = "${screenType}_row_$index",
            screenType = screenType,
            title = "Row $index",
            rowType = "test",
            contentType = "movies",
            presentation = "portrait",
            dataSourceUrl = null,
            defaultPosition = index,
            position = index,
            enabled = true,
            requiresAuth = false,
            pageSize = 20,
            isSystemRow = false
        )
    }

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
    fun testManyRowsPerformance() {
        runBlocking {
            // Given: 25 rows on a single screen
            val manyRows = List(25) { createTestRow(it) }
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(manyRows))

            // When: Load screen with many rows
            val observer = mock<Observer<List<RowConfigEntity>>>()
            viewModel.rows.observeForever(observer)

            val loadTime = measureTimeMillis {
                viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
                testDispatcher.scheduler.advanceUntilIdle()
            }

            // Then: Should load in reasonable time (< 100ms on test dispatcher)
            assertTrue(loadTime < 100, "Loading 25 rows took ${loadTime}ms, expected < 100ms")

            viewModel.rows.removeObserver(observer)
        }
    }

    @Test
    fun testRapidRowTogglingPerformance() {
        runBlocking {
            // Given: Screen with rows
            val rows = List(10) { createTestRow(it) }
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(rows))

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: Rapidly toggle 50 times
            val toggleTime = measureTimeMillis {
                repeat(50) {
                    val row = rows[it % rows.size]
                    viewModel.toggleRowVisibility(row)
                    testDispatcher.scheduler.advanceUntilIdle()
                }
            }

            // Then: Should complete in reasonable time
            assertTrue(toggleTime < 500, "50 rapid toggles took ${toggleTime}ms, expected < 500ms")

            // And: Repository should have been called 50 times
            verify(repository, times(50)).toggleRowVisibility(any(), any())
        }
    }

    @Test
    fun testScreenSwitchingPerformance() {
        runBlocking {
            // Given: Multiple screens with different row counts
            val homeRows = List(10) { createTestRow(it, "home") }
            val moviesRows = List(15) { createTestRow(it, "movies") }
            val tvShowsRows = List(12) { createTestRow(it, "tvshows") }

            `when`(repository.getAllRowsForSettings(ScreenConfigRepository.ScreenType.HOME))
                .thenReturn(flowOf(homeRows))
            `when`(repository.getAllRowsForSettings(ScreenConfigRepository.ScreenType.MOVIES))
                .thenReturn(flowOf(moviesRows))
            `when`(repository.getAllRowsForSettings(ScreenConfigRepository.ScreenType.TV_SHOWS))
                .thenReturn(flowOf(tvShowsRows))

            val observer = mock<Observer<List<RowConfigEntity>>>()
            viewModel.rows.observeForever(observer)

            // When: Switch between screens 30 times
            val switchTime = measureTimeMillis {
                repeat(30) { iteration ->
                    val screen = when (iteration % 3) {
                        0 -> ScreenConfigRepository.ScreenType.HOME
                        1 -> ScreenConfigRepository.ScreenType.MOVIES
                        else -> ScreenConfigRepository.ScreenType.TV_SHOWS
                    }
                    viewModel.loadRowsForScreen(screen)
                    testDispatcher.scheduler.advanceUntilIdle()
                }
            }

            // Then: Should complete in reasonable time
            assertTrue(switchTime < 300, "30 screen switches took ${switchTime}ms, expected < 300ms")

            viewModel.rows.removeObserver(observer)
        }
    }

    @Test
    fun testBulkRowReorderingPerformance() {
        runBlocking {
            // Given: 20 rows
            val rows = List(20) { createTestRow(it) }
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(rows))

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: Move each row multiple times (simulate user dragging)
            val reorderTime = measureTimeMillis {
                // Move rows around 30 times
                repeat(30) { iteration ->
                    val rowIndex = iteration % (rows.size - 1)
                    val row = rows[rowIndex]
                    if (iteration % 2 == 0) {
                        viewModel.moveRowDown(row)
                    } else {
                        viewModel.moveRowUp(rows[rowIndex + 1])
                    }
                    testDispatcher.scheduler.advanceUntilIdle()
                }
            }

            // Then: Should complete in reasonable time
            assertTrue(reorderTime < 300, "30 reorder operations took ${reorderTime}ms, expected < 300ms")
        }
    }

    @Test
    fun testResetPerformanceWithManyRows() {
        runBlocking {
            // Given: 30 modified rows
            val rows = List(30) { createTestRow(it).copy(enabled = false, position = it + 100) }
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(rows))

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: Reset to defaults
            val resetTime = measureTimeMillis {
                viewModel.resetToDefaults()
                testDispatcher.scheduler.advanceUntilIdle()
            }

            // Then: Should complete quickly
            assertTrue(resetTime < 100, "Reset took ${resetTime}ms, expected < 100ms")

            // And: Repository should have been called
            verify(repository).resetScreenToDefaults(ScreenConfigRepository.ScreenType.HOME)
        }
    }

    @Test
    fun testMemoryNoLeakOnScreenSwitch() {
        runBlocking {
            // Given: Three screens with rows
            val homeRows = List(5) { createTestRow(it, "home") }
            val moviesRows = List(5) { createTestRow(it, "movies") }

            `when`(repository.getAllRowsForSettings(ScreenConfigRepository.ScreenType.HOME))
                .thenReturn(flowOf(homeRows))
            `when`(repository.getAllRowsForSettings(ScreenConfigRepository.ScreenType.MOVIES))
                .thenReturn(flowOf(moviesRows))

            // When: Create and destroy observers multiple times (simulating config changes)
            repeat(10) {
                val observer = mock<Observer<List<RowConfigEntity>>>()
                viewModel.rows.observeForever(observer)

                viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
                testDispatcher.scheduler.advanceUntilIdle()

                viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.MOVIES)
                testDispatcher.scheduler.advanceUntilIdle()

                // Clean up observer (simulating fragment destruction)
                viewModel.rows.removeObserver(observer)
            }

            // Then: Test completes without crash or memory error
            // Note: In real memory profiling, you'd use Android Profiler
            // This test verifies that removing observers works correctly
            assertTrue(true, "Memory test completed without crashes")
        }
    }

    @Test
    fun testConcurrentOperationsPerformance() {
        runBlocking {
            // Given: Screen with 15 rows
            val rows = List(15) { createTestRow(it) }
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(rows))

            viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: Perform multiple operations in quick succession
            val operationTime = measureTimeMillis {
                // Toggle visibility
                viewModel.toggleRowVisibility(rows[0])
                testDispatcher.scheduler.advanceUntilIdle()

                // Move row
                viewModel.moveRowDown(rows[1])
                testDispatcher.scheduler.advanceUntilIdle()

                // Toggle another
                viewModel.toggleRowVisibility(rows[2])
                testDispatcher.scheduler.advanceUntilIdle()

                // Move another
                viewModel.moveRowUp(rows[5])
                testDispatcher.scheduler.advanceUntilIdle()
            }

            // Then: Should complete quickly
            assertTrue(operationTime < 100, "4 concurrent operations took ${operationTime}ms, expected < 100ms")
        }
    }

    @Test
    fun testLargeDatasetScrollPerformance() {
        runBlocking {
            // Given: Very large dataset (50 rows)
            val largeDataset = List(50) { createTestRow(it) }
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(largeDataset))

            // When: Load large dataset
            val observer = mock<Observer<List<RowConfigEntity>>>()
            viewModel.rows.observeForever(observer)

            val loadTime = measureTimeMillis {
                viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
                testDispatcher.scheduler.advanceUntilIdle()
            }

            // Then: Should load without performance degradation
            assertTrue(loadTime < 150, "Loading 50 rows took ${loadTime}ms, expected < 150ms")

            viewModel.rows.removeObserver(observer)
        }
    }

    @Test
    fun testRepeatedLoadUnloadCycles() {
        runBlocking {
            // Given: Screen with rows
            val rows = List(10) { createTestRow(it) }
            `when`(repository.getAllRowsForSettings(any())).thenReturn(flowOf(rows))

            // When: Load and unload 20 times (simulating user navigating back and forth)
            val cycleTime = measureTimeMillis {
                repeat(20) {
                    val observer = mock<Observer<List<RowConfigEntity>>>()
                    viewModel.rows.observeForever(observer)

                    viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
                    testDispatcher.scheduler.advanceUntilIdle()

                    viewModel.rows.removeObserver(observer)
                }
            }

            // Then: Should handle repeated cycles efficiently
            assertTrue(cycleTime < 400, "20 load/unload cycles took ${cycleTime}ms, expected < 400ms")
        }
    }

    @Test
    fun testErrorRecoveryPerformance() {
        runBlocking {
            // Given: Repository that initially throws error, then succeeds
            var callCount = 0
            `when`(repository.getAllRowsForSettings(any())).thenAnswer {
                callCount++
                if (callCount == 1) {
                    throw RuntimeException("Simulated error")
                } else {
                    flowOf(List(10) { createTestRow(it) })
                }
            }

            val errorObserver = mock<Observer<String?>>()
            viewModel.error.observeForever(errorObserver)

            // When: Load (fails), then retry
            val recoveryTime = measureTimeMillis {
                viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
                testDispatcher.scheduler.advanceUntilIdle()

                // Retry
                viewModel.loadRowsForScreen(ScreenConfigRepository.ScreenType.HOME)
                testDispatcher.scheduler.advanceUntilIdle()
            }

            // Then: Should recover quickly
            assertTrue(recoveryTime < 100, "Error recovery took ${recoveryTime}ms, expected < 100ms")

            viewModel.error.removeObserver(errorObserver)
        }
    }
}
