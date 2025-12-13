package com.strmr.tv.ui

import android.view.KeyEvent
import androidx.leanback.widget.BaseGridView

/**
 * Throttles DPAD key events to limit scroll speed when a user holds a direction.
 */
class ScrollThrottler(
    private val throttleMs: Long = 120L
) : BaseGridView.OnKeyInterceptListener {

    private var lastEventTime: Long = 0L

    override fun onInterceptKeyEvent(event: KeyEvent?): Boolean {
        event ?: return false
        if (event.action != KeyEvent.ACTION_DOWN) return false

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val now = System.currentTimeMillis()
                val tooSoon = now - lastEventTime < throttleMs
                if (!tooSoon) {
                    lastEventTime = now
                }
                tooSoon
            }
            else -> false
        }
    }
}
