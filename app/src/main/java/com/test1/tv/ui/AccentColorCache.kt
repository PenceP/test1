package com.test1.tv.ui

import android.graphics.Color
import android.util.LruCache
import com.test1.tv.data.model.ContentItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX #7: Memory-bounded cache using LruCache (max 200 entries).
 * Previously used unbounded MutableMap which could grow forever.
 */
@Singleton
class AccentColorCache @Inject constructor() {

    companion object {
        const val DEFAULT = Color.WHITE
        private const val MAX_ENTRIES = 200
    }

    private val colors = LruCache<Int, Int>(MAX_ENTRIES)

    fun get(item: ContentItem): Int? {
        val key = item.tmdbId.takeIf { it > 0 } ?: item.id
        return colors.get(key)
    }

    fun put(item: ContentItem, color: Int) {
        val key = item.tmdbId.takeIf { it > 0 } ?: item.id
        colors.put(key, color)
    }

    fun clear() {
        colors.evictAll()
    }
}
