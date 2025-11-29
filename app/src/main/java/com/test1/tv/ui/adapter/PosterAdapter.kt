package com.test1.tv.ui.adapter

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.test1.tv.R
import com.test1.tv.data.model.ContentItem
import com.test1.tv.ui.AccentColorCache
import androidx.palette.graphics.Palette
import kotlin.math.max
import kotlin.math.roundToInt

class PosterAdapter(
    private val onItemClick: (ContentItem, ImageView) -> Unit,
    private val onItemFocused: (ContentItem, Int) -> Unit,
    private val onNavigateToNavBar: () -> Unit,
    private val onNearEnd: () -> Unit,
    private val onItemLongPressed: ((ContentItem) -> Unit)? = null,
    private val presentation: RowPresentation = RowPresentation.PORTRAIT,
    private val accentColorCache: AccentColorCache
) : ListAdapter<ContentItem, PosterAdapter.PosterViewHolder>(ContentDiffCallback()) {

    companion object {
        private const val NEAR_END_THRESHOLD = 14
        private const val BORDER_WIDTH_DP = 20f
        private const val PORTRAIT_RADIUS_DP = 18f
        private const val LANDSCAPE_RADIUS_DP = 16f
        private const val DEFAULT_BORDER_COLOR = Color.WHITE
    }

    init {
        setHasStableIds(true) // FIX #6: Stable IDs for better animations
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).tmdbId.toLong()
    }

    fun hasPresentation(expected: RowPresentation): Boolean {
        return presentation == expected
    }

    inner class PosterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val posterImage: ImageView = itemView.findViewById(R.id.poster_image)
        val focusOverlay: View = itemView.findViewById(R.id.focus_overlay)
        val titleOverlay: TextView = itemView.findViewById(R.id.poster_title_overlay)
        val cardContainer: CardView? = itemView.findViewById(R.id.poster_card)
        val watchedBadge: ImageView? = itemView.findViewById(R.id.watched_badge)

        fun bind(item: ContentItem, position: Int) {
            // Reset recycled state
            posterImage.setImageDrawable(null)
            titleOverlay.text = ""
            titleOverlay.visibility = View.VISIBLE
            watchedBadge?.setImageResource(R.drawable.ic_check_badge)
            watchedBadge?.visibility = View.GONE
            watchedBadge?.alpha = 1f
            watchedBadge?.elevation = 24f

            val artworkUrl = if (presentation == RowPresentation.LANDSCAPE_16_9) {
                item.backdropUrl ?: item.posterUrl
            } else {
                item.posterUrl
            }
            val isPlaceholder = item.tmdbId == -1 || artworkUrl.isNullOrBlank()

            titleOverlay.text = item.title
            titleOverlay.visibility = View.VISIBLE
            cardContainer?.let { ViewCompat.setElevation(it, 6f) }

            val cachedAccent = accentColorCache.get(item)

            if (isPlaceholder) {
                if (item.title.contains("Trakt", ignoreCase = true)) {
                    posterImage.setImageResource(R.drawable.ic_trakt_logo)
                } else {
                    posterImage.setImageResource(R.drawable.default_background)
                }
                accentColorCache.put(item, DEFAULT_BORDER_COLOR)
            } else {
                Glide.with(itemView.context)
                    .load(artworkUrl)
                    .onlyRetrieveFromCache(true)  // Force cache-only for instant loading
                    .transition(DrawableTransitionOptions.withCrossFade(150))
                    .placeholder(R.drawable.default_background)
                    .error(R.drawable.default_background)
                    .override(300, 450)
                    .into(object : CustomTarget<Drawable>() {
                        override fun onLoadCleared(placeholder: Drawable?) {
                            posterImage.setImageDrawable(placeholder)
                            titleOverlay.visibility = View.VISIBLE
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            super.onLoadFailed(errorDrawable)
                            posterImage.setImageDrawable(errorDrawable)
                            titleOverlay.visibility = View.VISIBLE

                            // If cache fails, load from network in background
                            Glide.with(itemView.context)
                                .load(artworkUrl)
                                .onlyRetrieveFromCache(false)
                                .override(300, 450)
                                .into(object : CustomTarget<Drawable>() {
                                    override fun onLoadCleared(placeholder: Drawable?) {
                                        // Keep current state
                                    }

                                    override fun onResourceReady(
                                        resource: Drawable,
                                        transition: Transition<in Drawable>?
                                    ) {
                                        posterImage.setImageDrawable(resource)
                                        titleOverlay.visibility = View.GONE  // Hide title when network load succeeds
                                        val accentColor = cachedAccent ?: extractAccentColor(resource)
                                        accentColorCache.put(item, accentColor)
                                        if (itemView.isFocused) applyFocusOverlay(true, accentColor)
                                    }
                                })
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            transition: Transition<in Drawable>?
                        ) {
                            posterImage.setImageDrawable(resource)
                            titleOverlay.visibility = View.GONE
                            val accentColor = cachedAccent ?: extractAccentColor(resource)
                            accentColorCache.put(item, accentColor)
                            if (itemView.isFocused) {
                                applyFocusOverlay(true, accentColor)
                            }
                        }
                    })
            }

            itemView.setOnFocusChangeListener { _, hasFocus ->
                val accentColor = accentColorCache.get(item) ?: DEFAULT_BORDER_COLOR
                applyFocusOverlay(hasFocus, accentColor)
                if (hasFocus) {
                    itemView.playSoundEffect(SoundEffectConstants.CLICK)
                    onItemFocused(item, position)
                    val nearEndIndex = max(itemCount - NEAR_END_THRESHOLD, 0)
                    if (bindingAdapterPosition >= nearEndIndex) {
                        onNearEnd()
                    }
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
                onItemClick(item, posterImage)
            }

            if (onItemLongPressed != null) {
                itemView.isLongClickable = true
                itemView.setOnLongClickListener {
                    onItemLongPressed.invoke(item)
                    true
                }
            } else {
                itemView.isLongClickable = false
                itemView.setOnLongClickListener(null)
            }

            val progress = item.watchProgress
                ?: com.test1.tv.data.repository.WatchStatusProvider.getProgress(item.tmdbId, item.type)
            val isWatched = progress?.let { it >= 0.9 } == true
            watchedBadge?.visibility = if (isWatched) View.VISIBLE else View.GONE
            if (isWatched) {
                android.util.Log.d("PosterBadge", "watched badge on '${item.title}' progress=$progress")
                (cardContainer ?: itemView).post {
                    watchedBadge?.bringToFront()
                }
            }
        }

        private fun applyFocusOverlay(hasFocus: Boolean, accentColor: Int) {
            if (hasFocus) {
                val radius = if (presentation == RowPresentation.LANDSCAPE_16_9) {
                    LANDSCAPE_RADIUS_DP
                } else {
                    PORTRAIT_RADIUS_DP
                }
                focusOverlay.background = createFocusDrawable(
                    itemView.context,
                    accentColor,
                    radius
                )
                focusOverlay.visibility = View.VISIBLE
            } else {
                focusOverlay.background = null
                focusOverlay.visibility = View.INVISIBLE
            }
        }

        private fun createFocusDrawable(
            context: Context,
            color: Int,
            radiusDp: Float
        ): Drawable {
            val strokeColor = ColorUtils.blendARGB(color, Color.WHITE, 0.65f)
            val borderWidth = dpToPx(context, BORDER_WIDTH_DP).roundToInt()
            val cornerRadius = dpToPx(context, radiusDp)

            val borderLayer = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = cornerRadius
                setStroke(borderWidth, strokeColor)
                setColor(Color.TRANSPARENT)
            }

            val glowLayer = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = cornerRadius + dpToPx(context, 4f)
                setColor(ColorUtils.setAlphaComponent(color, 60))
                setStroke(borderWidth * 2, ColorUtils.setAlphaComponent(color, 40))
            }

            return LayerDrawable(arrayOf(glowLayer, borderLayer))
        }

        private fun dpToPx(context: Context, dp: Float): Float =
            dp * context.resources.displayMetrics.density

        private fun extractAccentColor(drawable: Drawable?): Int {
            drawable ?: return DEFAULT_BORDER_COLOR
            val bitmap = when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> drawable.toBitmap()
            }
            val palette = Palette.from(bitmap).generate()
            return palette.vibrantSwatch?.rgb
                ?: palette.darkVibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: DEFAULT_BORDER_COLOR
        }

        private fun getColorKey(item: ContentItem): Int =
            item.tmdbId.takeIf { it != 0 } ?: item.id
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PosterViewHolder {
        val layoutRes = if (presentation == RowPresentation.LANDSCAPE_16_9) {
            R.layout.item_poster_landscape
        } else {
            R.layout.item_poster
        }
        val view = LayoutInflater.from(parent.context)
            .inflate(layoutRes, parent, false)
        return PosterViewHolder(view)
    }

    override fun onBindViewHolder(holder: PosterViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }
}
