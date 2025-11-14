package com.test1.tv.ui.tvshows

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
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.button.MaterialButton
import com.test1.tv.R
import com.test1.tv.data.local.AppDatabase
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.remote.ApiClient
import com.test1.tv.data.repository.CacheRepository
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.ui.adapter.ContentRowAdapter
import android.graphics.drawable.Drawable

class TvShowsFragment : Fragment() {

    private lateinit var viewModel: TvShowsViewModel
    private lateinit var contentRowsView: VerticalGridView
    private lateinit var loadingIndicator: ProgressBar

    // Hero section views
    private lateinit var heroBackdrop: ImageView
    private lateinit var heroLogo: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroRating: TextView
    private lateinit var heroYear: TextView
    private lateinit var heroRuntime: TextView
    private lateinit var heroContentRating: TextView
    private lateinit var heroGenreContainer: android.widget.LinearLayout
    private lateinit var heroOverview: TextView
    private lateinit var heroCast: TextView

    // Navigation
    private lateinit var navSearch: MaterialButton
    private lateinit var navHome: MaterialButton
    private lateinit var navMovies: MaterialButton
    private lateinit var navTvShows: MaterialButton
    private lateinit var navSettings: MaterialButton
    private var lastFocusedNavButton: View? = null

    companion object {
        private const val TAG = "TvShowsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tv_shows, container, false)
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

        // Hero section
        heroBackdrop = view.findViewById(R.id.hero_backdrop)
        heroLogo = view.findViewById(R.id.hero_logo)
        heroTitle = view.findViewById(R.id.hero_title)
        heroRating = view.findViewById(R.id.hero_rating)
        heroYear = view.findViewById(R.id.hero_year)
        heroRuntime = view.findViewById(R.id.hero_runtime)
        heroContentRating = view.findViewById(R.id.hero_content_rating)
        heroGenreContainer = view.findViewById(R.id.hero_genre_container)
        heroOverview = view.findViewById(R.id.hero_overview)
        heroCast = view.findViewById(R.id.hero_cast)

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
        val factory = TvShowsViewModelFactory(contentRepository)
        viewModel = ViewModelProvider(this, factory)[TvShowsViewModel::class.java]
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

        lastFocusedNavButton = navTvShows
        navTvShows.requestFocus()

        navSearch.setOnClickListener {
            Toast.makeText(requireContext(), "Search coming soon", Toast.LENGTH_SHORT).show()
        }

        navHome.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        navMovies.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, com.test1.tv.ui.movies.MoviesFragment())
                .addToBackStack(null)
                .commit()
        }

        navTvShows.setOnClickListener {
            // Already on TV Shows page
        }

        navSettings.setOnClickListener {
            Toast.makeText(requireContext(), "Settings coming soon", Toast.LENGTH_SHORT).show()
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
        heroOverview.text = item.overview ?: ""
        updateHeroLogo(item.logoUrl)
        updateGenrePills(item.genres)

        // Update cast
        item.cast?.let {
            heroCast.text = it
            heroCast.visibility = View.VISIBLE
        } ?: run {
            heroCast.visibility = View.GONE
        }

        // Update rating
        item.ratingPercentage?.let { percentage ->
            heroRating.text = "$percentage%"
            heroRating.visibility = View.VISIBLE
        } ?: run {
            heroRating.visibility = View.GONE
        }

        // Update certification
        item.certification?.let {
            heroContentRating.text = it
            heroContentRating.visibility = View.VISIBLE
        } ?: run {
            heroContentRating.visibility = View.GONE
        }

        // Update runtime
        item.runtime?.let {
            heroRuntime.text = it
            heroRuntime.visibility = View.VISIBLE
        } ?: run {
            heroRuntime.visibility = View.GONE
        }
    }

    private fun updateGenrePills(genres: String?) {
        heroGenreContainer.removeAllViews()

        if (genres.isNullOrBlank()) {
            return
        }

        val genreList = genres.split(",").map { it.trim() }
        genreList.forEach { genre ->
            val pill = TextView(requireContext()).apply {
                text = genre
                textSize = 12f
                setTextColor(resources.getColor(android.R.color.white, null))
                setBackgroundResource(R.drawable.genre_pill_bg)
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.genre_pill_padding_horizontal),
                    resources.getDimensionPixelSize(R.dimen.genre_pill_padding_vertical),
                    resources.getDimensionPixelSize(R.dimen.genre_pill_padding_horizontal),
                    resources.getDimensionPixelSize(R.dimen.genre_pill_padding_vertical)
                )

                val params = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.marginEnd = resources.getDimensionPixelSize(R.dimen.genre_pill_margin)
                layoutParams = params
            }
            heroGenreContainer.addView(pill)
        }
    }

    private fun updateHeroLogo(logoUrl: String?) {
        heroTitle.visibility = View.VISIBLE
        heroLogo.visibility = View.GONE
        heroLogo.setImageDrawable(null)

        if (logoUrl.isNullOrBlank()) {
            return
        }

        Glide.with(this)
            .load(logoUrl)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    heroLogo.setImageDrawable(resource)
                    heroLogo.visibility = View.VISIBLE
                    heroTitle.visibility = View.GONE
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    heroLogo.setImageDrawable(placeholder)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    heroLogo.visibility = View.GONE
                    heroTitle.visibility = View.VISIBLE
                }
            })
    }

    private fun focusNavigationBar() {
        (lastFocusedNavButton ?: navTvShows).requestFocus()
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
