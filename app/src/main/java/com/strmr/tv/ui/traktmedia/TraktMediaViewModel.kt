package com.strmr.tv.ui.traktmedia

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strmr.tv.data.model.ContentItem
import com.strmr.tv.data.model.trakt.TraktMediaList
import com.strmr.tv.data.repository.TraktMediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

internal const val ARG_CATEGORY = "trakt_media_category"

@HiltViewModel
class TraktMediaViewModel @Inject constructor(
    private val traktMediaRepository: TraktMediaRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val categoryName = savedStateHandle.get<String>(ARG_CATEGORY)
        ?: TraktMediaList.MOVIE_COLLECTION.name

    val category: TraktMediaList = TraktMediaList.fromId(categoryName) ?: TraktMediaList.MOVIE_COLLECTION

    private val _rowItems = MutableLiveData<List<ContentItem>>(emptyList())
    val rowItems: LiveData<List<ContentItem>> = _rowItems

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _heroContent = MutableLiveData<ContentItem?>()
    val heroContent: LiveData<ContentItem?> = _heroContent

    init {
        load(forceRefresh = false)
    }

    fun refresh(forceRefresh: Boolean = false) {
        load(forceRefresh)
    }

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = traktMediaRepository.getMediaList(category, forceRefresh)
            _isLoading.value = false
            result.onSuccess { list ->
                _rowItems.value = list
                _heroContent.value = list.firstOrNull()
                _error.value = null
            }.onFailure { throwable ->
                _error.value = throwable.message ?: "Unable to load ${category.displayTitle}"
            }
        }
    }
}
