package com.test1.tv.ui.settings.adapter

import android.view.KeyEvent
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
    private val onMoveDown: (RowConfigEntity) -> Unit,
    private val onOrientationToggle: (RowConfigEntity) -> Unit,
    private val onNavigateUpFromFirst: () -> Unit = {},
    private val onNavigateDownFromLast: () -> Unit = {}
) : ListAdapter<RowConfigEntity, RowConfigAdapter.RowConfigViewHolder>(RowConfigDiffCallback()) {

    private var activeTabId: Int = -1

    fun setActiveTabId(tabId: Int) {
        activeTabId = tabId
        // Refresh the first and last items to update their focus navigation
        if (itemCount > 0) {
            notifyItemChanged(0)
            if (itemCount > 1) {
                notifyItemChanged(itemCount - 1)
            }
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
        //private val rowType: TextView = itemView.findViewById(R.id.row_type)
        //private val systemRowIndicator: TextView = itemView.findViewById(R.id.system_row_indicator)
        private val btnMoveUp: ImageButton = itemView.findViewById(R.id.btn_move_up)
        private val btnMoveDown: ImageButton = itemView.findViewById(R.id.btn_move_down)
        private val btnOrientation: ImageButton = itemView.findViewById(R.id.btn_orientation)
        private val visibilitySwitch: SwitchMaterial = itemView.findViewById(R.id.visibility_switch)

        fun bind(row: RowConfigEntity, position: Int, totalItems: Int) {
            rowTitle.text = row.title

            // Show row type and content type
            //val typeInfo = buildString {
            //    append(row.rowType)
            //    row.contentType?.let { append(" • $it") }
            //}
            //rowType.text = typeInfo

            // Show system row indicator
            //systemRowIndicator.visibility = if (row.isSystemRow) View.GONE else View.GONE

            // Disable move up button for first item
            btnMoveUp.isEnabled = position > 0
            btnMoveUp.isFocusable = position > 0  // Skip disabled buttons in D-pad navigation
            btnMoveUp.alpha = if (position > 0) 1.0f else 0.3f

            // Disable move down button for last item
            btnMoveDown.isEnabled = position < totalItems - 1
            btnMoveDown.isFocusable = position < totalItems - 1  // Skip disabled buttons in D-pad navigation
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

            // Set orientation icon based on current presentation
            val orientationIcon = when (row.presentation) {
                "portrait" -> R.drawable.ic_orientation_portrait
                "square" -> R.drawable.ic_orientation_square
                else -> R.drawable.ic_orientation_landscape // Default to landscape
            }
            btnOrientation.setImageResource(orientationIcon)

            btnOrientation.setOnClickListener {
                onOrientationToggle(row)
            }

            // TV-friendly: Set up focus handling
            // Make the card item focusable for vertical navigation
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true

            // For the first item, set nextFocusUp on all interactive elements to go to the active tab
            if (position == 0 && activeTabId != -1) {
                btnMoveUp.nextFocusUpId = activeTabId
                btnMoveDown.nextFocusUpId = activeTabId
                btnOrientation.nextFocusUpId = activeTabId
                visibilitySwitch.nextFocusUpId = activeTabId
                // Also set on the itemView itself for better navigation
                itemView.nextFocusUpId = activeTabId
            } else {
                // Clear nextFocusUp for non-first items to allow default navigation
                btnMoveUp.nextFocusUpId = View.NO_ID
                btnMoveDown.nextFocusUpId = View.NO_ID
                btnOrientation.nextFocusUpId = View.NO_ID
                visibilitySwitch.nextFocusUpId = View.NO_ID
                itemView.nextFocusUpId = View.NO_ID
            }

            // Don't manually set nextFocusDownId - let the VerticalGridView handle it
            // The onNavigateDownFromLast callback will handle navigation from the last item
            // Clear any previous focus settings to avoid stale navigation
            btnMoveUp.nextFocusDownId = View.NO_ID
            btnMoveDown.nextFocusDownId = View.NO_ID
            btnOrientation.nextFocusDownId = View.NO_ID
            visibilitySwitch.nextFocusDownId = View.NO_ID
            itemView.nextFocusDownId = View.NO_ID

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

            // Set up key listeners for boundary navigation
            // The key listener needs to check the CURRENT adapter state, not the bind-time state
            val boundaryKeyListener = View.OnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@OnKeyListener false

                val currentPosition = bindingAdapterPosition
                val currentItemCount = itemCount

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (currentPosition == 0) {
                            onNavigateUpFromFirst()
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Check if this is currently the last item in the adapter
                        if (currentPosition == currentItemCount - 1) {
                            onNavigateDownFromLast()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }

            // Apply key listener to ALL items - let the listener check if it's at boundary
            itemView.setOnKeyListener(boundaryKeyListener)
            btnMoveUp.setOnKeyListener(boundaryKeyListener)
            btnMoveDown.setOnKeyListener(boundaryKeyListener)
            btnOrientation.setOnKeyListener(boundaryKeyListener)
            visibilitySwitch.setOnKeyListener(boundaryKeyListener)
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
