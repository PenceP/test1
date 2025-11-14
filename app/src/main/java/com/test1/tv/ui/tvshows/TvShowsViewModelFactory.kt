package com.test1.tv.ui.tvshows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.test1.tv.data.repository.ContentRepository

class TvShowsViewModelFactory(
    private val contentRepository: ContentRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TvShowsViewModel::class.java)) {
            return TvShowsViewModel(contentRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
