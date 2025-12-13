package com.strmr.tv.ui.settings.util

import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.strmr.tv.R

/**
 * Extension function to setup focus effects on any View.
 * Applies scale animation and background changes on focus.
 *
 * @param scale The scale factor to apply when focused (default 1.1f)
 * @param applyBackgroundChange Whether to change background drawable on focus
 */
fun View.setupFocusEffect(
    scale: Float = 1.1f,
    applyBackgroundChange: Boolean = false
) {
    this.setOnFocusChangeListener { view, hasFocus ->
        val animScale = if (hasFocus) scale else 1.0f
        val animElevation = if (hasFocus) 8f else 0f

        view.animate()
            .scaleX(animScale)
            .scaleY(animScale)
            .translationZ(animElevation)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Optional: Add the yellow border glow programmatically
        if (applyBackgroundChange) {
            if (hasFocus) {
                view.background = ContextCompat.getDrawable(context, R.drawable.bg_glass_focused)
            } else {
                view.background = ContextCompat.getDrawable(context, R.drawable.bg_glass_panel)
            }
        }
    }
}

/**
 * Setup focus effect specifically for card views
 */
fun View.setupCardFocusEffect() {
    setupFocusEffect(scale = 1.05f, applyBackgroundChange = false)
}

/**
 * Setup focus effect for sidebar icons
 */
fun View.setupSidebarFocusEffect() {
    setupFocusEffect(scale = 1.15f, applyBackgroundChange = false)
}
