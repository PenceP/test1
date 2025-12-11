package com.test1.tv.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.lifecycleScope
import com.test1.tv.R
import com.test1.tv.data.local.entity.PlayerSettings
import com.test1.tv.data.repository.PlayerSettingsRepository
import com.test1.tv.data.repository.PreferencesRepository
import com.test1.tv.ui.settings.adapter.SettingsAdapter
import com.test1.tv.ui.settings.model.SelectOption
import com.test1.tv.ui.settings.model.SettingsItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackFragment : Fragment() {

    private lateinit var settingsList: VerticalGridView
    private lateinit var adapter: SettingsAdapter

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var playerSettingsRepository: PlayerSettingsRepository

    private var currentSkipMode: String = PlayerSettings.SKIP_MODE_INSTANT
    private var currentDecoderMode: String = PlayerSettings.DECODER_MODE_AUTO
    private var currentTunnelingEnabled: Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_playback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settingsList = view.findViewById(R.id.playback_settings_list)
        setupSettingsList()
    }

    private fun setupSettingsList() {
        // Fix VerticalGridView alignment - align to top edge instead of center
        settingsList.windowAlignment = BaseGridView.WINDOW_ALIGN_LOW_EDGE
        settingsList.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
        settingsList.itemAlignmentOffsetPercent = BaseGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED

        // Load current settings before building items
        lifecycleScope.launch {
            val settings = playerSettingsRepository.getSettings()
            currentSkipMode = settings.skipMode
            currentDecoderMode = settings.decoderMode
            currentTunnelingEnabled = settings.tunnelingEnabled

            val items = buildSettingsItems()
            adapter = SettingsAdapter(items)
            adapter.submitList(items)
            settingsList.adapter = adapter
        }
    }

    private fun buildSettingsItems(): List<SettingsItem> {
        return listOf(
            // Episode Section
            SettingsItem.Header(
                id = "header_episode",
                title = "Episode"
            ),
            SettingsItem.Toggle(
                id = "autoplay_next_episode",
                label = "Autoplay Next Episode",
                isEnabled = preferencesRepository.autoplayNextEpisode,
                onToggle = { enabled ->
                    preferencesRepository.autoplayNextEpisode = enabled
                    refreshItems()
                }
            ),

            // Skip Mode Section
            SettingsItem.Header(
                id = "header_skip_mode",
                title = "Skip Controls"
            ),
            SettingsItem.Select(
                id = "skip_mode",
                label = "Skip Mode",
                value = currentSkipMode,
                options = listOf(
                    SelectOption(PlayerSettings.SKIP_MODE_INSTANT, "Traditional (10s fixed)"),
                    SelectOption(PlayerSettings.SKIP_MODE_ADAPTIVE, "Incremental (5s → 10s → 30s → 1m → 5m → 10m)")
                ),
                onSelect = { value ->
                    lifecycleScope.launch {
                        playerSettingsRepository.updateSkipMode(value)
                        currentSkipMode = value
                        refreshItems()
                    }
                }
            ),

            // Video Player Engine Section
            SettingsItem.Header(
                id = "header_video_player",
                title = "Video Player Engine"
            ),
            SettingsItem.Select(
                id = "video_player_engine",
                label = "Player",
                value = preferencesRepository.videoPlayerEngine.value,
                options = PreferencesRepository.VideoPlayerEngine.entries.map {
                    SelectOption(it.value, it.label)
                },
                onSelect = { value ->
                    preferencesRepository.videoPlayerEngine = PreferencesRepository.VideoPlayerEngine.fromValue(value)
                    refreshItems()
                }
            ),

            // Hardware Decoder Section
            SettingsItem.Header(
                id = "header_decoder",
                title = "Decoder Settings"
            ),
            SettingsItem.Select(
                id = "decoder_mode",
                label = "Decoder Mode",
                value = currentDecoderMode,
                options = listOf(
                    SelectOption(PlayerSettings.DECODER_MODE_AUTO, "Auto (Recommended)"),
                    SelectOption(PlayerSettings.DECODER_MODE_HW_ONLY, "Hardware Only"),
                    SelectOption(PlayerSettings.DECODER_MODE_SW_PREFER, "Software Preferred")
                ),
                onSelect = { value ->
                    lifecycleScope.launch {
                        playerSettingsRepository.updateDecoderMode(value)
                        currentDecoderMode = value
                        refreshItems()
                    }
                }
            ),
            SettingsItem.Toggle(
                id = "tunneled_playback",
                label = "Tunneled Playback",
                isEnabled = currentTunnelingEnabled,
                onToggle = { enabled ->
                    lifecycleScope.launch {
                        playerSettingsRepository.updateTunnelingEnabled(enabled)
                        currentTunnelingEnabled = enabled
                        refreshItems()
                    }
                }
            )
        )
    }

    private fun refreshItems() {
        val newItems = buildSettingsItems()
        adapter.submitList(newItems)
    }
}
