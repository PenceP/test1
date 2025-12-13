package com.strmr.tv.ui.utils

import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

object FocusUtils {

    /**
     * Applies a subtle scale animation to buttons on focus.
     * Scale: 1.02f (Max) - just a "pop" effect
     * Elevation: Slight increase for shadow depth
     */
    fun applyButtonFocusAnimation(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // Focused: subtle scale up with elevation
                v.animate()
                    .scaleX(1.02f)
                    .scaleY(1.02f)
                    .translationZ(4f)
                    .setDuration(150)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            } else {
                // Not focused: return to normal
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationZ(0f)
                    .setDuration(150)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
        }
    }

    /**
     * Applies a more pronounced scale animation for larger cards/items.
     * Scale: 1.05f - more noticeable for larger elements
     */
    fun applyCardFocusAnimation(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate()
                    .scaleX(1.05f)
                    .scaleY(1.05f)
                    .translationZ(8f)
                    .setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            } else {
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationZ(0f)
                    .setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
        }
    }
}
