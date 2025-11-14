package com.test1.tv.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.test1.tv.R
import com.test1.tv.data.model.ContentItem

class PosterAdapter(
    private val items: List<ContentItem>,
    private val onItemClick: (ContentItem) -> Unit,
    private val onItemFocused: (ContentItem, Int) -> Unit
) : RecyclerView.Adapter<PosterAdapter.PosterViewHolder>() {

    inner class PosterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val posterImage: ImageView = itemView.findViewById(R.id.poster_image)
        val focusOverlay: View = itemView.findViewById(R.id.focus_overlay)

        fun bind(item: ContentItem, position: Int) {
            // Load poster image
            Glide.with(itemView.context)
                .load(item.posterUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.default_background)
                .error(R.drawable.default_background)
                .into(posterImage)

            // Handle focus changes
            itemView.setOnFocusChangeListener { _, hasFocus ->
                focusOverlay.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
                if (hasFocus) {
                    // Animate scale up
                    itemView.animate()
                        .scaleX(1.12f)
                        .scaleY(1.12f)
                        .setDuration(90)
                        .start()

                    onItemFocused(item, position)
                } else {
                    // Animate scale down
                    itemView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(90)
                        .start()
                }
            }

            // Handle clicks
            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PosterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_poster, parent, false)
        return PosterViewHolder(view)
    }

    override fun onBindViewHolder(holder: PosterViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size
}
