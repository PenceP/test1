package com.test1.tv.ui

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Centralized helper to load hero logos with thumbnails, proper bounds, and title fallback.
 */
object HeroLogoLoader {
    fun load(
        fragment: Fragment,
        logoUrl: String?,
        logoView: ImageView,
        titleView: View? = null,
        maxWidthRes: Int? = null,
        maxHeightRes: Int? = null
    ) {
        titleView?.visibility = View.VISIBLE
        logoView.visibility = View.GONE
        logoView.setImageDrawable(null)
        logoView.scaleX = 1f
        logoView.scaleY = 1f

        if (logoUrl.isNullOrBlank()) return

        val resources = logoView.resources
        val maxWidth = maxWidthRes?.let { resources.getDimensionPixelSize(it) }
        val maxHeight = maxHeightRes?.let { resources.getDimensionPixelSize(it) }

        val thumb = Glide.with(fragment)
            .load(logoUrl)
            .override(300, 100)

        Glide.with(fragment)
            .load(logoUrl)
            .thumbnail(thumb)
            .transition(DrawableTransitionOptions.withCrossFade(150))
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    logoView.setImageDrawable(resource)
                    applyBounds(logoView, resource, maxWidth, maxHeight)
                    logoView.visibility = View.VISIBLE
                    titleView?.visibility = View.GONE
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    logoView.setImageDrawable(placeholder)
                    logoView.scaleX = 1f
                    logoView.scaleY = 1f
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    logoView.visibility = View.GONE
                    titleView?.visibility = View.VISIBLE
                    logoView.scaleX = 1f
                    logoView.scaleY = 1f
                }
            })
    }

    private fun applyBounds(view: ImageView, resource: Drawable, maxWidth: Int?, maxHeight: Int?) {
        if (maxWidth == null || maxHeight == null) return
        val intrinsicWidth = if (resource.intrinsicWidth > 0) resource.intrinsicWidth else view.width
        val intrinsicHeight = if (resource.intrinsicHeight > 0) resource.intrinsicHeight else view.height
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return

        val widthRatio = maxWidth.toFloat() / intrinsicWidth
        val heightRatio = maxHeight.toFloat() / intrinsicHeight
        val scale = min(widthRatio, heightRatio)

        val params = view.layoutParams
        params.width = (intrinsicWidth * scale).roundToInt()
        params.height = (intrinsicHeight * scale).roundToInt()
        view.layoutParams = params
        view.scaleX = 1f
        view.scaleY = 1f
    }
}
