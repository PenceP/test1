package com.test1.tv.ui.sources.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.test1.tv.R
import com.test1.tv.ui.sources.model.BadgeColors
import com.test1.tv.ui.sources.model.SourceItem

class SourcesAdapter(
    private val onSourceClick: (SourceItem.Stream) -> Unit
) : ListAdapter<SourceItem, RecyclerView.ViewHolder>(SourceDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_STREAM = 0
        private const val VIEW_TYPE_SEPARATOR = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SourceItem.Stream -> VIEW_TYPE_STREAM
            is SourceItem.Separator -> VIEW_TYPE_SEPARATOR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_STREAM -> {
                val view = inflater.inflate(R.layout.item_source, parent, false)
                StreamViewHolder(view, onSourceClick)
            }
            VIEW_TYPE_SEPARATOR -> {
                val view = inflater.inflate(R.layout.item_source_separator, parent, false)
                SeparatorViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SourceItem.Stream -> (holder as StreamViewHolder).bind(item)
            is SourceItem.Separator -> (holder as SeparatorViewHolder).bind(item)
        }
    }

    class StreamViewHolder(
        itemView: View,
        private val onSourceClick: (SourceItem.Stream) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val container: LinearLayout = itemView.findViewById(R.id.source_item_container)
        private val debridBadge: TextView = itemView.findViewById(R.id.debrid_badge)
        private val qualityBadge: TextView = itemView.findViewById(R.id.quality_badge)
        private val filenameText: TextView = itemView.findViewById(R.id.filename_text)
        private val sizeBadge: TextView = itemView.findViewById(R.id.size_badge)

        fun bind(item: SourceItem.Stream) {
            // Set click listener
            itemView.setOnClickListener { onSourceClick(item) }

            // Debrid badge
            val debrid = item.debridBadge
            if (debrid != null) {
                debridBadge.text = debrid
                debridBadge.visibility = View.VISIBLE
            } else {
                debridBadge.visibility = View.GONE
            }

            // Quality badge with color
            qualityBadge.text = item.qualityBadge
            val qualityColor = BadgeColors.getQualityColor(item.quality)
            val qualityDrawable = qualityBadge.background.mutate() as GradientDrawable
            qualityDrawable.setColor(qualityColor)

            // File name
            filenameText.text = item.fileName

            // Size badge
            sizeBadge.text = item.sizeFormatted

            // Apply filtered-out styling
            if (item.isFilteredOut) {
                container.setBackgroundResource(R.drawable.selector_source_item_filtered)
                container.alpha = 0.6f
                filenameText.alpha = 0.7f
            } else {
                container.setBackgroundResource(R.drawable.selector_source_item)
                container.alpha = 1.0f
                filenameText.alpha = 1.0f
            }
        }
    }

    class SeparatorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val separatorText: TextView = itemView.findViewById(R.id.separator_text)

        fun bind(item: SourceItem.Separator) {
            separatorText.text = item.text
        }
    }

    class SourceDiffCallback : DiffUtil.ItemCallback<SourceItem>() {
        override fun areItemsTheSame(oldItem: SourceItem, newItem: SourceItem): Boolean {
            return when {
                oldItem is SourceItem.Stream && newItem is SourceItem.Stream ->
                    oldItem.streamInfo.infoHash == newItem.streamInfo.infoHash
                oldItem is SourceItem.Separator && newItem is SourceItem.Separator ->
                    oldItem.text == newItem.text
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: SourceItem, newItem: SourceItem): Boolean {
            return oldItem == newItem
        }
    }
}
