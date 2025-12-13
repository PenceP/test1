package com.strmr.tv.ui.actor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.strmr.tv.ActorDetailsActivity
import com.strmr.tv.BuildConfig
import com.strmr.tv.DetailsActivity
import com.strmr.tv.R
import com.strmr.tv.data.model.ContentItem
import com.strmr.tv.databinding.FragmentActorDetailsBinding
import com.strmr.tv.ui.HeroSectionHelper
import com.strmr.tv.ui.HeroBackgroundController
import com.strmr.tv.ui.HeroLogoLoader
import com.strmr.tv.ui.AccentColorCache
import com.strmr.tv.ui.adapter.ContentRow
import com.strmr.tv.ui.adapter.ContentRowAdapter
import com.strmr.tv.ui.adapter.RowPresentation
import com.strmr.tv.ui.WatchedBadgeManager
import com.strmr.tv.ui.contextmenu.ContextMenuActionHandler
import com.strmr.tv.ui.contextmenu.ContextMenuHelper
import com.strmr.tv.ui.contextmenu.shouldShowContextMenu
import com.strmr.tv.data.repository.TraktStatusProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ActorDetailsFragment : Fragment() {

    private var _binding: FragmentActorDetailsBinding? = null
    private val binding get() = _binding!!

    private var personId: Int = -1
    private var personName: String? = null

    private lateinit var heroBackgroundController: HeroBackgroundController
    private lateinit var heroLogoLoader: HeroLogoLoader

    private var rowsAdapter: ContentRowAdapter? = null
    private var heroUpdateJob: Job? = null
    private var heroSequence = 0
    private var currentHeroKey: Pair<Int, ContentItem.ContentType>? = null
    private var pendingHeroKey: Pair<Int, ContentItem.ContentType>? = null

    @Inject lateinit var tmdbApiService: com.strmr.tv.data.remote.api.TMDBApiService
    @Inject lateinit var accentColorCache: AccentColorCache
    @Inject lateinit var contextMenuActionHandler: ContextMenuActionHandler
    @Inject lateinit var traktStatusProvider: TraktStatusProvider
    @Inject lateinit var watchedBadgeManager: WatchedBadgeManager

    private lateinit var contextMenuHelper: ContextMenuHelper

    companion object {
        private const val TAG = "ActorDetailsFragment"
        private val DEFAULT_AMBIENT_COLOR = android.graphics.Color.parseColor("#0A0F1F")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        personId = arguments?.getInt(ActorDetailsActivity.PERSON_ID)
            ?: activity?.intent?.getIntExtra(ActorDetailsActivity.PERSON_ID, -1) ?: -1
        personName = arguments?.getString(ActorDetailsActivity.PERSON_NAME)
            ?: activity?.intent?.getStringExtra(ActorDetailsActivity.PERSON_NAME)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActorDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        heroBackgroundController = HeroBackgroundController(
            fragment = this,
            backdropView = binding.heroBackdrop,
            ambientOverlay = binding.ambientBackgroundOverlay,
            defaultAmbientColor = DEFAULT_AMBIENT_COLOR
        )
        heroBackgroundController.updateBackdrop(null, ContextCompat.getDrawable(requireContext(), R.drawable.default_background))
        heroLogoLoader = HeroLogoLoader(
            fragment = this,
            logoView = binding.heroLogo,
            titleView = binding.heroTitle,
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

        // Subscribe to badge updates for immediate UI refresh
        viewLifecycleOwner.lifecycleScope.launch {
            watchedBadgeManager.badgeUpdates.collectLatest { update ->
                rowsAdapter?.updateBadgeForItem(update.tmdbId)
            }
        }

        setupContentRows()

        if (personId != -1) {
            loadActorDetails(personId)
        } else {
            Toast.makeText(requireContext(), "Invalid actor ID", Toast.LENGTH_SHORT).show()
            activity?.finish()
        }
    }

    private fun setupContentRows() {
        // Configure vertical grid for rows
        binding.contentRows.setNumColumns(1)
        binding.contentRows.setItemSpacing(3)

        // Enable smooth scrolling with fixed row heights
        binding.contentRows.setHasFixedSize(true)
        binding.contentRows.setFocusScrollStrategy(VerticalGridView.FOCUS_SCROLL_ALIGNED)

        // Set window alignment for fixed focus at top with proper offset
        binding.contentRows.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_LOW_EDGE)
        binding.contentRows.setWindowAlignmentOffset(0)
        binding.contentRows.setWindowAlignmentOffsetPercent(VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED)

        // Set item alignment to prevent rows from being cut off
        binding.contentRows.setItemAlignmentOffset(0)
        binding.contentRows.setItemAlignmentOffsetPercent(VerticalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED)
    }

    private fun loadActorDetails(personId: Int) {
        binding.loadingIndicator.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val personDetails = withContext(Dispatchers.IO) {
                    tmdbApiService.getPersonDetails(
                        personId = personId,
                        apiKey = BuildConfig.TMDB_API_KEY
                    )
                }

                // Convert movie credits to ContentItems and sort by year (newest first)
                val movieItems = personDetails.movieCredits?.cast
                    ?.filter { it.posterPath != null }
                    ?.map { movie ->
                        ContentItem(
                            id = movie.id,
                            tmdbId = movie.id,
                            title = movie.title,
                            overview = movie.overview,
                            posterUrl = movie.getPosterUrl(),
                            backdropUrl = movie.getBackdropUrl(),
                            logoUrl = null,
                            year = movie.getYear(),
                            rating = movie.voteAverage,
                            ratingPercentage = movie.voteAverage?.times(10)?.toInt(),
                            genres = null,
                            type = ContentItem.ContentType.MOVIE,
                            runtime = null,
                            cast = null,
                            certification = null,
                            imdbId = null,
                            imdbRating = null,
                            rottenTomatoesRating = null,
                            traktRating = null
                        )
                    }
                    ?.sortedByDescending { it.year?.toIntOrNull() ?: 0 }
                    ?: emptyList()

                // Convert TV credits to ContentItems and sort by year (newest first)
                val tvItems = personDetails.tvCredits?.cast
                    ?.filter { it.posterPath != null }
                    ?.map { show ->
                        ContentItem(
                            id = show.id,
                            tmdbId = show.id,
                            title = show.name,
                            overview = show.overview,
                            posterUrl = show.getPosterUrl(),
                            backdropUrl = show.getBackdropUrl(),
                            logoUrl = null,
                            year = show.getYear(),
                            rating = show.voteAverage,
                            ratingPercentage = show.voteAverage?.times(10)?.toInt(),
                            genres = null,
                            type = ContentItem.ContentType.TV_SHOW,
                            runtime = null,
                            cast = null,
                            certification = null,
                            imdbId = null,
                            imdbRating = null,
                            rottenTomatoesRating = null,
                            traktRating = null
                        )
                    }
                    ?.sortedByDescending { it.year?.toIntOrNull() ?: 0 }
                    ?: emptyList()

                // Create rows
                val rows = mutableListOf<ContentRow>()
                if (movieItems.isNotEmpty()) {
                    rows.add(ContentRow("${personName}'s Movies", movieItems.toMutableList(), RowPresentation.PORTRAIT))
                }
                if (tvItems.isNotEmpty()) {
                    rows.add(ContentRow("${personName}'s TV Shows", tvItems.toMutableList(), RowPresentation.PORTRAIT))
                }

                // Set up adapter
                rowsAdapter = ContentRowAdapter(
                    initialRows = rows,
                    onItemClick = { item, imageView ->
                        handleItemClick(item, imageView)
                    },
                    onItemFocused = { item, rowIndex, itemIndex ->
                        handleItemFocused(item, rowIndex, itemIndex)
                    },
                    onNavigateToNavBar = {
                        // No nav bar in actor details
                    },
                    onItemLongPress = { item ->
                        if (item.shouldShowContextMenu()) {
                            contextMenuHelper.showContextMenu(item)
                        }
                    },
                    onRequestMore = { rowIndex ->
                        // No pagination for actor details
                    },
                    viewPool = null,
                    accentColorCache = accentColorCache,
                    coroutineScope = viewLifecycleOwner.lifecycleScope
                )
                binding.contentRows.adapter = rowsAdapter

                // Set initial hero content to first item
                val firstItem = movieItems.firstOrNull() ?: tvItems.firstOrNull()
                firstItem?.let { updateHeroSection(it) }

                binding.loadingIndicator.visibility = View.GONE

                // Request focus on content rows
                binding.contentRows.post {
                    binding.contentRows.requestFocus()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading actor details", e)
                binding.loadingIndicator.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Failed to load actor details: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                activity?.finish()
            }
        }
    }

    private fun updateHeroSection(item: ContentItem) {
        val key = item.tmdbId to item.type
        if (currentHeroKey == key) return
        Log.d(TAG, "Updating hero section with: ${item.title}")
        renderHero(item)
        loadBackdropAndPalette(item)
        heroUpdateJob?.cancel()
        currentHeroKey = key
        val sequence = ++heroSequence
        heroUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            fetchDetailedHero(item)?.let { detailed ->
                if (!isActive || sequence != heroSequence) return@launch
                renderHero(detailed)
                loadBackdropAndPalette(detailed)
            }
        }
    }

    private fun loadBackdropAndPalette(item: ContentItem) {
        val heroImageUrl = item.backdropUrl ?: item.posterUrl
        heroBackgroundController.updateBackdrop(
            backdropUrl = heroImageUrl,
            fallbackDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.default_background)
        )
    }

    private suspend fun fetchDetailedHero(item: ContentItem): ContentItem? = withContext(Dispatchers.IO) {
        runCatching {
            when (item.type) {
                ContentItem.ContentType.MOVIE -> {
                    val details = tmdbApiService.getMovieDetails(
                        movieId = item.tmdbId,
                        apiKey = BuildConfig.TMDB_API_KEY,
                        appendToResponse = "credits,images"
                    )
                    item.copy(
                        backdropUrl = details.getBackdropUrl() ?: item.backdropUrl,
                        logoUrl = details.getLogoUrl() ?: item.logoUrl,
                        genres = details.genres?.joinToString(", ") { it.name },
                        cast = details.getCastNames(),
                        rating = details.voteAverage ?: item.rating,
                        ratingPercentage = details.getRatingPercentage() ?: item.ratingPercentage,
                        year = details.getYear() ?: item.year,
                        certification = details.getCertification()
                    )
                }
                ContentItem.ContentType.TV_SHOW -> {
                    val details = tmdbApiService.getShowDetails(
                        showId = item.tmdbId,
                        apiKey = BuildConfig.TMDB_API_KEY,
                        appendToResponse = "credits,images,content_ratings"
                    )
                    item.copy(
                        backdropUrl = details.getBackdropUrl() ?: item.backdropUrl,
                        logoUrl = details.getLogoUrl() ?: item.logoUrl,
                        genres = details.genres?.joinToString(", ") { it.name },
                        cast = details.getCastNames(),
                        rating = details.voteAverage ?: item.rating,
                        ratingPercentage = details.getRatingPercentage() ?: item.ratingPercentage,
                        year = details.getYear() ?: item.year,
                        certification = details.getCertification()
                    )
                }
            }
        }.getOrElse {
            Log.w(TAG, "Failed to enrich hero details for ${item.title}", it)
            null
        }
    }

    private fun renderHero(item: ContentItem) {
        binding.heroTitle.text = item.title
        val overview = HeroSectionHelper.buildOverviewText(item)
        binding.heroOverview.text = overview ?: ""
        HeroSectionHelper.updateHeroMetadata(binding.heroMetadata, item)
        updateHeroLogo(item.logoUrl)
        HeroSectionHelper.updateGenres(binding.heroGenreText, item.genres)
        HeroSectionHelper.updateCast(binding.heroCast, item.cast)
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

    private fun handleItemFocused(item: ContentItem, rowIndex: Int, itemIndex: Int) {
        Log.d(TAG, "Item focused: ${item.title} at row $rowIndex, position $itemIndex")

        val key = item.tmdbId to item.type
        if (pendingHeroKey == key) return
        pendingHeroKey = key
        heroUpdateJob?.cancel()
        heroUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(250)
            if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                updateHeroSection(item)
            }
            pendingHeroKey = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        heroUpdateJob?.cancel()
        heroUpdateJob = null
        heroLogoLoader.cancel()
    }
}
