package com.test1.tv.ui.traktlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.test1.tv.BuildConfig
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.remote.api.TMDBApiService
import com.test1.tv.data.remote.api.TraktApiService
import com.test1.tv.data.repository.WatchStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

internal const val ARG_USERNAME = "trakt_list_username"
internal const val ARG_LIST_ID = "trakt_list_id"
internal const val ARG_TITLE = "trakt_list_title"
internal const val ARG_TRAKT_URL = "trakt_list_url"

@HiltViewModel
class TraktListViewModel @Inject constructor(
    private val traktApiService: TraktApiService,
    private val tmdbApiService: TMDBApiService,
    private val watchStatusRepository: WatchStatusRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val username = savedStateHandle.get<String>(ARG_USERNAME) ?: ""
    val listId = savedStateHandle.get<String>(ARG_LIST_ID) ?: ""
    val title = savedStateHandle.get<String>(ARG_TITLE) ?: "Trakt List"
    val traktUrl = savedStateHandle.get<String>(ARG_TRAKT_URL) ?: ""

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
            try {
                // Try loading movies first, then shows if movies list is empty
                val movieItems = fetchListMovies()
                val showItems = if (movieItems.isEmpty()) fetchListShows() else emptyList()

                val allItems = movieItems + showItems
                _rowItems.value = allItems
                _heroContent.value = allItems.firstOrNull()
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Unable to load Trakt list"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchListMovies(): List<ContentItem> = coroutineScope {
        return@coroutineScope try {
            val traktItems = traktApiService.getListMovies(
                user = username,
                list = listId,
                clientId = BuildConfig.TRAKT_CLIENT_ID
            )
            traktItems.take(40).mapNotNull { traktItem ->
                traktItem.movie?.ids?.tmdb?.let { tmdbId ->
                    async {
                        fetchMovieDetails(tmdbId, traktItem.movie.title, traktItem.movie.year)
                    }
                }
            }.awaitAll().filterNotNull()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchListShows(): List<ContentItem> = coroutineScope {
        return@coroutineScope try {
            val traktItems = traktApiService.getListShows(
                user = username,
                list = listId,
                clientId = BuildConfig.TRAKT_CLIENT_ID
            )
            traktItems.take(40).mapNotNull { traktItem ->
                traktItem.show?.ids?.tmdb?.let { tmdbId ->
                    async {
                        fetchShowDetails(tmdbId, traktItem.show.title, traktItem.show.year)
                    }
                }
            }.awaitAll().filterNotNull()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchMovieDetails(tmdbId: Int, title: String?, year: Int?): ContentItem? {
        return runCatching {
            tmdbApiService.getMovieDetails(
                movieId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY,
                appendToResponse = "images,credits,external_ids"
            )
        }.getOrNull()?.let { details ->
            ContentItem(
                id = tmdbId,
                tmdbId = tmdbId,
                imdbId = details.imdbId,
                title = details.title ?: title.orEmpty(),
                overview = details.overview,
                posterUrl = details.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                backdropUrl = details.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                logoUrl = details.images?.logos?.firstOrNull()?.filePath?.let { "https://image.tmdb.org/t/p/w500$it" },
                year = details.releaseDate?.take(4) ?: year?.toString(),
                rating = details.voteAverage,
                ratingPercentage = details.getRatingPercentage(),
                genres = details.genres?.joinToString(",") { it.name ?: "" },
                type = ContentItem.ContentType.MOVIE,
                runtime = details.runtime?.toString(),
                cast = details.credits?.cast?.joinToString(", ") { it.name ?: "" },
                certification = details.getCertification(),
                imdbRating = details.imdbId,
                rottenTomatoesRating = null,
                traktRating = null,
                watchProgress = watchStatusRepository.getProgress(tmdbId, ContentItem.ContentType.MOVIE)
            )
        }
    }

    private suspend fun fetchShowDetails(tmdbId: Int, title: String?, year: Int?): ContentItem? {
        val showDetails = runCatching {
            tmdbApiService.getShowDetails(
                showId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY,
                appendToResponse = "images,external_ids,credits"
            )
        }.getOrNull() ?: return null

        return ContentItem(
            id = tmdbId,
            tmdbId = tmdbId,
            imdbId = showDetails.externalIds?.imdbId,
            title = showDetails.name ?: title.orEmpty(),
            overview = showDetails.overview,
            posterUrl = showDetails.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            backdropUrl = showDetails.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
            logoUrl = showDetails.images?.logos?.firstOrNull()?.filePath?.let { "https://image.tmdb.org/t/p/w500$it" },
            year = showDetails.firstAirDate?.take(4) ?: year?.toString(),
            rating = showDetails.voteAverage,
            ratingPercentage = showDetails.getRatingPercentage(),
            genres = showDetails.genres?.joinToString(",") { it.name ?: "" },
            type = ContentItem.ContentType.TV_SHOW,
            runtime = showDetails.episodeRunTime?.firstOrNull()?.toString(),
            cast = showDetails.credits?.cast?.joinToString(", ") { it.name ?: "" },
            certification = showDetails.getCertification(),
            imdbRating = showDetails.externalIds?.imdbId,
            rottenTomatoesRating = null,
            traktRating = null,
            watchProgress = watchStatusRepository.getProgress(tmdbId, ContentItem.ContentType.TV_SHOW)
        )
    }
}
