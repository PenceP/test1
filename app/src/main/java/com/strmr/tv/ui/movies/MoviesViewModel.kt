package com.strmr.tv.ui.movies

import com.strmr.tv.data.repository.ScreenConfigRepository
import com.strmr.tv.data.repository.WatchStatusRepository
import com.strmr.tv.domain.ContentLoaderUseCase
import com.strmr.tv.ui.base.BaseContentViewModel
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
