package com.test1.tv.ui.tvshows

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
class TvShowsViewModel @Inject constructor(
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
        loadTvShowContent()
    }

    fun loadTvShowContent(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val rows = mutableListOf<ContentRow>()

                val trendingResource = mediaRepository.getTrendingShows().last()
                val trending = mapResource(trendingResource)
                trending.onSuccess { shows ->
                    if (shows.isNotEmpty()) {
                        rows.add(
                            ContentRow(
                                title = "Trending Shows",
                                items = shows.toMutableList(),
                                presentation = RowPresentation.PORTRAIT
                            )
                        )
                        if (_heroContent.value == null) {
                            _heroContent.value = shows.first()
                        }
                    }
                }.onFailure { e ->
                    _error.value = "Failed to load trending shows: ${e.message}"
                }

                val popularResource = mediaRepository.getPopularShows().last()
                val popular = mapResource(popularResource)
                popular.onSuccess { shows ->
                    if (shows.isNotEmpty()) {
                        rows.add(
                            ContentRow(
                                title = "Popular Shows",
                                items = shows.toMutableList(),
                                presentation = RowPresentation.PORTRAIT
                            )
                        )
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
}
