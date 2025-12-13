package com.strmr.tv.ui.base

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strmr.tv.data.local.entity.toRowState
import com.strmr.tv.data.model.ContentItem
import com.strmr.tv.data.repository.ScreenConfigRepository
import com.strmr.tv.data.repository.WatchStatusProvider
import com.strmr.tv.data.repository.WatchStatusRepository
import com.strmr.tv.domain.ContentLoaderUseCase
import com.strmr.tv.ui.adapter.ContentRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Base ViewModel for content screens (Home, Movies, TV Shows).
 * Provides shared functionality for loading and managing content rows.
 */
abstract class BaseContentViewModel(
    protected val screenConfigRepository: ScreenConfigRepository,
    protected val contentLoader: ContentLoaderUseCase,
    protected val watchStatusRepository: WatchStatusRepository,
    protected val screenType: ScreenConfigRepository.ScreenType
) : ViewModel() {

    protected val rowStates = mutableListOf<ContentRowState>()

    private val _contentRows = MutableLiveData<List<ContentRow>>()
    val contentRows: LiveData<List<ContentRow>> = _contentRows

    private val _rowAppendEvents = MutableLiveData<RowAppendEvent>()
    val rowAppendEvents: LiveData<RowAppendEvent> = _rowAppendEvents

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _heroContent = MutableLiveData<ContentItem?>()
    val heroContent: LiveData<ContentItem?> = _heroContent

    private val _refreshComplete = MutableLiveData<Boolean>()
    val refreshComplete: LiveData<Boolean> = _refreshComplete

    init {
        viewModelScope.launch(Dispatchers.IO) {
            watchStatusRepository.preload()
            WatchStatusProvider.set(watchStatusRepository)
        }
        viewModelScope.launch {
            buildRowsFromConfig()
            loadAllRows()
            _refreshComplete.postValue(true)
        }
    }

    /**
     * Build row states from database configuration.
     * Subclasses can override to add custom row building logic.
     * Pre-populates rows with skeleton items for instant UI feedback.
     */
    protected open suspend fun buildRowsFromConfig() {
        screenConfigRepository.getRowsForScreen(screenType).collect { configs ->
            // Only rebuild if row count changed (avoid rebuilding on position/visibility changes)
            if (rowStates.size != configs.size) {
                rowStates.clear()
                configs.forEach { config ->
                    val state = config.toRowState()
                    // Pre-populate with skeleton items (6 placeholders)
                    state.items.addAll(ContentItem.createPlaceholders(6))
                    rowStates.add(state)
                }

                // Publish skeleton rows immediately so UI shows loading state
                publishRows()

                if (rowStates.isNotEmpty()) {
                    loadAllRows()
                }
            }
        }
    }

    /**
     * Load all rows with optimized progressive loading.
     * First row loads immediately, critical rows stagger at 200ms,
     * non-essential rows defer by 1 second to reduce startup API load.
     */
    protected open suspend fun loadAllRows(forceRefresh: Boolean = false) {
        _isLoading.value = true

        // Load first row immediately for instant UI
        rowStates.firstOrNull()?.let { first ->
            loadRowContent(0, first, forceRefresh = forceRefresh)
        }

        _isLoading.value = false

        // Categorize rows by priority
        val (criticalRows, deferredRows) = rowStates.drop(1).withIndex().partition { (_, state) ->
            // Critical: Trending, Popular, Continue Watching
            state.rowType in listOf("trending", "popular", "continue_watching")
        }

        // Load critical rows with moderate stagger (200ms)
        kotlinx.coroutines.coroutineScope {
            criticalRows.forEach { (idx, state) ->
                launch {
                    delay(200L * (idx + 1))
                    loadRowContent(idx + 1, state, forceRefresh = forceRefresh)
                }
            }
        }

        // Defer non-essential rows (Networks, Directors, Studios, Collections) by 1 second
        kotlinx.coroutines.coroutineScope {
            deferredRows.forEach { (idx, state) ->
                launch {
                    delay(1000L + (150L * idx))  // Start after 1s, then 150ms stagger
                    loadRowContent(idx + 1, state, forceRefresh = forceRefresh)
                }
            }
        }

        // Prefetch next pages after all rows loaded
        delay(500)
        prefetchNextPages()
    }

    /**
     * Load content for a specific row.
     * @param rowIndex The index of the row in rowStates
     * @param state The row state
     * @param forceRefresh Whether to force refresh from network
     */
    protected open suspend fun loadRowContent(
        rowIndex: Int,
        state: ContentRowState,
        forceRefresh: Boolean
    ) {
        try {
            state.isLoading = true

            val items = contentLoader.loadForRowType(
                rowType = state.rowType,
                contentType = state.contentType,
                page = 1,
                forceRefresh = forceRefresh,
                dataSourceUrl = state.dataSourceUrl
            )

            state.items.clear()
            state.items.addAll(items)
            state.currentPage = 1
            // Static rows (collections, directors, networks) have no pagination
            state.hasMore = when (state.rowType) {
                "collections", "directors", "networks" -> false
                else -> items.size >= state.pageSize
            }

            publishRows()

            // Set hero content from first row's first item
            if (_heroContent.value == null && rowIndex == 0 && items.isNotEmpty()) {
                _heroContent.value = items.firstOrNull { it.tmdbId != -1 }
            }

            state.isLoading = false
        } catch (e: Exception) {
            _error.value = "Failed to load ${state.title}: ${e.message}"
            state.isLoading = false
        }
    }

    /**
     * Request next page of content for a specific row.
     * Called by UI when user scrolls to end of row.
     */
    fun requestNextPage(rowIndex: Int) {
        val state = rowStates.getOrNull(rowIndex) ?: return
        if (state.isLoading || !state.hasMore) return

        viewModelScope.launch {
            try {
                state.isLoading = true
                val nextPage = state.currentPage + 1

                val newItems = contentLoader.loadForRowType(
                    rowType = state.rowType,
                    contentType = state.contentType,
                    page = nextPage,
                    forceRefresh = false,
                    dataSourceUrl = state.dataSourceUrl
                )

                if (newItems.isNotEmpty()) {
                    state.items.addAll(newItems)
                    state.currentPage = nextPage
                    state.hasMore = newItems.size >= state.pageSize
                    _rowAppendEvents.value = RowAppendEvent(rowIndex, newItems)
                } else {
                    state.hasMore = false
                }

                state.isLoading = false
            } catch (e: Exception) {
                _error.value = "Failed to load more: ${e.message}"
                state.isLoading = false
            }
        }
    }

    /**
     * Publish current row states to UI.
     */
    protected fun publishRows() {
        _contentRows.value = rowStates.map {
            ContentRow(
                title = it.title,
                items = it.items,
                presentation = it.presentation
            )
        }
    }

    /**
     * Prefetch next pages for first few rows.
     */
    private suspend fun prefetchNextPages() {
        rowStates.take(3).forEachIndexed { idx, state ->
            if (state.hasMore && state.prefetchingPage == null) {
                viewModelScope.launch {
                    state.prefetchingPage = state.currentPage + 1
                    // Prefetch logic here if needed
                }
            }
        }
    }

    /**
     * Update the hero content displayed in the header.
     */
    fun updateHeroContent(item: ContentItem) {
        _heroContent.value = item
    }

    /**
     * Refresh all content after authentication change.
     */
    fun refreshAfterAuth() {
        viewModelScope.launch {
            _isLoading.value = true
            _refreshComplete.value = false
            _heroContent.value = null

            rowStates.clear()
            _contentRows.value = emptyList()

            buildRowsFromConfig()
            loadAllRows(forceRefresh = true)

            _refreshComplete.value = true
        }
    }

    /**
     * Clean up cache.
     */
    fun cleanupCache() {
        viewModelScope.launch {
            runCatching { contentLoader.cleanupCache() }
        }
    }
}
