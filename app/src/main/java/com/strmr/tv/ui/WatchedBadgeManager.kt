package com.strmr.tv.ui

import com.strmr.tv.data.model.ContentItem
import com.strmr.tv.data.repository.WatchStatusProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages watched badge state updates across the app.
 * Uses SharedFlow for reactive updates when watched state changes.
 */
@Singleton
class WatchedBadgeManager @Inject constructor() {

    private val _badgeUpdates = MutableSharedFlow<BadgeUpdate>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val badgeUpdates: SharedFlow<BadgeUpdate> = _badgeUpdates.asSharedFlow()

    /**
     * Represents a badge state change.
     */
    data class BadgeUpdate(
        val tmdbId: Int,
        val type: ContentItem.ContentType,
        val isWatched: Boolean
    )

    /**
     * Notify that an item's watched state has changed.
     * This will trigger UI updates in all subscribers (PosterAdapter instances).
     */
    suspend fun notifyWatchedStateChanged(
        tmdbId: Int,
        type: ContentItem.ContentType,
        isWatched: Boolean
    ) {
        // Update the WatchStatusProvider cache
        if (isWatched) {
            WatchStatusProvider.setProgress(tmdbId, type, 1.0)
        } else {
            WatchStatusProvider.removeProgress(tmdbId, type)
        }

        // Emit the update for UI subscribers
        _badgeUpdates.emit(BadgeUpdate(tmdbId, type, isWatched))
    }

    /**
     * Convenience method for notifying from non-suspend context.
     */
    fun notifyWatchedStateChangedSync(
        tmdbId: Int,
        type: ContentItem.ContentType,
        isWatched: Boolean
    ) {
        // Update cache synchronously
        if (isWatched) {
            WatchStatusProvider.setProgress(tmdbId, type, 1.0)
        } else {
            WatchStatusProvider.removeProgress(tmdbId, type)
        }

        // Try to emit (non-blocking)
        _badgeUpdates.tryEmit(BadgeUpdate(tmdbId, type, isWatched))
    }
}
