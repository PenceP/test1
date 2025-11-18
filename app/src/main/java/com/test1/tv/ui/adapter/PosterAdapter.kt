package com.test1.tv.ui.adapter

import android.graphics.drawable.Drawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.test1.tv.R
import com.test1.tv.data.model.ContentItem
import kotlin.math.max

class PosterAdapter(
    initialItems: List<ContentItem>,
    private val onItemClick: (ContentItem) -> Unit,
    private val onItemFocused: (ContentItem, Int) -> Unit,
    private val onNavigateToNavBar: () -> Unit,
    private val onNearEnd: () -> Unit
) : RecyclerView.Adapter<PosterAdapter.PosterViewHolder>() {

    companion object {
        private const val NEAR_END_THRESHOLD = 10
    }

    inner class PosterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val posterImage: ImageView = itemView.findViewById(R.id.poster_image)
        val focusOverlay: View = itemView.findViewById(R.id.focus_overlay)
        val titleOverlay: TextView = itemView.findViewById(R.id.poster_title_overlay)

        fun bind(item: ContentItem, position: Int) {
            titleOverlay.text = item.title
            titleOverlay.visibility = View.VISIBLE

            Glide.with(itemView.context)
                .load(item.posterUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.default_background)
                .error(R.drawable.default_background)
                .into(object : CustomTarget<Drawable>() {
                    override fun onLoadCleared(placeholder: Drawable?) {
                        posterImage.setImageDrawable(placeholder)
                        titleOverlay.visibility = View.VISIBLE
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        super.onLoadFailed(errorDrawable)
                        posterImage.setImageDrawable(errorDrawable)
                        titleOverlay.visibility = View.VISIBLE
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        posterImage.setImageDrawable(resource)
                        titleOverlay.visibility = View.GONE
                    }
                })

            itemView.setOnFocusChangeListener { _, hasFocus ->
                focusOverlay.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
                if (hasFocus) {
                    itemView.animate()
                        .scaleX(1.12f)
                        .scaleY(1.12f)
                        .setDuration(90)
                        .start()

                    onItemFocused(item, position)
                    val nearEndIndex = max(itemCount - NEAR_END_THRESHOLD, 0)
                    if (bindingAdapterPosition >= nearEndIndex) {
                        onNearEnd()
                    }
                } else {
                    itemView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(90)
                        .start()
                }
            }

            itemView.setOnKeyListener { _, keyCode, event ->
                if (
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    bindingAdapterPosition == 0
                ) {
                    onNavigateToNavBar()
                    true
                } else {
                    false
                }
            }

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

    private val items = initialItems.toMutableList()

    override fun onBindViewHolder(holder: PosterViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    fun replaceAll(newItems: List<ContentItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun appendItems(newItems: List<ContentItem>) {
        val start = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(start, newItems.size)
    }
}
