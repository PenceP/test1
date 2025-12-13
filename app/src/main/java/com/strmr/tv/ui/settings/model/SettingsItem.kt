package com.strmr.tv.ui.settings.model

sealed class SettingsItem {
    data class Header(
        val id: String,
        val title: String
    ) : SettingsItem()

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

    /**
     * Account card variant for API key-based authentication (e.g., Premiumize)
     */
    data class AccountCardApiKey(
        val id: String,
        val serviceName: String,
        val serviceDescription: String,
        val iconText: String,
        val iconBackgroundColor: Int,
        val isConnected: Boolean,
        val apiKey: String = "",
        val isVerifying: Boolean = false,
        val userName: String? = null,
        val accountStatus: String? = null,
        val daysRemaining: Int? = null,
        val onApiKeyChange: (String) -> Unit,
        val onVerify: (String) -> Unit,
        val onDisconnect: () -> Unit
    ) : SettingsItem()

    /**
     * Quality chip group for toggling multiple quality options
     */
    data class QualityChipGroup(
        val id: String,
        val label: String,
        val qualities: List<QualityChip>,
        val onToggle: (String, Boolean) -> Unit
    ) : SettingsItem()

    /**
     * Stepper for increment/decrement values (e.g., min link count)
     */
    data class Stepper(
        val id: String,
        val label: String,
        val value: Int,
        val min: Int,
        val max: Int,
        val step: Int = 1,
        val onValueChange: (Int) -> Unit
    ) : SettingsItem()

    /**
     * Tag input for adding/removing exclude phrases
     */
    data class TagInput(
        val id: String,
        val label: String,
        val tags: Set<String>,
        val placeholder: String,
        val onAddTag: (String) -> Unit,
        val onRemoveTag: (String) -> Unit
    ) : SettingsItem()
}

data class SelectOption(
    val value: String,
    val label: String
)

data class QualityChip(
    val id: String,
    val label: String,
    val isEnabled: Boolean
)

enum class AccountAction {
    AUTHENTICATE,
    SYNC,
    LOGOUT,
    CONNECT,
    DISCONNECT
}
