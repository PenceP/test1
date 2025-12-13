package com.test1.tv.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.repository.SearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _movieResults = MutableLiveData<List<ContentItem>>(emptyList())
    val movieResults: LiveData<List<ContentItem>> = _movieResults

    private val _showResults = MutableLiveData<List<ContentItem>>(emptyList())
    val showResults: LiveData<List<ContentItem>> = _showResults

    private val _peopleResults = MutableLiveData<List<ContentItem>>(emptyList())
    val peopleResults: LiveData<List<ContentItem>> = _peopleResults

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _movieResults.value = emptyList()
            _showResults.value = emptyList()
            _peopleResults.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            _isLoading.value = true
            // tiny debounce
            delay(200)
            runCatching {
                searchRepository.search(query)
            }.onSuccess { triple ->
                _movieResults.value = triple.first
                _showResults.value = triple.second
                _peopleResults.value = triple.third
            }.onFailure {
                _movieResults.value = emptyList()
                _showResults.value = emptyList()
                _peopleResults.value = emptyList()
            }
            _isLoading.value = false
        }
    }
}
