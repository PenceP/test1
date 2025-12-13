package com.test1.tv.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.VerticalGridView
import com.test1.tv.R
import com.test1.tv.data.repository.LinkFilterPreferences
import com.test1.tv.ui.settings.adapter.SettingsAdapter
import com.test1.tv.ui.settings.model.SelectOption
import com.test1.tv.ui.settings.model.SettingsItem
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LinkResolvingFragment : Fragment() {

    private lateinit var settingsList: VerticalGridView
    private lateinit var adapter: SettingsAdapter

    @Inject
    lateinit var linkFilterPreferences: LinkFilterPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_link_resolving, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settingsList = view.findViewById(R.id.link_resolving_settings_list)
        setupSettingsList()
    }

    private fun setupSettingsList() {
        // Fix VerticalGridView alignment - align to top edge instead of center
        settingsList.windowAlignment = BaseGridView.WINDOW_ALIGN_LOW_EDGE
        settingsList.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
        settingsList.itemAlignmentOffsetPercent = BaseGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED

        val items = buildSettingsItems()
        adapter = SettingsAdapter(items)
        adapter.submitList(items)
        settingsList.adapter = adapter
    }

    private fun buildSettingsItems(): List<SettingsItem> {
        return listOf(
            // Autoselect Section
            SettingsItem.Header(
                id = "header_autoselect",
                title = "Autoselect"
            ),

            SettingsItem.Toggle(
                id = "autoselect_enabled",
                label = "Autoselect Link",
                isEnabled = linkFilterPreferences.autoselectEnabled,
                onToggle = { enabled ->
                    linkFilterPreferences.autoselectEnabled = enabled
                    refreshItems()
                }
            ),

            SettingsItem.Stepper(
                id = "min_link_count",
                label = "Minimum Link Count for Autoselect",
                value = linkFilterPreferences.minLinkCountForAutoselect,
                min = 1,
                max = 50,
                step = 1,
                onValueChange = { value ->
                    linkFilterPreferences.minLinkCountForAutoselect = value
                    refreshItems()
                }
            ),

            // Sorting Section
            SettingsItem.Header(
                id = "header_sorting",
                title = "Sorting"
            ),

            SettingsItem.Select(
                id = "primary_sort",
                label = "Primary Sort Method",
                value = linkFilterPreferences.primarySortMethod.value,
                options = LinkFilterPreferences.SortMethod.entries.map {
                    SelectOption(it.value, it.label)
                },
                onSelect = { value ->
                    linkFilterPreferences.primarySortMethod = LinkFilterPreferences.SortMethod.fromValue(value)
                    refreshItems()
                }
            ),

            SettingsItem.Select(
                id = "secondary_sort",
                label = "Secondary Sort Method",
                value = linkFilterPreferences.secondarySortMethod.value,
                options = LinkFilterPreferences.SortMethod.entries.map {
                    SelectOption(it.value, it.label)
                },
                onSelect = { value ->
                    linkFilterPreferences.secondarySortMethod = LinkFilterPreferences.SortMethod.fromValue(value)
                    refreshItems()
                }
            )
        )
    }

    private fun refreshItems() {
        val newItems = buildSettingsItems()
        adapter.submitList(newItems)
    }
}
