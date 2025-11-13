package com.test1.tv.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.HorizontalGridView
import androidx.recyclerview.widget.RecyclerView
import com.test1.tv.R
import com.test1.tv.data.model.ContentItem

data class ContentRow(
    val title: String,
    val items: List<ContentItem>
)

class ContentRowAdapter(
    private val rows: List<ContentRow>,
    private val onItemClick: (ContentItem) -> Unit,
    private val onItemFocused: (ContentItem, Int, Int) -> Unit // item, rowIndex, itemIndex
) : RecyclerView.Adapter<ContentRowAdapter.RowViewHolder>() {

    inner class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rowTitle: TextView = itemView.findViewById(R.id.row_title)
        private val rowContent: HorizontalGridView = itemView.findViewById(R.id.row_content)

        fun bind(row: ContentRow, rowIndex: Int) {
            rowTitle.text = row.title

            // Set up horizontal grid view
            val adapter = PosterAdapter(
                items = row.items,
                onItemClick = onItemClick,
                onItemFocused = { item, itemIndex ->
                    onItemFocused(item, rowIndex, itemIndex)
                }
            )

            rowContent.adapter = adapter
            rowContent.setNumRows(1)
            rowContent.setItemSpacing(0)

            // Enable smooth scrolling with fixed focus behavior
            rowContent.setHasFixedSize(true)
            rowContent.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)

            // Set window alignment to keep focus at a fixed position (left side)
            rowContent.setWindowAlignment(HorizontalGridView.WINDOW_ALIGN_LOW_EDGE)
            rowContent.setWindowAlignmentOffset(144) // Position where focus stays (poster width 120 + spacing 12 * 2)
            rowContent.setWindowAlignmentOffsetPercent(HorizontalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED)

            // Align items by their center for smooth focus
            rowContent.setItemAlignmentOffset(60) // Half of poster width (120dp / 2)
            rowContent.setItemAlignmentOffsetPercent(HorizontalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED)
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
}
