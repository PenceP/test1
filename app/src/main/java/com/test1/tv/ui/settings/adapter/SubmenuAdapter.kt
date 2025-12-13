package com.test1.tv.ui.settings.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.test1.tv.R
import com.test1.tv.ui.settings.model.SubmenuItem
import com.test1.tv.ui.utils.FocusUtils

class SubmenuAdapter(
    private val items: List<SubmenuItem>,
    private val onItemClick: (SubmenuItem) -> Unit
) : RecyclerView.Adapter<SubmenuAdapter.ViewHolder>() {

    private var selectedPosition = 0

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.submenu_icon)
        val label: TextView = view.findViewById(R.id.submenu_label)

        init {
            // Apply subtle focus animation to submenu items
            FocusUtils.applyButtonFocusAnimation(view)

            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val previousPosition = selectedPosition
                    selectedPosition = position
                    notifyItemChanged(previousPosition)
                    notifyItemChanged(position)
                    onItemClick(items[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_submenu, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.icon.setImageResource(item.iconRes)
        holder.label.text = item.label

        holder.itemView.isSelected = position == selectedPosition

        // Request focus for selected item on first load
        if (position == selectedPosition && position == 0) {
            holder.itemView.post {
                holder.itemView.requestFocus()
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun setSelectedPosition(position: Int) {
        val previousPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(previousPosition)
        notifyItemChanged(position)
    }
}
