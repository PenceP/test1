package com.test1.tv.ui.contextmenu

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.test1.tv.R
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.repository.TraktStatusProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Helper class for showing context menus in fragments.
 * Handles the logic for building actions based on auth state and executing actions.
 */
class ContextMenuHelper(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val actionHandler: ContextMenuActionHandler,
    private val traktStatusProvider: TraktStatusProvider,
    private val onWatchedStateChanged: ((Int, ContentItem.ContentType, Boolean) -> Unit)? = null
) {
    /**
     * Show context menu for an item.
     * Handles all the logic for determining available actions and executing them.
     */
    fun showContextMenu(item: ContentItem) {
        // Check if item should show context menu
        if (!item.shouldShowContextMenu()) {
            Toast.makeText(context, "Actions not available for this item", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Check auth status and build actions
                val isAuthenticated = traktStatusProvider.isAuthenticated()
                val actions = actionHandler.buildActions(item, isAuthenticated)

                // Show the dialog on main thread
                withContext(Dispatchers.Main) {
                    ContextMenuDialog.show(
                        context = context,
                        item = item,
                        actions = actions,
                        onActionSelected = { action ->
                            handleActionSelected(item, action)
                        }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to load menu", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleActionSelected(item: ContentItem, action: ContextMenuAction) {
        // Handle Play action separately (doesn't need API call)
        if (action is ContextMenuAction.Play) {
            Toast.makeText(context, "Play: ${item.title}", Toast.LENGTH_SHORT).show()
            return
        }

        // Execute the action
        lifecycleScope.launch {
            actionHandler.executeAction(
                item = item,
                action = action,
                callback = object : ContextMenuActionHandler.ActionCallback {
                    override fun onSuccess(action: ContextMenuAction) {
                        val message = when (action) {
                            is ContextMenuAction.MarkWatched -> "Marked as watched"
                            is ContextMenuAction.MarkUnwatched -> "Marked as unwatched"
                            is ContextMenuAction.AddToCollection -> "Added to collection"
                            is ContextMenuAction.RemoveFromCollection -> "Removed from collection"
                            is ContextMenuAction.AddToWatchlist -> "Added to watchlist"
                            is ContextMenuAction.RemoveFromWatchlist -> "Removed from watchlist"
                            else -> "Done"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

                        // Notify about watched state changes for badge updates
                        when (action) {
                            is ContextMenuAction.MarkWatched -> {
                                onWatchedStateChanged?.invoke(item.tmdbId, item.type, true)
                            }
                            is ContextMenuAction.MarkUnwatched -> {
                                onWatchedStateChanged?.invoke(item.tmdbId, item.type, false)
                            }
                            else -> { /* no badge update needed */ }
                        }
                    }

                    override fun onFailure(action: ContextMenuAction, error: String) {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

/**
 * Extension function to check if an item should show the context menu.
 * Excludes collection rows (networks, directors, My Trakt placeholders).
 */
fun ContentItem.shouldShowContextMenu(): Boolean {
    // Must have a valid TMDB ID
    if (tmdbId == -1) return false

    // Must not be a placeholder
    if (isPlaceholder) return false

    // Must not be a Trakt list URL (collection items like networks, directors)
    if (imdbId?.startsWith("https://trakt.tv/") == true) return false

    return true
}
