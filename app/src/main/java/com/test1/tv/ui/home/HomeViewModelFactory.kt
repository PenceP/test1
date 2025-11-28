package com.test1.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.test1.tv.data.model.home.HomeConfig
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.data.repository.ContinueWatchingRepository

class HomeViewModelFactory(
    private val contentRepository: ContentRepository,
    private val homeConfig: HomeConfig?,
    private val continueWatchingRepository: ContinueWatchingRepository?
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(contentRepository, homeConfig, continueWatchingRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
