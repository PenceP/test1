package com.strmr.tv.ui.details

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.strmr.tv.R
import com.strmr.tv.data.model.tmdb.TMDBSeason

class SeasonAdapter(
    private val seasons: List<TMDBSeason>,
    private val onSeasonClick: (TMDBSeason, Int) -> Unit,
    private val onSeasonLongPress: ((TMDBSeason, Int) -> Unit)? = null
) : RecyclerView.Adapter<SeasonAdapter.SeasonViewHolder>() {

    var selectedPosition: Int = 0
        private set

    inner class SeasonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.season_title)

        fun bind(season: TMDBSeason, position: Int) {
            val label = season.seasonNumber?.let { "Season $it" }
                ?: season.name?.takeIf { it.isNotBlank() }
                ?: "Season"
            title.text = label
            itemView.isSelected = position == selectedPosition

            itemView.setOnClickListener {
                val adapterPosition = bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    setSelectedPosition(adapterPosition)
                    onSeasonClick(season, adapterPosition)
                }
            }

            itemView.setOnLongClickListener {
                val adapterPosition = bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onSeasonLongPress?.invoke(season, adapterPosition)
                }
                true
            }

            itemView.setOnFocusChangeListener { view, hasFocus ->
                view.isSelected = position == selectedPosition
                if (hasFocus) {
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120).start()
                } else {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeasonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_season_chip, parent, false)
        return SeasonViewHolder(view)
    }

    override fun onBindViewHolder(holder: SeasonViewHolder, position: Int) {
        holder.bind(seasons[position], position)
    }

    override fun getItemCount(): Int = seasons.size

    fun setSelectedPosition(newPosition: Int) {
        if (newPosition == selectedPosition) return
        val previous = selectedPosition
        selectedPosition = newPosition
        notifyItemChanged(previous)
        notifyItemChanged(newPosition)
    }

    fun getSelectedSeason(): TMDBSeason? = seasons.getOrNull(selectedPosition)

    fun getSeason(position: Int): TMDBSeason? = seasons.getOrNull(position)

    /**
     * Find the adapter position for a given season number.
     * Returns -1 if season not found.
     */
    fun getPositionForSeasonNumber(seasonNumber: Int): Int {
        return seasons.indexOfFirst { it.seasonNumber == seasonNumber }
    }
}