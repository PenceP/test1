package com.test1.tv

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.test1.tv.ui.home.HomeFragment
import com.test1.tv.ui.movies.MoviesFragment
import com.test1.tv.ui.search.SearchFragment
import com.test1.tv.ui.tvshows.TvShowsFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Hosts the main sections and handles navigation/back behavior.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            navigateToSection(Section.HOME)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val visible = supportFragmentManager.fragments.firstOrNull { it.isVisible }
                when (visible?.tag) {
                    Section.SEARCH.tag,
                    Section.MOVIES.tag,
                    Section.TV_SHOWS.tag -> navigateToSection(Section.HOME)
                    Section.HOME.tag, null -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    fun navigateToSection(section: Section) {
        val currentTag = supportFragmentManager.fragments.firstOrNull { it.isVisible }?.tag
        if (currentTag == section.tag) return

        val fragment: Fragment = when (section) {
            Section.HOME -> HomeFragment()
            Section.MOVIES -> MoviesFragment()
            Section.TV_SHOWS -> TvShowsFragment()
            Section.SEARCH -> SearchFragment()
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_fragment, fragment, section.tag)
            .commitNow()
    }

    enum class Section(val tag: String) {
        HOME("section_home"),
        MOVIES("section_movies"),
        TV_SHOWS("section_tv_shows"),
        SEARCH("section_search")
    }
}
