package com.test1.tv.domain

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
    private val continueWatchingRepository: ContinueWatchingRepository
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
        // Placeholder for network loading
        // Will be implemented when network rows are added
        return emptyList()
    }

    private suspend fun loadCollections(): List<ContentItem> {
        // Placeholder for collections loading
        // Will be implemented when collection rows are added
        return emptyList()
    }

    private suspend fun loadDirectors(): List<ContentItem> {
        // Placeholder for directors loading
        // Will be implemented when director rows are added
        return emptyList()
    }

    /**
     * Clean up old cached content.
     */
    suspend fun cleanupCache() {
        // Implement cache cleanup logic if needed
    }
}
