package com.test1.tv.ui.settings.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.test1.tv.R
import com.test1.tv.data.local.entity.RowConfigEntity

class RowConfigAdapter(
    private val onToggleVisibility: (RowConfigEntity) -> Unit,
    private val onMoveUp: (RowConfigEntity) -> Unit,
    private val onMoveDown: (RowConfigEntity) -> Unit
) : ListAdapter<RowConfigEntity, RowConfigAdapter.RowConfigViewHolder>(RowConfigDiffCallback()) {

    private var activeTabId: Int = -1

    fun setActiveTabId(tabId: Int) {
        activeTabId = tabId
        // Refresh the first item to update its focus navigation
        if (itemCount > 0) {
            notifyItemChanged(0)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowConfigViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_row_config, parent, false)
        return RowConfigViewHolder(view)
    }

    override fun onBindViewHolder(holder: RowConfigViewHolder, position: Int) {
        val row = getItem(position)
        holder.bind(row, position, itemCount)
    }

    inner class RowConfigViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rowTitle: TextView = itemView.findViewById(R.id.row_title)
        private val rowType: TextView = itemView.findViewById(R.id.row_type)
        private val systemRowIndicator: TextView = itemView.findViewById(R.id.system_row_indicator)
        private val btnMoveUp: ImageButton = itemView.findViewById(R.id.btn_move_up)
        private val btnMoveDown: ImageButton = itemView.findViewById(R.id.btn_move_down)
        private val visibilitySwitch: SwitchMaterial = itemView.findViewById(R.id.visibility_switch)

        fun bind(row: RowConfigEntity, position: Int, totalItems: Int) {
            rowTitle.text = row.title

            // Show row type and content type
            val typeInfo = buildString {
                append(row.rowType)
                row.contentType?.let { append(" • $it") }
            }
            rowType.text = typeInfo

            // Show system row indicator
            systemRowIndicator.visibility = if (row.isSystemRow) View.VISIBLE else View.GONE

            // Disable move up button for first item
            btnMoveUp.isEnabled = position > 0
            btnMoveUp.alpha = if (position > 0) 1.0f else 0.3f

            // Disable move down button for last item
            btnMoveDown.isEnabled = position < totalItems - 1
            btnMoveDown.alpha = if (position < totalItems - 1) 1.0f else 0.3f

            // Set visibility switch
            visibilitySwitch.isChecked = row.enabled

            // Wire up click listeners
            btnMoveUp.setOnClickListener {
                if (position > 0) {
                    onMoveUp(row)
                }
            }

            btnMoveDown.setOnClickListener {
                if (position < totalItems - 1) {
                    onMoveDown(row)
                }
            }

            visibilitySwitch.setOnCheckedChangeListener { _, _ ->
                onToggleVisibility(row)
            }

            // TV-friendly: Set up focus handling
            // Make the card item focusable for vertical navigation
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true

            // For the first item, set nextFocusUp on all interactive elements to go to the active tab
            if (position == 0 && activeTabId != -1) {
                btnMoveUp.nextFocusUpId = activeTabId
                btnMoveDown.nextFocusUpId = activeTabId
                visibilitySwitch.nextFocusUpId = activeTabId
            } else {
                // Clear nextFocusUp for non-first items to allow default navigation
                btnMoveUp.nextFocusUpId = View.NO_ID
                btnMoveDown.nextFocusUpId = View.NO_ID
                visibilitySwitch.nextFocusUpId = View.NO_ID
            }

            // When the card item gets focus, automatically focus the first interactive button
            itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    // Delay slightly to ensure layout is complete
                    itemView.post {
                        // Give focus to the first enabled button
                        if (btnMoveUp.isEnabled) {
                            btnMoveUp.requestFocus()
                        } else if (btnMoveDown.isEnabled) {
                            btnMoveDown.requestFocus()
                        } else {
                            visibilitySwitch.requestFocus()
                        }
                    }
                }
            }
        }
    }

    private class RowConfigDiffCallback : DiffUtil.ItemCallback<RowConfigEntity>() {
        override fun areItemsTheSame(oldItem: RowConfigEntity, newItem: RowConfigEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RowConfigEntity, newItem: RowConfigEntity): Boolean {
            return oldItem == newItem
        }
    }
}
