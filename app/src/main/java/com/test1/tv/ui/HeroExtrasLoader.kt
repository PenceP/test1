package com.test1.tv.ui

import androidx.lifecycle.LifecycleCoroutineScope
import com.test1.tv.BuildConfig
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.remote.ApiClient
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
        onUpdated: (ContentItem) -> Unit
    ): Job {
        existingJob?.cancel()
        return scope.launch(Dispatchers.IO) {
            val enriched = runCatching {
                when (item.type) {
                    ContentItem.ContentType.MOVIE -> {
                        val details = ApiClient.tmdbApiService.getMovieDetails(
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
                        val details = ApiClient.tmdbApiService.getShowDetails(
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
