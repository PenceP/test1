package com.strmr.tv.domain

import com.strmr.tv.data.config.StaticRowData
import com.strmr.tv.data.model.ContentItem
import com.strmr.tv.data.repository.ContentRepository
import com.strmr.tv.data.repository.ContinueWatchingRepository
import com.strmr.tv.data.repository.MediaRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val traktApiService: com.strmr.tv.data.remote.api.TraktApiService,
    private val traktAccountRepository: com.strmr.tv.data.repository.TraktAccountRepository,
    private val tmdbApiService: com.strmr.tv.data.remote.api.TMDBApiService
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
        forceRefresh: Boolean,
        dataSourceUrl: String? = null
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
            "trakt_list" -> loadTraktList(dataSourceUrl, page)
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
                    clientId = com.strmr.tv.BuildConfig.TRAKT_CLIENT_ID
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
     * Load content from a Trakt list.
     * @param dataSourceUrl Format: "trakt_list:username:listSlug"
     * @param page The page number (1-indexed)
     */
    private suspend fun loadTraktList(dataSourceUrl: String?, page: Int): List<ContentItem> {
        if (dataSourceUrl.isNullOrBlank()) return emptyList()

        // Parse the dataSourceUrl format: "trakt_list:username:listSlug"
        val parts = dataSourceUrl.split(":")
        if (parts.size < 3 || parts[0] != "trakt_list") return emptyList()

        val username = parts[1]
        val listSlug = parts[2]

        return try {
            // Try loading items from the list (movies first, then shows)
            val movieItems = loadTraktListMovies(username, listSlug, page)
            val showItems = loadTraktListShows(username, listSlug, page)

            movieItems + showItems
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun loadTraktListMovies(username: String, listSlug: String, page: Int): List<ContentItem> {
        return try {
            val traktItems = traktApiService.getListMovies(
                user = username,
                list = listSlug,
                clientId = com.strmr.tv.BuildConfig.TRAKT_CLIENT_ID,
                page = page,
                limit = 20
            )

            coroutineScope {
                traktItems.mapNotNull { traktItem ->
                    traktItem.movie?.ids?.tmdb?.let { tmdbId ->
                        async {
                            fetchMovieDetails(tmdbId, traktItem.movie.title, traktItem.movie.year)
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun loadTraktListShows(username: String, listSlug: String, page: Int): List<ContentItem> {
        return try {
            val traktItems = traktApiService.getListShows(
                user = username,
                list = listSlug,
                clientId = com.strmr.tv.BuildConfig.TRAKT_CLIENT_ID,
                page = page,
                limit = 20
            )

            coroutineScope {
                traktItems.mapNotNull { traktItem ->
                    traktItem.show?.ids?.tmdb?.let { tmdbId ->
                        async {
                            fetchShowDetails(tmdbId, traktItem.show.title, traktItem.show.year)
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchMovieDetails(tmdbId: Int, title: String?, year: Int?): ContentItem? {
        return runCatching {
            tmdbApiService.getMovieDetails(
                movieId = tmdbId,
                apiKey = com.strmr.tv.BuildConfig.TMDB_API_KEY,
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
                watchProgress = null
            )
        }
    }

    private suspend fun fetchShowDetails(tmdbId: Int, title: String?, year: Int?): ContentItem? {
        val showDetails = runCatching {
            tmdbApiService.getShowDetails(
                showId = tmdbId,
                apiKey = com.strmr.tv.BuildConfig.TMDB_API_KEY,
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
            watchProgress = null
        )
    }

    /**
     * Clean up old cached content.
     */
    suspend fun cleanupCache() {
        // Implement cache cleanup logic if needed
    }
}
