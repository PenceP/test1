package com.test1.tv.data.repository

import android.content.Context
import com.test1.tv.data.config.DefaultRowConfigs
import com.test1.tv.data.local.dao.RowConfigDao
import com.test1.tv.data.local.entity.RowConfigEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenConfigRepository @Inject constructor(
    private val rowConfigDao: RowConfigDao,
    private val traktAccountRepository: TraktAccountRepository,
    @ApplicationContext private val context: Context
) {
    enum class ScreenType(val key: String) {
        HOME("home"),
        MOVIES("movies"),
        TV_SHOWS("tvshows")
    }

    /**
     * Get enabled rows for a specific screen.
     * Returns a Flow that emits updated list whenever database changes.
     * Only returns rows where enabled = true and authentication requirements are met.
     */
    fun getRowsForScreen(screen: ScreenType): Flow<List<RowConfigEntity>> =
        kotlinx.coroutines.flow.flow {
            rowConfigDao.getEnabledRowsForScreen(screen.key).collect { rows ->
                // Filter out rows that require auth if user is not authenticated
                val account = traktAccountRepository.getAccount()
                val isAuthenticated = account != null

                val filteredRows = rows.filter { row ->
                    !row.requiresAuth || isAuthenticated
                }

                emit(filteredRows)
            }
        }

    /**
     * Get all rows for a specific screen (including disabled ones).
     * Used in Settings UI to show all available rows.
     */
    fun getAllRowsForSettings(screen: ScreenType): Flow<List<RowConfigEntity>> =
        rowConfigDao.getAllRowsForScreen(screen.key)

    /**
     * Toggle visibility of a specific row.
     * @param rowId The unique identifier of the row
     * @param enabled True to show the row, false to hide it
     */
    suspend fun toggleRowVisibility(rowId: String, enabled: Boolean) {
        rowConfigDao.setRowEnabled(rowId, enabled)
    }

    /**
     * Reorder a row to a new position.
     * @param rowId The unique identifier of the row to move
     * @param newPosition The new position (0-indexed)
     */
    suspend fun reorderRow(rowId: String, newPosition: Int) {
        rowConfigDao.updateRowPosition(rowId, newPosition)
    }

    /**
     * Reset all rows for a specific screen to their default configuration.
     * This restores original positions and enables all rows.
     * @param screen The screen to reset
     */
    suspend fun resetScreenToDefaults(screen: ScreenType) {
        rowConfigDao.resetScreenToDefaults(screen.key)
    }

    /**
     * Initialize database with default row configurations.
     * This should be called on first app launch.
     * Checks if rows already exist to avoid duplicates.
     */
    suspend fun initializeDefaults() {
        val existingHomeRows = rowConfigDao.getRowCountForScreen(ScreenType.HOME.key)
        if (existingHomeRows == 0) {
            // No rows exist, insert defaults for all screens
            rowConfigDao.insertAll(DefaultRowConfigs.all)
        }
    }

    /**
     * Get a specific row by its ID.
     * @param rowId The unique identifier of the row
     * @return The row entity or null if not found
     */
    suspend fun getRowById(rowId: String): RowConfigEntity? =
        rowConfigDao.getRowById(rowId)

    /**
     * Update a specific row configuration.
     * @param row The updated row entity
     */
    suspend fun updateRow(row: RowConfigEntity) {
        rowConfigDao.updateRow(row)
    }

    /**
     * Get the total count of rows for a specific screen.
     * @param screen The screen to count rows for
     * @return The number of rows
     */
    suspend fun getRowCount(screen: ScreenType): Int =
        rowConfigDao.getRowCountForScreen(screen.key)

    /**
     * Delete a non-system row.
     * System rows cannot be deleted, only disabled.
     * @param rowId The unique identifier of the row to delete
     */
    suspend fun deleteRow(rowId: String) {
        rowConfigDao.deleteNonSystemRow(rowId)
    }

    /**
     * Swap positions of two rows.
     * Useful for drag-and-drop reordering in UI.
     * @param rowId1 First row ID
     * @param rowId2 Second row ID
     */
    suspend fun swapRowPositions(rowId1: String, rowId2: String) {
        val row1 = rowConfigDao.getRowById(rowId1) ?: return
        val row2 = rowConfigDao.getRowById(rowId2) ?: return

        val tempPosition = row1.position
        rowConfigDao.updateRowPosition(row1.id, row2.position)
        rowConfigDao.updateRowPosition(row2.id, tempPosition)
    }

    /**
     * Insert a new row (e.g., a Trakt liked list).
     * Position is set to the end of the current rows.
     * @param row The row entity to insert
     */
    suspend fun insertRow(row: RowConfigEntity) {
        rowConfigDao.insert(row)
    }

    /**
     * Get the next available position for a new row on a screen.
     * @param screen The screen type
     * @return The next position number
     */
    suspend fun getNextPosition(screen: ScreenType): Int {
        val rows = rowConfigDao.getAllRowsForScreen(screen.key).first()
        return (rows.maxOfOrNull { it.position } ?: -1) + 1
    }

    /**
     * Update the presentation/orientation of a row.
     * @param rowId The unique identifier of the row
     * @param presentation The new presentation value ("landscape", "portrait", "square")
     */
    suspend fun updateRowPresentation(rowId: String, presentation: String) {
        val row = rowConfigDao.getRowById(rowId) ?: return
        rowConfigDao.updateRow(row.copy(presentation = presentation))
    }
}
