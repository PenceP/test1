package com.strmr.tv.ui.settings.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import com.strmr.tv.R
import com.strmr.tv.ui.settings.util.setupSidebarFocusEffect

data class SidebarIconItem(
    @DrawableRes val iconRes: Int,
    val id: String,
    val isActive: Boolean = false
)

class SidebarAdapter(
    private val items: List<SidebarIconItem>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<SidebarAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)

        init {
            view.setupSidebarFocusEffect()
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(items[position].id)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sidebar_icon, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.icon.setImageResource(item.iconRes)

        // Request focus for the active item
        if (item.isActive) {
            holder.itemView.post {
                holder.itemView.requestFocus()
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
