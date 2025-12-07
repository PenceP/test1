package com.test1.tv.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.leanback.widget.VerticalGridView
import com.test1.tv.R
import com.test1.tv.data.repository.PreferencesRepository
import com.test1.tv.ui.settings.adapter.SettingsAdapter
import com.test1.tv.ui.settings.model.SelectOption
import com.test1.tv.ui.settings.model.SettingsItem
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackFragment : Fragment() {

    private lateinit var settingsList: VerticalGridView
    private lateinit var adapter: SettingsAdapter

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

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
        val items = buildSettingsItems()
        adapter = SettingsAdapter(items)
        adapter.submitList(items)
        settingsList.adapter = adapter
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
            )
        )
    }

    private fun refreshItems() {
        val newItems = buildSettingsItems()
        adapter.submitList(newItems)
    }
}
