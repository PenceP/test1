package com.test1.tv.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.button.MaterialButton
import com.test1.tv.R
import com.test1.tv.data.local.AppDatabase
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.remote.ApiClient
import com.test1.tv.data.repository.CacheRepository
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.ui.adapter.ContentRowAdapter

class HomeFragment : Fragment() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var contentRowsView: VerticalGridView
    private lateinit var loadingIndicator: ProgressBar

    // Hero section views
    private lateinit var heroBackdrop: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroRating: TextView
    private lateinit var heroYear: TextView
    private lateinit var heroRuntime: TextView
    private lateinit var heroGenre: TextView
    private lateinit var heroOverview: TextView
    private lateinit var homeContentContainer: View
    private lateinit var comingSoonContainer: View
    private lateinit var comingSoonText: TextView

    // Navigation
    private lateinit var navSearch: MaterialButton
    private lateinit var navHome: MaterialButton
    private lateinit var navMovies: MaterialButton
    private lateinit var navTvShows: MaterialButton
    private lateinit var navSettings: MaterialButton
    private var lastFocusedNavButton: View? = null

    companion object {
        private const val TAG = "HomeFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupViewModel()
        setupNavigation()
        setupContentRows()
        observeViewModel()
    }

    private fun initViews(view: View) {
        // Content rows
        contentRowsView = view.findViewById(R.id.content_rows)
        loadingIndicator = view.findViewById(R.id.loading_indicator)

        // Hero section (now directly in main layout)
        heroBackdrop = view.findViewById(R.id.hero_backdrop)
        heroTitle = view.findViewById(R.id.hero_title)
        heroRating = view.findViewById(R.id.hero_rating)
        heroYear = view.findViewById(R.id.hero_year)
        heroRuntime = view.findViewById(R.id.hero_runtime)
        heroGenre = view.findViewById(R.id.hero_genre)
        heroOverview = view.findViewById(R.id.hero_overview)
        homeContentContainer = view.findViewById(R.id.home_content_container)
        comingSoonContainer = view.findViewById(R.id.coming_soon_container)
        comingSoonText = view.findViewById(R.id.coming_soon_text)

        // Navigation
        navSearch = view.findViewById(R.id.nav_search)
        navHome = view.findViewById(R.id.nav_home)
        navMovies = view.findViewById(R.id.nav_movies)
        navTvShows = view.findViewById(R.id.nav_tv_shows)
        navSettings = view.findViewById(R.id.nav_settings)
    }

    private fun setupViewModel() {
        // Initialize repositories
        val database = AppDatabase.getDatabase(requireContext())
        val cacheRepository = CacheRepository(database.cachedContentDao())
        val contentRepository = ContentRepository(
            traktApiService = ApiClient.traktApiService,
            tmdbApiService = ApiClient.tmdbApiService,
            cacheRepository = cacheRepository
        )

        // Create ViewModel
        val factory = HomeViewModelFactory(contentRepository)
        viewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]
    }

    private fun setupNavigation() {
        val navButtons = listOf(navSearch, navHome, navMovies, navTvShows, navSettings)

        navButtons.forEach { button ->
            button.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    lastFocusedNavButton = view
                }
            }
        }

        lastFocusedNavButton = navHome
        navHome.requestFocus()

        navSearch.setOnClickListener {
            showComingSoonPage("Search")
        }

        navHome.setOnClickListener {
            showHomeContent()
        }

        navMovies.setOnClickListener {
            showComingSoonPage("Movies")
        }

        navTvShows.setOnClickListener {
            showComingSoonPage("TV Shows")
        }

        navSettings.setOnClickListener {
            showComingSoonPage("Settings")
        }
    }

    private fun setupContentRows() {
        // Configure vertical grid for rows
        contentRowsView.setNumColumns(1)
        contentRowsView.setItemSpacing(60)

        // Enable smooth scrolling with fixed row heights
        contentRowsView.setHasFixedSize(true)
        contentRowsView.setFocusScrollStrategy(VerticalGridView.FOCUS_SCROLL_ALIGNED)

        // Set window alignment for fixed focus at top with proper offset
        contentRowsView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_LOW_EDGE)
        contentRowsView.setWindowAlignmentOffset(0)
        contentRowsView.setWindowAlignmentOffsetPercent(VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED)

        // Set item alignment to prevent rows from being cut off
        contentRowsView.setItemAlignmentOffset(0)
        contentRowsView.setItemAlignmentOffsetPercent(VerticalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED)
    }

    private fun observeViewModel() {
        // Observe content rows
        viewModel.contentRows.observe(viewLifecycleOwner) { rows ->
            Log.d(TAG, "Content rows updated: ${rows.size} rows")

            val adapter = ContentRowAdapter(
                rows = rows,
                onItemClick = { item ->
                    handleItemClick(item)
                },
                onItemFocused = { item, rowIndex, itemIndex ->
                    handleItemFocused(item, rowIndex, itemIndex)
                },
                onNavigateToNavBar = {
                    focusNavigationBar()
                }
            )

            contentRowsView.adapter = adapter

            // Request focus on first item after content loads
            contentRowsView.post {
                contentRowsView.requestFocus()
            }
        }

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observe errors
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error: $it")
            }
        }

        // Observe hero content
        viewModel.heroContent.observe(viewLifecycleOwner) { item ->
            item?.let { updateHeroSection(it) }
        }
    }

    private fun updateHeroSection(item: ContentItem) {
        Log.d(TAG, "Updating hero section with: ${item.title}")

        // Load backdrop image
        Glide.with(this)
            .load(item.backdropUrl)
            .transition(DrawableTransitionOptions.withCrossFade())
            .placeholder(R.drawable.default_background)
            .error(R.drawable.default_background)
            .into(heroBackdrop)

        // Update text content
        heroTitle.text = item.title
        heroYear.text = item.year ?: ""
        heroGenre.text = item.genres ?: ""
        heroOverview.text = item.overview ?: ""

        // Update rating
        item.ratingPercentage?.let { percentage ->
            heroRating.text = "$percentage%"
            heroRating.visibility = View.VISIBLE
        } ?: run {
            heroRating.visibility = View.GONE
        }

        // Update runtime
        item.runtime?.let {
            heroRuntime.text = it
            heroRuntime.visibility = View.VISIBLE
        } ?: run {
            heroRuntime.visibility = View.GONE
        }
    }

    private fun showComingSoonPage(pageName: String) {
        comingSoonText.text = "$pageName coming soon"
        comingSoonContainer.visibility = View.VISIBLE
        homeContentContainer.visibility = View.GONE
    }

    private fun showHomeContent() {
        comingSoonContainer.visibility = View.GONE
        homeContentContainer.visibility = View.VISIBLE
    }

    private fun focusNavigationBar() {
        (lastFocusedNavButton ?: navHome).requestFocus()
    }

    private fun handleItemClick(item: ContentItem) {
        Log.d(TAG, "Item clicked: ${item.title}")
        Toast.makeText(
            requireContext(),
            "Details for: ${item.title}",
            Toast.LENGTH_SHORT
        ).show()
        // TODO: Navigate to details screen
    }

    private fun handleItemFocused(item: ContentItem, rowIndex: Int, itemIndex: Int) {
        Log.d(TAG, "Item focused: ${item.title} at row $rowIndex, position $itemIndex")

        // Update hero section for ANY focused item
        viewModel.updateHeroContent(item)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.cleanupCache()
    }
}
