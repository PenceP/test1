package com.strmr.tv.ui.common

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.strmr.tv.R

/**
 * Generic list selection dialog styled to match the context menu.
 * Supports D-pad navigation for Android TV.
 */
class ListSelectionDialog<T>(
    context: Context,
    private val title: String,
    private val items: List<T>,
    private val itemLabelProvider: (T) -> String,
    private val onItemSelected: (T) -> Unit
) : Dialog(context, R.style.ContextMenuDialogTheme) {

    private var keyUpReceived = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_context_menu)

        setupTitle()
        setupItemsList()
        setupDismissOnBackPress()
    }

    override fun show() {
        super.show()
        keyUpReceived = false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    if (!keyUpReceived) {
                        keyUpReceived = true
                        return true
                    }
                }
                if (event.action == KeyEvent.ACTION_DOWN && !keyUpReceived) {
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setupTitle() {
        val titleView = findViewById<TextView>(R.id.context_menu_title)
        titleView.text = title
    }

    private fun setupItemsList() {
        val recyclerView = findViewById<RecyclerView>(R.id.context_menu_actions)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = ListItemAdapter(
            items = items,
            labelProvider = itemLabelProvider,
            onItemSelected = { item ->
                dismiss()
                onItemSelected(item)
            },
            onDismissRequest = { dismiss() }
        )

        // Focus on first item
        recyclerView.post {
            recyclerView.getChildAt(0)?.requestFocus()
        }
    }

    private fun setupDismissOnBackPress() {
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                dismiss()
                true
            } else {
                false
            }
        }
    }

    override fun onStart() {
        super.onStart()
        window?.let { win ->
            win.setLayout(
                context.resources.getDimensionPixelSize(R.dimen.context_menu_width),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /**
     * Adapter for list items
     */
    private inner class ListItemAdapter<T>(
        private val items: List<T>,
        private val labelProvider: (T) -> String,
        private val onItemSelected: (T) -> Unit,
        private val onDismissRequest: () -> Unit
    ) : RecyclerView.Adapter<ListItemAdapter<T>.ItemViewHolder>() {

        inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val label: TextView = itemView.findViewById(R.id.action_label)
            private val icon: View = itemView.findViewById(R.id.action_icon)

            fun bind(item: T, position: Int) {
                label.text = labelProvider(item)
                icon.visibility = View.GONE // No icons for list items

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

                itemView.setOnClickListener {
                    onItemSelected(item)
                }

                itemView.setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                onItemSelected(item)
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

                if (position == 0) {
                    itemView.post {
                        itemView.requestFocus()
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_context_menu_action, parent, false)
            return ItemViewHolder(view)
        }

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            holder.bind(items[position], position)
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        /**
         * Create and show a list selection dialog
         */
        fun <T> show(
            context: Context,
            title: String,
            items: List<T>,
            itemLabelProvider: (T) -> String,
            onItemSelected: (T) -> Unit
        ): ListSelectionDialog<T> {
            return ListSelectionDialog(context, title, items, itemLabelProvider, onItemSelected).apply {
                show()
            }
        }
    }
}
