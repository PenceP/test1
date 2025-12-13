package com.strmr.tv.ui.contextmenu

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.strmr.tv.R

/**
 * Represents an action that can be performed from the context menu.
 * Actions are categorized by whether they require Trakt authentication.
 */
sealed class ContextMenuAction(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val requiresTrakt: Boolean = false
) {
    // Non-Trakt actions
    object Play : ContextMenuAction(
        labelRes = R.string.action_play,
        iconRes = R.drawable.ic_play_24,
        requiresTrakt = false
    )

    // Trakt watch history actions
    object MarkWatched : ContextMenuAction(
        labelRes = R.string.action_mark_watched,
        iconRes = R.drawable.ic_check_circle_24,
        requiresTrakt = true
    )

    object MarkUnwatched : ContextMenuAction(
        labelRes = R.string.action_mark_unwatched,
        iconRes = R.drawable.ic_check_circle_outline_24,
        requiresTrakt = true
    )

    // Trakt collection actions
    object AddToCollection : ContextMenuAction(
        labelRes = R.string.action_add_to_collection,
        iconRes = R.drawable.ic_folder_add_24,
        requiresTrakt = true
    )

    object RemoveFromCollection : ContextMenuAction(
        labelRes = R.string.action_remove_from_collection,
        iconRes = R.drawable.ic_folder_remove_24,
        requiresTrakt = true
    )

    // Trakt watchlist actions
    object AddToWatchlist : ContextMenuAction(
        labelRes = R.string.action_add_to_watchlist,
        iconRes = R.drawable.ic_bookmark_add_24,
        requiresTrakt = true
    )

    object RemoveFromWatchlist : ContextMenuAction(
        labelRes = R.string.action_remove_from_watchlist,
        iconRes = R.drawable.ic_bookmark_remove_24,
        requiresTrakt = true
    )
}
