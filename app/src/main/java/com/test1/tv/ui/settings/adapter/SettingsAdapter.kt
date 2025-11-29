package com.test1.tv.ui.settings.adapter

import android.animation.ValueAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.test1.tv.R
import com.test1.tv.ui.settings.model.AccountAction
import com.test1.tv.ui.settings.model.SettingsItem
import com.test1.tv.ui.utils.FocusUtils

class SettingsAdapter(
    items: List<SettingsItem>
) : ListAdapter<SettingsItem, RecyclerView.ViewHolder>(SettingsDiffCallback()) {

    companion object {
        private const val TYPE_TOGGLE = 0
        private const val TYPE_ACCOUNT_CARD = 1
        private const val TYPE_SELECT = 2
        private const val TYPE_SLIDER = 3
        private const val TYPE_INPUT = 4
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SettingsItem.Toggle -> TYPE_TOGGLE
            is SettingsItem.AccountCard -> TYPE_ACCOUNT_CARD
            is SettingsItem.Select -> TYPE_SELECT
            is SettingsItem.Slider -> TYPE_SLIDER
            is SettingsItem.Input -> TYPE_INPUT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TOGGLE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_setting_toggle, parent, false)
                ToggleViewHolder(view)
            }
            TYPE_ACCOUNT_CARD -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_account_card, parent, false)
                AccountCardViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SettingsItem.Toggle -> (holder as ToggleViewHolder).bind(item)
            is SettingsItem.AccountCard -> (holder as AccountCardViewHolder).bind(item)
            else -> {}
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
}
