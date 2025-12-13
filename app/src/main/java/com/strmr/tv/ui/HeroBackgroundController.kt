package com.strmr.tv.ui

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlin.math.max

/**
 * Handles backdrop loading for hero sections.
 * Uses a simple transparent black overlay instead of palette extraction for performance.
 */
class HeroBackgroundController(
    private val fragment: Fragment,
    private val backdropView: ImageView,
    private val ambientOverlay: View,
    private val defaultAmbientColor: Int = Color.parseColor("#80000000") // Transparent black
) {

    companion object {
        // Blur settings - radius 15-25 gives a nice cinematic effect
        private const val BLUR_RADIUS = 8
        private const val BLUR_SAMPLING = 3 // Downsampling for performance
    }

    private var requestVersion: Long = 0
    private var currentBackdropTarget: CustomTarget<Drawable>? = null
    private var overlayInitialized = false

    fun updateBackdrop(backdropUrl: String?, fallbackDrawable: Drawable?) {
        val version = ++requestVersion

        // Cancel previous Glide requests to save resources
        currentBackdropTarget?.let { Glide.with(fragment).clear(it) }

        // Set up simple transparent black overlay once (no palette extraction needed)
        if (!overlayInitialized) {
            setupStaticOverlay()
            overlayInitialized = true
        }

        crossfadeBackdrop(fallbackDrawable)

        if (backdropUrl.isNullOrBlank()) {
            return
        }

        // Convert drawable:// URLs to resource IDs for Glide
        val isDrawableResource = backdropUrl.startsWith("drawable://")
        val loadTarget: Any = if (isDrawableResource) {
            val drawableName = backdropUrl.removePrefix("drawable://")
            val drawableId = fragment.requireContext().resources.getIdentifier(
                drawableName,
                "drawable",
                fragment.requireContext().packageName
            )
            if (drawableId != 0) drawableId else backdropUrl
        } else {
            backdropUrl
        }

        // Disable disk caching for drawable resources to prevent VectorDrawable encoding crash
        val diskCacheStrategy = if (isDrawableResource) DiskCacheStrategy.NONE else DiskCacheStrategy.AUTOMATIC

        // Apply blur transformation for a softer, more cinematic look
        val blurTransform = BlurTransformation(BLUR_RADIUS, BLUR_SAMPLING)

        val thumbnailRequest = Glide.with(fragment)
            .load(loadTarget)
            .diskCacheStrategy(diskCacheStrategy)
            .transform(blurTransform)
            .override(320, 180)

        // Main backdrop - track target for cancellation
        currentBackdropTarget = object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                if (version != requestVersion) return
                crossfadeBackdrop(resource)
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                if (version != requestVersion) return
                crossfadeBackdrop(placeholder ?: fallbackDrawable)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                if (version != requestVersion) return
                crossfadeBackdrop(errorDrawable ?: fallbackDrawable)
            }
        }

        Glide.with(fragment)
            .load(loadTarget)
            .diskCacheStrategy(diskCacheStrategy)
            .transform(blurTransform)
            .thumbnail(thumbnailRequest)
            .into(currentBackdropTarget!!)
    }

    /**
     * Sets up a simple transparent black gradient overlay.
     * No palette extraction - saves processing time.
     */
    private fun setupStaticOverlay() {
        val widthCandidates = listOf(
            backdropView.width,
            ambientOverlay.width,
            ambientOverlay.resources.displayMetrics.widthPixels
        ).filter { it > 0 }
        val heightCandidates = listOf(
            backdropView.height,
            ambientOverlay.height,
            ambientOverlay.resources.displayMetrics.heightPixels
        ).filter { it > 0 }

        val width = widthCandidates.maxOrNull() ?: ambientOverlay.resources.displayMetrics.widthPixels
        val height = heightCandidates.maxOrNull() ?: ambientOverlay.resources.displayMetrics.heightPixels
        val radius = max(width, height).toFloat() * 0.95f

        // Simple transparent black gradient - no color changes
        val transparentBlack = Color.parseColor("#000000")
        val gradient = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
            gradientRadius = radius
            setGradientCenter(0.32f, 0.28f)
            colors = intArrayOf(
                ColorUtils.setAlphaComponent(transparentBlack, 180),
                ColorUtils.setAlphaComponent(transparentBlack, 100),
                ColorUtils.setAlphaComponent(transparentBlack, 10)
            )
        }
        ambientOverlay.background = gradient
        ambientOverlay.alpha = 1f
    }

    private fun crossfadeBackdrop(newDrawable: Drawable?) {
        backdropView.animate().cancel()
        val safeDrawable = cloneDrawable(newDrawable)

        if (safeDrawable == null) {
            backdropView.setImageDrawable(null)
            backdropView.alpha = 1f
            return
        }

        backdropView.animate()
            .alpha(0f)
            .setDuration(200L)
            .withEndAction {
                backdropView.setImageDrawable(safeDrawable)
                backdropView.animate()
                    .alpha(1f)
                    .setDuration(180L)
                    .start()
            }.start()
    }

    private fun cloneDrawable(drawable: Drawable?): Drawable? {
        return drawable?.constantState?.newDrawable()?.mutate()
            ?: drawable?.let { it.constantState?.newDrawable()?.mutate() ?: it }
    }
}
