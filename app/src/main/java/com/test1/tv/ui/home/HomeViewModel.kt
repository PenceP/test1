package com.test1.tv.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.ui.adapter.ContentRow
import com.test1.tv.ui.adapter.RowPresentation
import kotlinx.coroutines.launch

private data class ContentRowState(
    val category: String,
    val title: String,
    val presentation: RowPresentation = RowPresentation.PORTRAIT,
    val pageSize: Int = 20,
    val items: MutableList<ContentItem> = mutableListOf(),
    var currentPage: Int = 0,
    var hasMore: Boolean = true,
    var isLoading: Boolean = false,
    var prefetchingPage: Int? = null
)

data class RowAppendEvent(
    val rowIndex: Int,
    val newItems: List<ContentItem>
)

class HomeViewModel(
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val rowStates = mutableListOf(
        ContentRowState(
            category = ContentRepository.CATEGORY_CONTINUE_WATCHING,
            title = "Continue Watching",
            presentation = RowPresentation.LANDSCAPE_16_9,
            pageSize = 12
        ),
        ContentRowState(ContentRepository.CATEGORY_TRENDING_MOVIES, "Trending Movies"),
        ContentRowState(ContentRepository.CATEGORY_POPULAR_MOVIES, "Popular Movies"),
        ContentRowState(ContentRepository.CATEGORY_TRENDING_SHOWS, "Trending Shows"),
        ContentRowState(ContentRepository.CATEGORY_POPULAR_SHOWS, "Popular Shows")
    )

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

    init {
        loadInitialRows()
    }

    private fun loadInitialRows(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            rowStates.forEachIndexed { index, _ ->
                loadRowPage(index, page = 1, forceRefresh = forceRefresh)
            }
            _isLoading.value = false
            prefetchNextPages()
        }
    }

    fun requestNextPage(rowIndex: Int) {
        val state = rowStates.getOrNull(rowIndex) ?: return
        if (state.isLoading || !state.hasMore) return

        viewModelScope.launch {
            loadRowPage(rowIndex, page = state.currentPage + 1, forceRefresh = false)
        }
    }

    private suspend fun loadRowPage(
        rowIndex: Int,
        page: Int,
        forceRefresh: Boolean
    ) {
        val state = rowStates.getOrNull(rowIndex) ?: return
        if (state.isLoading) return
        if (!forceRefresh && page > 1 && !state.hasMore) return

        state.isLoading = true
        val result = when (state.category) {
            ContentRepository.CATEGORY_TRENDING_MOVIES ->
                contentRepository.getTrendingMoviesPage(page, state.pageSize, forceRefresh)
            ContentRepository.CATEGORY_POPULAR_MOVIES ->
                contentRepository.getPopularMoviesPage(page, state.pageSize, forceRefresh)
            ContentRepository.CATEGORY_TRENDING_SHOWS ->
                contentRepository.getTrendingShowsPage(page, state.pageSize, forceRefresh)
            ContentRepository.CATEGORY_POPULAR_SHOWS ->
                contentRepository.getPopularShowsPage(page, state.pageSize, forceRefresh)
            ContentRepository.CATEGORY_CONTINUE_WATCHING ->
                contentRepository.getTrendingShowsPage(page, state.pageSize, forceRefresh)
            else -> Result.success(emptyList())
        }

        result.onSuccess { items ->
            applyRowItems(rowIndex, state, page, items)
        }.onFailure { throwable ->
            _error.value = "Failed to load ${state.title}: ${throwable.message}"
        }

        state.isLoading = false
    }

    private fun applyRowItems(
        rowIndex: Int,
        state: ContentRowState,
        page: Int,
        items: List<ContentItem>
    ) {
        if (page == 1) {
            state.items.clear()
        } else {
            val expectedOffset = (page - 1) * state.pageSize
            if (state.items.size > expectedOffset) {
                state.items.subList(expectedOffset, state.items.size).clear()
            }
        }

        state.items.addAll(items)
        state.currentPage = page
        state.hasMore = items.size >= state.pageSize

        if (page == 1) {
            publishRows()
            if (_heroContent.value == null && state.items.isNotEmpty()) {
                _heroContent.value = state.items.first()
            }
        } else if (items.isNotEmpty()) {
            _rowAppendEvents.value = RowAppendEvent(rowIndex, items.toList())
        }

        prefetchNextForState(state)
    }

    private fun publishRows() {
        _contentRows.value = rowStates.map {
            ContentRow(
                title = it.title,
                items = it.items,
                presentation = it.presentation
            )
        }
    }

    private fun prefetchNextPages() {
        rowStates.forEach { state ->
            prefetchNextForState(state)
        }
    }

    private fun prefetchNextForState(state: ContentRowState) {
        if (!state.hasMore || state.currentPage == 0) return
        val nextPage = state.currentPage + 1
        if (state.prefetchingPage == nextPage) return

        state.prefetchingPage = nextPage
        viewModelScope.launch {
            try {
                contentRepository.prefetchCategoryPage(
                    category = state.category,
                    page = nextPage,
                    pageSize = state.pageSize
                )
            } finally {
                if (state.prefetchingPage == nextPage) {
                    state.prefetchingPage = null
                }
            }
        }
    }

    fun updateHeroContent(item: ContentItem) {
        _heroContent.value = item
    }

    fun cleanupCache() {
        viewModelScope.launch {
            try {
                contentRepository.cleanupCache()
            } catch (_: Exception) {
                // ignore cleanup errors
            }
        }
    }
}
