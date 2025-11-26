package com.test1.tv.data.repository

import com.test1.tv.BuildConfig
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.model.tmdb.TMDBSearchResult
import com.test1.tv.data.remote.api.TMDBApiService

class SearchRepository(
    private val tmdbApiService: TMDBApiService
) {
    suspend fun search(query: String): Triple<List<ContentItem>, List<ContentItem>, List<ContentItem>> {
        if (query.isBlank()) return Triple(emptyList(), emptyList(), emptyList())
        val response = tmdbApiService.multiSearch(
            apiKey = BuildConfig.TMDB_API_KEY,
            query = query
        )
        val results = response.results.orEmpty()

        val movies = results
            .filter { it.mediaType == "movie" }
            .map { result -> result.toContentItem(ContentItem.ContentType.MOVIE) }

        val shows = results
            .filter { it.mediaType == "tv" }
            .map { result -> result.toContentItem(ContentItem.ContentType.TV_SHOW) }

        val people = results
            .filter { it.mediaType == "person" }
            .map { result ->
                ContentItem(
                    id = result.id,
                    tmdbId = result.id,
                    imdbId = null,
                    title = result.name ?: "",
                    overview = null,
                    posterUrl = result.getProfileUrl(),
                    backdropUrl = result.getBackdropUrl(),
                    logoUrl = null,
                    year = null,
                    rating = null,
                    ratingPercentage = null,
                    genres = null,
                    type = ContentItem.ContentType.MOVIE, // uses existing adapter; flag cast to route
                    runtime = null,
                    cast = "__PERSON__",
                    certification = null,
                    imdbRating = null,
                    rottenTomatoesRating = null,
                    traktRating = null
                )
            }

        return Triple(movies, shows, people)
    }
}

private fun TMDBSearchResult.toContentItem(type: ContentItem.ContentType): ContentItem {
    val titleValue = if (type == ContentItem.ContentType.MOVIE) title ?: "" else name ?: ""
    return ContentItem(
        id = id,
        tmdbId = id,
        imdbId = null,
        title = titleValue,
        overview = overview,
        posterUrl = getPosterUrl(),
        backdropUrl = getBackdropUrl(),
        logoUrl = null,
        year = getYear(),
        rating = voteAverage,
        ratingPercentage = voteAverage?.times(10)?.toInt(),
        genres = null,
        type = type,
        runtime = null,
        cast = null,
        certification = null,
        imdbRating = null,
        rottenTomatoesRating = null,
        traktRating = null
    )
}
