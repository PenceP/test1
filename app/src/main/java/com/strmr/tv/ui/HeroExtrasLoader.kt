package com.strmr.tv.ui

import androidx.lifecycle.LifecycleCoroutineScope
import com.strmr.tv.BuildConfig
import com.strmr.tv.data.model.ContentItem
import com.strmr.tv.data.remote.api.TMDBApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Enriches hero content with cast/genres only when missing to avoid duplicate copy/paste.
 */
object HeroExtrasLoader {
    fun load(
        scope: LifecycleCoroutineScope,
        existingJob: Job?,
        item: ContentItem,
        tmdbApiService: TMDBApiService,
        onUpdated: (ContentItem) -> Unit
    ): Job {
        existingJob?.cancel()
        return scope.launch(Dispatchers.IO) {
            val enriched = runCatching {
                when (item.type) {
                    ContentItem.ContentType.MOVIE -> {
                        val details = tmdbApiService.getMovieDetails(
                            movieId = item.tmdbId,
                            apiKey = BuildConfig.TMDB_API_KEY,
                            appendToResponse = "credits,images"
                        )
                        item.copy(
                            cast = details.getCastNames(),
                            genres = details.genres?.joinToString(", ") { it.name }
                        )
                    }
                    ContentItem.ContentType.TV_SHOW -> {
                        val details = tmdbApiService.getShowDetails(
                            showId = item.tmdbId,
                            apiKey = BuildConfig.TMDB_API_KEY,
                            appendToResponse = "credits,images,content_ratings"
                        )
                        item.copy(
                            cast = details.getCastNames(),
                            genres = details.genres?.joinToString(", ") { it.name }
                        )
                    }
                }
            }.getOrNull()

            enriched?.let { updated ->
                withContext(Dispatchers.Main) {
                    onUpdated(updated)
                }
            }
        }
    }
}
