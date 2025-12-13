package com.strmr.tv.ui.settings.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strmr.tv.data.local.entity.RowConfigEntity
import com.strmr.tv.data.repository.ScreenConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RowCustomizationViewModel @Inject constructor(
    private val screenConfigRepository: ScreenConfigRepository
) : ViewModel() {

    private val _rows = MutableLiveData<List<RowConfigEntity>>()
    val rows: LiveData<List<RowConfigEntity>> = _rows

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var currentScreen = ScreenConfigRepository.ScreenType.HOME
    private var loadRowsJob: Job? = null

    init {
        loadRowsForScreen(currentScreen)
    }

    fun loadRowsForScreen(screen: ScreenConfigRepository.ScreenType) {
        currentScreen = screen

        // Cancel previous collection job to prevent multiple Flows from competing
        loadRowsJob?.cancel()

        loadRowsJob = viewModelScope.launch {
            try {
                screenConfigRepository.getAllRowsForSettings(screen)
                    .collect { _rows.value = it }
            } catch (e: CancellationException) {
                // Expected when switching screens - don't show error
                throw e  // Re-throw to properly propagate cancellation
            } catch (e: Exception) {
                _error.value = "Failed to load rows: ${e.message}"
            }
        }
    }

    fun toggleRowVisibility(row: RowConfigEntity) {
        viewModelScope.launch {
            try {
                screenConfigRepository.toggleRowVisibility(row.id, !row.enabled)
            } catch (e: Exception) {
                _error.value = "Failed to toggle visibility: ${e.message}"
            }
        }
    }

    fun moveRowUp(row: RowConfigEntity) {
        val currentList = _rows.value ?: return
        val index = currentList.indexOf(row)
        if (index > 0) {
            swapRows(index, index - 1)
        }
    }

    fun moveRowDown(row: RowConfigEntity) {
        val currentList = _rows.value ?: return
        val index = currentList.indexOf(row)
        if (index < currentList.size - 1) {
            swapRows(index, index + 1)
        }
    }

    private fun swapRows(fromIndex: Int, toIndex: Int) {
        val currentList = _rows.value?.toMutableList() ?: return
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return

        viewModelScope.launch {
            try {
                val fromRow = currentList[fromIndex]
                val toRow = currentList[toIndex]

                screenConfigRepository.swapRowPositions(fromRow.id, toRow.id)
            } catch (e: Exception) {
                _error.value = "Failed to reorder rows: ${e.message}"
            }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            try {
                screenConfigRepository.resetScreenToDefaults(currentScreen)
            } catch (e: Exception) {
                _error.value = "Failed to reset to defaults: ${e.message}"
            }
        }
    }

    /**
     * Cycle the row's orientation/presentation between landscape -> portrait -> square -> landscape.
     * @param row The row to update
     */
    fun cycleRowOrientation(row: RowConfigEntity) {
        viewModelScope.launch {
            try {
                val nextPresentation = when (row.presentation) {
                    "landscape" -> "portrait"
                    "portrait" -> "square"
                    "square" -> "landscape"
                    else -> "portrait" // Default cycle from unknown
                }
                screenConfigRepository.updateRowPresentation(row.id, nextPresentation)
            } catch (e: Exception) {
                _error.value = "Failed to change orientation: ${e.message}"
            }
        }
    }

    /**
     * Add a Trakt list as a new row on the current screen.
     * @param title The display title for the row
     * @param username The Trakt username who owns the list
     * @param listSlug The list slug identifier
     * @param screenType The screen to add the row to
     */
    fun addTraktListRow(
        title: String,
        username: String,
        listSlug: String,
        screenType: ScreenConfigRepository.ScreenType
    ) {
        viewModelScope.launch {
            try {
                val nextPosition = screenConfigRepository.getNextPosition(screenType)
                val newRow = RowConfigEntity(
                    id = UUID.randomUUID().toString(),
                    screenType = screenType.key,
                    title = title,
                    rowType = "trakt_list",
                    contentType = null, // Mixed content
                    presentation = "landscape",
                    dataSourceUrl = "trakt_list:$username:$listSlug",
                    defaultPosition = nextPosition,
                    position = nextPosition,
                    enabled = true,
                    requiresAuth = true,
                    pageSize = 20,
                    isSystemRow = false
                )
                screenConfigRepository.insertRow(newRow)
            } catch (e: Exception) {
                _error.value = "Failed to add list: ${e.message}"
            }
        }
    }
}
