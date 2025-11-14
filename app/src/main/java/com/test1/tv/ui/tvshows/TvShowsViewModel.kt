package com.test1.tv.ui.tvshows

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.ui.adapter.ContentRow
import kotlinx.coroutines.launch

class TvShowsViewModel(
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
        loadTvShowContent()
    }

    fun loadTvShowContent(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val rows = mutableListOf<ContentRow>()

                // Fetch trending shows
                contentRepository.getTrendingShows(forceRefresh).onSuccess { shows ->
                    if (shows.isNotEmpty()) {
                        rows.add(ContentRow("Trending Shows", shows))
                        // Set first item as hero if not already set
                        if (_heroContent.value == null) {
                            _heroContent.value = shows.first()
                        }
                    }
                }.onFailure { e ->
                    _error.value = "Failed to load trending shows: ${e.message}"
                }

                // Fetch popular shows
                contentRepository.getPopularShows(forceRefresh).onSuccess { shows ->
                    if (shows.isNotEmpty()) {
                        rows.add(ContentRow("Popular Shows", shows))
                    }
                }.onFailure { e ->
                    _error.value = "Failed to load popular shows: ${e.message}"
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
