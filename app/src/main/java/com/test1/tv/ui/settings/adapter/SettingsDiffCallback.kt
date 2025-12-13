package com.test1.tv.ui.settings.adapter

import androidx.recyclerview.widget.DiffUtil
import com.test1.tv.ui.settings.model.SettingsItem

class SettingsDiffCallback : DiffUtil.ItemCallback<SettingsItem>() {
    override fun areItemsTheSame(oldItem: SettingsItem, newItem: SettingsItem): Boolean {
        return when {
            oldItem is SettingsItem.Toggle && newItem is SettingsItem.Toggle -> oldItem.id == newItem.id
            oldItem is SettingsItem.AccountCard && newItem is SettingsItem.AccountCard -> oldItem.id == newItem.id
            oldItem is SettingsItem.Select && newItem is SettingsItem.Select -> oldItem.id == newItem.id
            oldItem is SettingsItem.Slider && newItem is SettingsItem.Slider -> oldItem.id == newItem.id
            oldItem is SettingsItem.Input && newItem is SettingsItem.Input -> oldItem.id == newItem.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: SettingsItem, newItem: SettingsItem): Boolean {
        return oldItem == newItem
    }
}
