package com.test1.tv.ui.settings.model

sealed class SettingsItem {
    data class Toggle(
        val id: String,
        val label: String,
        val isEnabled: Boolean,
        val onToggle: (Boolean) -> Unit
    ) : SettingsItem()

    data class Select(
        val id: String,
        val label: String,
        val value: String,
        val options: List<SelectOption>,
        val onSelect: (String) -> Unit
    ) : SettingsItem()

    data class Slider(
        val id: String,
        val label: String,
        val value: Int,
        val min: Int,
        val max: Int,
        val unit: String = "",
        val onValueChange: (Int) -> Unit
    ) : SettingsItem()

    data class Input(
        val id: String,
        val label: String,
        val value: String,
        val placeholder: String,
        val onValueChange: (String) -> Unit
    ) : SettingsItem()

    data class AccountCard(
        val id: String,
        val serviceName: String,
        val serviceDescription: String,
        val iconText: String,
        val iconBackgroundColor: Int,
        val isConnected: Boolean,
        val userName: String? = null,
        val additionalInfo: String? = null,
        val onAction: (AccountAction) -> Unit
    ) : SettingsItem()
}

data class SelectOption(
    val value: String,
    val label: String
)

enum class AccountAction {
    AUTHENTICATE,
    SYNC,
    LOGOUT,
    CONNECT,
    DISCONNECT
}
