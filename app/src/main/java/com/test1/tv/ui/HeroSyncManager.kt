package com.test1.tv.ui

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.test1.tv.data.model.ContentItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * CRITICAL FIX: Manages hero section updates to prevent desync during fast scrolling.
 *
 * Problem: Hero section shows wrong content when user scrolls quickly through rows
 * because multiple async image loads complete out of order.
 *
 * Solution:
 * 1. Debounce scroll events (150ms) to avoid thrashing
 * 2. Cancel pending updates when new content selected
 * 3. Use sequence numbers to ignore stale updates
 */
class HeroSyncManager(
    private val lifecycleOwner: LifecycleOwner,
    private val onHeroUpdate: (ContentItem) -> Unit
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // StateFlow for debouncing rapid selection changes
    private val _selectedContent = MutableStateFlow<Pair<Long, ContentItem?>>(0L to null)

    // Current update job - cancelled when new content selected
    private var updateJob: Job? = null

    // Sequence number to detect stale updates
    private var sequenceNumber = 0L

    init {
        lifecycleOwner.lifecycle.addObserver(this)

        // Debounce selection changes to prevent hero thrashing
        scope.launch {
            _selectedContent
                .debounce(150) // Wait 150ms after last scroll event
                .distinctUntilChanged { old, new ->
                    // Compare by id (unique per item) instead of tmdbId to handle
                    // Networks/Directors/Franchises which all have tmdbId = -1
                    old.second?.id == new.second?.id
                }
                .collect { (sequence, content) ->
                    content?.let {
                        updateHeroInternal(sequence, it)
                    }
                }
        }
    }

    /**
     * Called when user scrolls to new content.
     * Debounced automatically - safe to call on every scroll event.
     */
    fun onContentSelected(content: ContentItem) {
        // Cancel any pending update
        updateJob?.cancel()

        // Increment sequence to invalidate any in-flight updates
        val newSequence = ++sequenceNumber

        // Emit new selection (will be debounced)
        _selectedContent.value = newSequence to content
    }

    /**
     * Force immediate hero update (used for click events).
     */
    fun updateHeroImmediate(content: ContentItem) {
        updateJob?.cancel()
        val sequence = ++sequenceNumber
        updateHeroInternal(sequence, content)
    }

    private fun updateHeroInternal(sequence: Long, content: ContentItem) {
        // Only proceed if this is still the latest selection
        if (sequence != sequenceNumber) {
            return // Stale update, ignore
        }

        updateJob = scope.launch {
            try {
                onHeroUpdate(content)
            } catch (e: CancellationException) {
                // Expected when new content selected
            } catch (e: Exception) {
                // Log error but don't crash
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        updateJob?.cancel()
        scope.cancel()
        super.onDestroy(owner)
    }
}
