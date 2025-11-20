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
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.test1.tv.R
import com.test1.tv.data.model.ContentItem
import androidx.palette.graphics.Palette
import kotlin.math.max
import kotlin.math.roundToInt

class PosterAdapter(
    initialItems: List<ContentItem>,
    private val onItemClick: (ContentItem, ImageView) -> Unit,
    private val onItemFocused: (ContentItem, Int) -> Unit,
    private val onNavigateToNavBar: () -> Unit,
    private val onNearEnd: () -> Unit,
    private val onItemLongPressed: ((ContentItem) -> Unit)? = null,
    private val presentation: RowPresentation = RowPresentation.PORTRAIT
) : RecyclerView.Adapter<PosterAdapter.PosterViewHolder>() {

    companion object {
        private const val NEAR_END_THRESHOLD = 10
        private const val BORDER_WIDTH_DP = 20f
        private const val PORTRAIT_RADIUS_DP = 18f
        private const val LANDSCAPE_RADIUS_DP = 16f
        private const val DEFAULT_BORDER_COLOR = Color.WHITE
    }

    private val posterAccentColors = mutableMapOf<Int, Int>()

    inner class PosterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val posterImage: ImageView = itemView.findViewById(R.id.poster_image)
        val focusOverlay: View = itemView.findViewById(R.id.focus_overlay)
        val titleOverlay: TextView = itemView.findViewById(R.id.poster_title_overlay)
        val cardContainer: CardView? = itemView.findViewById(R.id.poster_card)

        fun bind(item: ContentItem, position: Int) {
            titleOverlay.text = item.title
            titleOverlay.visibility = View.VISIBLE
            cardContainer?.let { ViewCompat.setElevation(it, 6f) }

            Glide.with(itemView.context)
                .load(item.posterUrl)
                .thumbnail(0.25f)  // Load a 25% quality version first for faster display
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.default_background)
                .error(R.drawable.default_background)
                .override(300, 450)  // Optimize image size for portrait posters
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
                        val accentColor = extractAccentColor(resource)
                        posterAccentColors[getColorKey(item)] = accentColor
                        if (itemView.isFocused) {
                            applyFocusOverlay(true, accentColor)
                        }
                    }
                })

            itemView.setOnFocusChangeListener { _, hasFocus ->
                val accentColor = posterAccentColors[getColorKey(item)] ?: DEFAULT_BORDER_COLOR
                applyFocusOverlay(hasFocus, accentColor)
                val targetScale = if (hasFocus) 1.1f else 1.0f
                if (hasFocus) {
                    itemView.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .setDuration(110)
                        .start()

                    cardContainer?.let { ViewCompat.setElevation(it, 18f) }
                    itemView.playSoundEffect(SoundEffectConstants.CLICK)
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
                    cardContainer?.let { ViewCompat.setElevation(it, 6f) }
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
