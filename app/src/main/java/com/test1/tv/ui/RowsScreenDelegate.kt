package com.test1.tv.ui

import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.RecyclerView
import androidx.fragment.app.Fragment
import androidx.leanback.widget.VerticalGridView
import com.google.android.material.button.MaterialButton
import com.test1.tv.data.model.ContentItem
import com.test1.tv.ui.adapter.ContentRow
import com.test1.tv.ui.adapter.ContentRowAdapter
import com.test1.tv.ui.base.RowAppendEvent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope

/**
 * Coordinates repeated nav/rows wiring used by home/movies/tv fragments so behavior stays identical.
 */
class RowsScreenDelegate(
    private val fragment: Fragment,
    private val lifecycleOwner: LifecycleOwner,
    private val navButtons: NavButtons,
    private val defaultSection: NavTarget,
    private val contentRowsView: VerticalGridView,
    private val loadingIndicator: View,
    private val sharedViewPool: RecyclerView.RecycledViewPool,
    private val rowPrefetchManager: RowPrefetchManager,
    private val accentColorCache: AccentColorCache,
    private val heroSyncManager: HeroSyncManager,
    private val onItemClick: (ContentItem, ImageView) -> Unit,
    private val onItemLongPress: (ContentItem) -> Unit,
    private val onRequestMore: (Int) -> Unit = {},
    private val onNavigate: (NavTarget) -> Unit,
    private val navFocusEffect: ((View, Boolean) -> Unit)? = null,
    private val coroutineScope: CoroutineScope
) {

    enum class NavTarget {
        SEARCH,
        HOME,
        MOVIES,
        TV_SHOWS,
        SETTINGS
    }

    data class NavButtons(
        val search: MaterialButton,
        val home: MaterialButton,
        val movies: MaterialButton,
        val tvShows: MaterialButton,
        val settings: MaterialButton
    )

    private var rowsAdapter: ContentRowAdapter? = null
    private var hasRequestedInitialFocus = false
    private var lastFocusedNavButton: View? = null
    private var activeSection: NavTarget = defaultSection

    init {
        setupNavigation()
        RowLayoutHelper.configureVerticalGrid(contentRowsView)
        contentRowsView.itemAnimator = null
    }

    fun bind(
        contentRows: LiveData<List<ContentRow>>,
        rowAppendEvents: LiveData<out Any?>?,
        isLoading: LiveData<Boolean>,
        error: LiveData<String?>,
        heroContent: LiveData<ContentItem?>
    ) {
        contentRows.observe(lifecycleOwner) { rows ->
            // Clear adapter and prefetch state when rows are empty
            if (rows.isEmpty()) {
                rowsAdapter = null
                contentRowsView.adapter = null
                rowPrefetchManager.clearPrefetchState()
                return@observe
            }

            if (rowsAdapter == null) {
                rowsAdapter = ContentRowAdapter(
                    initialRows = rows,
                    onItemClick = onItemClick,
                    onItemFocused = { item, rowIndex, _ ->
                        heroSyncManager.onContentSelected(item)
                        rowsAdapter?.currentRows()?.let { rowPrefetchManager.onRowFocused(rowIndex, it) }
                    },
                    onNavigateToNavBar = { focusNavigationBar() },
                    onItemLongPress = onItemLongPress,
                    onRequestMore = onRequestMore,
                    viewPool = sharedViewPool,
                    accentColorCache = accentColorCache,
                    coroutineScope = coroutineScope
                )
            } else {
                rowsAdapter?.updateRows(rows)
            }

            if (contentRowsView.adapter !== rowsAdapter) {
                contentRowsView.adapter = rowsAdapter
            }

            rowPrefetchManager.clearPrefetchState()
            contentRowsView.post {
                if (!hasRequestedInitialFocus) {
                    hasRequestedInitialFocus = true
                    contentRowsView.requestFocus()
                }
            }
        }

        rowAppendEvents?.observe(lifecycleOwner) { event ->
            val appendEvent = event as? RowAppendEvent ?: return@observe
            if (appendEvent.newItems.isNotEmpty()) {
                rowsAdapter?.appendItems(appendEvent.rowIndex, appendEvent.newItems)
            }
        }

        isLoading.observe(lifecycleOwner) { loading ->
            loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        }

        error.observe(lifecycleOwner) { message ->
            message?.let {
                Toast.makeText(fragment.requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }

        heroContent.observe(lifecycleOwner) { item ->
            item?.let { heroSyncManager.updateHeroImmediate(it) }
        }
    }

    fun focusNavigationBar() {
        (lastFocusedNavButton ?: navButtons.buttonFor(activeSection)).requestFocus()
    }

    private fun setupNavigation() {
        val navMap = mapOf(
            NavTarget.SEARCH to navButtons.search,
            NavTarget.HOME to navButtons.home,
            NavTarget.MOVIES to navButtons.movies,
            NavTarget.TV_SHOWS to navButtons.tvShows,
            NavTarget.SETTINGS to navButtons.settings
        )

        navMap.values.forEach { button ->
            button.stateListAnimator = null
            button.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    lastFocusedNavButton = view
                }
                navFocusEffect?.invoke(view, hasFocus)
            }
            button.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                    contentRowsView.requestFocus()
                    true
                } else {
                    false
                }
            }
        }

        setActiveSection(defaultSection)
        navMap.forEach { (section, button) ->
            button.setOnClickListener {
                setActiveSection(section)
                onNavigate(section)
            }
        }
    }

    private fun setActiveSection(section: NavTarget) {
        activeSection = section
        listOf(
            navButtons.search,
            navButtons.home,
            navButtons.movies,
            navButtons.tvShows,
            navButtons.settings
        ).forEach { it.isActivated = false }
        navButtons.buttonFor(section).isActivated = true
    }

    private fun NavButtons.buttonFor(section: NavTarget): MaterialButton {
        return when (section) {
            NavTarget.SEARCH -> search
            NavTarget.HOME -> home
            NavTarget.MOVIES -> movies
            NavTarget.TV_SHOWS -> tvShows
            NavTarget.SETTINGS -> settings
        }
    }
}
