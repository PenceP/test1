package com.test1.tv.ui

import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.test1.tv.R
import kotlin.math.abs

/**
 * Smarter scroll listener that only pauses Glide during very fast flings,
 * reducing unnecessary pauses during normal D-pad navigation.
 */
object SmartRowScrollManager {

    fun attach(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var wasFastScrolling = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE && wasFastScrolling) {
                    runCatching { Glide.with(recyclerView).resumeRequests() }
                    wasFastScrolling = false
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val posterWidth = recyclerView.resources.getDimensionPixelSize(R.dimen.poster_width_portrait)
                val threshold = posterWidth * 3 // roughly 3 items per frame => fast scroll
                if (abs(dx) > threshold || abs(dy) > threshold) {
                    if (!wasFastScrolling) {
                        runCatching { Glide.with(recyclerView).pauseRequests() }
                        wasFastScrolling = true
                    }
                }
            }
        })

        if (recyclerView.onFlingListener == null) {
            recyclerView.onFlingListener = object : RecyclerView.OnFlingListener() {
                override fun onFling(velocityX: Int, velocityY: Int): Boolean {
                    // Gently dampen flings; TV remotes shouldn't fling aggressively
                    val dampedX = (velocityX * 0.2f).toInt()
                    val dampedY = (velocityY * 0.2f).toInt()
                    return recyclerView.fling(dampedX, dampedY)
                }
            }
        }
    }
}
