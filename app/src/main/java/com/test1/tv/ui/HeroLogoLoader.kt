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
 * Logo loader with versioning to avoid stale updates when focus changes quickly.
 */
class HeroLogoLoader(
    private val fragment: Fragment,
    private val logoView: ImageView,
    private val titleView: View?,
    maxWidthRes: Int? = null,
    maxHeightRes: Int? = null
) {
    private var currentVersion = 0L
    private var currentTarget: CustomTarget<Drawable>? = null
    private val maxWidth = maxWidthRes?.let { logoView.resources.getDimensionPixelSize(it) }
    private val maxHeight = maxHeightRes?.let { logoView.resources.getDimensionPixelSize(it) }

    fun load(logoUrl: String?) {
        val version = ++currentVersion
        currentTarget?.let {
            runCatching { Glide.with(fragment).clear(it) }
        }
        currentTarget = null

        titleView?.visibility = View.VISIBLE
        logoView.visibility = View.GONE
        logoView.setImageDrawable(null)

        if (logoUrl.isNullOrBlank()) return

        currentTarget = object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                if (version != currentVersion) return
                logoView.setImageDrawable(resource)
                applyBounds(resource)
                logoView.visibility = View.VISIBLE
                titleView?.visibility = View.GONE
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                if (version != currentVersion) return
                logoView.setImageDrawable(placeholder)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                if (version != currentVersion) return
                logoView.visibility = View.GONE
                titleView?.visibility = View.VISIBLE
            }
        }

        Glide.with(fragment)
            .load(logoUrl)
            .thumbnail(
                Glide.with(fragment)
                    .load(logoUrl)
                    .override(300, 100)
            )
            .transition(DrawableTransitionOptions.withCrossFade(150))
            .into(currentTarget!!)
    }

    fun cancel() {
        currentTarget?.let {
            runCatching { Glide.with(fragment).clear(it) }
        }
        currentTarget = null
    }

    private fun applyBounds(resource: Drawable) {
        val maxW = maxWidth ?: return
        val maxH = maxHeight ?: return

        val intrinsicWidth = resource.intrinsicWidth.takeIf { it > 0 } ?: logoView.width
        val intrinsicHeight = resource.intrinsicHeight.takeIf { it > 0 } ?: logoView.height
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return

        val scale = min(maxW.toFloat() / intrinsicWidth, maxH.toFloat() / intrinsicHeight)
        logoView.layoutParams = logoView.layoutParams.apply {
            width = (intrinsicWidth * scale).roundToInt()
            height = (intrinsicHeight * scale).roundToInt()
        }
        logoView.scaleX = 1f
        logoView.scaleY = 1f
    }
}
