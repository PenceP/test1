package com.strmr.tv.ui

import androidx.leanback.widget.VerticalGridView
import com.strmr.tv.R

object RowLayoutHelper {
    fun configureVerticalGrid(grid: VerticalGridView) {
        val context = grid.context
        val spacingPx = context.resources.getDimensionPixelSize(R.dimen.row_item_spacing)
        grid.setNumColumns(1)
        grid.setItemSpacing(spacingPx)
        grid.setHasFixedSize(true)
        grid.setFocusScrollStrategy(VerticalGridView.FOCUS_SCROLL_ALIGNED)
        grid.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_LOW_EDGE)
        grid.setWindowAlignmentOffset(0)
        grid.setWindowAlignmentOffsetPercent(VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED)
        grid.setItemAlignmentOffset(0)
        grid.setItemAlignmentOffsetPercent(VerticalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED)
    }
}
