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
import com.test1.tv.ui.settings.model.QualityChip
import com.test1.tv.ui.settings.model.SettingsItem
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LinkFilteringFragment : Fragment() {

    private lateinit var settingsList: VerticalGridView
    private lateinit var adapter: SettingsAdapter

    @Inject
    lateinit var linkFilterPreferences: LinkFilterPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_link_filtering, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settingsList = view.findViewById(R.id.link_filtering_settings_list)
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
            // Quality Resolution Section
            SettingsItem.QualityChipGroup(
                id = "quality_resolution",
                label = "QUALITY RESOLUTION",
                qualities = listOf(
                    QualityChip("4k", "4K", linkFilterPreferences.quality4kEnabled),
                    QualityChip("1080p", "1080p", linkFilterPreferences.quality1080pEnabled),
                    QualityChip("720p", "720p", linkFilterPreferences.quality720pEnabled),
                    QualityChip("sd", "SD", linkFilterPreferences.qualitySdEnabled),
                    QualityChip("cam", "CAM", linkFilterPreferences.qualityCamEnabled),
                    QualityChip("unknown", "Unknown", linkFilterPreferences.qualityUnknownEnabled)
                ),
                onToggle = { id, enabled ->
                    when (id) {
                        "4k" -> linkFilterPreferences.quality4kEnabled = enabled
                        "1080p" -> linkFilterPreferences.quality1080pEnabled = enabled
                        "720p" -> linkFilterPreferences.quality720pEnabled = enabled
                        "sd" -> linkFilterPreferences.qualitySdEnabled = enabled
                        "cam" -> linkFilterPreferences.qualityCamEnabled = enabled
                        "unknown" -> linkFilterPreferences.qualityUnknownEnabled = enabled
                    }
                    refreshItems()
                }
            ),

            // Bitrate Section Header
            SettingsItem.Header(
                id = "header_bitrate",
                title = "Bitrate Limits"
            ),

            // Minimum Bitrate Slider
            SettingsItem.Slider(
                id = "min_bitrate",
                label = "Minimum Bitrate",
                value = linkFilterPreferences.minBitrateMbps,
                min = 0,
                max = 100,
                unit = "Mbps",
                onValueChange = { value ->
                    linkFilterPreferences.minBitrateMbps = value
                    refreshItems()
                }
            ),

            // Maximum Bitrate Slider
            SettingsItem.Slider(
                id = "max_bitrate",
                label = "Maximum Bitrate",
                value = linkFilterPreferences.maxBitrateMbps,
                min = 0,
                max = 100,
                unit = "Mbps",
                onValueChange = { value ->
                    linkFilterPreferences.maxBitrateMbps = value
                    refreshItems()
                }
            ),

            // Exclude Phrases Section
            SettingsItem.TagInput(
                id = "exclude_phrases",
                label = "EXCLUDE PHRASES",
                tags = linkFilterPreferences.excludePhrases,
                placeholder = "DV, 3D, HEVC...",
                onAddTag = { tag ->
                    linkFilterPreferences.addExcludePhrase(tag)
                    refreshItems()
                },
                onRemoveTag = { tag ->
                    linkFilterPreferences.removeExcludePhrase(tag)
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
