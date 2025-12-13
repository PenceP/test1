package com.test1.tv.ui

import android.util.LruCache
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.service.TmdbEnrichmentService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeroPrefetchManager @Inject constructor(
    private val enrichmentService: TmdbEnrichmentService
) {
    private val prefetchedIds = LruCache<Int, Boolean>(100)
    private var lastFocusIndex = -1
    private var currentRowItems: List<ContentItem> = emptyList()

    fun onRowLoaded(items: List<ContentItem>) {
        currentRowItems = items
        // Pre-fetch first 6 items immediately
        prefetchRange(0, 5)
    }

    fun onFocusChanged(focusIndex: Int) {
        if (focusIndex == lastFocusIndex) return
        lastFocusIndex = focusIndex

        // Prefetch window: [focus-2, focus+4]
        val start = (focusIndex - 2).coerceAtLeast(0)
        val end = (focusIndex + 4).coerceAtMost(currentRowItems.lastIndex)
        prefetchRange(start, end)
    }

    private fun prefetchRange(start: Int, end: Int) {
        if (currentRowItems.isEmpty()) return

        val itemsToFetch = (start..end)
            .mapNotNull { currentRowItems.getOrNull(it) }
            .filter { it.tmdbId > 0 && prefetchedIds.get(it.tmdbId) == null }

        if (itemsToFetch.isEmpty()) return

        itemsToFetch.forEach { item ->
            prefetchedIds.put(item.tmdbId, true)
        }

        val type = when (itemsToFetch.first().type) {
            ContentItem.ContentType.MOVIE -> TmdbEnrichmentService.ContentType.MOVIE
            ContentItem.ContentType.TV_SHOW -> TmdbEnrichmentService.ContentType.TV_SHOW
        }

        enrichmentService.preload(itemsToFetch.map { it.tmdbId }, type)
    }

    fun clearForNewRow() {
        lastFocusIndex = -1
        currentRowItems = emptyList()
        prefetchedIds.evictAll()
    }
}
