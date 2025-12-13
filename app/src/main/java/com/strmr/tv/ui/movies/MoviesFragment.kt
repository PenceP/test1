package com.strmr.tv.ui.movies

import android.content.Intent
import android.util.Log
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.leanback.widget.VerticalGridView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.button.MaterialButton
import com.strmr.tv.DetailsActivity
import com.strmr.tv.MainActivity
import com.strmr.tv.R
import com.strmr.tv.data.model.ContentItem
import com.strmr.tv.ui.HeroSectionHelper
import com.strmr.tv.ui.HeroBackgroundController
import com.strmr.tv.ui.HeroSyncManager
import com.strmr.tv.ui.RowPrefetchManager
import com.strmr.tv.ui.AccentColorCache
import com.strmr.tv.ui.RowsScreenDelegate
import com.strmr.tv.ui.HeroExtrasLoader
import com.strmr.tv.ui.HeroLogoLoader
import com.strmr.tv.ui.WatchedBadgeManager
import com.strmr.tv.ui.contextmenu.ContextMenuActionHandler
import com.strmr.tv.ui.contextmenu.ContextMenuHelper
import com.strmr.tv.data.repository.TraktStatusProvider
import android.graphics.drawable.Drawable
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MoviesFragment : Fragment() {

    @Inject lateinit var sharedViewPool: RecyclerView.RecycledViewPool
    @Inject lateinit var rowPrefetchManager: RowPrefetchManager

    private val viewModel: MoviesViewModel by viewModels()
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
    private lateinit var heroLogoLoader: HeroLogoLoader
    private var heroEnrichmentJob: Job? = null
    private lateinit var heroSyncManager: HeroSyncManager
    private lateinit var rowsDelegate: RowsScreenDelegate

    // Navigation
    private lateinit var navSearch: MaterialButton
    private lateinit var navHome: MaterialButton
    private lateinit var navMovies: MaterialButton
    private lateinit var navTvShows: MaterialButton
    private lateinit var navSettings: MaterialButton

    companion object {
        private const val TAG = "MoviesFragment"
    }
    @Inject lateinit var accentColorCache: AccentColorCache
    @Inject lateinit var tmdbApiService: com.strmr.tv.data.remote.api.TMDBApiService
    @Inject lateinit var contextMenuActionHandler: ContextMenuActionHandler
    @Inject lateinit var traktStatusProvider: TraktStatusProvider
    @Inject lateinit var watchedBadgeManager: WatchedBadgeManager

    private lateinit var contextMenuHelper: ContextMenuHelper

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
        heroSyncManager = HeroSyncManager(viewLifecycleOwner) { content ->
            updateHeroSection(content)
        }
        heroLogoLoader = HeroLogoLoader(
            fragment = this,
            logoView = heroLogo,
            titleView = heroTitle,
            maxWidthRes = R.dimen.hero_logo_max_width,
            maxHeightRes = R.dimen.hero_logo_max_height
        )
        contextMenuHelper = ContextMenuHelper(
            context = requireContext(),
            lifecycleScope = viewLifecycleOwner.lifecycleScope,
            actionHandler = contextMenuActionHandler,
            traktStatusProvider = traktStatusProvider,
            onWatchedStateChanged = { tmdbId, type, isWatched ->
                viewLifecycleOwner.lifecycleScope.launch {
                    watchedBadgeManager.notifyWatchedStateChanged(tmdbId, type, isWatched)
                }
            }
        )
        rowsDelegate = RowsScreenDelegate(
            fragment = this,
            lifecycleOwner = viewLifecycleOwner,
            navButtons = RowsScreenDelegate.NavButtons(
                search = navSearch,
                home = navHome,
                movies = navMovies,
                tvShows = navTvShows,
                settings = navSettings
            ),
            defaultSection = RowsScreenDelegate.NavTarget.MOVIES,
            contentRowsView = contentRowsView,
            loadingIndicator = loadingIndicator,
            sharedViewPool = sharedViewPool,
            rowPrefetchManager = rowPrefetchManager,
            accentColorCache = accentColorCache,
            heroSyncManager = heroSyncManager,
            onItemClick = { item, posterView -> handleItemClick(item, posterView) },
            onItemLongPress = { item -> contextMenuHelper.showContextMenu(item) },
            onRequestMore = { rowIndex -> viewModel.requestNextPage(rowIndex) },
            onNavigate = { section ->
                when (section) {
                    RowsScreenDelegate.NavTarget.SEARCH -> (activity as? MainActivity)?.navigateToSection(MainActivity.Section.SEARCH)
                    RowsScreenDelegate.NavTarget.HOME -> (activity as? MainActivity)?.navigateToSection(MainActivity.Section.HOME)
                    RowsScreenDelegate.NavTarget.MOVIES -> {
                        // Already here
                    }
                    RowsScreenDelegate.NavTarget.TV_SHOWS -> (activity as? MainActivity)?.navigateToSection(MainActivity.Section.TV_SHOWS)
                    RowsScreenDelegate.NavTarget.SETTINGS -> {
                        val intent = Intent(requireContext(), com.strmr.tv.ui.settings.SettingsActivity::class.java)
                        startActivity(intent)
                    }
                }
            },
            coroutineScope = viewLifecycleOwner.lifecycleScope
        )
        rowsDelegate.bind(
            contentRows = viewModel.contentRows,
            rowAppendEvents = viewModel.rowAppendEvents,
            isLoading = viewModel.isLoading,
            error = viewModel.error,
            heroContent = viewModel.heroContent
        )

        // Subscribe to badge updates for immediate UI refresh
        viewLifecycleOwner.lifecycleScope.launch {
            watchedBadgeManager.badgeUpdates.collectLatest { update ->
                rowsDelegate.updateBadgeForItem(update.tmdbId)
            }
        }
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

    private fun updateHeroSection(item: ContentItem) {
        Log.d(TAG, "Updating hero section with: ${item.title}")

        heroBackgroundController.updateBackdrop(
            backdropUrl = item.backdropUrl ?: item.posterUrl,
            fallbackDrawable = requireContext().getDrawable(R.drawable.default_background)
        )

        // Update text content
        heroTitle.text = item.title
        val overview = HeroSectionHelper.buildOverviewText(item)
        heroOverview.text = overview ?: ""
        updateHeroLogo(item.logoUrl)
        HeroSectionHelper.updateGenres(heroGenreText, item.genres)
        HeroSectionHelper.updateHeroMetadata(heroMetadata, item)
        HeroSectionHelper.updateCast(heroCast, item.cast)
        ensureHeroExtras(item)
    }

    private fun updateHeroLogo(logoUrl: String?) {
        heroLogoLoader.load(logoUrl)
    }

    private fun handleItemClick(item: ContentItem, posterView: ImageView) {
        Log.d(TAG, "Item clicked: ${item.title}")

        val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
            putExtra(DetailsActivity.CONTENT_ITEM, item)
        }
        posterView.transitionName = DetailsActivity.SHARED_ELEMENT_NAME
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            requireActivity(),
            posterView,
            DetailsActivity.SHARED_ELEMENT_NAME
        )
        startActivity(intent, options.toBundle())
    }

    private fun ensureHeroExtras(item: ContentItem) {
        if (!item.cast.isNullOrBlank() && !item.genres.isNullOrBlank()) return
        heroEnrichmentJob = HeroExtrasLoader.load(
            scope = viewLifecycleOwner.lifecycleScope,
            existingJob = heroEnrichmentJob,
            item = item,
            tmdbApiService = tmdbApiService
        ) { enrichedItem ->
            HeroSectionHelper.updateGenres(heroGenreText, enrichedItem.genres)
            HeroSectionHelper.updateCast(heroCast, enrichedItem.cast)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.cleanupCache()
        heroLogoLoader.cancel()
    }
}
