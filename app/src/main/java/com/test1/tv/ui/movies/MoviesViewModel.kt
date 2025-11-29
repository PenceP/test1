package com.test1.tv.ui.movies

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.test1.tv.data.Resource
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.data.repository.MediaRepository
import com.test1.tv.data.repository.WatchStatusProvider
import com.test1.tv.data.repository.WatchStatusRepository
import com.test1.tv.ui.adapter.ContentRow
import com.test1.tv.ui.adapter.RowPresentation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val mediaRepository: MediaRepository,
    private val watchStatusRepository: WatchStatusRepository
) : ViewModel() {

    private val _contentRows = MutableLiveData<List<ContentRow>>()
    val contentRows: LiveData<List<ContentRow>> = _contentRows

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _heroContent = MutableLiveData<ContentItem?>()
    val heroContent: LiveData<ContentItem?> = _heroContent

    private var trendingPage = 1
    private var popularPage = 1
    private var trendingHasMore = true
    private var popularHasMore = true
    private var trendingLoading = false
    private var popularLoading = false
    private val pageSize = 20

    private val rows = mutableListOf<ContentRow>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            watchStatusRepository.preload()
            WatchStatusProvider.set(watchStatusRepository)
        }
        loadMovieContent()
    }

    fun loadMovieContent(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                rows.clear()
                trendingPage = 1
                popularPage = 1
                trendingHasMore = true
                popularHasMore = true

                val trending = loadTrendingPage(trendingPage, forceRefresh)
                if (trending.isNotEmpty()) {
                    rows.add(
                        ContentRow(
                            title = "Trending Movies",
                            items = trending.toMutableList(),
                            presentation = RowPresentation.PORTRAIT
                        )
                    )
                    if (_heroContent.value == null) {
                        _heroContent.value = trending.first()
                    }
                    trendingHasMore = trending.size >= pageSize
                }

                val popular = contentRepository.getPopularMovies(forceRefresh).getOrDefault(emptyList())
                if (popular.isNotEmpty()) {
                    rows.add(
                        ContentRow(
                            title = "Popular Movies",
                            items = popular.toMutableList(),
                            presentation = RowPresentation.PORTRAIT
                        )
                    )
                    popularHasMore = popular.size >= pageSize
                }

                contentRepository.getLatest4KMovies(forceRefresh).onSuccess { movies ->
                    if (movies.isNotEmpty()) {
                        rows.add(
                            ContentRow(
                                title = "Latest 4K Releases",
                                items = movies.toMutableList(),
                                presentation = RowPresentation.PORTRAIT
                            )
                        )
                    }
                }.onFailure { e ->
                    _error.value = "Failed to load 4K releases: ${e.message}"
                }

                _contentRows.value = rows.toList()
            } catch (e: Exception) {
                _error.value = "Failed to load content: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun requestNextPage(rowIndex: Int) {
        when (rowIndex) {
            0 -> loadMoreTrending()
            1 -> loadMorePopular()
        }
    }

    private fun loadMoreTrending() {
        if (!trendingHasMore || trendingLoading) return
        trendingLoading = true
        viewModelScope.launch {
            val nextPage = trendingPage + 1
            val newItems = loadTrendingPage(nextPage, forceRefresh = false)
            if (newItems.isNotEmpty()) {
                trendingPage = nextPage
                trendingHasMore = newItems.size >= pageSize
                appendToRow(0, newItems)
            } else {
                trendingHasMore = false
            }
            trendingLoading = false
        }
    }

    private fun loadMorePopular() {
        if (!popularHasMore || popularLoading) return
        popularLoading = true
        viewModelScope.launch {
            val nextPage = popularPage + 1
            val popularResult = contentRepository.getPopularMovies(forceRefresh = true).getOrDefault(emptyList())
            if (popularResult.isNotEmpty()) {
                popularPage = nextPage
                popularHasMore = popularResult.size >= pageSize
                appendToRow(1, popularResult)
            } else {
                popularHasMore = false
            }
            popularLoading = false
        }
    }

    private suspend fun loadTrendingPage(page: Int, forceRefresh: Boolean): List<ContentItem> {
        val resource = mediaRepository.getTrendingMovies(page).last()
        return when (resource) {
            is Resource.Success -> resource.data
            is Resource.Loading -> resource.cachedData ?: emptyList()
            is Resource.Error -> resource.cachedData ?: emptyList()
        }
    }

    private fun appendToRow(rowIndex: Int, newItems: List<ContentItem>) {
        val mutable = rows.getOrNull(rowIndex) ?: return
        mutable.items.addAll(newItems)
        _contentRows.value = rows.toList()
    }

    fun updateHeroContent(item: ContentItem) {
        _heroContent.value = item
    }

    fun cleanupCache() {
        viewModelScope.launch {
            try {
                contentRepository.cleanupCache()
            } catch (e: Exception) {
                // Silently fail cache cleanup
            }
        }
    }
}
