package com.strmr.tv.data.local.entity

import com.strmr.tv.ui.adapter.RowPresentation
import com.strmr.tv.ui.base.ContentRowState

/**
 * Extension function to convert RowConfigEntity to ContentRowState.
 * This bridges the database layer and UI layer.
 */
fun RowConfigEntity.toRowState(): ContentRowState {
    return ContentRowState(
        category = id,
        title = title,
        rowType = rowType,
        contentType = contentType,
        dataSourceUrl = dataSourceUrl,
        presentation = when (presentation) {
            "landscape" -> RowPresentation.LANDSCAPE_16_9
            else -> RowPresentation.PORTRAIT
        },
        pageSize = pageSize
    )
}
