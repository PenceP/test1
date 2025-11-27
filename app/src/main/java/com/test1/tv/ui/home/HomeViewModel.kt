package com.test1.tv.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.model.home.HomeConfig
import com.test1.tv.data.model.home.HomeRowType
import com.test1.tv.data.model.home.PosterOrientation
import com.test1.tv.data.model.home.TraktListConfig
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.ui.adapter.ContentRow
import com.test1.tv.ui.adapter.RowPresentation
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

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
    private val contentRepository: ContentRepository,
    private val homeConfig: HomeConfig?
) : ViewModel() {

    private val rowStates = mutableListOf<ContentRowState>()

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
        buildRowsFromConfig()
        loadInitialRows()
    }

    private fun loadInitialRows(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true

            // Netflix strategy: Load first 2 rows immediately for instant UI
            if (rowStates.isNotEmpty()) {
                val first = rowStates[0]
                if (!first.hasMore && first.items.isNotEmpty()) {
                    publishRows()
                    if (_heroContent.value == null) {
                        _heroContent.value = first.items.first()
                    }
                } else {
                    loadRowPage(0, page = 1, forceRefresh = forceRefresh)
                }
            }

            _isLoading.value = false

            // Lazy load remaining rows with small delays (Netflix uses ~100ms stagger)
            rowStates.drop(1).forEachIndexed { idx, state ->
                launch {
                    kotlinx.coroutines.delay(50L * (idx + 1))
                    if (!state.hasMore && state.items.isNotEmpty()) {
                        publishRows()
                    } else {
                        loadRowPage(idx + 1, page = 1, forceRefresh = forceRefresh)
                    }
                }
            }

            // Prefetch next pages after all rows loaded
            kotlinx.coroutines.delay(200)
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

    private fun buildRowsFromConfig() {
        val configRows = homeConfig?.home?.rows.orEmpty()
        if (configRows.isEmpty()) {
            rowStates.addAll(
                listOf(
                    ContentRowState(
                        category = ContentRepository.CATEGORY_CONTINUE_WATCHING,
                        title = "Continue Watching",
                        presentation = RowPresentation.LANDSCAPE_16_9,
                        pageSize = 12
                    ),
                    ContentRowState(
                        category = ContentRepository.CATEGORY_TRENDING_MOVIES,
                        title = "Trending Movies",
                        presentation = RowPresentation.PORTRAIT
                    ),
                    ContentRowState(
                        category = ContentRepository.CATEGORY_POPULAR_MOVIES,
                        title = "Popular Movies",
                        presentation = RowPresentation.PORTRAIT
                    ),
                    ContentRowState(
                        category = ContentRepository.CATEGORY_TRENDING_SHOWS,
                        title = "Trending Shows",
                        presentation = RowPresentation.LANDSCAPE_16_9
                    ),
                    ContentRowState(
                        category = ContentRepository.CATEGORY_POPULAR_SHOWS,
                        title = "Popular Shows",
                        presentation = RowPresentation.LANDSCAPE_16_9
                    )
                )
            )
            return
        }

        configRows.forEach { row ->
            val type = row.type ?: return@forEach
            val orientation = row.poster_orientation ?: PosterOrientation.PORTRAIT
            when (type) {
                HomeRowType.TRAKT_LIST -> {
                    val state = createTraktListState(row.trakt_list, row.title, orientation)
                    if (state != null) rowStates.add(state)
                }
                HomeRowType.COLLECTION -> {
                    val items = row.items.orEmpty().mapIndexed { index, item ->
                        ContentItem(
                            id = index,
                            tmdbId = index,
                            imdbId = null,
                            title = item.label,
                            overview = null,
                            posterUrl = item.image_url,
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
                    }.toMutableList()
                    rowStates.add(
                        ContentRowState(
                            category = row.id,
                            title = row.title,
                            presentation = mapOrientation(row.poster_orientation),
                            pageSize = max(items.size, 1),
                            items = items,
                            currentPage = 1,
                            hasMore = false
                        )
                    )
                }
            }
        }
    }

    private fun createTraktListState(
        traktList: TraktListConfig?,
        title: String,
        orientation: PosterOrientation
    ): ContentRowState? {
        traktList ?: return null
        if (traktList.list_type != "builtin") return null

        val category = when (traktList.slug.lowercase()) {
            "trending" -> if (traktList.kind == "movies") {
                ContentRepository.CATEGORY_TRENDING_MOVIES
            } else if (traktList.kind == "shows") {
                ContentRepository.CATEGORY_TRENDING_SHOWS
            } else null
            "popular" -> if (traktList.kind == "movies") {
                ContentRepository.CATEGORY_POPULAR_MOVIES
            } else if (traktList.kind == "shows") {
                ContentRepository.CATEGORY_POPULAR_SHOWS
            } else null
            else -> null
        } ?: return null

        val pageSize = if (orientation == PosterOrientation.LANDSCAPE) 12 else 20
        return ContentRowState(
            category = category,
            title = title,
            presentation = mapOrientation(orientation),
            pageSize = pageSize
        )
    }

    private fun mapOrientation(orientation: PosterOrientation?): RowPresentation {
        return when (orientation) {
            PosterOrientation.LANDSCAPE -> RowPresentation.LANDSCAPE_16_9
            PosterOrientation.SQUARE -> RowPresentation.PORTRAIT
            else -> RowPresentation.PORTRAIT
        }
    }
}
