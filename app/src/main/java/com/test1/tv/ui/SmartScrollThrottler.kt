package com.test1.tv.ui

import android.os.SystemClock
import android.view.KeyEvent
import androidx.leanback.widget.BaseGridView

/**
 * Throttles only rapid repeated DPAD events; lets first press through immediately.
 * More tolerant of natural remote repeat rates than the old ScrollThrottler.
 */
class SmartScrollThrottler(
    private val initialDelayMs: Long = 0L,
    private val repeatDelayMs: Long = 80L
) : BaseGridView.OnKeyInterceptListener {

    private var lastEventTime: Long = 0L
    private var isHolding: Boolean = false

    override fun onInterceptKeyEvent(event: KeyEvent?): Boolean {
        event ?: return false

        val isNavigationKey = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> true
            else -> false
        }
        if (!isNavigationKey) return false

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                val now = SystemClock.uptimeMillis()
                val elapsed = now - lastEventTime

                if (!isHolding || elapsed > 300) {
                    // Fresh press or pause between presses
                    isHolding = true
                    lastEventTime = now
                    false
                } else {
                    val delay = if (lastEventTime == 0L) initialDelayMs else repeatDelayMs
                    val tooSoon = elapsed < delay
                    if (!tooSoon) {
                        lastEventTime = now
                    }
                    tooSoon
                }
            }
            KeyEvent.ACTION_UP -> {
                isHolding = false
                false
            }
            else -> false
        }
    }
}
