package com.test1.tv.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.test1.tv.data.local.AppDatabase
import com.test1.tv.data.local.entity.RowConfigEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RowConfigDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var rowConfigDao: RowConfigDao

    private val testRow1 = RowConfigEntity(
        id = "test_home_trending",
        screenType = "home",
        title = "Trending",
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
    )

    private val testRow2 = RowConfigEntity(
        id = "test_home_popular",
        screenType = "home",
        title = "Popular",
        rowType = "popular",
        contentType = "movies",
        presentation = "portrait",
        dataSourceUrl = null,
        defaultPosition = 1,
        position = 1,
        enabled = true,
        requiresAuth = false,
        pageSize = 20,
        isSystemRow = true
    )

    private val testRow3 = RowConfigEntity(
        id = "test_movies_trending",
        screenType = "movies",
        title = "Trending Movies",
        rowType = "trending",
        contentType = "movies",
        presentation = "portrait",
        dataSourceUrl = null,
        defaultPosition = 0,
        position = 0,
        enabled = false,
        requiresAuth = false,
        pageSize = 20,
        isSystemRow = false
    )

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        rowConfigDao = database.rowConfigDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insertAll and getEnabledRowsForScreen returns only enabled rows`() = runBlocking {
        // Insert test rows
        rowConfigDao.insertAll(listOf(testRow1, testRow2, testRow3))

        // Query enabled rows for home screen
        val homeRows = rowConfigDao.getEnabledRowsForScreen("home").first()

        // Should return 2 enabled rows for home
        assertEquals(2, homeRows.size)
        assertTrue(homeRows.all { it.screenType == "home" })
        assertTrue(homeRows.all { it.enabled })
        assertEquals("Trending", homeRows[0].title)
        assertEquals("Popular", homeRows[1].title)
    }

    @Test
    fun `getAllRowsForScreen returns all rows regardless of enabled status`() = runBlocking {
        rowConfigDao.insertAll(listOf(testRow1, testRow2, testRow3))

        // Query all rows for home screen
        val homeRows = rowConfigDao.getAllRowsForScreen("home").first()

        // Should return all 2 rows for home (including disabled)
        assertEquals(2, homeRows.size)
        assertTrue(homeRows.all { it.screenType == "home" })
    }

    @Test
    fun `getRowById returns correct row`() = runBlocking {
        rowConfigDao.insertAll(listOf(testRow1))

        val row = rowConfigDao.getRowById("test_home_trending")

        assertNotNull(row)
        assertEquals("Trending", row.title)
        assertEquals("home", row.screenType)
    }

    @Test
    fun `getRowById returns null for non-existent row`() = runBlocking {
        val row = rowConfigDao.getRowById("non_existent_id")
        assertNull(row)
    }

    @Test
    fun `setRowEnabled updates enabled status`() = runBlocking {
        rowConfigDao.insertAll(listOf(testRow1))

        // Disable the row
        rowConfigDao.setRowEnabled("test_home_trending", false)

        val row = rowConfigDao.getRowById("test_home_trending")
        assertNotNull(row)
        assertFalse(row.enabled)

        // Re-enable the row
        rowConfigDao.setRowEnabled("test_home_trending", true)

        val enabledRow = rowConfigDao.getRowById("test_home_trending")
        assertNotNull(enabledRow)
        assertTrue(enabledRow.enabled)
    }

    @Test
    fun `updateRowPosition changes position`() = runBlocking {
        rowConfigDao.insertAll(listOf(testRow1))

        // Update position
        rowConfigDao.updateRowPosition("test_home_trending", 5)

        val row = rowConfigDao.getRowById("test_home_trending")
        assertNotNull(row)
        assertEquals(5, row.position)
    }

    @Test
    fun `resetScreenToDefaults resets position and enables all rows for screen`() = runBlocking {
        // Insert a modified row (disabled, position changed)
        val modifiedRow = testRow1.copy(enabled = false, position = 10)
        rowConfigDao.insertAll(listOf(modifiedRow, testRow2))

        // Reset to defaults
        rowConfigDao.resetScreenToDefaults("home")

        val rows = rowConfigDao.getAllRowsForScreen("home").first()

        // All rows should be enabled
        assertTrue(rows.all { it.enabled })

        // Positions should match defaultPosition
        rows.forEach { row ->
            assertEquals(row.defaultPosition, row.position)
        }
    }

    @Test
    fun `deleteNonSystemRow deletes non-system rows only`() = runBlocking {
        rowConfigDao.insertAll(listOf(testRow1, testRow2))

        // Try to delete non-system row (should succeed)
        rowConfigDao.deleteNonSystemRow("test_home_trending")
        val deletedRow = rowConfigDao.getRowById("test_home_trending")
        assertNull(deletedRow)

        // Try to delete system row (should not delete)
        rowConfigDao.deleteNonSystemRow("test_home_popular")
        val systemRow = rowConfigDao.getRowById("test_home_popular")
        assertNotNull(systemRow)
    }

    @Test
    fun `getRowCountForScreen returns correct count`() = runBlocking {
        rowConfigDao.insertAll(listOf(testRow1, testRow2, testRow3))

        val homeCount = rowConfigDao.getRowCountForScreen("home")
        val moviesCount = rowConfigDao.getRowCountForScreen("movies")

        assertEquals(2, homeCount)
        assertEquals(1, moviesCount)
    }

    @Test
    fun `rows are ordered by position`() = runBlocking {
        val row1 = testRow1.copy(position = 2)
        val row2 = testRow2.copy(position = 0)
        val row3 = testRow1.copy(id = "test_home_third", position = 1)

        rowConfigDao.insertAll(listOf(row1, row2, row3))

        val rows = rowConfigDao.getEnabledRowsForScreen("home").first()

        // Should be ordered by position: 0, 1, 2
        assertEquals(0, rows[0].position)
        assertEquals(1, rows[1].position)
        assertEquals(2, rows[2].position)
    }

    @Test
    fun `insert with REPLACE strategy updates existing row`() = runBlocking {
        rowConfigDao.insert(testRow1)

        // Insert same ID with different title
        val updatedRow = testRow1.copy(title = "Updated Title")
        rowConfigDao.insert(updatedRow)

        val row = rowConfigDao.getRowById("test_home_trending")
        assertNotNull(row)
        assertEquals("Updated Title", row.title)

        // Should still only have 1 row
        val count = rowConfigDao.getRowCountForScreen("home")
        assertEquals(1, count)
    }

    @Test
    fun `updateRow updates all fields`() = runBlocking {
        rowConfigDao.insert(testRow1)

        val updatedRow = testRow1.copy(
            title = "New Title",
            position = 5,
            enabled = false,
            presentation = "landscape"
        )
        rowConfigDao.updateRow(updatedRow)

        val row = rowConfigDao.getRowById("test_home_trending")
        assertNotNull(row)
        assertEquals("New Title", row.title)
        assertEquals(5, row.position)
        assertFalse(row.enabled)
        assertEquals("landscape", row.presentation)
    }
}
