package com.test1.tv.ui.adapter

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
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
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.palette.graphics.Palette
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

class PosterAdapter(
    private val onItemClick: (ContentItem, ImageView) -> Unit,
    private val onItemFocused: (ContentItem, Int) -> Unit,
    private val onNavigateToNavBar: () -> Unit,
    private val onNearEnd: () -> Unit,
    private val onItemLongPressed: ((ContentItem) -> Unit)? = null,
    private val presentation: RowPresentation = RowPresentation.PORTRAIT,
    private val accentColorCache: AccentColorCache,
    private val coroutineScope: CoroutineScope
) : ListAdapter<ContentItem, PosterAdapter.PosterViewHolder>(ContentDiffCallback()) {

    companion object {
        private const val NEAR_END_THRESHOLD = 14
        private const val BORDER_WIDTH_DP = 2f
        private const val PORTRAIT_RADIUS_DP = 18f
        private const val LANDSCAPE_RADIUS_DP = 16f
        private const val DEFAULT_BORDER_COLOR = Color.WHITE
        private const val SCALE_FOCUSED = 1.1f
        private const val SCALE_UNFOCUSED = 1.0f
        private val PLACEHOLDER_DRAWABLE = ColorDrawable(Color.DKGRAY)
    }

    init {
        setHasStableIds(true) // FIX #6: Stable IDs for better animations
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun getItemId(position: Int): Long {
        val item = getItem(position)
        // Use unique 'id' field for items with tmdbId = -1 (collections, directors, networks)
        // Otherwise use tmdbId for regular content items
        return if (item.tmdbId == -1) item.id.toLong() else item.tmdbId.toLong()
    }

    override fun getItemViewType(position: Int): Int {
        return if (presentation == RowPresentation.LANDSCAPE_16_9) 1 else 0
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
        val progressBar: View? = itemView.findViewById(R.id.progress_bar)
        private var paletteJob: Job? = null

        private var placeholderAnimator: ValueAnimator? = null

        fun bind(item: ContentItem, position: Int) {
            // Reset recycled state
            posterImage.setImageDrawable(null)
            titleOverlay.text = ""
            titleOverlay.visibility = View.VISIBLE
            itemView.scaleX = SCALE_UNFOCUSED
            itemView.scaleY = SCALE_UNFOCUSED
            watchedBadge?.setImageResource(R.drawable.ic_check_badge)
            watchedBadge?.visibility = View.GONE
            watchedBadge?.alpha = 1f
            watchedBadge?.elevation = 24f
            progressBar?.visibility = View.GONE
            progressBar?.alpha = 1f

            val artworkUrl = if (presentation == RowPresentation.LANDSCAPE_16_9) {
                // For continue-watching (has watchProgress), prefer poster/still so hero can keep show backdrop.
                if (item.watchProgress != null) {
                    item.posterUrl ?: item.backdropUrl
                } else {
                    item.backdropUrl ?: item.posterUrl
                }
            } else {
                item.posterUrl
            }
            val isPlaceholder = item.isPlaceholder || item.tmdbId == -1 || artworkUrl.isNullOrBlank()
            val (overrideWidth, overrideHeight) = if (presentation == RowPresentation.LANDSCAPE_16_9) {
                600 to 338
            } else {
                300 to 450
            }

            val keepTitle = shouldKeepTitleOverlay(item)
            titleOverlay.text = item.title
            titleOverlay.typeface = if (keepTitle) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            titleOverlay.visibility = if (keepTitle) View.VISIBLE else View.VISIBLE
            cardContainer?.let { ViewCompat.setElevation(it, 6f) }

            val cachedAccent = accentColorCache.get(item)
            val glideContext = itemView.context
            if (!glideContext.isValidForGlide()) {
                // Activity is going away or context wrapped around a destroyed activity; skip Glide work.
                return
            }

            // Determine loading strategy upfront to avoid conflicts
            val isDrawableResource = artworkUrl?.startsWith("drawable://") == true

            when {
                isDrawableResource -> {
                    // Handle drawable resources (collections, directors, networks) using Glide for async loading
                    stopPlaceholderAnimation()
                    val drawableName = artworkUrl.removePrefix("drawable://")
                    val drawableId = glideContext.resources.getIdentifier(
                        drawableName,
                        "drawable",
                        glideContext.packageName
                    )
                    if (drawableId != 0) {
                        // Use Glide to load drawable resources asynchronously to avoid blocking UI thread
                        Glide.with(glideContext)
                            .load(drawableId)
                            .transition(DrawableTransitionOptions.withCrossFade(100))
                            .placeholder(PLACEHOLDER_DRAWABLE)
                            .override(overrideWidth, overrideHeight)
                            .into(object : CustomTarget<Drawable>() {
                                override fun onLoadCleared(placeholder: Drawable?) {
                                    posterImage.setImageDrawable(placeholder)
                                }

                                override fun onResourceReady(
                                    resource: Drawable,
                                    transition: Transition<in Drawable>?
                                ) {
                                    posterImage.setImageDrawable(resource)
                                    titleOverlay.visibility = if (keepTitle) View.VISIBLE else View.GONE
                                    accentColorCache.put(item, DEFAULT_BORDER_COLOR)
                                }
                            })
                    } else {
                        posterImage.setImageDrawable(PLACEHOLDER_DRAWABLE)
                        titleOverlay.visibility = View.VISIBLE
                        accentColorCache.put(item, DEFAULT_BORDER_COLOR)
                    }
                }
                isPlaceholder -> {
                    // Handle placeholders
                    stopPlaceholderAnimation()
                    if (item.title.contains("Trakt", ignoreCase = true)) {
                        posterImage.setImageResource(R.drawable.ic_trakt_logo)
                    } else {
                        posterImage.setImageDrawable(PLACEHOLDER_DRAWABLE)
                    }
                    accentColorCache.put(item, DEFAULT_BORDER_COLOR)
                    startPlaceholderAnimation()
                }
                else -> {
                    // Only use Glide for regular HTTP/HTTPS URLs
                    stopPlaceholderAnimation()
                    Glide.with(glideContext)
                        .load(artworkUrl)
                        .onlyRetrieveFromCache(true)  // Force cache-only for instant loading
                        .transition(DrawableTransitionOptions.withCrossFade(150))
                        .placeholder(PLACEHOLDER_DRAWABLE)
                        .error(PLACEHOLDER_DRAWABLE)
                        .override(overrideWidth, overrideHeight)
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
                                Glide.with(glideContext)
                                    .load(artworkUrl)
                                    .onlyRetrieveFromCache(false)
                                    .override(overrideWidth, overrideHeight)
                                    .into(object : CustomTarget<Drawable>() {
                                        override fun onLoadCleared(placeholder: Drawable?) {
                                            // Keep current state
                                        }

                                        override fun onResourceReady(
                                            resource: Drawable,
                                            transition: Transition<in Drawable>?
                                        ) {
                                            posterImage.setImageDrawable(resource)
                                            titleOverlay.visibility = if (keepTitle) View.VISIBLE else View.GONE
                                            if (cachedAccent != null) {
                                                accentColorCache.put(item, cachedAccent)
                                                if (itemView.isFocused) applyFocusOverlay(true, cachedAccent)
                                            } else {
                                                extractAccentColorAsync(item, resource)
                                            }
                                            stopPlaceholderAnimation()
                                        }
                                    })
                            }

                            override fun onResourceReady(
                                resource: Drawable,
                                transition: Transition<in Drawable>?
                            ) {
                                posterImage.setImageDrawable(resource)
                                titleOverlay.visibility = if (keepTitle) View.VISIBLE else View.GONE
                                if (cachedAccent != null) {
                                    accentColorCache.put(item, cachedAccent)
                                    if (itemView.isFocused) {
                                        applyFocusOverlay(true, cachedAccent)
                                    }
                                } else {
                                    extractAccentColorAsync(item, resource)
                                }
                                stopPlaceholderAnimation()
                            }
                        })
                }
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
                if (!isPlaceholder) {
                    onItemClick(item, posterImage)
                }
            }

            if (onItemLongPressed != null) {
                itemView.isLongClickable = !isPlaceholder
                itemView.setOnLongClickListener {
                    if (isPlaceholder) return@setOnLongClickListener false
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

            // Show red progress bar for continue watching (landscape) items
            if (presentation == RowPresentation.LANDSCAPE_16_9 && progress != null && progressBar != null) {
                val clamped = progress.coerceIn(0.0, 1.0)
                progressBar.visibility = View.VISIBLE
                progressBar.post {
                    val params = progressBar.layoutParams
                    params.width = (itemView.width * clamped).toInt().coerceAtLeast(1)
                    progressBar.layoutParams = params
                }
                progressBar.alpha = if (clamped >= 0.99) 0.25f else 1f // faint bar when essentially complete
            }
        }

        fun recycle() {
            paletteJob?.cancel()
            paletteJob = null
            stopPlaceholderAnimation()
        }

        private fun applyFocusOverlay(hasFocus: Boolean, accentColor: Int) {
            val targetScale = if (hasFocus) SCALE_FOCUSED else SCALE_UNFOCUSED
            itemView.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(120L)
                .start()

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
            val strokeColor = Color.WHITE
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
                setColor(ColorUtils.setAlphaComponent(color, 40))
                setStroke(borderWidth * 2, ColorUtils.setAlphaComponent(color, 30))
            }

            return LayerDrawable(arrayOf(glowLayer, borderLayer))
        }

        private fun dpToPx(context: Context, dp: Float): Float =
            dp * context.resources.displayMetrics.density

        private fun extractAccentColorAsync(item: ContentItem, drawable: Drawable) {
            paletteJob?.cancel()
            paletteJob = coroutineScope.launch(Dispatchers.Default) {
                val color = runCatching {
                    val bitmap = when (drawable) {
                        is BitmapDrawable -> drawable.bitmap
                            ?.takeIf { !it.isRecycled }
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                            ?: return@runCatching DEFAULT_BORDER_COLOR
                        else -> drawable.toBitmap(50, 75)
                    }
                    if (bitmap.isRecycled) return@runCatching DEFAULT_BORDER_COLOR
                    val palette = Palette.from(bitmap).generate()
                    palette.vibrantSwatch?.rgb
                        ?: palette.darkVibrantSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                        ?: DEFAULT_BORDER_COLOR
                }.getOrDefault(DEFAULT_BORDER_COLOR)

                accentColorCache.put(item, color)
                withContext(Dispatchers.Main) {
                    if (itemView.isFocused) {
                        applyFocusOverlay(true, color)
                    }
                }
            }
        }

        private fun getColorKey(item: ContentItem): Int =
            item.tmdbId.takeIf { it != 0 } ?: item.id

        private fun shouldKeepTitleOverlay(item: ContentItem): Boolean =
            item.tmdbId < 0

        private fun startPlaceholderAnimation() {
            placeholderAnimator?.cancel()
            placeholderAnimator = ValueAnimator.ofFloat(0.6f, 1f).apply {
                duration = 750L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    posterImage.alpha = it.animatedValue as Float
                }
                start()
            }
        }

        private fun stopPlaceholderAnimation() {
            placeholderAnimator?.cancel()
            placeholderAnimator = null
            posterImage.alpha = 1f
        }
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

    override fun onViewRecycled(holder: PosterViewHolder) {
        super.onViewRecycled(holder)
        holder.recycle()
        Glide.with(holder.itemView).clear(holder.posterImage)
    }

    private fun Context?.isValidForGlide(): Boolean {
        if (this == null) return false
        if (this is android.app.Activity) {
            if (this.isDestroyed || this.isFinishing) return false
        }
        return if (this is ContextWrapper) {
            this.baseContext.isValidForGlide()
        } else {
            true
        }
    }
}
