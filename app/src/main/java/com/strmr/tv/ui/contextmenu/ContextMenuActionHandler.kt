package com.strmr.tv.ui.contextmenu

import com.strmr.tv.data.model.ContentItem
import com.strmr.tv.data.repository.TraktStatusProvider
import com.strmr.tv.data.repository.TraktSyncRepository
import com.strmr.tv.data.repository.WatchStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles building and executing context menu actions.
 * Uses TraktStatusProvider for fast status lookups and TraktSyncRepository for API calls.
 */
@Singleton
class ContextMenuActionHandler @Inject constructor(
    private val traktSyncRepository: TraktSyncRepository,
    private val traktStatusProvider: TraktStatusProvider,
    private val watchStatusRepository: WatchStatusRepository
) {
    /**
     * Callback interface for action results
     */
    interface ActionCallback {
        fun onSuccess(action: ContextMenuAction)
        fun onFailure(action: ContextMenuAction, error: String)
    }

    /**
     * Build the list of available actions for an item based on authentication state
     * and current item status.
     */
    suspend fun buildActions(
        item: ContentItem,
        isTraktAuthenticated: Boolean
    ): List<ContextMenuAction> = withContext(Dispatchers.IO) {
        val actions = mutableListOf<ContextMenuAction>()

        // Play is always available
        actions.add(ContextMenuAction.Play)

        // If not authenticated, only show Play
        if (!isTraktAuthenticated) {
            return@withContext actions
        }

        // Get current status from cache (instant)
        val status = traktStatusProvider.getItemStatus(item.tmdbId, item.type)

        // Watched toggle
        if (status.isWatched) {
            actions.add(ContextMenuAction.MarkUnwatched)
        } else {
            actions.add(ContextMenuAction.MarkWatched)
        }

        // Collection toggle
        if (status.isInCollection) {
            actions.add(ContextMenuAction.RemoveFromCollection)
        } else {
            actions.add(ContextMenuAction.AddToCollection)
        }

        // Watchlist toggle
        if (status.isInWatchlist) {
            actions.add(ContextMenuAction.RemoveFromWatchlist)
        } else {
            actions.add(ContextMenuAction.AddToWatchlist)
        }

        actions
    }

    /**
     * Execute an action and update caches accordingly.
     * Returns true on success, false on failure.
     */
    suspend fun executeAction(
        item: ContentItem,
        action: ContextMenuAction,
        callback: ActionCallback
    ) = withContext(Dispatchers.IO) {
        val isMovie = item.type == ContentItem.ContentType.MOVIE

        val result = when (action) {
            is ContextMenuAction.Play -> {
                // Play is handled separately by the UI
                true
            }

            is ContextMenuAction.MarkWatched -> {
                val success = if (isMovie) {
                    traktSyncRepository.markMovieWatched(item.tmdbId)
                } else {
                    traktSyncRepository.markShowWatched(item.tmdbId)
                }
                if (success) {
                    traktStatusProvider.markWatched(item.tmdbId)
                    // Sync to ensure local DB is updated
                    traktSyncRepository.syncHistoryOnly()
                }
                success
            }

            is ContextMenuAction.MarkUnwatched -> {
                val success = if (isMovie) {
                    traktSyncRepository.markMovieUnwatched(item.tmdbId)
                } else {
                    traktSyncRepository.markShowUnwatched(item.tmdbId)
                }
                if (success) {
                    traktStatusProvider.markUnwatched(item.tmdbId)
                    // Sync to ensure local DB is updated
                    traktSyncRepository.syncHistoryOnly()
                }
                success
            }

            is ContextMenuAction.AddToCollection -> {
                val success = traktSyncRepository.addToCollection(item.tmdbId, isMovie)
                if (success) {
                    traktStatusProvider.addToCollection(item.tmdbId)
                    traktSyncRepository.syncCollectionOnly()
                }
                success
            }

            is ContextMenuAction.RemoveFromCollection -> {
                val success = traktSyncRepository.removeFromCollection(item.tmdbId, isMovie)
                if (success) {
                    traktStatusProvider.removeFromCollection(item.tmdbId)
                    traktSyncRepository.syncCollectionOnly()
                }
                success
            }

            is ContextMenuAction.AddToWatchlist -> {
                val success = traktSyncRepository.addToWatchlist(item.tmdbId, isMovie)
                if (success) {
                    traktStatusProvider.addToWatchlist(item.tmdbId)
                    traktSyncRepository.syncWatchlistOnly()
                }
                success
            }

            is ContextMenuAction.RemoveFromWatchlist -> {
                val success = traktSyncRepository.removeFromWatchlist(item.tmdbId, isMovie)
                if (success) {
                    traktStatusProvider.removeFromWatchlist(item.tmdbId)
                    traktSyncRepository.syncWatchlistOnly()
                }
                success
            }
        }

        withContext(Dispatchers.Main) {
            if (result) {
                callback.onSuccess(action)
            } else {
                callback.onFailure(action, "Action failed. Please try again.")
            }
        }
    }

    /**
     * Get the display label for an action
     */
    fun getActionLabel(action: ContextMenuAction): Int = action.labelRes

    /**
     * Get the icon resource for an action
     */
    fun getActionIcon(action: ContextMenuAction): Int = action.iconRes
}
