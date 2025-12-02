package com.test1.tv.ui.settings.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.test1.tv.data.local.entity.RowConfigEntity
import com.test1.tv.data.repository.ScreenConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
}
