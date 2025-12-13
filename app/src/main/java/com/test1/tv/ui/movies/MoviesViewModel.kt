package com.test1.tv.ui.movies

import com.test1.tv.data.repository.ScreenConfigRepository
import com.test1.tv.data.repository.WatchStatusRepository
import com.test1.tv.domain.ContentLoaderUseCase
import com.test1.tv.ui.base.BaseContentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MoviesViewModel @Inject constructor(
    screenConfigRepository: ScreenConfigRepository,
    contentLoader: ContentLoaderUseCase,
    watchStatusRepository: WatchStatusRepository
) : BaseContentViewModel(
    screenConfigRepository,
    contentLoader,
    watchStatusRepository,
    ScreenConfigRepository.ScreenType.MOVIES
)
