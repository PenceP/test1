package com.test1.tv.ui.movies

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.ui.adapter.ContentRow
import com.test1.tv.ui.adapter.RowPresentation
import kotlinx.coroutines.launch

class MoviesViewModel(
    private val contentRepository: ContentRepository
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
        loadMovieContent()
    }

    fun loadMovieContent(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val rows = mutableListOf<ContentRow>()

                // Fetch trending movies
                contentRepository.getTrendingMovies(forceRefresh).onSuccess { movies ->
                    if (movies.isNotEmpty()) {
                        rows.add(
                            ContentRow(
                                title = "Trending Movies",
                                items = movies.toMutableList(),
                                presentation = RowPresentation.PORTRAIT
                            )
                        )
                        // Set first item as hero if not already set
                        if (_heroContent.value == null) {
                            _heroContent.value = movies.first()
                        }
                    }
                }.onFailure { e ->
                    _error.value = "Failed to load trending movies: ${e.message}"
                }

                // Fetch popular movies
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
