package com.test1.tv.ui.home

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.test1.tv.DetailsActivity
import com.test1.tv.R
import com.test1.tv.data.local.AppDatabase
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.remote.ApiClient
import com.test1.tv.data.repository.CacheRepository
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.ui.adapter.ContentRowAdapter
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var contentRowsView: VerticalGridView
    private lateinit var loadingIndicator: ProgressBar

    // Hero section views
    private lateinit var heroBackdrop: ImageView
    private lateinit var heroLogo: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroYear: TextView
    private lateinit var heroRuntime: TextView
    private lateinit var heroContentRating: TextView
    private lateinit var heroGenreContainer: LinearLayout
    private lateinit var heroOverview: TextView
    private lateinit var heroCast: TextView
    private lateinit var heroRatingContainer: LinearLayout
    private lateinit var heroRatingImdb: View
    private lateinit var heroRatingImdbValue: TextView
    private lateinit var heroRatingRotten: View
    private lateinit var heroRatingRottenValue: TextView
    private lateinit var heroRatingTmdb: View
    private lateinit var heroRatingTmdbValue: TextView
    private lateinit var heroRatingTrakt: View
    private lateinit var heroRatingTraktValue: TextView
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

    private var rowsAdapter: ContentRowAdapter? = null
    private var hasRequestedInitialFocus = false

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
        heroLogo = view.findViewById(R.id.hero_logo)
        heroTitle = view.findViewById(R.id.hero_title)
        heroYear = view.findViewById(R.id.hero_year)
        heroRuntime = view.findViewById(R.id.hero_runtime)
        heroContentRating = view.findViewById(R.id.hero_content_rating)
        heroGenreContainer = view.findViewById(R.id.hero_genre_container)
        heroOverview = view.findViewById(R.id.hero_overview)
        heroCast = view.findViewById(R.id.hero_cast)
        heroRatingContainer = view.findViewById(R.id.hero_rating_container)
        heroRatingImdb = view.findViewById(R.id.hero_rating_imdb)
        heroRatingImdbValue = view.findViewById(R.id.hero_rating_imdb_value)
        heroRatingRotten = view.findViewById(R.id.hero_rating_rotten)
        heroRatingRottenValue = view.findViewById(R.id.hero_rating_rotten_value)
        heroRatingTmdb = view.findViewById(R.id.hero_rating_tmdb)
        heroRatingTmdbValue = view.findViewById(R.id.hero_rating_tmdb_value)
        heroRatingTrakt = view.findViewById(R.id.hero_rating_trakt)
        heroRatingTraktValue = view.findViewById(R.id.hero_rating_trakt_value)
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
            omdbApiService = ApiClient.omdbApiService,
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
            button.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                    focusPrimaryContent()
                    true
                } else {
                    false
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
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, com.test1.tv.ui.movies.MoviesFragment())
                .addToBackStack(null)
                .commit()
        }

        navTvShows.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, com.test1.tv.ui.tvshows.TvShowsFragment())
                .addToBackStack(null)
                .commit()
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

            if (rowsAdapter == null) {
                rowsAdapter = ContentRowAdapter(
                    initialRows = rows,
                    onItemClick = { item ->
                        handleItemClick(item)
                    },
                    onItemFocused = { item, rowIndex, itemIndex ->
                        handleItemFocused(item, rowIndex, itemIndex)
                    },
                    onNavigateToNavBar = {
                        focusNavigationBar()
                    },
                    onRequestMore = { rowIndex ->
                        viewModel.requestNextPage(rowIndex)
                    }
                )
            }

            if (contentRowsView.adapter !== rowsAdapter) {
                contentRowsView.adapter = rowsAdapter
            }

            rowsAdapter?.updateRows(rows)

            contentRowsView.post {
                if (!hasRequestedInitialFocus) {
                    hasRequestedInitialFocus = true
                    contentRowsView.requestFocus()
                }
            }
        }

        viewModel.rowAppendEvents.observe(viewLifecycleOwner) { event ->
            if (event.newItems.isNotEmpty()) {
                rowsAdapter?.appendItems(event.rowIndex, event.newItems)
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

        updateHeroRatings(item)

        // Update certification
        item.certification?.let {
            heroContentRating.text = it
            heroContentRating.visibility = View.VISIBLE
        } ?: run {
            heroContentRating.visibility = View.GONE
        }

        // Update runtime
        formatRuntimeText(item.runtime)?.let {
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

    private fun updateHeroRatings(item: ContentItem) {
        val hasImdb = bindRatingBadge(
            heroRatingImdb,
            heroRatingImdbValue,
            formatImdbRating(item.imdbRating)
        )
        val hasRotten = bindRatingBadge(
            heroRatingRotten,
            heroRatingRottenValue,
            item.rottenTomatoesRating?.takeIf { it.isNotBlank() && it != "N/A" }
        )
        val hasTmdb = bindRatingBadge(
            heroRatingTmdb,
            heroRatingTmdbValue,
            formatScore(item.rating)
        )
        val hasTrakt = bindRatingBadge(
            heroRatingTrakt,
            heroRatingTraktValue,
            formatScore(item.traktRating)
        )

        heroRatingContainer.visibility =
            if (hasImdb || hasRotten || hasTmdb || hasTrakt) View.VISIBLE else View.GONE
    }

    private fun bindRatingBadge(container: View, valueView: TextView, value: String?): Boolean {
        return if (!value.isNullOrBlank()) {
            valueView.text = value
            container.visibility = View.VISIBLE
            true
        } else {
            container.visibility = View.GONE
            false
        }
    }

    private fun formatScore(value: Double?): String? {
        value ?: return null
        if (value <= 0.0) return null
        return String.format(Locale.US, "%.1f", value)
    }

    private fun formatImdbRating(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "N/A") return null
        val parts = raw.split("/")
        return parts.firstOrNull()?.trim().takeUnless { it.isNullOrBlank() } ?: raw
    }

    private fun formatRuntimeText(runtime: String?): String? {
        if (runtime.isNullOrBlank()) return null
        if (runtime.contains("h")) return runtime

        val minutes = runtime.filter { it.isDigit() }.toIntOrNull() ?: return runtime
        return if (minutes >= 60) {
            val hours = minutes / 60
            val remaining = minutes % 60
            if (remaining == 0) {
                "${hours}h"
            } else {
                "${hours}h ${remaining}m"
            }
        } else {
            "${minutes}m"
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

    private fun focusPrimaryContent() {
        if (comingSoonContainer.visibility == View.VISIBLE) {
            comingSoonContainer.requestFocus()
        } else {
            contentRowsView.requestFocus()
        }
    }

    private fun focusNavigationBar() {
        (lastFocusedNavButton ?: navHome).requestFocus()
    }

    private fun handleItemClick(item: ContentItem) {
        Log.d(TAG, "Item clicked: ${item.title}")
        val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
            putExtra(DetailsActivity.CONTENT_ITEM, item)
        }
        startActivity(intent)
    }

    private fun handleItemFocused(item: ContentItem, rowIndex: Int, itemIndex: Int) {
        Log.d(TAG, "Item focused: ${item.title} at row $rowIndex, position $itemIndex")

        // Update hero section for ANY focused item
        viewModel.updateHeroContent(item)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hasRequestedInitialFocus = false
        viewModel.cleanupCache()
    }
}
