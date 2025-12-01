package com.test1.tv.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.test1.tv.data.Resource
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.model.home.HomeConfig
import com.test1.tv.data.model.home.HomeRowType
import com.test1.tv.data.model.home.PosterOrientation
import com.test1.tv.data.model.home.TraktListConfig
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.data.repository.ContinueWatchingRepository
import com.test1.tv.data.repository.HomeConfigRepository
import com.test1.tv.data.repository.MediaRepository
import com.test1.tv.data.repository.WatchStatusProvider
import com.test1.tv.data.repository.WatchStatusRepository
import com.test1.tv.ui.adapter.ContentRow
import com.test1.tv.ui.adapter.RowPresentation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException
import kotlin.math.max
import kotlin.math.min
import javax.inject.Inject

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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val mediaRepository: MediaRepository,
    private val homeConfigRepository: HomeConfigRepository,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val watchStatusRepository: WatchStatusRepository
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
    private val _refreshComplete = MutableLiveData<Boolean>()
    val refreshComplete: LiveData<Boolean> = _refreshComplete

    init {
        viewModelScope.launch(Dispatchers.IO) {
            watchStatusRepository.preload()
            WatchStatusProvider.set(watchStatusRepository)
        }
        viewModelScope.launch {
            buildRows()
            loadInitialRows()
            _refreshComplete.postValue(true)
        }
    }

    private suspend fun loadInitialRows(forceRefresh: Boolean = false) {
        _isLoading.value = true

        // Netflix strategy: Load first 2 rows immediately for instant UI
        if (rowStates.isNotEmpty()) {
            val first = rowStates[0]
            if (!first.hasMore && first.items.isNotEmpty()) {
                publishRows()
                if (_heroContent.value == null) {
                    first.items.firstOrNull { it.tmdbId != -1 }?.let {
                        _heroContent.value = it
                    }
                }
            } else {
                if (first.category == ContentRepository.CATEGORY_CONTINUE_WATCHING) {
                    loadContinueWatching(0, forceRefresh = forceRefresh)
                } else {
                    loadRowPage(0, page = 1, forceRefresh = forceRefresh)
                }
            }
        }

        _isLoading.value = false

        // Lazy load remaining rows with small delays (Netflix uses ~100ms stagger)
        kotlinx.coroutines.coroutineScope {
            rowStates.drop(1).forEachIndexed { idx, state ->
                launch {
                    kotlinx.coroutines.delay(50L * (idx + 1))
                    if (!state.hasMore && state.items.isNotEmpty()) {
                        publishRows()
                    } else {
                        val targetIndex = idx + 1
                        if (state.category == ContentRepository.CATEGORY_CONTINUE_WATCHING) {
                            loadContinueWatching(targetIndex, forceRefresh = forceRefresh)
                        } else {
                            loadRowPage(targetIndex, page = 1, forceRefresh = forceRefresh)
                        }
                    }
                }
            }
        }

        // Prefetch next pages after all rows loaded
        kotlinx.coroutines.delay(200)
        prefetchNextPages()
    }

    fun requestNextPage(rowIndex: Int) {
        val state = rowStates.getOrNull(rowIndex) ?: return
        if (state.isLoading || !state.hasMore) return

        viewModelScope.launch {
            if (state.category == ContentRepository.CATEGORY_CONTINUE_WATCHING) {
                loadContinueWatching(rowIndex)
            } else {
                loadRowPage(rowIndex, page = state.currentPage + 1, forceRefresh = false)
            }
        }
    }

    fun refreshAfterAuth() {
        viewModelScope.launch {
            _isLoading.value = true
            _refreshComplete.value = false
            _heroContent.value = null  // CRITICAL: Clear hero first to prevent desync

            rowStates.clear()
            _contentRows.value = emptyList()
            kotlinx.coroutines.delay(100)  // Let UI clear

            buildRows()

            if (rowStates.isNotEmpty()) {
                // Load first row synchronously to anchor hero + UI state
                val firstState = rowStates[0]
                if (firstState.category == ContentRepository.CATEGORY_CONTINUE_WATCHING) {
                    loadContinueWatching(0, forceRefresh = true)
                } else {
                    loadRowPage(0, page = 1, forceRefresh = true)
                }

                // Prime hero from the first row once data is available
                firstState.items.firstOrNull { it.tmdbId != -1 }?.let {
                    _heroContent.value = it
                }

                // Load remaining rows with a light stagger to avoid thrash
                rowStates.drop(1).forEachIndexed { idx, state ->
                    launch {
                        kotlinx.coroutines.delay(50L * (idx + 1))
                        val rowIndex = idx + 1
                        if (state.category == ContentRepository.CATEGORY_CONTINUE_WATCHING) {
                            loadContinueWatching(rowIndex, forceRefresh = true)
                        } else {
                            loadRowPage(rowIndex, page = 1, forceRefresh = true)
                        }
                    }
                }
            }

            // Set refresh complete AFTER loading finishes (not before)
            _isLoading.value = false
            _refreshComplete.postValue(true)
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
        val result: Result<List<ContentItem>> = when (state.category) {
            ContentRepository.CATEGORY_TRENDING_MOVIES -> {
                val resource = withTimeoutOrNull(15_000L) {
                    mediaRepository.getTrendingMovies(page).first { it !is Resource.Loading }
                } ?: Resource.Error(TimeoutException("Request timed out"), null)
                mapResource(resource)
            }
            ContentRepository.CATEGORY_POPULAR_MOVIES -> {
                val resource = withTimeoutOrNull(15_000L) {
                    mediaRepository.getPopularMovies(page).first { it !is Resource.Loading }
                } ?: Resource.Error(TimeoutException("Request timed out"), null)
                mapResource(resource)
            }
            ContentRepository.CATEGORY_TRENDING_SHOWS -> {
                val resource = withTimeoutOrNull(15_000L) {
                    mediaRepository.getTrendingShows(page).first { it !is Resource.Loading }
                } ?: Resource.Error(TimeoutException("Request timed out"), null)
                mapResource(resource)
            }
            ContentRepository.CATEGORY_POPULAR_SHOWS -> {
                val resource = withTimeoutOrNull(15_000L) {
                    mediaRepository.getPopularShows(page).first { it !is Resource.Loading }
                } ?: Resource.Error(TimeoutException("Request timed out"), null)
                mapResource(resource)
            }
            ContentRepository.CATEGORY_CONTINUE_WATCHING -> Result.success(emptyList())
            else -> Result.success(emptyList())
        }

        result.onSuccess { items ->
            applyRowItems(rowIndex, state, page, items)
        }.onFailure { throwable ->
            _error.value = "Failed to load ${state.title}: ${throwable.message}"
        }

        state.isLoading = false
    }

    private suspend fun loadContinueWatching(rowIndex: Int, forceRefresh: Boolean = false) {
        val repo = continueWatchingRepository
        val state = rowStates.getOrNull(rowIndex) ?: return
        if (state.isLoading) return
        if (!repo.hasAccount()) {
            if (state.items.isEmpty()) {
                state.items.add(createPlaceholderItem("Login with Trakt to populate"))
                publishRows()
            }
            state.hasMore = false
            return
        }
        state.isLoading = true
        val items = runCatching { repo.load(forceRefresh = forceRefresh) }.getOrDefault(emptyList())
        applyRowItems(rowIndex, state, page = 1, items = items)
        state.hasMore = false
        state.isLoading = false
    }

    private fun createContinueRowState(
        orientation: PosterOrientation,
        isAuthenticated: Boolean
    ): ContentRowState {
        val presentation = mapOrientation(orientation)
        if (!isAuthenticated) {
            return ContentRowState(
                category = ContentRepository.CATEGORY_CONTINUE_WATCHING,
                title = "Continue Watching",
                presentation = presentation,
                pageSize = 20,
                items = mutableListOf(createPlaceholderItem("Login with Trakt to populate")),
                currentPage = 1,
                hasMore = false
            )
        }
        return ContentRowState(
            category = ContentRepository.CATEGORY_CONTINUE_WATCHING,
            title = "Continue Watching",
            presentation = presentation,
            pageSize = 20,
            items = mutableListOf(),
            currentPage = 0,
            hasMore = true
        )
    }

    private fun createLoginPlaceholderRow(
        category: String,
        title: String,
        presentation: RowPresentation
    ): ContentRowState {
        return ContentRowState(
            category = category,
            title = title,
            presentation = presentation,
            pageSize = 1,
            items = mutableListOf(createPlaceholderItem("Login with Trakt to populate")),
            currentPage = 1,
            hasMore = false
        )
    }

    private fun createPlaceholderItem(message: String): ContentItem =
        ContentItem(
            id = -1,
            tmdbId = -1,
            imdbId = null,
            title = message,
            overview = null,
            posterUrl = null,
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
            if (_heroContent.value == null) {
                state.items.firstOrNull { it.tmdbId != -1 }?.let {
                    _heroContent.value = it
                }
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

    private fun mapResource(resource: Resource<List<ContentItem>>): Result<List<ContentItem>> {
        return when (resource) {
            is Resource.Success -> Result.success(resource.data)
            is Resource.Loading -> Result.success(resource.cachedData ?: emptyList())
            is Resource.Error -> {
                resource.cachedData?.let { cached ->
                    if (cached.isNotEmpty()) return Result.success(cached)
                }
                Result.failure(resource.exception)
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

    private suspend fun buildRows() {
        rowStates.clear()
        val isAuthenticated = continueWatchingRepository.hasAccount()
        buildRowsFromConfig(isAuthenticated)
    }

    private fun buildRowsFromConfig(isAuthenticated: Boolean) {
        val homeConfig = homeConfigRepository.loadConfig()
        val configRows = homeConfig?.home?.rows.orEmpty()
        if (configRows.isEmpty()) {
            val defaults = mutableListOf<ContentRowState>()
            defaults.add(
                createContinueRowState(
                    orientation = PosterOrientation.LANDSCAPE,
                    isAuthenticated = isAuthenticated
                )
            )
            defaults.addAll(
                listOf(
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
            rowStates.addAll(defaults)
            return
        }

        configRows.forEach { row ->
            val type = row.type ?: return@forEach
            val orientation = row.poster_orientation ?: PosterOrientation.PORTRAIT
            val requiresTrakt = row.requires_trakt == true
            if (requiresTrakt && !isAuthenticated && type != HomeRowType.CONTINUE_WATCHING) {
                rowStates.add(
                    createLoginPlaceholderRow(
                        category = row.id,
                        title = row.title,
                        presentation = mapOrientation(row.poster_orientation)
                    )
                )
                return@forEach
            }
            when (type) {
                HomeRowType.CONTINUE_WATCHING -> {
                    rowStates.add(
                        createContinueRowState(
                            orientation = orientation,
                            isAuthenticated = isAuthenticated
                        )
                    )
                }
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
