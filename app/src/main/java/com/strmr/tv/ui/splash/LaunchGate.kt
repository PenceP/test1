package com.strmr.tv.ui.splash

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple process-wide gate to coordinate splash dismissal once home rows are ready.
 */
object LaunchGate {
    private val _homeReady = MutableStateFlow(false)
    val homeReady = _homeReady.asStateFlow()

    fun reset() {
        _homeReady.value = false
    }

    fun markHomeReady() {
        _homeReady.value = true
    }
}
