package com.test1.tv.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.test1.tv.data.repository.ContentRepository

class MoviesViewModelFactory(
    private val contentRepository: ContentRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MoviesViewModel::class.java)) {
            return MoviesViewModel(contentRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
