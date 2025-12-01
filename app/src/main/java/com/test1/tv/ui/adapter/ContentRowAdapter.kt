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
import com.test1.tv.ui.SmartRowScrollManager
import com.test1.tv.ui.SmartScrollThrottler
import kotlinx.coroutines.CoroutineScope

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
    private val onRequestMore: (Int) -> Unit,
    private val viewPool: RecyclerView.RecycledViewPool? = null,
    private val accentColorCache: com.test1.tv.ui.AccentColorCache,
    private val coroutineScope: CoroutineScope
) : RecyclerView.Adapter<ContentRowAdapter.RowViewHolder>() {

    private val rows = initialRows.toMutableList()
    private val rowAdapters = SparseArray<PosterAdapter>()
    private val scrollThrottler = SmartScrollThrottler()

    inner class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rowTitle: TextView = itemView.findViewById(R.id.row_title)
        private val rowContent: HorizontalGridView = itemView.findViewById(R.id.row_content)

        fun bind(row: ContentRow, rowIndex: Int) {
            rowTitle.text = row.title

            var adapter = rowAdapters.get(rowIndex)

            // FIX #1: Check if presentation changed - if so, need new adapter
            val needsNewAdapter = adapter == null ||
                !adapter.hasPresentation(row.presentation)

            if (needsNewAdapter) {
                adapter = PosterAdapter(
                    onItemClick = onItemClick,
                    onItemFocused = { item, itemIndex ->
                        onItemFocused(item, rowIndex, itemIndex)
                    },
                    onNavigateToNavBar = onNavigateToNavBar,
                    onItemLongPressed = onItemLongPress,
                    presentation = row.presentation,
                    onNearEnd = {
                        onRequestMore(rowIndex)
                    },
                    accentColorCache = accentColorCache,
                    coroutineScope = coroutineScope
                )
                rowAdapters.put(rowIndex, adapter)

                // Force re-attach since adapter changed
                rowContent.adapter = null
            }

            if (rowContent.adapter !== adapter) {
                rowContent.adapter = adapter
                viewPool?.let { rowContent.setRecycledViewPool(it) }
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
                SmartRowScrollManager.attach(rowContent)
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

            adapter.submitList(row.items.toList())
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
        stateRow.items.addAll(newItems)
        rowAdapters.get(rowIndex)?.submitList(stateRow.items.toList())
    }

    fun currentRows(): List<ContentRow> = rows.toList()
}
