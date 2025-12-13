package com.strmr.tv.ui.settings.adapter

import android.animation.ValueAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.JustifyContent
import com.strmr.tv.R
import com.strmr.tv.ui.settings.model.AccountAction
import com.strmr.tv.ui.settings.model.QualityChip
import com.strmr.tv.ui.settings.model.SettingsItem
import com.strmr.tv.ui.utils.FocusUtils

class SettingsAdapter(
    items: List<SettingsItem>
) : ListAdapter<SettingsItem, RecyclerView.ViewHolder>(SettingsDiffCallback()) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_TOGGLE = 1
        private const val TYPE_ACCOUNT_CARD = 2
        private const val TYPE_SELECT = 3
        private const val TYPE_SLIDER = 4
        private const val TYPE_INPUT = 5
        private const val TYPE_ACCOUNT_CARD_APIKEY = 6
        private const val TYPE_QUALITY_CHIP_GROUP = 7
        private const val TYPE_STEPPER = 8
        private const val TYPE_TAG_INPUT = 9
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SettingsItem.Header -> TYPE_HEADER
            is SettingsItem.Toggle -> TYPE_TOGGLE
            is SettingsItem.AccountCard -> TYPE_ACCOUNT_CARD
            is SettingsItem.Select -> TYPE_SELECT
            is SettingsItem.Slider -> TYPE_SLIDER
            is SettingsItem.Input -> TYPE_INPUT
            is SettingsItem.AccountCardApiKey -> TYPE_ACCOUNT_CARD_APIKEY
            is SettingsItem.QualityChipGroup -> TYPE_QUALITY_CHIP_GROUP
            is SettingsItem.Stepper -> TYPE_STEPPER
            is SettingsItem.TagInput -> TYPE_TAG_INPUT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_setting_header, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_TOGGLE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_setting_toggle, parent, false)
                ToggleViewHolder(view)
            }
            TYPE_SELECT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_setting_select, parent, false)
                SelectViewHolder(view)
            }
            TYPE_ACCOUNT_CARD -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_account_card, parent, false)
                AccountCardViewHolder(view)
            }
            TYPE_ACCOUNT_CARD_APIKEY -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_account_card_apikey, parent, false)
                AccountCardApiKeyViewHolder(view)
            }
            TYPE_SLIDER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_setting_slider, parent, false)
                SliderViewHolder(view)
            }
            TYPE_QUALITY_CHIP_GROUP -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_setting_quality_chips, parent, false)
                QualityChipGroupViewHolder(view)
            }
            TYPE_STEPPER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_setting_stepper, parent, false)
                StepperViewHolder(view)
            }
            TYPE_TAG_INPUT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_setting_tag_input, parent, false)
                TagInputViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SettingsItem.Header -> (holder as HeaderViewHolder).bind(item)
            is SettingsItem.Toggle -> (holder as ToggleViewHolder).bind(item)
            is SettingsItem.Select -> (holder as SelectViewHolder).bind(item)
            is SettingsItem.AccountCard -> (holder as AccountCardViewHolder).bind(item)
            is SettingsItem.AccountCardApiKey -> (holder as AccountCardApiKeyViewHolder).bind(item)
            is SettingsItem.Slider -> (holder as SliderViewHolder).bind(item)
            is SettingsItem.QualityChipGroup -> (holder as QualityChipGroupViewHolder).bind(item)
            is SettingsItem.Stepper -> (holder as StepperViewHolder).bind(item)
            is SettingsItem.TagInput -> (holder as TagInputViewHolder).bind(item)
            else -> {}
        }
    }

    // Header ViewHolder
    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.header_title)

        fun bind(item: SettingsItem.Header) {
            title.text = item.title
        }
    }

    // Select ViewHolder
    inner class SelectViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.select_label)
        private val value: TextView = view.findViewById(R.id.select_value)
        private val optionsContainer: LinearLayout = view.findViewById(R.id.options_container)

        fun bind(item: SettingsItem.Select) {
            label.text = item.label
            value.text = item.options.find { it.value == item.value }?.label ?: item.value

            // Build option buttons
            optionsContainer.removeAllViews()
            item.options.forEach { option ->
                val button = Button(itemView.context).apply {
                    text = option.label
                    isSelected = option.value == item.value
                    setBackgroundResource(
                        if (isSelected) R.drawable.bg_select_option_selected
                        else R.drawable.bg_select_option
                    )
                    setTextColor(Color.WHITE)
                    setPadding(32, 16, 32, 16)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setOnClickListener {
                        item.onSelect(option.value)
                    }
                    setOnFocusChangeListener { v, hasFocus ->
                        if (hasFocus) {
                            v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                        } else {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                        }
                    }
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 16
                }
                optionsContainer.addView(button, params)
            }
        }
    }

    // Toggle ViewHolder
    inner class ToggleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.toggle_label)
        private val toggleContainer: FrameLayout = view.findViewById(R.id.toggle_container)
        private val toggleTrack: View = view.findViewById(R.id.toggle_track)
        private val toggleThumb: View = view.findViewById(R.id.toggle_thumb)

        init {
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position) as SettingsItem.Toggle
                    item.onToggle(!item.isEnabled)
                }
            }
        }

        fun bind(item: SettingsItem.Toggle) {
            label.text = item.label
            animateToggle(item.isEnabled)
        }

        private fun animateToggle(isEnabled: Boolean) {
            // Animate thumb position
            val startX = if (isEnabled) 4f else 36f
            val endX = if (isEnabled) 36f else 4f

            ValueAnimator.ofFloat(startX, endX).apply {
                duration = 300
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animation ->
                    val value = animation.animatedValue as Float
                    toggleThumb.translationX = value
                }
                start()
            }

            // Update track background
            val trackBackground = if (isEnabled) {
                ContextCompat.getDrawable(itemView.context, R.drawable.bg_toggle_on)
            } else {
                ContextCompat.getDrawable(itemView.context, R.drawable.bg_toggle_off)
            }
            toggleTrack.background = trackBackground
        }
    }

    // Account Card ViewHolder
    inner class AccountCardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: CardView = view.findViewById(R.id.account_card)
        private val serviceIconContainer: CardView = view.findViewById(R.id.service_icon_container)
        private val serviceIconText: TextView = view.findViewById(R.id.service_icon_text)
        private val serviceName: TextView = view.findViewById(R.id.service_name)
        private val serviceDescription: TextView = view.findViewById(R.id.service_description)
        private val connectedBadge: LinearLayout = view.findViewById(R.id.connected_badge)

        private val loggedOutView: LinearLayout = view.findViewById(R.id.logged_out_view)
        private val loggedInView: LinearLayout = view.findViewById(R.id.logged_in_view)

        private val btnAuthenticate: Button = view.findViewById(R.id.btn_authenticate)
        private val userName: TextView = view.findViewById(R.id.user_name)
        private val scrobblesCount: TextView = view.findViewById(R.id.scrobbles_count)
        private val btnSync: Button = view.findViewById(R.id.btn_sync)
        private val btnLogout: Button = view.findViewById(R.id.btn_logout)

        init {
            // Apply subtle focus animations to buttons, not the card
            FocusUtils.applyButtonFocusAnimation(btnAuthenticate)
            FocusUtils.applyButtonFocusAnimation(btnSync)
            FocusUtils.applyButtonFocusAnimation(btnLogout)
        }

        fun bind(item: SettingsItem.AccountCard) {
            // Setup service info
            serviceIconText.text = item.iconText
            serviceIconContainer.setCardBackgroundColor(item.iconBackgroundColor)
            serviceName.text = item.serviceName
            serviceDescription.text = item.serviceDescription

            // Show/hide connected badge
            connectedBadge.visibility = if (item.isConnected) View.VISIBLE else View.GONE

            // Show appropriate view based on connection status
            if (item.isConnected) {
                loggedOutView.visibility = View.GONE
                loggedInView.visibility = View.VISIBLE
                userName.text = item.userName ?: "Unknown"
                scrobblesCount.text = item.additionalInfo ?: "0"

                btnSync.setOnClickListener { item.onAction(AccountAction.SYNC) }
                btnLogout.setOnClickListener { item.onAction(AccountAction.LOGOUT) }
            } else {
                loggedOutView.visibility = View.VISIBLE
                loggedInView.visibility = View.GONE

                btnAuthenticate.setOnClickListener { item.onAction(AccountAction.AUTHENTICATE) }
            }
        }
    }

    // Slider ViewHolder
    inner class SliderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.slider_label)
        private val valueText: TextView = view.findViewById(R.id.slider_value)
        private val seekBar: SeekBar = view.findViewById(R.id.slider_seekbar)
        private val btnDecrease: Button = view.findViewById(R.id.btn_decrease)
        private val btnIncrease: Button = view.findViewById(R.id.btn_increase)

        init {
            FocusUtils.applyButtonFocusAnimation(btnDecrease)
            FocusUtils.applyButtonFocusAnimation(btnIncrease)
        }

        fun bind(item: SettingsItem.Slider) {
            label.text = item.label
            updateValueText(item.value, item.unit)

            seekBar.min = item.min
            seekBar.max = item.max
            seekBar.progress = item.value

            // SeekBar change listener
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        updateValueText(progress, item.unit)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.let { item.onValueChange(it.progress) }
                }
            })

            // Decrease button
            btnDecrease.setOnClickListener {
                val newValue = (seekBar.progress - 5).coerceAtLeast(item.min)
                seekBar.progress = newValue
                updateValueText(newValue, item.unit)
                item.onValueChange(newValue)
            }

            // Increase button
            btnIncrease.setOnClickListener {
                val newValue = (seekBar.progress + 5).coerceAtMost(item.max)
                seekBar.progress = newValue
                updateValueText(newValue, item.unit)
                item.onValueChange(newValue)
            }

            // Update button enabled states
            btnDecrease.alpha = if (item.value <= item.min) 0.5f else 1f
            btnIncrease.alpha = if (item.value >= item.max) 0.5f else 1f
        }

        private fun updateValueText(value: Int, unit: String) {
            valueText.text = if (value == 0) {
                "Off"
            } else if (unit.isNotEmpty()) {
                "$value $unit"
            } else {
                value.toString()
            }
        }
    }

    // Quality Chip Group ViewHolder
    inner class QualityChipGroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.chip_group_label)
        private val chipsContainer: FlexboxLayout = view.findViewById(R.id.chips_container)

        fun bind(item: SettingsItem.QualityChipGroup) {
            label.text = item.label

            // Configure FlexboxLayout
            chipsContainer.flexWrap = FlexWrap.WRAP
            chipsContainer.justifyContent = JustifyContent.FLEX_START

            // Build chip buttons
            chipsContainer.removeAllViews()
            item.qualities.forEach { chip ->
                val button = Button(itemView.context).apply {
                    text = chip.label
                    isSelected = chip.isEnabled
                    background = if (chip.isEnabled) {
                        itemView.context.getDrawable(R.drawable.bg_quality_chip_enabled)
                    } else {
                        itemView.context.getDrawable(R.drawable.bg_quality_chip_disabled)
                    }
                    setTextColor(Color.WHITE)
                    setPadding(48, 24, 48, 24)
                    textSize = 14f
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setOnClickListener {
                        item.onToggle(chip.id, !chip.isEnabled)
                    }
                    setOnFocusChangeListener { v, hasFocus ->
                        if (hasFocus) {
                            v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                        } else {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                        }
                    }
                }

                val params = FlexboxLayout.LayoutParams(
                    FlexboxLayout.LayoutParams.WRAP_CONTENT,
                    FlexboxLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 16, 16)
                }
                chipsContainer.addView(button, params)
            }
        }
    }

    // Stepper ViewHolder
    inner class StepperViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.stepper_label)
        private val valueText: TextView = view.findViewById(R.id.stepper_value)
        private val btnDecrement: Button = view.findViewById(R.id.btn_decrement)
        private val btnIncrement: Button = view.findViewById(R.id.btn_increment)

        init {
            FocusUtils.applyButtonFocusAnimation(btnDecrement)
            FocusUtils.applyButtonFocusAnimation(btnIncrement)
        }

        fun bind(item: SettingsItem.Stepper) {
            label.text = item.label
            valueText.text = item.value.toString()

            btnDecrement.setOnClickListener {
                val newValue = (item.value - item.step).coerceAtLeast(item.min)
                item.onValueChange(newValue)
            }

            btnIncrement.setOnClickListener {
                val newValue = (item.value + item.step).coerceAtMost(item.max)
                item.onValueChange(newValue)
            }

            // Update button enabled states
            btnDecrement.alpha = if (item.value <= item.min) 0.5f else 1f
            btnIncrement.alpha = if (item.value >= item.max) 0.5f else 1f
        }
    }

    // Tag Input ViewHolder
    inner class TagInputViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.tag_input_label)
        private val tagsContainer: FlexboxLayout = view.findViewById(R.id.tags_container)
        private val tagInput: EditText = view.findViewById(R.id.tag_input)
        private val btnAddTag: Button = view.findViewById(R.id.btn_add_tag)

        init {
            FocusUtils.applyButtonFocusAnimation(btnAddTag)
        }

        fun bind(item: SettingsItem.TagInput) {
            label.text = item.label
            tagInput.hint = item.placeholder

            // Configure FlexboxLayout
            tagsContainer.flexWrap = FlexWrap.WRAP
            tagsContainer.justifyContent = JustifyContent.FLEX_START

            // Build tag chips
            tagsContainer.removeAllViews()
            item.tags.forEach { tag ->
                val tagView = createTagChip(tag) { item.onRemoveTag(tag) }
                tagsContainer.addView(tagView)
            }

            // Add button click
            btnAddTag.setOnClickListener {
                val newTag = tagInput.text.toString().trim()
                if (newTag.isNotEmpty()) {
                    item.onAddTag(newTag)
                    tagInput.text.clear()
                }
            }

            // Handle IME action
            tagInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    val newTag = tagInput.text.toString().trim()
                    if (newTag.isNotEmpty()) {
                        item.onAddTag(newTag)
                        tagInput.text.clear()
                    }
                    true
                } else {
                    false
                }
            }
        }

        private fun createTagChip(tag: String, onRemove: () -> Unit): View {
            val chipView = LinearLayout(itemView.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                background = itemView.context.getDrawable(R.drawable.bg_tag_chip)
                setPadding(24, 12, 16, 12)
            }

            val tagText = TextView(itemView.context).apply {
                text = tag
                setTextColor(Color.WHITE)
                textSize = 14f
            }

            val removeBtn = TextView(itemView.context).apply {
                text = "✕"
                setTextColor(Color.parseColor("#71717A"))
                textSize = 14f
                setPadding(16, 0, 0, 0)
                isFocusable = true
                setOnClickListener { onRemove() }
                setOnFocusChangeListener { v, hasFocus ->
                    setTextColor(if (hasFocus) Color.parseColor("#F87171") else Color.parseColor("#71717A"))
                }
            }

            chipView.addView(tagText)
            chipView.addView(removeBtn)

            val params = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 12, 12)
            }
            chipView.layoutParams = params

            return chipView
        }
    }

    // Account Card API Key ViewHolder (for Premiumize, etc.)
    inner class AccountCardApiKeyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val serviceIconContainer: CardView = view.findViewById(R.id.service_icon_container)
        private val serviceIconText: TextView = view.findViewById(R.id.service_icon_text)
        private val serviceName: TextView = view.findViewById(R.id.service_name)
        private val serviceDescription: TextView = view.findViewById(R.id.service_description)
        private val connectedBadge: LinearLayout = view.findViewById(R.id.connected_badge)

        private val disconnectedView: LinearLayout = view.findViewById(R.id.disconnected_view)
        private val connectedView: LinearLayout = view.findViewById(R.id.connected_view)

        private val apiKeyInput: EditText = view.findViewById(R.id.api_key_input)
        private val btnVerify: Button = view.findViewById(R.id.btn_verify)
        private val verifyingView: LinearLayout = view.findViewById(R.id.verifying_view)

        private val accountStatus: TextView = view.findViewById(R.id.account_status)
        private val daysRemaining: TextView = view.findViewById(R.id.days_remaining)
        private val btnDisconnect: Button = view.findViewById(R.id.btn_disconnect)

        init {
            FocusUtils.applyButtonFocusAnimation(btnVerify)
            FocusUtils.applyButtonFocusAnimation(btnDisconnect)
        }

        fun bind(item: SettingsItem.AccountCardApiKey) {
            // Setup service info
            serviceIconText.text = item.iconText
            serviceIconContainer.setCardBackgroundColor(item.iconBackgroundColor)
            serviceName.text = item.serviceName
            serviceDescription.text = item.serviceDescription

            // Show/hide connected badge
            connectedBadge.visibility = if (item.isConnected) View.VISIBLE else View.GONE

            if (item.isConnected) {
                // Show connected view
                disconnectedView.visibility = View.GONE
                connectedView.visibility = View.VISIBLE

                // Set account info
                val status = item.accountStatus ?: "Connected"
                accountStatus.text = status
                accountStatus.setTextColor(
                    if (status.lowercase() == "premium") Color.parseColor("#22C55E")
                    else Color.parseColor("#A1A1AA")
                )

                val days = item.daysRemaining
                daysRemaining.text = when {
                    days == null -> "N/A"
                    days <= 0 -> "Expired"
                    days == 1 -> "1 day"
                    else -> "$days days"
                }

                btnDisconnect.setOnClickListener { item.onDisconnect() }
            } else {
                // Show disconnected view
                disconnectedView.visibility = View.VISIBLE
                connectedView.visibility = View.GONE

                // Handle verifying state
                if (item.isVerifying) {
                    btnVerify.visibility = View.GONE
                    verifyingView.visibility = View.VISIBLE
                    apiKeyInput.isEnabled = false
                } else {
                    btnVerify.visibility = View.VISIBLE
                    verifyingView.visibility = View.GONE
                    apiKeyInput.isEnabled = true
                }

                // Set API key if provided
                if (apiKeyInput.text.toString() != item.apiKey) {
                    apiKeyInput.setText(item.apiKey)
                    apiKeyInput.setSelection(item.apiKey.length)
                }

                // API key change listener
                apiKeyInput.addTextChangedListener { text ->
                    item.onApiKeyChange(text?.toString() ?: "")
                }

                // Handle IME action
                apiKeyInput.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        val key = apiKeyInput.text.toString().trim()
                        if (key.isNotEmpty()) {
                            item.onVerify(key)
                        }
                        true
                    } else {
                        false
                    }
                }

                // Verify button click
                btnVerify.setOnClickListener {
                    val key = apiKeyInput.text.toString().trim()
                    if (key.isNotEmpty()) {
                        item.onVerify(key)
                    }
                }
            }
        }
    }
}
