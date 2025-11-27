package com.test1.tv.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlin.math.max

/**
 * Handles backdrop loading + ambient gradient updates for hero sections.
 */
class HeroBackgroundController(
    private val fragment: Fragment,
    private val backdropView: ImageView,
    private val ambientOverlay: View,
    private val defaultAmbientColor: Int = Color.parseColor("#0A0F1F")
) {

    private val argbEvaluator = ArgbEvaluator()
    private var ambientAnimator: ValueAnimator? = null
    private var currentAmbientColor: Int = defaultAmbientColor
    private var requestVersion: Long = 0

    fun updateBackdrop(backdropUrl: String?, fallbackDrawable: Drawable?) {
        val version = ++requestVersion
        crossfadeBackdrop(fallbackDrawable)

        if (backdropUrl.isNullOrBlank()) {
            animateAmbientToColor(defaultAmbientColor)
            return
        }

        // Main backdrop
        Glide.with(fragment)
            .load(backdropUrl)
            .thumbnail(0.2f)
            .into(object : CustomTarget<Drawable>() {
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
                    animateAmbientToColor(defaultAmbientColor)
                }
            })

        // Palette for ambient
        Glide.with(fragment)
            .asBitmap()
            .load(backdropUrl)
            .override(150, 150)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    if (version != requestVersion) return
                    extractPalette(resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) = Unit
                override fun onLoadFailed(errorDrawable: Drawable?) {
                    if (version != requestVersion) return
                    animateAmbientToColor(defaultAmbientColor)
                }
            })
    }

    private fun extractPalette(bitmap: Bitmap) {
        Palette.from(bitmap).generate { palette ->
            val swatchColor = palette?.vibrantSwatch?.rgb
                ?: palette?.darkVibrantSwatch?.rgb
                ?: palette?.dominantSwatch?.rgb
                ?: palette?.mutedSwatch?.rgb
                ?: defaultAmbientColor
            val deepColor = ColorUtils.blendARGB(swatchColor, Color.BLACK, 0.55f)
            animateAmbientToColor(deepColor)
        }
    }

    private fun animateAmbientToColor(targetColor: Int) {
        if (!fragment.isAdded) return
        ambientAnimator?.cancel()
        val startColor = currentAmbientColor
        if (startColor == targetColor) {
            updateAmbientGradient(targetColor)
            return
        }

        ambientAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200L
            addUpdateListener { animator ->
                val blended = argbEvaluator.evaluate(
                    animator.animatedFraction,
                    startColor,
                    targetColor
                ) as Int
                updateAmbientGradient(blended)
            }
            start()
        }
        fadeAmbientOverlayIn()
    }

    private fun fadeAmbientOverlayIn() {
        ambientOverlay.animate().cancel()
        ambientOverlay.animate()
            .alpha(1f)
            .setDuration(180L)
            .start()
    }

    private fun updateAmbientGradient(color: Int) {
        currentAmbientColor = color
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

        val gradient = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
            gradientRadius = radius
            setGradientCenter(0.32f, 0.28f)
            colors = intArrayOf(
                ColorUtils.setAlphaComponent(color, 220),
                ColorUtils.setAlphaComponent(color, 120),
                ColorUtils.setAlphaComponent(color, 10)
            )
        }
        ambientOverlay.background = gradient
    }

    private fun crossfadeBackdrop(newDrawable: Drawable?) {
        val current = backdropView.drawable
        if (current != null && newDrawable != null && current !== newDrawable) {
            val transition = TransitionDrawable(arrayOf(current, newDrawable))
            transition.isCrossFadeEnabled = true
            backdropView.setImageDrawable(transition)
            transition.startTransition(180)
        } else {
            backdropView.setImageDrawable(newDrawable)
        }
    }
}
