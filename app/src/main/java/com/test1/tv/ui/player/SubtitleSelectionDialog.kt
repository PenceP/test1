package com.test1.tv.ui.player

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.test1.tv.R
import com.test1.tv.data.subtitle.SubtitleOption

/**
 * Enhanced subtitle selection dialog with sections for embedded and external subtitles.
 * Designed for TV remote navigation with D-pad support.
 */
class SubtitleSelectionDialog(
    context: Context,
    private val subtitleOptions: List<SubtitleOption>,
    private val currentSelection: SubtitleOption?,
    private val isLoading: Boolean = false,
    private val onSubtitleSelected: (SubtitleOption) -> Unit
) : Dialog(context, R.style.ContextMenuDialogTheme) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var titleText: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var emptyText: TextView

    private var keyUpReceived = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_subtitle_selection)

        initViews()
        setupRecyclerView()
        updateLoadingState()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.subtitle_list)
        titleText = findViewById(R.id.dialog_title)
        loadingIndicator = findViewById(R.id.loading_indicator)
        emptyText = findViewById(R.id.empty_text)

        titleText.text = "Subtitles"
    }

    private fun setupRecyclerView() {
        val adapter = SubtitleAdapter(
            options = subtitleOptions,
            currentSelection = currentSelection,
            onItemSelected = { option ->
                onSubtitleSelected(option)
                dismiss()
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // Find and focus current selection
        val currentIndex = subtitleOptions.indexOfFirst {
            it == currentSelection ||
            (currentSelection == null && it is SubtitleOption.Off)
        }
        if (currentIndex >= 0) {
            recyclerView.post {
                recyclerView.scrollToPosition(currentIndex)
                recyclerView.findViewHolderForAdapterPosition(currentIndex)?.itemView?.requestFocus()
            }
        } else if (subtitleOptions.isNotEmpty()) {
            recyclerView.post {
                recyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            }
        }
    }

    private fun updateLoadingState() {
        if (isLoading) {
            loadingIndicator.visibility = View.VISIBLE
            emptyText.visibility = View.GONE
        } else {
            loadingIndicator.visibility = View.GONE
            if (subtitleOptions.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                emptyText.text = "No subtitles available"
            } else {
                emptyText.visibility = View.GONE
            }
        }
    }

    override fun show() {
        super.show()
        keyUpReceived = false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // Wait for key UP before allowing any selection
                if (event.action == KeyEvent.ACTION_UP) {
                    if (!keyUpReceived) {
                        keyUpReceived = true
                        return true // Consume this first key up
                    }
                }

                // Only process DOWN after we've seen UP
                if (event.action == KeyEvent.ACTION_DOWN && !keyUpReceived) {
                    return true // Consume - user still holding from long press
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * RecyclerView adapter for subtitle options
     */
    private class SubtitleAdapter(
        private val options: List<SubtitleOption>,
        private val currentSelection: SubtitleOption?,
        private val onItemSelected: (SubtitleOption) -> Unit
    ) : RecyclerView.Adapter<SubtitleAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_subtitle_option, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(options[position])
        }

        override fun getItemCount(): Int = options.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconView: ImageView = itemView.findViewById(R.id.subtitle_icon)
            private val labelView: TextView = itemView.findViewById(R.id.subtitle_label)
            private val detailView: TextView = itemView.findViewById(R.id.subtitle_detail)
            private val checkView: ImageView = itemView.findViewById(R.id.subtitle_check)
            private val badgeContainer: FrameLayout = itemView.findViewById(R.id.badge_container)
            private val badgeText: TextView = itemView.findViewById(R.id.badge_text)

            init {
                itemView.isFocusable = true
                itemView.isFocusableInTouchMode = true
            }

            fun bind(option: SubtitleOption) {
                when (option) {
                    is SubtitleOption.Off -> {
                        iconView.setImageResource(R.drawable.ic_subtitles_off)
                        labelView.text = "Off"
                        detailView.visibility = View.GONE
                        badgeContainer.visibility = View.GONE
                    }
                    is SubtitleOption.Embedded -> {
                        iconView.setImageResource(R.drawable.ic_subtitles)
                        labelView.text = option.label
                        detailView.visibility = View.VISIBLE
                        detailView.text = buildString {
                            append("Embedded")
                            if (option.isForced) append(" • Forced")
                            if (option.isDefault) append(" • Default")
                        }
                        badgeContainer.visibility = View.VISIBLE
                        badgeText.text = "EMB"
                    }
                    is SubtitleOption.External -> {
                        iconView.setImageResource(R.drawable.ic_subtitles)
                        labelView.text = option.subtitle.language
                        detailView.visibility = View.VISIBLE
                        detailView.text = buildString {
                            append(option.subtitle.fileName.take(40))
                            if (option.subtitle.fileName.length > 40) append("...")
                            option.subtitle.downloadCount?.let {
                                append(" • ${formatDownloadCount(it)} downloads")
                            }
                        }

                        // Show badge based on subtitle properties
                        badgeContainer.visibility = View.VISIBLE
                        badgeText.text = when {
                            option.subtitle.hashMatched -> "HASH"
                            option.subtitle.hearingImpaired -> "HI"
                            else -> "EXT"
                        }
                    }
                }

                // Show check mark for current selection
                val isSelected = when {
                    option is SubtitleOption.Off && currentSelection == null -> true
                    option is SubtitleOption.Off && currentSelection is SubtitleOption.Off -> true
                    option == currentSelection -> true
                    else -> false
                }
                checkView.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE

                // Click listener
                itemView.setOnClickListener {
                    onItemSelected(option)
                }

                // Focus change styling
                itemView.setOnFocusChangeListener { _, hasFocus ->
                    itemView.isSelected = hasFocus
                    if (hasFocus) {
                        itemView.animate()
                            .scaleX(1.02f)
                            .scaleY(1.02f)
                            .setDuration(100)
                            .start()
                    } else {
                        itemView.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start()
                    }
                }
            }

            private fun formatDownloadCount(count: Int): String {
                return when {
                    count >= 1_000_000 -> "${count / 1_000_000}M"
                    count >= 1_000 -> "${count / 1_000}K"
                    else -> count.toString()
                }
            }
        }
    }
}
