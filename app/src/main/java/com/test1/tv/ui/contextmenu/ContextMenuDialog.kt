package com.test1.tv.ui.contextmenu

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.Window
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.test1.tv.R
import com.test1.tv.data.model.ContentItem

/**
 * Custom dialog for context menu actions.
 * Styled to match the nav bar aesthetic with semi-transparent background.
 * Supports D-pad navigation for Android TV.
 *
 * Implements key debouncing to prevent accidental selection when the user
 * is still holding the OK button from the long press that triggered this menu.
 */
class ContextMenuDialog private constructor(
    context: Context,
    private val titleText: String,
    private val actions: List<ContextMenuAction>,
    private val onActionSelected: (ContextMenuAction) -> Unit
) : Dialog(context, R.style.ContextMenuDialogTheme) {

    // Secondary constructor for backward compatibility with ContentItem
    constructor(
        context: Context,
        item: ContentItem,
        actions: List<ContextMenuAction>,
        onActionSelected: (ContextMenuAction) -> Unit
    ) : this(context, item.title, actions, onActionSelected)

    private var actionSelectedCallback: ((ContextMenuAction) -> Unit)? = null

    // Track whether we've received the initial key up from the long press
    private var keyUpReceived = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_context_menu)

        setupTitle()
        setupActionsList()
        setupDismissOnBackPress()
    }

    override fun show() {
        super.show()
        // Reset key state when dialog is shown
        keyUpReceived = false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // Wait for key UP before allowing any selection
                if (event.action == KeyEvent.ACTION_UP) {
                    if (!keyUpReceived) {
                        // This is the first key up - consume it (user releasing from long press)
                        keyUpReceived = true
                        return true
                    }
                }

                // Only process DOWN after we've seen UP (user released and pressed again)
                if (event.action == KeyEvent.ACTION_DOWN && !keyUpReceived) {
                    // User is still holding from long press - consume this
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setupTitle() {
        val titleView = findViewById<TextView>(R.id.context_menu_title)
        titleView.text = titleText
    }

    private fun setupActionsList() {
        val recyclerView = findViewById<RecyclerView>(R.id.context_menu_actions)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = ContextMenuAdapter(
            actions = actions,
            onActionSelected = { action ->
                dismiss()
                onActionSelected(action)
            },
            onDismissRequest = { dismiss() }
        )

        // Ensure focus starts on first action item
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
        // Set window attributes for centered display
        window?.let { win ->
            win.setLayout(
                context.resources.getDimensionPixelSize(R.dimen.context_menu_width),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
    }

    companion object {
        /**
         * Create and show a context menu dialog for a ContentItem
         */
        fun show(
            context: Context,
            item: ContentItem,
            actions: List<ContextMenuAction>,
            onActionSelected: (ContextMenuAction) -> Unit
        ): ContextMenuDialog {
            return ContextMenuDialog(context, item.title, actions, onActionSelected).apply {
                show()
            }
        }

        /**
         * Create and show a context menu dialog with a custom title
         */
        fun show(
            context: Context,
            title: String,
            actions: List<ContextMenuAction>,
            onActionSelected: (ContextMenuAction) -> Unit
        ): ContextMenuDialog {
            return ContextMenuDialog(context, title, actions, onActionSelected).apply {
                show()
            }
        }
    }
}
