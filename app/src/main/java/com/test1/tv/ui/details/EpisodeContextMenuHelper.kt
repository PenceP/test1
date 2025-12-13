package com.test1.tv.ui.details

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.test1.tv.R
import com.test1.tv.data.model.tmdb.TMDBEpisode
import com.test1.tv.data.model.tmdb.TMDBSeason
import com.test1.tv.data.repository.TraktAccountRepository
import com.test1.tv.data.repository.TraktSyncRepository
import com.test1.tv.ui.contextmenu.ContextMenuDialog
import com.test1.tv.ui.contextmenu.ContextMenuAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Helper class for showing context menus on episodes and seasons.
 */
class EpisodeContextMenuHelper(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val traktAccountRepository: TraktAccountRepository,
    private val traktSyncRepository: TraktSyncRepository,
    private val onEpisodeWatchedStateChanged: ((Int, Int, Boolean) -> Unit)? = null, // seasonNumber, episodeNumber, isWatched
    private val onSeasonWatchedStateChanged: ((Int, Boolean) -> Unit)? = null // seasonNumber, isWatched
) {
    /**
     * Show context menu for an episode
     */
    fun showEpisodeContextMenu(
        showTmdbId: Int,
        showTitle: String,
        episode: TMDBEpisode,
        isWatched: Boolean
    ) {
        lifecycleScope.launch {
            val isAuthenticated = traktAccountRepository.getAccount() != null
            val actions = buildEpisodeActions(isAuthenticated, isWatched)

            val episodeLabel = "S${episode.seasonNumber}E${episode.episodeNumber}"
            val title = episode.name?.let { "$episodeLabel: $it" } ?: episodeLabel

            withContext(Dispatchers.Main) {
                ContextMenuDialog.show(
                    context = context,
                    title = title,
                    actions = actions,
                    onActionSelected = { action ->
                        handleEpisodeAction(showTmdbId, episode, action)
                    }
                )
            }
        }
    }

    /**
     * Show context menu for a season
     */
    fun showSeasonContextMenu(
        showTmdbId: Int,
        showTitle: String,
        season: TMDBSeason,
        isSeasonWatched: Boolean
    ) {
        lifecycleScope.launch {
            val isAuthenticated = traktAccountRepository.getAccount() != null
            val actions = buildSeasonActions(isAuthenticated, isSeasonWatched)

            val title = season.seasonNumber?.let { "Season $it" } ?: season.name ?: "Season"

            withContext(Dispatchers.Main) {
                ContextMenuDialog.show(
                    context = context,
                    title = title,
                    actions = actions,
                    onActionSelected = { action ->
                        handleSeasonAction(showTmdbId, season, action)
                    }
                )
            }
        }
    }

    private fun buildEpisodeActions(isAuthenticated: Boolean, isWatched: Boolean): List<ContextMenuAction> {
        val actions = mutableListOf<ContextMenuAction>()

        // Play is always available
        actions.add(ContextMenuAction.Play)

        // Watched toggle only if authenticated
        if (isAuthenticated) {
            if (isWatched) {
                actions.add(ContextMenuAction.MarkUnwatched)
            } else {
                actions.add(ContextMenuAction.MarkWatched)
            }
        }

        return actions
    }

    private fun buildSeasonActions(isAuthenticated: Boolean, isSeasonWatched: Boolean): List<ContextMenuAction> {
        val actions = mutableListOf<ContextMenuAction>()

        // Season actions only if authenticated
        if (isAuthenticated) {
            if (isSeasonWatched) {
                actions.add(ContextMenuAction.MarkUnwatched)
            } else {
                actions.add(ContextMenuAction.MarkWatched)
            }
        }

        return actions
    }

    private fun handleEpisodeAction(showTmdbId: Int, episode: TMDBEpisode, action: ContextMenuAction) {
        val seasonNumber = episode.seasonNumber ?: return
        val episodeNumber = episode.episodeNumber ?: return

        when (action) {
            is ContextMenuAction.Play -> {
                Toast.makeText(context, "Play: S${seasonNumber}E${episodeNumber}", Toast.LENGTH_SHORT).show()
            }
            is ContextMenuAction.MarkWatched -> {
                markEpisodeWatched(showTmdbId, seasonNumber, episodeNumber)
            }
            is ContextMenuAction.MarkUnwatched -> {
                markEpisodeUnwatched(showTmdbId, seasonNumber, episodeNumber)
            }
            else -> { /* Not applicable for episodes */ }
        }
    }

    private fun handleSeasonAction(showTmdbId: Int, season: TMDBSeason, action: ContextMenuAction) {
        val seasonNumber = season.seasonNumber ?: return

        when (action) {
            is ContextMenuAction.MarkWatched -> {
                markSeasonWatched(showTmdbId, seasonNumber)
            }
            is ContextMenuAction.MarkUnwatched -> {
                markSeasonUnwatched(showTmdbId, seasonNumber)
            }
            else -> { /* Not applicable for seasons */ }
        }
    }

    private fun markEpisodeWatched(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                traktSyncRepository.markEpisodeWatched(showTmdbId, seasonNumber, episodeNumber)
            }
            if (success) {
                Toast.makeText(context, "Marked as watched", Toast.LENGTH_SHORT).show()
                onEpisodeWatchedStateChanged?.invoke(seasonNumber, episodeNumber, true)
            } else {
                Toast.makeText(context, "Failed to mark as watched", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun markEpisodeUnwatched(showTmdbId: Int, seasonNumber: Int, episodeNumber: Int) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                traktSyncRepository.markEpisodeUnwatched(showTmdbId, seasonNumber, episodeNumber)
            }
            if (success) {
                Toast.makeText(context, "Marked as unwatched", Toast.LENGTH_SHORT).show()
                onEpisodeWatchedStateChanged?.invoke(seasonNumber, episodeNumber, false)
            } else {
                Toast.makeText(context, "Failed to mark as unwatched", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun markSeasonWatched(showTmdbId: Int, seasonNumber: Int) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                traktSyncRepository.markSeasonWatched(showTmdbId, seasonNumber)
            }
            if (success) {
                Toast.makeText(context, "Season marked as watched", Toast.LENGTH_SHORT).show()
                onSeasonWatchedStateChanged?.invoke(seasonNumber, true)
            } else {
                Toast.makeText(context, "Failed to mark season as watched", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun markSeasonUnwatched(showTmdbId: Int, seasonNumber: Int) {
        lifecycleScope.launch {
            // Note: Trakt doesn't have a direct "unwatch season" API,
            // so we'll need to remove all episodes in the season from history
            // For now, show a message that this requires episode-by-episode removal
            Toast.makeText(context, "Unmarking season requires episode-by-episode removal", Toast.LENGTH_SHORT).show()
            // TODO: Implement batch episode removal when needed
        }
    }
}
