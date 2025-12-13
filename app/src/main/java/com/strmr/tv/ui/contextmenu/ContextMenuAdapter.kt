package com.strmr.tv.ui.contextmenu

import android.graphics.Color
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.strmr.tv.R

/**
 * Adapter for context menu action items.
 * Supports D-pad navigation and focus handling for TV.
 */
class ContextMenuAdapter(
    private val actions: List<ContextMenuAction>,
    private val onActionSelected: (ContextMenuAction) -> Unit,
    private val onDismissRequest: () -> Unit
) : RecyclerView.Adapter<ContextMenuAdapter.ActionViewHolder>() {

    inner class ActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.action_icon)
        private val label: TextView = itemView.findViewById(R.id.action_label)

        fun bind(action: ContextMenuAction, position: Int) {
            icon.setImageResource(action.iconRes)
            label.setText(action.labelRes)

            // Focus handling for TV
            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.setBackgroundColor(Color.parseColor("#33FFFFFF"))
                    view.scaleX = 1.02f
                    view.scaleY = 1.02f
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                } else {
                    view.setBackgroundColor(Color.TRANSPARENT)
                    view.scaleX = 1.0f
                    view.scaleY = 1.0f
                }
            }

            // Click handling
            itemView.setOnClickListener {
                onActionSelected(action)
            }

            // Key handling for TV remote
            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            onActionSelected(action)
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            onDismissRequest()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }

            // Request focus on first item when created
            if (position == 0) {
                itemView.post {
                    itemView.requestFocus()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_context_menu_action, parent, false)
        return ActionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        holder.bind(actions[position], position)
    }

    override fun getItemCount(): Int = actions.size
}
