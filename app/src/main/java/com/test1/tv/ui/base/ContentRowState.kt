package com.test1.tv.ui.base

import com.test1.tv.data.model.ContentItem
import com.test1.tv.ui.adapter.RowPresentation

/**
 * Represents the state of a content row in the UI.
 * Used to track pagination, loading status, and content for each row.
 */
data class ContentRowState(
    val category: String,
    val title: String,
    val rowType: String,
    val contentType: String? = null,
    val presentation: RowPresentation = RowPresentation.PORTRAIT,
    val pageSize: Int = 20,
    val items: MutableList<ContentItem> = mutableListOf(),
    var currentPage: Int = 0,
    var hasMore: Boolean = true,
    var isLoading: Boolean = false,
    var prefetchingPage: Int? = null
)

/**
 * Event emitted when new items are appended to a row.
 * Used by UI to animate new items in.
 */
data class RowAppendEvent(
    val rowIndex: Int,
    val newItems: List<ContentItem>
)
