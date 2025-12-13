package com.strmr.tv.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.strmr.tv.BuildConfig
import com.strmr.tv.ui.common.ListSelectionDialog
import com.strmr.tv.R
import com.strmr.tv.data.model.trakt.TraktUserList
import com.strmr.tv.data.remote.api.TraktApiService
import com.strmr.tv.data.repository.ScreenConfigRepository
import com.strmr.tv.data.repository.TraktAccountRepository
import com.strmr.tv.ui.settings.adapter.RowConfigAdapter
import com.strmr.tv.ui.settings.viewmodel.RowCustomizationViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class RowCustomizationFragment : Fragment() {

    @Inject lateinit var traktAccountRepository: TraktAccountRepository
    @Inject lateinit var traktApiService: TraktApiService

    private val viewModel: RowCustomizationViewModel by viewModels()
    private lateinit var adapter: RowConfigAdapter

    private lateinit var tabHome: MaterialButton
    private lateinit var tabMovies: MaterialButton
    private lateinit var tabTvShows: MaterialButton
    private lateinit var rowsList: VerticalGridView
    private lateinit var btnAddLikedList: MaterialButton
    private lateinit var btnResetDefaults: MaterialButton

    private var currentScreen = ScreenConfigRepository.ScreenType.HOME

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_row_customization, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide the header and adjust container margin for this fragment
        activity?.findViewById<View>(R.id.content_header)?.apply {
            visibility = View.GONE
        }

        // Reduce the fragment container's top margin since header is hidden
        activity?.findViewById<View>(R.id.fragment_container)?.apply {
            val params = layoutParams as? ViewGroup.MarginLayoutParams
            params?.topMargin = 0
            layoutParams = params
        }

        initializeViews(view)
        setupTabs()
        setupRecyclerView()
        setupAddLikedListButton()
        setupResetButton()
        observeViewModel()

        // Load initial screen
        switchScreen(ScreenConfigRepository.ScreenType.HOME)

        // Request focus on the Home tab for TV remote navigation
        tabHome.post {
            tabHome.requestFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Restore header visibility and container margin when leaving this fragment
        activity?.findViewById<View>(R.id.content_header)?.visibility = View.VISIBLE

        activity?.findViewById<View>(R.id.fragment_container)?.apply {
            val params = layoutParams as? ViewGroup.MarginLayoutParams
            // Restore original 140dp margin (convert to pixels)
            val marginInDp = 140
            val marginInPx = (marginInDp * resources.displayMetrics.density).toInt()
            params?.topMargin = marginInPx
            layoutParams = params
        }
    }

    private fun initializeViews(view: View) {
        tabHome = view.findViewById(R.id.tab_home)
        tabMovies = view.findViewById(R.id.tab_movies)
        tabTvShows = view.findViewById(R.id.tab_tv_shows)
        rowsList = view.findViewById(R.id.rows_list)
        btnAddLikedList = view.findViewById(R.id.btn_add_liked_list)
        btnResetDefaults = view.findViewById(R.id.btn_reset_defaults)
    }

    private fun setupTabs() {
        tabHome.setOnClickListener {
            switchScreen(ScreenConfigRepository.ScreenType.HOME)
        }

        tabMovies.setOnClickListener {
            switchScreen(ScreenConfigRepository.ScreenType.MOVIES)
        }

        tabTvShows.setOnClickListener {
            switchScreen(ScreenConfigRepository.ScreenType.TV_SHOWS)
        }
    }

    private fun switchScreen(screen: ScreenConfigRepository.ScreenType) {
        currentScreen = screen
        viewModel.loadRowsForScreen(screen)
        updateTabSelection()
        updateFocusNavigation()
    }

    private fun updateFocusNavigation() {
        // Set up bi-directional focus between tabs and list
        // When pressing UP from the first card, focus should return to the active tab
        val activeTab = when (currentScreen) {
            ScreenConfigRepository.ScreenType.HOME -> tabHome
            ScreenConfigRepository.ScreenType.MOVIES -> tabMovies
            ScreenConfigRepository.ScreenType.TV_SHOWS -> tabTvShows
        }

        // Tell the adapter which tab is active so it can set nextFocusUp on the first item
        adapter.setActiveTabId(activeTab.id)
    }

    private fun updateTabSelection() {
        // Reset all tabs
        tabHome.alpha = 0.6f
        tabMovies.alpha = 0.6f
        tabTvShows.alpha = 0.6f

        // Highlight selected tab
        when (currentScreen) {
            ScreenConfigRepository.ScreenType.HOME -> tabHome.alpha = 1.0f
            ScreenConfigRepository.ScreenType.MOVIES -> tabMovies.alpha = 1.0f
            ScreenConfigRepository.ScreenType.TV_SHOWS -> tabTvShows.alpha = 1.0f
        }
    }

    private fun setupRecyclerView() {
        // Fix VerticalGridView alignment - align to top edge instead of center
        rowsList.windowAlignment = BaseGridView.WINDOW_ALIGN_LOW_EDGE
        rowsList.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
        rowsList.itemAlignmentOffsetPercent = BaseGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED

        adapter = RowConfigAdapter(
            onToggleVisibility = { row -> viewModel.toggleRowVisibility(row) },
            onMoveUp = { row -> viewModel.moveRowUp(row) },
            onMoveDown = { row -> viewModel.moveRowDown(row) },
            onOrientationToggle = { row -> viewModel.cycleRowOrientation(row) },
            onNavigateUpFromFirst = {
                // When UP is pressed on the first item, move focus to the active tab
                getActiveTab().requestFocus()
            },
            onNavigateDownFromLast = {
                // When DOWN is pressed on the last item, move focus to the reset button
                btnResetDefaults.requestFocus()
            }
        )

        rowsList.adapter = adapter
    }
    
    private fun getActiveTab(): MaterialButton {
        return when (currentScreen) {
            ScreenConfigRepository.ScreenType.HOME -> tabHome
            ScreenConfigRepository.ScreenType.MOVIES -> tabMovies
            ScreenConfigRepository.ScreenType.TV_SHOWS -> tabTvShows
        }
    }

    private fun setupResetButton() {
        btnResetDefaults.setOnClickListener {
            showResetConfirmationDialog()
        }

        // Set up focus navigation: UP from reset button should go back to the list
        btnResetDefaults.nextFocusUpId = R.id.rows_list

        // Set up focus effect: scale to 1.05x when focused (matching tab button style)
        btnResetDefaults.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(150)
                    .start()
            } else {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start()
            }
        }
    }

    private fun showResetConfirmationDialog() {
        val screenName = when (currentScreen) {
            ScreenConfigRepository.ScreenType.HOME -> "Home"
            ScreenConfigRepository.ScreenType.MOVIES -> "Movies"
            ScreenConfigRepository.ScreenType.TV_SHOWS -> "TV Shows"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reset to Defaults")
            .setMessage("This will reset all row settings for $screenName to their defaults. Continue?")
            .setPositiveButton("Reset") { _, _ ->
                viewModel.resetToDefaults()
                Toast.makeText(
                    requireContext(),
                    "$screenName rows reset to defaults",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.rows.observe(viewLifecycleOwner) { rows ->
            adapter.submitList(rows) {
                // After list is updated, refresh focus navigation for first and last items
                if (rows.isNotEmpty()) {
                    // Refresh focus navigation setup after list update
                    updateFocusNavigation()
                }
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupAddLikedListButton() {
        btnAddLikedList.setOnClickListener {
            showAddFromLikedListsDialog()
        }

        // Set up focus navigation
        btnAddLikedList.nextFocusUpId = R.id.rows_list

        // Set up focus effect (matching reset button)
        btnAddLikedList.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(150)
                    .start()
            } else {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start()
            }
        }
    }

    private fun showAddFromLikedListsDialog() {
        // Check if user is authenticated with Trakt
        lifecycleScope.launch {
            val account = withContext(Dispatchers.IO) {
                traktAccountRepository.getAccount()
            }

            if (account == null) {
                Toast.makeText(
                    requireContext(),
                    "Please authenticate with Trakt first",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            // Show loading toast
            Toast.makeText(requireContext(), "Loading liked lists...", Toast.LENGTH_SHORT).show()

            try {
                val authHeader = "Bearer ${account.accessToken}"
                val likedListsResponse = withContext(Dispatchers.IO) {
                    traktApiService.getLikedLists(
                        authHeader = authHeader,
                        clientId = BuildConfig.TRAKT_CLIENT_ID
                    )
                }

                // Extract actual lists from the wrapper
                val lists = likedListsResponse.mapNotNull { it.list }

                if (lists.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "No liked lists found",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Show selection dialog
                showLikedListsSelectionDialog(lists)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Failed to load liked lists: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showLikedListsSelectionDialog(lists: List<TraktUserList>) {
        ListSelectionDialog.show(
            context = requireContext(),
            title = "Add from Liked Lists",
            items = lists,
            itemLabelProvider = { list ->
                val owner = list.user?.username ?: "Unknown"
                "${list.name} (by $owner)"
            },
            onItemSelected = { selectedList ->
                addListAsRow(selectedList)
            }
        )
    }

    private fun addListAsRow(list: TraktUserList) {
        // Use user's slug (not username) for Trakt API calls
        val userSlug = list.user?.ids?.slug ?: list.user?.username ?: "me"
        val listSlug = list.ids?.slug ?: return

        viewModel.addTraktListRow(
            title = list.name,
            username = userSlug,
            listSlug = listSlug,
            screenType = currentScreen
        )

        Toast.makeText(
            requireContext(),
            "Added '${list.name}' row",
            Toast.LENGTH_SHORT
        ).show()
    }
}
