package com.test1.tv.ui.details

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.test1.tv.R
import com.test1.tv.data.model.tmdb.TMDBEpisode

class EpisodeAdapter(
    private val episodes: List<TMDBEpisode>,
    private val showTmdbId: Int,
    private val watchedEpisodes: Set<String> = emptySet(),
    private val onEpisodeFocused: (TMDBEpisode?) -> Unit,
    private val onEpisodeClick: ((TMDBEpisode) -> Unit)? = null,
    private val onEpisodeLongPress: ((TMDBEpisode, Boolean) -> Unit)? = null
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

    // Mutable set to track watched episodes for immediate UI updates
    private val mutableWatchedEpisodes = watchedEpisodes.toMutableSet()

    inner class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val episodeImage: ImageView = itemView.findViewById(R.id.episode_image)
        private val episodeTitle: TextView = itemView.findViewById(R.id.episode_title)
        private val focusOverlay: View = itemView.findViewById(R.id.episode_focus_overlay)
        private val watchedBadge: ImageView = itemView.findViewById(R.id.episode_watched_badge)

        fun bind(episode: TMDBEpisode) {
            Glide.with(itemView.context)
                .load(episode.getStillUrl())
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.default_background)
                .error(R.drawable.default_background)
                .into(episodeImage)

            val seasonEpisode = buildSeasonEpisodeLabel(episode.seasonNumber, episode.episodeNumber)
            episodeTitle.text = seasonEpisode.ifBlank { "" }

            // Show watched badge if episode is watched
            val episodeKey = "S${episode.seasonNumber}E${episode.episodeNumber}"
            val isWatched = mutableWatchedEpisodes.contains(episodeKey)
            watchedBadge.visibility = if (isWatched) View.VISIBLE else View.GONE

            itemView.setOnFocusChangeListener { _, hasFocus ->
                focusOverlay.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
                if (hasFocus) {
                    itemView.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                    onEpisodeFocused(episode)
                } else {
                    itemView.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }
            }

            // Click handler
            itemView.setOnClickListener {
                onEpisodeClick?.invoke(episode)
            }

            // Long press handler for context menu
            itemView.setOnLongClickListener {
                onEpisodeLongPress?.invoke(episode, isWatched)
                true
            }
        }

        private fun buildSeasonEpisodeLabel(seasonNumber: Int?, episodeNumber: Int?): String {
            if (seasonNumber == null && episodeNumber == null) return ""
            return buildString {
                seasonNumber?.let { append("S$it") }
                if (episodeNumber != null) {
                    if (isNotEmpty()) append(" ")
                    append("E$episodeNumber")
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode_card, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(episodes[position])
    }

    override fun getItemCount(): Int = episodes.size

    fun getEpisode(position: Int): TMDBEpisode? = episodes.getOrNull(position)

    /**
     * Update watched status for a specific episode and refresh its badge
     */
    fun updateEpisodeWatchedStatus(seasonNumber: Int, episodeNumber: Int, isWatched: Boolean) {
        val episodeKey = "S${seasonNumber}E${episodeNumber}"
        if (isWatched) {
            mutableWatchedEpisodes.add(episodeKey)
        } else {
            mutableWatchedEpisodes.remove(episodeKey)
        }
        // Find and update the specific episode
        val position = episodes.indexOfFirst {
            it.seasonNumber == seasonNumber && it.episodeNumber == episodeNumber
        }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    /**
     * Mark all episodes in a season as watched/unwatched
     */
    fun updateSeasonWatchedStatus(seasonNumber: Int, isWatched: Boolean) {
        episodes.filter { it.seasonNumber == seasonNumber }.forEach { episode ->
            val episodeKey = "S${episode.seasonNumber}E${episode.episodeNumber}"
            if (isWatched) {
                mutableWatchedEpisodes.add(episodeKey)
            } else {
                mutableWatchedEpisodes.remove(episodeKey)
            }
        }
        notifyDataSetChanged()
    }
}