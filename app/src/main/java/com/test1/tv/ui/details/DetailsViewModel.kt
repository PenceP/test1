package com.test1.tv.ui.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.model.tmdb.TMDBCast
import com.test1.tv.data.model.tmdb.TMDBEpisode
import com.test1.tv.data.model.tmdb.TMDBMovie
import com.test1.tv.data.model.tmdb.TMDBSeason
import com.test1.tv.data.model.tmdb.TMDBShow
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.data.repository.ContentRepository.MovieDetailsBundle
import com.test1.tv.data.repository.ContentRepository.ShowDetailsBundle
import kotlinx.coroutines.launch

class DetailsViewModel(private val contentRepository: ContentRepository) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _cast = MutableLiveData<List<TMDBCast>>()
    val cast: LiveData<List<TMDBCast>> = _cast

    private val _similarContent = MutableLiveData<List<ContentItem>>()
    val similarContent: LiveData<List<ContentItem>> = _similarContent

    private val _collection = MutableLiveData<Pair<String, List<ContentItem>>>()
    val collection: LiveData<Pair<String, List<ContentItem>>> = _collection

    private val _seasons = MutableLiveData<List<TMDBSeason>>()
    val seasons: LiveData<List<TMDBSeason>> = _seasons

    private val _episodes = MutableLiveData<List<TMDBEpisode>>()
    val episodes: LiveData<List<TMDBEpisode>> = _episodes

    fun loadDetails(item: ContentItem) {
        _isLoading.value = false
    }

    private fun loadMovieDetails(movieId: Int) = Unit

    private fun loadShowDetails(showId: Int) = Unit

    fun loadEpisodesForSeason(showId: Int, seasonNumber: Int) {
        _episodes.postValue(emptyList())
    }
}

class DetailsViewModelFactory(private val contentRepository: ContentRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetailsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetailsViewModel(contentRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
