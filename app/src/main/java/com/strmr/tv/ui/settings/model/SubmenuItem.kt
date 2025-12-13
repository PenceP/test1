package com.strmr.tv.ui.settings.model

import androidx.annotation.DrawableRes

data class SubmenuItem(
    val id: String,
    val label: String,
    @DrawableRes val iconRes: Int,
    val description: String = ""
)
