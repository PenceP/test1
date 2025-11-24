package com.test1.tv.ui.adapter

import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.HorizontalGridView
import androidx.recyclerview.widget.RecyclerView
import com.test1.tv.R
import com.test1.tv.data.model.ContentItem
import com.test1.tv.ui.RowScrollPauser
import com.test1.tv.ui.ScrollThrottler

enum class RowPresentation {
    PORTRAIT,
    LANDSCAPE_16_9
}

data class ContentRow(
    val title: String,
    val items: MutableList<ContentItem>,
    val presentation: RowPresentation
)

class ContentRowAdapter(
    initialRows: List<ContentRow>,
    private val onItemClick: (ContentItem, ImageView) -> Unit,
    private val onItemFocused: (ContentItem, Int, Int) -> Unit, // item, rowIndex, itemIndex
    private val onNavigateToNavBar: () -> Unit,
    private val onItemLongPress: (ContentItem) -> Unit,
    private val onRequestMore: (Int) -> Unit
) : RecyclerView.Adapter<ContentRowAdapter.RowViewHolder>() {

    private val rows = initialRows.toMutableList()
    private val rowAdapters = SparseArray<PosterAdapter>()
    private val scrollThrottler = ScrollThrottler()

    inner class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rowTitle: TextView = itemView.findViewById(R.id.row_title)
        private val rowContent: HorizontalGridView = itemView.findViewById(R.id.row_content)

        fun bind(row: ContentRow, rowIndex: Int) {
            rowTitle.text = row.title

            var adapter = rowAdapters.get(rowIndex)
            if (adapter == null) {
                adapter = PosterAdapter(
                    initialItems = row.items,
                    onItemClick = onItemClick,
                    onItemFocused = { item, itemIndex ->
                        onItemFocused(item, rowIndex, itemIndex)
                    },
                    onNavigateToNavBar = onNavigateToNavBar,
                    onItemLongPressed = onItemLongPress,
                    presentation = row.presentation,
                    onNearEnd = {
                        onRequestMore(rowIndex)
                    }
                )
                rowAdapters.put(rowIndex, adapter)
            }

            if (rowContent.adapter !== adapter) {
                rowContent.adapter = adapter
                rowContent.setNumRows(1)
                rowContent.setItemSpacing(
                    if (row.presentation == RowPresentation.LANDSCAPE_16_9) 16 else 8
                )
                rowContent.setHasFixedSize(true)
                rowContent.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)
                rowContent.setWindowAlignment(HorizontalGridView.WINDOW_ALIGN_LOW_EDGE)
                rowContent.setWindowAlignmentOffset(144)
                rowContent.setWindowAlignmentOffsetPercent(HorizontalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED)
                rowContent.setItemAlignmentOffset(60)
                rowContent.setItemAlignmentOffsetPercent(HorizontalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED)
                RowScrollPauser.attach(rowContent)
                rowContent.setOnKeyInterceptListener(scrollThrottler)
            }

            val layoutParams = rowContent.layoutParams
            val heightRes = if (row.presentation == RowPresentation.LANDSCAPE_16_9) {
                R.dimen.row_height_landscape
            } else {
                R.dimen.row_height_portrait
            }
            layoutParams.height = rowContent.resources.getDimensionPixelSize(heightRes)
            rowContent.layoutParams = layoutParams

            adapter.replaceAll(row.items)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.content_row, parent, false)
        return RowViewHolder(view)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(rows[position], position)
    }

    override fun getItemCount(): Int = rows.size

    fun updateRows(newRows: List<ContentRow>) {
        rows.clear()
        rows.addAll(newRows)
        rowAdapters.clear()
        notifyDataSetChanged()
    }

    fun appendItems(rowIndex: Int, newItems: List<ContentItem>) {
        val stateRow = rows.getOrNull(rowIndex) ?: return
        rowAdapters.get(rowIndex)?.appendItems(newItems)
    }
}
