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
                val rows = mutableListOf<ContentRow>()

                // Fetch trending movies via new MediaRepository (offline-first)
                val trendingResource = mediaRepository.getTrendingMovies().last()
                val trendingMovies = when (trendingResource) {
                    is Resource.Success -> trendingResource.data
                    is Resource.Loading -> trendingResource.cachedData ?: emptyList()
                    is Resource.Error -> trendingResource.cachedData ?: emptyList()
                }

                if (trendingMovies.isNotEmpty()) {
                    rows.add(
                        ContentRow(
                            title = "Trending Movies",
                            items = trendingMovies.toMutableList(),
                            presentation = RowPresentation.PORTRAIT
                        )
                    )
                    if (_heroContent.value == null) {
                        _heroContent.value = trendingMovies.first()
                    }
                } else if (trendingResource is Resource.Error) {
                    _error.value = "Failed to load trending movies: ${trendingResource.exception.message}"
                }

                // Fetch trending movies
                // Fetch popular movies (legacy repository)
                contentRepository.getPopularMovies(forceRefresh).onSuccess { movies ->
                    if (movies.isNotEmpty()) {
                        rows.add(
                            ContentRow(
                                title = "Popular Movies",
                                items = movies.toMutableList(),
                                presentation = RowPresentation.PORTRAIT
                            )
                        )
                    }
                }.onFailure { e ->
                    _error.value = "Failed to load popular movies: ${e.message}"
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

                _contentRows.value = rows
            } catch (e: Exception) {
                _error.value = "Failed to load content: ${e.message}"
            } finally {
                _isLoading.value = false
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
            } catch (e: Exception) {
                // Silently fail cache cleanup
            }
        }
    }
}
