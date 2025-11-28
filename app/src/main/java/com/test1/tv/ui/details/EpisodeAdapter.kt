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
    private val onEpisodeFocused: (TMDBEpisode?) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

    inner class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val episodeImage: ImageView = itemView.findViewById(R.id.episode_image)
        private val episodeTitle: TextView = itemView.findViewById(R.id.episode_title)
        private val focusOverlay: View = itemView.findViewById(R.id.episode_focus_overlay)

        fun bind(episode: TMDBEpisode) {
            Glide.with(itemView.context)
                .load(episode.getStillUrl())
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.default_background)
                .error(R.drawable.default_background)
                .into(episodeImage)

            val seasonEpisode = buildSeasonEpisodeLabel(episode.seasonNumber, episode.episodeNumber)
            episodeTitle.text = seasonEpisode.ifBlank { "" }

            itemView.setOnFocusChangeListener { _, hasFocus ->
                focusOverlay.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
                if (hasFocus) {
                    itemView.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                    onEpisodeFocused(episode)
                } else {
                    itemView.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }
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
}