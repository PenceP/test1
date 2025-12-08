package com.test1.tv.ui

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.test1.tv.ui.adapter.ContentRow
import com.test1.tv.ui.adapter.RowPresentation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prefetches poster/backdrop images for adjacent rows when a row gains focus.
 * Helps avoid jank when moving up/down between rows.
 */
@Singleton
class RowPrefetchManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefetchedRows = mutableSetOf<Int>()

    fun onRowFocused(currentRowIndex: Int, allRows: List<ContentRow>) {
        prefetchRowIfNeeded(currentRowIndex + 1, allRows)
        prefetchRowIfNeeded(currentRowIndex - 1, allRows)
    }

    fun clearPrefetchState() {
        prefetchedRows.clear()
    }

    private fun prefetchRowIfNeeded(rowIndex: Int, allRows: List<ContentRow>) {
        if (rowIndex !in allRows.indices) return
        if (!prefetchedRows.add(rowIndex)) return

        val row = allRows[rowIndex]
        val isLandscape = row.presentation == RowPresentation.LANDSCAPE_16_9
        val targetWidth = if (isLandscape) 400 else 240
        val targetHeight = if (isLandscape) 225 else 360

        row.items.take(8).forEach { item ->
            val url = if (isLandscape) item.backdropUrl ?: item.posterUrl else item.posterUrl
            url?.let { imageUrl ->
                // Skip drawable:// URLs - they're local resources and don't benefit from prefetching
                // Also, VectorDrawables can't be cached by Glide
                if (imageUrl.startsWith("drawable://")) {
                    return@let
                }

                Glide.with(context)
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .preload(targetWidth, targetHeight)
            }
        }
    }
}
