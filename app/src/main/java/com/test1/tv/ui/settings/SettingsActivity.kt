package com.test1.tv.ui.settings

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.VerticalGridView
import com.test1.tv.R
import com.test1.tv.ui.settings.adapter.SubmenuAdapter
import com.test1.tv.ui.settings.fragments.AboutFragment
import com.test1.tv.ui.settings.fragments.AccountsFragment
import com.test1.tv.ui.settings.fragments.LinkFilteringFragment
import com.test1.tv.ui.settings.fragments.LinkResolvingFragment
import com.test1.tv.ui.settings.fragments.PlaybackFragment
import com.test1.tv.ui.settings.fragments.RowCustomizationFragment
import com.test1.tv.ui.settings.model.SubmenuItem
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : FragmentActivity() {

    private lateinit var backgroundImage: ImageView
    private lateinit var submenuList: VerticalGridView
    private lateinit var contentTitle: TextView
    private lateinit var contentSubtitle: TextView
    private lateinit var fragmentContainer: FrameLayout

    private lateinit var submenuAdapter: SubmenuAdapter

    private val submenuItems = listOf(
        SubmenuItem("accounts", "Accounts", R.drawable.ic_user, "Manage your accounts preferences"),
        SubmenuItem("layout", "Layout & Rows", R.drawable.ic_layout, "Customize row visibility and order"),
        SubmenuItem("resolving", "Link Resolving", R.drawable.ic_link, "Configure link resolution settings"),
        SubmenuItem("filtering", "Link Filtering", R.drawable.ic_filter, "Set up content filtering rules"),
        SubmenuItem("playback", "Playback", R.drawable.ic_play_circle, "Adjust playback preferences"),
        SubmenuItem("display", "Display", R.drawable.ic_monitor, "Customize display settings"),
        SubmenuItem("about", "About", R.drawable.ic_info, "App information and version")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initializeViews()
        setupBlurEffect()
        setupSubmenu()

        // Load initial fragment
        loadFragment(submenuItems[0])
    }

    private fun initializeViews() {
        backgroundImage = findViewById(R.id.background_image)
        submenuList = findViewById(R.id.submenu_list)
        contentTitle = findViewById(R.id.content_title)
        contentSubtitle = findViewById(R.id.content_subtitle)
        fragmentContainer = findViewById(R.id.fragment_container)
    }

    private fun setupBlurEffect() {
        // Apply blur effect on API 31+ (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Reduced blur for better readability (5f instead of 25f)
            val blurEffect = RenderEffect.createBlurEffect(
                5f, 5f, Shader.TileMode.CLAMP
            )
            // Apply blur only to the background, not the entire container
            backgroundImage.setRenderEffect(blurEffect)
        }
    }

    override fun onBackPressed() {
        // Return to home page when back is pressed
        super.onBackPressed()
        finish()
    }

    private fun setupSubmenu() {
        // Fix VerticalGridView alignment - align to top edge instead of center
        submenuList.windowAlignment = BaseGridView.WINDOW_ALIGN_LOW_EDGE
        submenuList.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
        submenuList.itemAlignmentOffsetPercent = BaseGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED

        submenuAdapter = SubmenuAdapter(submenuItems) { item ->
            loadFragment(item)
        }

        submenuList.adapter = submenuAdapter
    }

    private fun loadFragment(item: SubmenuItem) {
        // Load appropriate fragment
        val fragment = when (item.id) {
            "accounts" -> AccountsFragment()
            "layout" -> RowCustomizationFragment()
            "resolving" -> LinkResolvingFragment()
            "filtering" -> LinkFilteringFragment()
            "playback" -> PlaybackFragment()
            "display" -> AccountsFragment() // Placeholder - create DisplayFragment
            "about" -> AboutFragment()
            else -> AccountsFragment()
        }

        // Update header (RowCustomizationFragment will hide it and manage its own tabs)
        if (fragment !is RowCustomizationFragment) {
            contentTitle.text = item.label
            contentSubtitle.text = item.description
            findViewById<View>(R.id.content_header)?.visibility = View.VISIBLE
        }

        val transaction = supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.fade_in,
                R.anim.fade_out,
                R.anim.fade_in,
                R.anim.fade_out
            )
            .replace(R.id.fragment_container, fragment)

        if (supportFragmentManager.isStateSaved) {
            transaction.commitAllowingStateLoss()
        } else {
            transaction.commit()
        }
    }
}
