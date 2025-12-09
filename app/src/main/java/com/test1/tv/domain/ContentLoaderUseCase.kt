package com.test1.tv.domain

import com.test1.tv.data.config.StaticRowData
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.data.repository.ContinueWatchingRepository
import com.test1.tv.data.repository.MediaRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for loading content based on row type.
 * Provides a unified interface for different content loading strategies.
 */
@Singleton
class ContentLoaderUseCase @Inject constructor(
    private val contentRepository: ContentRepository,
    private val mediaRepository: MediaRepository,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val traktApiService: com.test1.tv.data.remote.api.TraktApiService,
    private val traktAccountRepository: com.test1.tv.data.repository.TraktAccountRepository
) {

    /**
     * Load content for a specific row type.
     * @param rowType The type of row (trending, popular, continue_watching, etc.)
     * @param contentType The type of content (movies, shows, null for mixed)
     * @param page The page number to load (1-indexed)
     * @param forceRefresh Whether to force refresh from network
     * @return List of content items
     */
    suspend fun loadForRowType(
        rowType: String,
        contentType: String?,
        page: Int,
        forceRefresh: Boolean
    ): List<ContentItem> {
        return when (rowType) {
            "trending" -> loadTrending(contentType, page, forceRefresh)
            "popular" -> loadPopular(contentType, page, forceRefresh)
            "continue_watching" -> loadContinueWatching()
            "4k_releases" -> load4KReleases(page, forceRefresh)
            "networks" -> loadNetworks()
            "collections" -> loadCollections()
            "directors" -> loadDirectors()
            "my_trakt" -> loadMyTraktLists()
            else -> emptyList()
        }
    }

    private suspend fun loadTrending(
        contentType: String?,
        page: Int,
        forceRefresh: Boolean
    ): List<ContentItem> {
        return when (contentType) {
            "movies" -> {
                if (page == 1) {
                    contentRepository.getTrendingMovies(forceRefresh).getOrDefault(emptyList())
                } else {
                    contentRepository.getTrendingMoviesPage(page, pageSize = 20, forceRefresh)
                        .getOrDefault(emptyList())
                }
            }
            "shows" -> {
                if (page == 1) {
                    contentRepository.getTrendingShows(forceRefresh).getOrDefault(emptyList())
                } else {
                    contentRepository.getTrendingShowsPage(page, pageSize = 20, forceRefresh)
                        .getOrDefault(emptyList())
                }
            }
            else -> emptyList()
        }
    }

    private suspend fun loadPopular(
        contentType: String?,
        page: Int,
        forceRefresh: Boolean
    ): List<ContentItem> {
        return when (contentType) {
            "movies" -> {
                if (page == 1) {
                    contentRepository.getPopularMovies(forceRefresh).getOrDefault(emptyList())
                } else {
                    contentRepository.getPopularMoviesPage(page, pageSize = 20, forceRefresh)
                        .getOrDefault(emptyList())
                }
            }
            "shows" -> {
                if (page == 1) {
                    contentRepository.getPopularShows(forceRefresh).getOrDefault(emptyList())
                } else {
                    contentRepository.getPopularShowsPage(page, pageSize = 20, forceRefresh)
                        .getOrDefault(emptyList())
                }
            }
            else -> emptyList()
        }
    }

    private suspend fun loadContinueWatching(): List<ContentItem> {
        return continueWatchingRepository.load(limit = 20, forceRefresh = false)
    }

    private suspend fun load4KReleases(page: Int, forceRefresh: Boolean): List<ContentItem> {
        return if (page == 1) {
            contentRepository.getLatest4KMovies(forceRefresh).getOrDefault(emptyList())
        } else {
            contentRepository.getLatest4KMoviesPage(page, pageSize = 20, forceRefresh)
                .getOrDefault(emptyList())
        }
    }

    private suspend fun loadNetworks(): List<ContentItem> {
        return StaticRowData.networks.mapIndexed { index, network ->
            ContentItem(
                id = "network_${network.id}".hashCode() + index * 100000,
                tmdbId = -1,
                imdbId = network.traktListUrl,
                title = network.name,
                overview = null,
                posterUrl = "drawable://network_${network.id}",
                backdropUrl = null,
                logoUrl = null,
                year = null,
                rating = null,
                ratingPercentage = null,
                genres = null,
                type = ContentItem.ContentType.MOVIE,
                runtime = null,
                cast = null,
                certification = null,
                imdbRating = null,
                rottenTomatoesRating = null,
                traktRating = null,
                watchProgress = null,
                isPlaceholder = false
            )
        }
    }

    private suspend fun loadCollections(): List<ContentItem> {
        return StaticRowData.collections.mapIndexed { index, collection ->
            ContentItem(
                id = "collection_${collection.id}".hashCode() + index * 100000,
                tmdbId = -1,
                imdbId = collection.traktListUrl,
                title = collection.name,
                overview = null,
                posterUrl = "drawable://collection_${collection.id.replace("-", "_")}",
                backdropUrl = null,
                logoUrl = null,
                year = null,
                rating = null,
                ratingPercentage = null,
                genres = null,
                type = ContentItem.ContentType.MOVIE,
                runtime = null,
                cast = null,
                certification = null,
                imdbRating = null,
                rottenTomatoesRating = null,
                traktRating = null,
                watchProgress = null,
                isPlaceholder = false
            )
        }
    }

    private suspend fun loadDirectors(): List<ContentItem> {
        return StaticRowData.directors.mapIndexed { index, director ->
            ContentItem(
                id = "director_${director.id}".hashCode() + index * 100000,
                tmdbId = -1,
                imdbId = director.traktListUrl,
                title = director.name,
                overview = null,
                posterUrl = "drawable://director_${director.id}",
                backdropUrl = null,
                logoUrl = null,
                year = null,
                rating = null,
                ratingPercentage = null,
                genres = null,
                type = ContentItem.ContentType.MOVIE,
                runtime = null,
                cast = null,
                certification = null,
                imdbRating = null,
                rottenTomatoesRating = null,
                traktRating = null,
                watchProgress = null,
                isPlaceholder = false
            )
        }
    }

    private suspend fun loadMyTraktLists(): List<ContentItem> {
        return try {
            val account = traktAccountRepository.getAccount() ?: return emptyList()
            val authHeader = traktAccountRepository.buildAuthHeader(account.accessToken)

            // First, add the 4 standard Trakt items in order
            val standardItems = listOf(
                ContentItem(
                    id = "trakt_movie_collection".hashCode(),
                    tmdbId = -1,
                    imdbId = "MOVIE_COLLECTION", // TraktMediaList enum name
                    title = "Movie Collection",
                    overview = "Movies you've collected on Trakt",
                    posterUrl = "drawable://trakt2",
                    backdropUrl = null,
                    logoUrl = null,
                    year = null,
                    rating = null,
                    ratingPercentage = null,
                    genres = null,
                    type = ContentItem.ContentType.MOVIE,
                    runtime = null,
                    cast = null,
                    certification = null,
                    imdbRating = null,
                    rottenTomatoesRating = null,
                    traktRating = null,
                    watchProgress = null,
                    isPlaceholder = false
                ),
                ContentItem(
                    id = "trakt_movie_watchlist".hashCode(),
                    tmdbId = -1,
                    imdbId = "MOVIE_WATCHLIST",
                    title = "Movie Watchlist",
                    overview = "Movies you want to watch on Trakt",
                    posterUrl = "drawable://trakt2",
                    backdropUrl = null,
                    logoUrl = null,
                    year = null,
                    rating = null,
                    ratingPercentage = null,
                    genres = null,
                    type = ContentItem.ContentType.MOVIE,
                    runtime = null,
                    cast = null,
                    certification = null,
                    imdbRating = null,
                    rottenTomatoesRating = null,
                    traktRating = null,
                    watchProgress = null,
                    isPlaceholder = false
                ),
                ContentItem(
                    id = "trakt_show_collection".hashCode(),
                    tmdbId = -1,
                    imdbId = "TV_COLLECTION",
                    title = "TV Show Collection",
                    overview = "TV shows you've collected on Trakt",
                    posterUrl = "drawable://trakt2",
                    backdropUrl = null,
                    logoUrl = null,
                    year = null,
                    rating = null,
                    ratingPercentage = null,
                    genres = null,
                    type = ContentItem.ContentType.TV_SHOW,
                    runtime = null,
                    cast = null,
                    certification = null,
                    imdbRating = null,
                    rottenTomatoesRating = null,
                    traktRating = null,
                    watchProgress = null,
                    isPlaceholder = false
                ),
                ContentItem(
                    id = "trakt_show_watchlist".hashCode(),
                    tmdbId = -1,
                    imdbId = "TV_WATCHLIST",
                    title = "TV Show Watchlist",
                    overview = "TV shows you want to watch on Trakt",
                    posterUrl = "drawable://trakt2",
                    backdropUrl = null,
                    logoUrl = null,
                    year = null,
                    rating = null,
                    ratingPercentage = null,
                    genres = null,
                    type = ContentItem.ContentType.TV_SHOW,
                    runtime = null,
                    cast = null,
                    certification = null,
                    imdbRating = null,
                    rottenTomatoesRating = null,
                    traktRating = null,
                    watchProgress = null,
                    isPlaceholder = false
                )
            )

            // Then, fetch and add user's custom lists
            val customLists = try {
                val lists = traktApiService.getUserLists(
                    authHeader = authHeader,
                    clientId = com.test1.tv.BuildConfig.TRAKT_CLIENT_ID
                )

                lists.mapIndexed { index, list ->
                    val username = list.user?.username ?: account.userSlug
                    val slug = list.ids?.slug ?: ""
                    val traktUrl = "https://trakt.tv/users/$username/lists/$slug"

                    ContentItem(
                        id = "trakt_list_${list.ids?.trakt ?: index}".hashCode(),
                        tmdbId = -1,
                        imdbId = traktUrl,
                        title = list.name,
                        overview = list.description,
                        posterUrl = "drawable://trakt2",
                        backdropUrl = null,
                        logoUrl = null,
                        year = null,
                        rating = null,
                        ratingPercentage = null,
                        genres = null,
                        type = ContentItem.ContentType.MOVIE,
                        runtime = null,
                        cast = null,
                        certification = null,
                        imdbRating = null,
                        rottenTomatoesRating = null,
                        traktRating = null,
                        watchProgress = null,
                        isPlaceholder = false
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }

            // Return standard items first, then custom lists
            standardItems + customLists
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Clean up old cached content.
     */
    suspend fun cleanupCache() {
        // Implement cache cleanup logic if needed
    }
}
