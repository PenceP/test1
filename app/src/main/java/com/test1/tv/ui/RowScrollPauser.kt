package com.test1.tv.ui

import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * Utility to pause/resume Glide while rows are flinging and lightly limit fling velocity.
 */
object RowScrollPauser {
    private const val FLING_FACTOR = 0.05f

    fun attach(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                val glide = Glide.with(recyclerView)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    glide.resumeRequests()
                    recyclerView.stopScroll()
                } else {
                    glide.pauseRequests()
                }
            }
        })

        if (recyclerView.onFlingListener == null) {
            recyclerView.onFlingListener = object : RecyclerView.OnFlingListener() {
                override fun onFling(velocityX: Int, velocityY: Int): Boolean {
                    val limitedX = (velocityX * FLING_FACTOR).toInt()
                    val limitedY = (velocityY * FLING_FACTOR).toInt()
                    recyclerView.onFlingListener = null
                    val handled = recyclerView.fling(limitedX, limitedY)
                    recyclerView.onFlingListener = this
                    return handled
                }
            }
        }
    }
}
