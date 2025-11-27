package com.test1.tv.ui.movies

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
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
import com.test1.tv.MainActivity
import com.test1.tv.R
import com.test1.tv.data.local.AppDatabase
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.remote.ApiClient
import com.test1.tv.data.repository.CacheRepository
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.ui.HeroSectionHelper
import com.test1.tv.ui.HeroBackgroundController
import com.test1.tv.ui.RowLayoutHelper
import com.test1.tv.ui.adapter.ContentRowAdapter
import android.graphics.drawable.Drawable
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

class MoviesFragment : Fragment() {

    private lateinit var viewModel: MoviesViewModel
    private lateinit var contentRowsView: VerticalGridView
    private lateinit var loadingIndicator: ProgressBar

    // Hero section views
    private lateinit var heroBackdrop: ImageView
    private lateinit var heroLogo: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroMetadata: TextView
    private lateinit var heroGenreText: TextView
    private lateinit var heroOverview: TextView
    private lateinit var heroCast: TextView
    private lateinit var ambientOverlay: View
    private lateinit var heroBackgroundController: HeroBackgroundController
    private var heroEnrichmentJob: Job? = null

    // Navigation
    private lateinit var navSearch: MaterialButton
    private lateinit var navHome: MaterialButton
    private lateinit var navMovies: MaterialButton
    private lateinit var navTvShows: MaterialButton
    private lateinit var navSettings: MaterialButton
    private var lastFocusedNavButton: View? = null
    private var activeNavButton: MaterialButton? = null

    companion object {
        private const val TAG = "MoviesFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_movies, container, false)
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
        heroMetadata = view.findViewById(R.id.hero_metadata)
        heroGenreText = view.findViewById(R.id.hero_genre_text)
        heroOverview = view.findViewById(R.id.hero_overview)
        heroCast = view.findViewById(R.id.hero_cast)
        ambientOverlay = view.findViewById(R.id.ambient_background_overlay)
        heroBackgroundController = HeroBackgroundController(
            fragment = this,
            backdropView = heroBackdrop,
            ambientOverlay = ambientOverlay
        )

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
        val factory = MoviesViewModelFactory(contentRepository)
        viewModel = ViewModelProvider(this, factory)[MoviesViewModel::class.java]
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
                    focusContentRows()
                    true
                } else {
                    false
                }
            }
        }

        lastFocusedNavButton = navMovies
        navMovies.requestFocus()
        setActiveNavButton(navMovies)

        navSearch.setOnClickListener {
            (activity as? MainActivity)?.navigateToSection(MainActivity.Section.SEARCH)
        }

        navHome.setOnClickListener {
            (activity as? MainActivity)?.navigateToSection(MainActivity.Section.HOME)
        }

        navMovies.setOnClickListener {
            // Already on Movies page
        }

        navTvShows.setOnClickListener {
            (activity as? MainActivity)?.navigateToSection(MainActivity.Section.TV_SHOWS)
        }

        navSettings.setOnClickListener {
            val intent = Intent(requireContext(), com.test1.tv.ui.settings.SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setActiveNavButton(button: MaterialButton) {
        // Clear activated state from all nav buttons
        listOf(navSearch, navHome, navMovies, navTvShows, navSettings).forEach {
            it.isActivated = false
        }
        // Set the current button as activated
        button.isActivated = true
        activeNavButton = button
    }

    private fun setupContentRows() {
        RowLayoutHelper.configureVerticalGrid(contentRowsView)
    }

    private fun observeViewModel() {
        // Observe content rows
        viewModel.contentRows.observe(viewLifecycleOwner) { rows ->
            Log.d(TAG, "Content rows updated: ${rows.size} rows")

            val adapter = ContentRowAdapter(
                initialRows = rows,
                onItemClick = { item, _ ->
                    handleItemClick(item)
                },
                onItemFocused = { item, rowIndex, itemIndex ->
                    handleItemFocused(item, rowIndex, itemIndex)
                },
                onNavigateToNavBar = {
                    focusNavigationBar()
                },
                onItemLongPress = { item ->
                    Toast.makeText(
                        requireContext(),
                        "Actions coming soon for ${item.title}",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onRequestMore = {}
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

        heroBackgroundController.updateBackdrop(
            backdropUrl = item.backdropUrl ?: item.posterUrl,
            fallbackDrawable = requireContext().getDrawable(R.drawable.default_background)
        )

        // Update text content
        heroTitle.text = item.title
        heroOverview.text = item.overview ?: ""
        updateHeroLogo(item.logoUrl)
        HeroSectionHelper.updateGenres(heroGenreText, item.genres)
        HeroSectionHelper.updateHeroMetadata(heroMetadata, item)
        HeroSectionHelper.updateCast(heroCast, item.cast)
        ensureHeroExtras(item)
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
        (lastFocusedNavButton ?: navMovies).requestFocus()
    }

    private fun focusContentRows() {
        contentRowsView.requestFocus()
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

    private fun ensureHeroExtras(item: ContentItem) {
        if (!item.cast.isNullOrBlank() && !item.genres.isNullOrBlank()) return
        heroEnrichmentJob?.cancel()
        heroEnrichmentJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val enriched = runCatching {
                when (item.type) {
                    ContentItem.ContentType.MOVIE -> {
                        val details = ApiClient.tmdbApiService.getMovieDetails(
                            movieId = item.tmdbId,
                            apiKey = com.test1.tv.BuildConfig.TMDB_API_KEY,
                            appendToResponse = "credits,images"
                        )
                        item.copy(
                            cast = details.getCastNames(),
                            genres = details.genres?.joinToString(", ") { it.name }
                        )
                    }
                    ContentItem.ContentType.TV_SHOW -> {
                        val details = ApiClient.tmdbApiService.getShowDetails(
                            showId = item.tmdbId,
                            apiKey = com.test1.tv.BuildConfig.TMDB_API_KEY,
                            appendToResponse = "credits,images,content_ratings"
                        )
                        item.copy(
                            cast = details.getCastNames(),
                            genres = details.genres?.joinToString(", ") { it.name }
                        )
                    }
                }
            }.getOrNull()

            enriched?.let { enrichedItem ->
                withContext(Dispatchers.Main) {
                    HeroSectionHelper.updateGenres(heroGenreText, enrichedItem.genres)
                    HeroSectionHelper.updateCast(heroCast, enrichedItem.cast)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.cleanupCache()
    }
}
