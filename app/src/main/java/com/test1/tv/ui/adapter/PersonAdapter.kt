package com.test1.tv.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.test1.tv.R
import com.test1.tv.data.model.tmdb.TMDBCast

class PersonAdapter(
    private val people: List<TMDBCast>,
    private val onPersonClick: ((TMDBCast) -> Unit)? = null
) : RecyclerView.Adapter<PersonAdapter.PersonViewHolder>() {

    inner class PersonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val personPhoto: ImageView = itemView.findViewById(R.id.person_photo)
        val personName: TextView = itemView.findViewById(R.id.person_name)
        val focusOverlay: View = itemView.findViewById(R.id.focus_overlay)

        fun bind(person: TMDBCast) {
            // Load profile photo
            val cornerRadius = itemView.resources.getDimensionPixelSize(R.dimen.actor_corner_radius)
            val placeholder = ColorDrawable(Color.TRANSPARENT)
            Glide.with(itemView.context)
                .load(person.getProfileUrl())
                .transition(DrawableTransitionOptions.withCrossFade())
                .apply(RequestOptions().transforms(CenterCrop(), RoundedCorners(cornerRadius)))
                .placeholder(placeholder)
                .error(placeholder)
                .into(personPhoto)

            personName.text = person.name

            // Handle click
            itemView.setOnClickListener {
                onPersonClick?.invoke(person)
            }

            // Handle focus changes with glassmorphism style
            itemView.setOnFocusChangeListener { _, hasFocus ->
                focusOverlay.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
                if (hasFocus) {
                    // Animate scale up to 1.1x
                    itemView.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(150)
                        .start()
                } else {
                    // Animate scale down
                    itemView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .start()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_person_card_glass, parent, false)
        return PersonViewHolder(view)
    }

    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        holder.bind(people[position])
    }

    override fun getItemCount(): Int = people.size
}
