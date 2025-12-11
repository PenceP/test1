package com.test1.tv.ui.details

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.leanback.widget.HorizontalGridView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import android.widget.ImageButton
import com.google.android.material.button.MaterialButton
import androidx.recyclerview.widget.RecyclerView
import com.test1.tv.ui.HeroLogoLoader
import java.text.SimpleDateFormat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.test1.tv.ActorDetailsActivity
import com.test1.tv.BuildConfig
import com.test1.tv.DetailsActivity
import com.test1.tv.Movie
import com.test1.tv.R
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.model.tmdb.TMDBCast
import com.test1.tv.data.model.tmdb.TMDBCollection
import com.test1.tv.data.model.tmdb.TMDBMovie
import com.test1.tv.data.model.tmdb.TMDBEpisode
import com.test1.tv.data.model.tmdb.TMDBSeason
import com.test1.tv.data.model.trakt.TraktShowProgress
import com.test1.tv.ui.HeroSectionHelper
import com.test1.tv.ui.HeroBackgroundController
import com.test1.tv.ui.adapter.PersonAdapter
import com.test1.tv.ui.adapter.PosterAdapter
import com.test1.tv.ui.AccentColorCache
import com.test1.tv.ui.WatchedBadgeManager
import com.test1.tv.ui.contextmenu.ContextMenuActionHandler
import com.test1.tv.ui.contextmenu.ContextMenuHelper
import com.test1.tv.ui.contextmenu.shouldShowContextMenu
import com.test1.tv.data.repository.TraktStatusProvider
import java.io.Serializable
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.util.Locale
import androidx.core.widget.NestedScrollView
import java.util.Date
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.leanback.widget.BaseGridView
import com.test1.tv.ui.SmartRowScrollManager
import com.test1.tv.ui.SmartScrollThrottler
import android.transition.TransitionManager
import com.test1.tv.ui.sources.SourcesActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DetailsFragment : Fragment() {

    private var contentItem: ContentItem? = null

    private lateinit var backdrop: ImageView
    private lateinit var ambientOverlay: View
    private lateinit var logo: ImageView
    private lateinit var title: TextView
    private lateinit var detailsMetadata: TextView
    private lateinit var detailsGenreText: TextView
    private lateinit var overview: TextView
    private lateinit var detailsCast: TextView
    private lateinit var ratingContainer: LinearLayout
    private lateinit var ratingImdb: View
    private lateinit var ratingImdbValue: TextView
    private lateinit var ratingRotten: View
    private lateinit var ratingRottenValue: TextView
    private lateinit var ratingTmdb: View
    private lateinit var ratingTmdbValue: TextView
    private lateinit var ratingTrakt: View
    private lateinit var ratingTraktValue: TextView

    private lateinit var buttonPlay: MaterialButton
    private lateinit var buttonTrailer: MaterialButton
    private lateinit var buttonWatched: MaterialButton
    private lateinit var buttonMore: MaterialButton

    private lateinit var castSection: LinearLayout
    private lateinit var similarSection: LinearLayout
    private lateinit var collectionSection: LinearLayout
    private lateinit var castSectionTitle: TextView
    private lateinit var similarSectionTitle: TextView
    private lateinit var collectionSectionTitle: TextView
    private lateinit var castRow: HorizontalGridView
    private lateinit var similarRow: HorizontalGridView
    private lateinit var collectionRow: HorizontalGridView
    private lateinit var castEmpty: TextView
    private lateinit var similarEmpty: TextView
    private lateinit var collectionEmpty: TextView
    private lateinit var detailsScroll: NestedScrollView
    private lateinit var tvShowSection: LinearLayout
    private lateinit var seasonRow: HorizontalGridView
    private lateinit var episodeRow: HorizontalGridView
    private lateinit var rowsContainer: LinearLayout
    private lateinit var episodeInfoShelf: LinearLayout
    private lateinit var shelfEpisodeTitle: TextView
    private lateinit var shelfEpisodeOverview: TextView
    private val scrollThrottler = SmartScrollThrottler(repeatDelayMs = 120L)

    private var seasonAdapter: com.test1.tv.ui.details.SeasonAdapter? = null
    private var episodeAdapter: com.test1.tv.ui.details.EpisodeAdapter? = null
    @Inject lateinit var accentColorCache: AccentColorCache
    @Inject lateinit var rateLimiter: com.test1.tv.data.remote.RateLimiter
    @Inject lateinit var tmdbApiService: com.test1.tv.data.remote.api.TMDBApiService
    @Inject lateinit var traktApiService: com.test1.tv.data.remote.api.TraktApiService
    @Inject lateinit var contextMenuActionHandler: ContextMenuActionHandler
    @Inject lateinit var traktStatusProvider: TraktStatusProvider
    @Inject lateinit var watchedBadgeManager: WatchedBadgeManager
    @Inject lateinit var traktSyncRepository: com.test1.tv.data.repository.TraktSyncRepository
    @Inject lateinit var traktAccountRepository: com.test1.tv.data.repository.TraktAccountRepository

    private lateinit var contextMenuHelper: ContextMenuHelper
    private var episodeContextMenuHelper: EpisodeContextMenuHelper? = null

    // Show progress for episode watched status
    private var showProgress: com.test1.tv.data.model.trakt.TraktShowProgress? = null
    private var currentShowTmdbId: Int? = null
    // Local set to track watched episodes (persists across season changes)
    private val localWatchedEpisodes = mutableSetOf<String>()
    // Episode playback progress (in-progress episodes with 5%-90% watched)
    private var episodePlaybackProgress: Map<String, Float> = emptyMap()

    private var showTitleOriginal: String? = null
    private var showMetadataOriginal: CharSequence? = null
    private var showOverviewOriginal: String? = null

    // Trailer YouTube key
    private var trailerKey: String? = null

    // Movie playback progress for resume functionality
    private var moviePlaybackProgress: com.test1.tv.data.repository.MoviePlaybackProgress? = null
    private var movieRuntimeMinutes: Int? = null

    // Currently selected season/episode for Play button
    private var currentSeasonNumber: Int = 1
    private var currentEpisodeNumber: Int = 1

    private var isWatched = false
    private var isInCollection = false
    private var currentRating: Int = 0 // 0 = none, 1 = thumbs up, 2 = thumbs down
    private lateinit var heroBackgroundController: HeroBackgroundController
    private lateinit var heroLogoLoader: HeroLogoLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contentItem = arguments.parcelableCompat(ARG_CONTENT_ITEM)
            ?: activity?.intent.parcelableExtraCompat(DetailsActivity.CONTENT_ITEM)

        if (contentItem == null) {
            val legacyMovie = arguments.serializableCompat<Movie>(ARG_MOVIE)
                ?: activity?.intent.serializableExtraCompat<Movie>(DetailsActivity.MOVIE)
            legacyMovie?.let { contentItem = it.toContentItem() }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)

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

        episodeContextMenuHelper = EpisodeContextMenuHelper(
            context = requireContext(),
            lifecycleScope = viewLifecycleOwner.lifecycleScope,
            traktAccountRepository = traktAccountRepository,
            traktSyncRepository = traktSyncRepository,
            onEpisodeWatchedStateChanged = { seasonNumber, episodeNumber, isWatched ->
                // Update local tracking set for persistence across season changes
                val episodeKey = "S${seasonNumber}E${episodeNumber}"
                if (isWatched) {
                    localWatchedEpisodes.add(episodeKey)
                } else {
                    localWatchedEpisodes.remove(episodeKey)
                }
                episodeAdapter?.updateEpisodeWatchedStatus(seasonNumber, episodeNumber, isWatched)
            },
            onSeasonWatchedStateChanged = { seasonNumber, isWatched ->
                // Update local tracking set for all episodes in season
                episodeAdapter?.let { adapter ->
                    for (i in 0 until adapter.itemCount) {
                        adapter.getEpisode(i)?.let { episode ->
                            if (episode.seasonNumber == seasonNumber) {
                                val episodeKey = "S${episode.seasonNumber}E${episode.episodeNumber}"
                                if (isWatched) {
                                    localWatchedEpisodes.add(episodeKey)
                                } else {
                                    localWatchedEpisodes.remove(episodeKey)
                                }
                            }
                        }
                    }
                }
                episodeAdapter?.updateSeasonWatchedStatus(seasonNumber, isWatched)
            }
        )

        // Subscribe to badge updates for immediate UI refresh
        viewLifecycleOwner.lifecycleScope.launch {
            watchedBadgeManager.badgeUpdates.collectLatest { update ->
                (similarRow.adapter as? PosterAdapter)?.updateBadgeForItem(update.tmdbId)
                (collectionRow.adapter as? PosterAdapter)?.updateBadgeForItem(update.tmdbId)
            }
        }

        contentItem?.let { bindContent(it) } ?: showMissingContent()
    }

    private fun initViews(view: View) {
        detailsScroll = view.findViewById(R.id.details_scroll)
        backdrop = view.findViewById(R.id.details_backdrop)
        ambientOverlay = view.findViewById(R.id.ambient_background_overlay)
        logo = view.findViewById(R.id.details_logo)
        title = view.findViewById(R.id.details_title)
        detailsMetadata = view.findViewById(R.id.details_metadata)
        detailsGenreText = view.findViewById(R.id.details_genre_text)
        overview = view.findViewById(R.id.details_overview)
        detailsCast = view.findViewById(R.id.details_cast)

        buttonPlay = view.findViewById(R.id.button_play)
        buttonTrailer = view.findViewById(R.id.button_trailer)
        buttonWatched = view.findViewById(R.id.button_watched)
        buttonMore = view.findViewById(R.id.button_more)
        heroBackgroundController = HeroBackgroundController(
            fragment = this,
            backdropView = backdrop,
            ambientOverlay = ambientOverlay,
            defaultAmbientColor = DEFAULT_AMBIENT_COLOR
        )
        heroBackgroundController.updateBackdrop(null, resources.getDrawable(R.drawable.default_background, null))
        heroLogoLoader = HeroLogoLoader(
            fragment = this,
            logoView = logo,
            titleView = title,
            maxWidthRes = R.dimen.hero_logo_max_width,
            maxHeightRes = R.dimen.hero_logo_max_height
        )

        castSection = view.findViewById(R.id.cast_section)
        similarSection = view.findViewById(R.id.similar_section)
        collectionSection = view.findViewById(R.id.collection_section)
        castSectionTitle = view.findViewById(R.id.cast_section_title)
        similarSectionTitle = view.findViewById(R.id.similar_section_title)
        collectionSectionTitle = view.findViewById(R.id.collection_section_title)
        castRow = view.findViewById(R.id.cast_row)
        similarRow = view.findViewById(R.id.similar_row)
        collectionRow = view.findViewById(R.id.collection_row)
        castEmpty = view.findViewById(R.id.cast_empty_text)
        similarEmpty = view.findViewById(R.id.similar_empty_text)
        collectionEmpty = view.findViewById(R.id.collection_empty_text)
        tvShowSection = view.findViewById(R.id.tv_show_section)
        seasonRow = view.findViewById(R.id.season_row)
        episodeRow = view.findViewById(R.id.episode_row)
        rowsContainer = view.findViewById(R.id.rows_container)
        episodeInfoShelf = view.findViewById(R.id.episode_info_shelf)
        shelfEpisodeTitle = view.findViewById(R.id.shelf_episode_title)
        shelfEpisodeOverview = view.findViewById(R.id.shelf_episode_overview)

        buttonPlay.setOnClickListener {
            launchSources()
        }

        buttonTrailer.setOnClickListener {
            openTrailer()
        }

        buttonWatched.setOnClickListener {
            toggleWatchedStatus()
        }

        buttonMore.setOnClickListener {
            showMoreOptionsMenu()
        }

        // Setup button focus handlers
        setupButtons()

        updateWatchedButton()

        // Request focus on Play button by default
        buttonPlay.post {
            buttonPlay.requestFocus()
        }
    }

    private fun setupButtons() {
        // Setup Play button (no expanding, just scale animation)
        setupPlayButtonFocus(buttonPlay)

        // Setup secondary buttons with expanding pill animation
        val secondaryButtons = listOf(
            buttonTrailer to "Trailer",
            buttonWatched to if (isWatched) "Unwatched" else "Watched",
            buttonMore to "More"
        )

        secondaryButtons.forEach { (button, labelText) ->
            setupExpandingPillButton(button, labelText)
        }
    }

    private fun setupPlayButtonFocus(button: MaterialButton) {
        button.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                // Animate button scale on focus
                view.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(150)
                    .start()
            } else {
                // Animate button back to normal
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start()
            }
        }
    }

    private fun setupExpandingPillButton(button: MaterialButton, labelText: String) {
        button.text = "" // Start empty

        button.setOnFocusChangeListener { view, hasFocus ->
            val materialButton = view as MaterialButton
            val parent = materialButton.parent as? ViewGroup

            // 2. Tell the parent layout to animate the next change
            parent?.let {
                val transition = android.transition.AutoTransition()
                transition.duration = 200 // Milliseconds
                transition.ordering = android.transition.TransitionSet.ORDERING_TOGETHER
                android.transition.TransitionManager.beginDelayedTransition(it, transition)
            }

            // 3. MODIFY THE WIDTH
            val params = materialButton.layoutParams

            if (hasFocus) {
                // STATE: EXPANDED
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT // Let it grow!
                materialButton.text = labelText
                materialButton.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                materialButton.iconPadding = (12 * resources.displayMetrics.density).toInt() // 12dp spacing between icon and text
                val horizontalPadding = (16 * resources.displayMetrics.density).toInt() // 16dp horizontal padding
                materialButton.setPadding(horizontalPadding, materialButton.paddingTop, horizontalPadding, materialButton.paddingBottom)
                materialButton.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.black))
                materialButton.iconTint = ColorStateList.valueOf(Color.BLACK)
            } else {
                // STATE: COLLAPSED - Make it a perfect circle
                params.width = params.height // Set width to match height for perfect circle
                materialButton.text = ""
                // Reset iconGravity to match XML default (textStart) which centers better when no text
                materialButton.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                materialButton.iconPadding = 0 // No padding - centers the icon
                // Remove all padding to let the icon center naturally in the circular button
                materialButton.setPadding(0, 0, 0, 0)
                materialButton.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.white))
                materialButton.iconTint = ColorStateList.valueOf(Color.WHITE)
            }

            // Apply the new width
            materialButton.layoutParams = params
        }

        // Set initial width to match height for perfect circle (before any focus happens)
        button.post {
            val params = button.layoutParams
            params.width = params.height
            button.layoutParams = params
            // Remove all padding to let the icon center naturally in the circular button
            button.setPadding(0, 0, 0, 0)
            button.iconPadding = 0
            // Reset iconGravity to match XML default
            button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        heroLogoLoader.cancel()
    }

    private fun bindContent(item: ContentItem) {
        heroBackgroundController.updateBackdrop(
            backdropUrl = item.backdropUrl,
            fallbackDrawable = resources.getDrawable(R.drawable.default_background, null)
        )

        title.text = item.title
        showTitleOriginal = item.title
        val overviewText = HeroSectionHelper.buildOverviewText(item)
        showOverviewOriginal = overviewText?.toString() ?: getString(R.string.details_section_similar_empty)
        showMetadataOriginal = HeroSectionHelper.buildMetadataLine(item)
        overview.text = overviewText ?: getString(R.string.details_section_similar_empty)
        overview.visibility = View.VISIBLE

        updateHeroLogo(item.logoUrl)
        HeroSectionHelper.updateHeroMetadata(detailsMetadata, item)
        HeroSectionHelper.updateGenres(detailsGenreText, item.genres)
        HeroSectionHelper.updateCast(detailsCast, item.cast)
        //updateRatingBadges(item)

        // Show row sections
        showRowSections()
    }

    private fun showRowSections() {
        // Show People section (cast/crew)
        castSection.visibility = View.VISIBLE

        // Show Similar section
        similarSection.visibility = View.VISIBLE

        // Hide Collection section for now (will show when collection data is available)
        collectionSection.visibility = View.GONE

        // Fetch and populate row data
        contentItem?.let { fetchDetailsData(it) }
    }

    private fun fetchDetailsData(item: ContentItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (item.type) {
                    ContentItem.ContentType.MOVIE -> {
                        tvShowSection.visibility = View.GONE
                        fetchMovieDetails(item.tmdbId)
                    }
                    ContentItem.ContentType.TV_SHOW -> {
                        fetchShowDetails(item.tmdbId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching details data", e)
                showEmptyStates()
            }
        }
    }

    private suspend fun fetchMovieDetails(tmdbId: Int) = withContext(Dispatchers.IO) {
        try {
            // Fetch movie details with credits
            rateLimiter.acquire()
            val movieDetails = tmdbApiService.getMovieDetails(
                movieId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY
            )

            // Store runtime for progress calculation
            movieRuntimeMinutes = movieDetails.runtime

            val relatedItems = fetchRelatedMovies(
                resolveImdbId(
                    primary = movieDetails.imdbId,
                    external = movieDetails.externalIds?.imdbId,
                    trakt = null
                )
            )

            // Fetch collection if exists
            val collectionMovies = movieDetails.belongsToCollection?.let { collection ->
                rateLimiter.acquire()
                tmdbApiService.getCollectionDetails(
                    collectionId = collection.id,
                    apiKey = BuildConfig.TMDB_API_KEY
                )
            }

            // Fetch playback progress from Trakt (for resume functionality)
            val playbackProgress = traktSyncRepository.getMoviePlaybackProgress(tmdbId)

            withContext(Dispatchers.Main) {
                // Store playback progress
                moviePlaybackProgress = playbackProgress

                // Update Play button if we have progress
                updatePlayButtonForMovie()

                // Extract trailer key
                trailerKey = movieDetails.getTrailerKey()
                Log.d(TAG, "Movie trailer key: $trailerKey")

                // Populate People row
                populateCastRow(movieDetails.credits?.cast)

                // Populate Similar row
                populateSimilarRowWithItems(relatedItems)

                // Populate Collection row if exists
                if (collectionMovies != null) {
                    populateCollectionRow(
                        movieDetails.belongsToCollection.name,
                        collectionMovies.parts
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching movie details", e)
            withContext(Dispatchers.Main) {
                showEmptyStates()
            }
        }
    }

    private suspend fun fetchShowDetails(tmdbId: Int) = withContext(Dispatchers.IO) {
        try {
            // Fetch show details with credits
            rateLimiter.acquire()
            val showDetails = tmdbApiService.getShowDetails(
                showId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY
            )

            val relatedItems = fetchRelatedShows(showDetails.externalIds?.imdbId)

            withContext(Dispatchers.Main) {
                // Extract trailer key
                trailerKey = showDetails.getTrailerKey()
                Log.d(TAG, "Show trailer key: $trailerKey")

                // Populate People row
                populateCastRow(showDetails.credits?.cast)

                // Populate Similar row using Trakt related list
                populateSimilarRowWithItems(relatedItems)

                // Populate seasons/episodes
                val orderedSeasons = showDetails.seasons
                    ?.filter { (it.seasonNumber ?: 0) > 0 }
                    ?.sortedBy { it.seasonNumber }
                populateSeasonsAndEpisodes(tmdbShowId = tmdbId, seasons = orderedSeasons.orEmpty())

                // TV shows don't have collections
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching show details", e)
            withContext(Dispatchers.Main) {
                showEmptyStates()
            }
        }
    }

    private suspend fun fetchRelatedMovies(imdbId: String?): List<ContentItem> = coroutineScope {
        if (imdbId.isNullOrBlank()) {
            Log.w(TAG, "Cannot fetch related movies without imdb id")
            return@coroutineScope emptyList()
        }

        val relatedMovies = runCatching {
            traktApiService.getRelatedMovies(
                movieId = imdbId,
                clientId = BuildConfig.TRAKT_CLIENT_ID
            )
        }.getOrElse {
            Log.e(TAG, "Error fetching related movies from Trakt", it)
            return@coroutineScope emptyList()
        }

        val jobs = relatedMovies
            .take(20)
            .mapNotNull { traktMovie ->
                val relatedTmdbId = traktMovie.ids.tmdb
                if (relatedTmdbId == null) {
                    Log.w(TAG, "Skipping related movie without TMDB id: ${traktMovie.title}")
                    return@mapNotNull null
                }

                async(Dispatchers.IO) {
                    runCatching {
                        rateLimiter.acquire()  // Wait for rate limit token
                        tmdbApiService.getMovieDetails(
                            movieId = relatedTmdbId,
                            apiKey = BuildConfig.TMDB_API_KEY
                        )
                    }.onFailure { error ->
                        Log.w(TAG, "Failed to fetch TMDB details for related movie $relatedTmdbId", error)
                    }.getOrNull()?.let { tmdbDetails ->
                        ContentItem(
                            id = tmdbDetails.id,
                            tmdbId = tmdbDetails.id,
                            imdbId = resolveImdbId(
                                primary = tmdbDetails.imdbId,
                                external = tmdbDetails.externalIds?.imdbId,
                                trakt = traktMovie.ids.imdb
                            ),
                            title = tmdbDetails.title,
                            overview = tmdbDetails.overview,
                            posterUrl = tmdbDetails.getPosterUrl(),
                            backdropUrl = tmdbDetails.getBackdropUrl(),
                            logoUrl = tmdbDetails.getLogoUrl(),
                            year = tmdbDetails.getYear(),
                            rating = tmdbDetails.voteAverage,
                            ratingPercentage = tmdbDetails.getRatingPercentage(),
                            genres = tmdbDetails.genres?.joinToString(", ") { it.name },
                            type = ContentItem.ContentType.MOVIE,
                            runtime = tmdbDetails.runtime?.let { "$it min" },
                            cast = tmdbDetails.getCastNames(),
                            certification = tmdbDetails.getCertification(),
                            imdbRating = null,
                            rottenTomatoesRating = null,
                            traktRating = traktMovie.rating
                        )
                    }
                }
            }

        jobs.awaitAll().filterNotNull()
    }

    private suspend fun fetchRelatedShows(imdbId: String?): List<ContentItem> = coroutineScope {
        if (imdbId.isNullOrBlank()) {
            Log.w(TAG, "Cannot fetch related shows without imdb id")
            return@coroutineScope emptyList()
        }

        val relatedShows = runCatching {
            traktApiService.getRelatedShows(
                showId = imdbId,
                clientId = BuildConfig.TRAKT_CLIENT_ID
            )
        }.getOrElse {
            Log.e(TAG, "Error fetching related shows from Trakt", it)
            return@coroutineScope emptyList()
        }

        val jobs = relatedShows
            .take(20)
            .mapNotNull { traktShow ->
                val relatedTmdbId = traktShow.ids.tmdb
                if (relatedTmdbId == null) {
                    Log.w(TAG, "Skipping related show without TMDB id: ${traktShow.title}")
                    return@mapNotNull null
                }

                async(Dispatchers.IO) {
                    runCatching {
                        rateLimiter.acquire()  // Wait for rate limit token
                        tmdbApiService.getShowDetails(
                            showId = relatedTmdbId,
                            apiKey = BuildConfig.TMDB_API_KEY
                        )
                    }.onFailure { error ->
                        Log.w(TAG, "Failed to fetch TMDB details for related show $relatedTmdbId", error)
                    }.getOrNull()?.let { tmdbDetails ->
                        ContentItem(
                            id = tmdbDetails.id,
                            tmdbId = tmdbDetails.id,
                            imdbId = resolveImdbId(
                                primary = null,
                                external = tmdbDetails.externalIds?.imdbId,
                                trakt = traktShow.ids.imdb
                            ),
                            title = tmdbDetails.name,
                            overview = tmdbDetails.overview,
                            posterUrl = tmdbDetails.getPosterUrl(),
                            backdropUrl = tmdbDetails.getBackdropUrl(),
                            logoUrl = tmdbDetails.getLogoUrl(),
                            year = tmdbDetails.getYear(),
                            rating = tmdbDetails.voteAverage,
                            ratingPercentage = tmdbDetails.getRatingPercentage(),
                            genres = tmdbDetails.genres?.joinToString(", ") { it.name },
                            type = ContentItem.ContentType.TV_SHOW,
                            runtime = tmdbDetails.episodeRunTime?.firstOrNull()?.let { "$it min" },
                            cast = tmdbDetails.getCastNames(),
                            certification = tmdbDetails.getCertification(),
                            imdbRating = null,
                            rottenTomatoesRating = null,
                            traktRating = traktShow.rating
                        )
                    }
                }
            }

        jobs.awaitAll().filterNotNull()
    }

    private fun populateCastRow(cast: List<TMDBCast>?) {
        if (cast.isNullOrEmpty()) {
            castEmpty.visibility = View.VISIBLE
            castRow.visibility = View.GONE
            return
        }

        val sortedCast = cast.sortedBy { it.order ?: Int.MAX_VALUE }.take(20)
        val adapter = PersonAdapter(sortedCast) { person ->
            handlePersonClick(person)
        }

        castRow.adapter = adapter
        castRow.setNumRows(1)
        castRow.setItemSpacing(0)
        castRow.setHasFixedSize(true)
        castRow.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)
        configureFixedFocusRow(castRow)
        SmartRowScrollManager.attach(castRow)
        castRow.setOnKeyInterceptListener(scrollThrottler)

        castEmpty.visibility = View.GONE
        castRow.visibility = View.VISIBLE
    }

    private fun populateSimilarRowWithItems(similarItems: List<ContentItem>?) {
        if (similarItems.isNullOrEmpty()) {
            similarEmpty.visibility = View.VISIBLE
            similarRow.visibility = View.GONE
            return
        }

        val adapter = PosterAdapter(
            onItemClick = { item, _ ->
                DetailsActivity.start(requireContext(), item)
            },
            onItemFocused = { _, _ -> },
            onNavigateToNavBar = { },
            onNearEnd = {},
            onItemLongPressed = { item ->
                if (item.shouldShowContextMenu()) {
                    contextMenuHelper.showContextMenu(item)
                }
            },
            accentColorCache = accentColorCache,
            coroutineScope = viewLifecycleOwner.lifecycleScope
        )
        adapter.submitList(similarItems.take(20))

        similarRow.adapter = adapter
        similarRow.setNumRows(1)
        similarRow.setItemSpacing(0)
        similarRow.setHasFixedSize(true)
        similarRow.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)
        configureFixedFocusRow(similarRow)
        SmartRowScrollManager.attach(similarRow)
        similarRow.setOnKeyInterceptListener(scrollThrottler)

        similarEmpty.visibility = View.GONE
        similarRow.visibility = View.VISIBLE
    }

    private fun populateCollectionRow(collectionName: String, movies: List<TMDBMovie>?) {
        if (movies.isNullOrEmpty()) {
            collectionSection.visibility = View.GONE
            return
        }

        collectionSectionTitle.text = collectionName

        val collectionItems = movies.take(20).map { movie ->
            ContentItem(
                id = movie.id,
                tmdbId = movie.id,
                imdbId = movie.imdbId,
                title = movie.title,
                overview = movie.overview,
                posterUrl = movie.getPosterUrl(),
                backdropUrl = movie.getBackdropUrl(),
                logoUrl = null,
                year = movie.releaseDate?.take(4),
                rating = movie.voteAverage,
                ratingPercentage = movie.voteAverage?.times(10)?.toInt(),
                genres = null,
                type = ContentItem.ContentType.MOVIE,
                runtime = null,
                cast = null,
                certification = null,
                imdbRating = null,
                rottenTomatoesRating = null,
                traktRating = null
            )
        }

        val adapter = PosterAdapter(
            onItemClick = { item, _ ->
                DetailsActivity.start(requireContext(), item)
            },
            onItemFocused = { _, _ -> },
            onNavigateToNavBar = { },
            onNearEnd = {},
            onItemLongPressed = { item ->
                if (item.shouldShowContextMenu()) {
                    contextMenuHelper.showContextMenu(item)
                }
            },
            accentColorCache = accentColorCache,
            coroutineScope = viewLifecycleOwner.lifecycleScope
        )
        adapter.submitList(collectionItems)

        collectionRow.adapter = adapter
        collectionRow.setNumRows(1)
        collectionRow.setItemSpacing(0)
        collectionRow.setHasFixedSize(true)
        collectionRow.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)
        configureFixedFocusRow(collectionRow)
        SmartRowScrollManager.attach(collectionRow)
        collectionRow.setOnKeyInterceptListener(scrollThrottler)

        collectionEmpty.visibility = View.GONE
        collectionRow.visibility = View.VISIBLE
        collectionSection.visibility = View.VISIBLE
    }

    private fun populateSeasonsAndEpisodes(tmdbShowId: Int, seasons: List<TMDBSeason>) {
        if (seasons.isEmpty()) {
            tvShowSection.visibility = View.GONE
            return
        }

        // Store the show ID for context menu operations
        currentShowTmdbId = tmdbShowId
        // Clear local watched episodes when loading a new show
        localWatchedEpisodes.clear()

        tvShowSection.visibility = View.VISIBLE
        seasonRow.visibility = View.VISIBLE

        if (seasonAdapter == null) {
            seasonAdapter = com.test1.tv.ui.details.SeasonAdapter(
                seasons = seasons,
                onSeasonClick = { season, position ->
                    season.seasonNumber?.let {
                        loadEpisodesForSeason(tmdbShowId, it, position)
                        seasonAdapter?.setSelectedPosition(position)
                    }
                },
                onSeasonLongPress = { season, _ ->
                    val seasonNumber = season.seasonNumber ?: return@SeasonAdapter
                    // Check both remote progress and local changes
                    val remoteWatched = showProgress?.isSeasonWatched(seasonNumber) ?: false
                    val localWatched = seasons.find { it.seasonNumber == seasonNumber }?.let { s ->
                        // A season is locally watched if we have entries for all its episodes
                        // For simplicity, check if any episode of this season is in local set
                        localWatchedEpisodes.any { it.startsWith("S${seasonNumber}E") }
                    } ?: false
                    val isSeasonWatched = remoteWatched || localWatched
                    episodeContextMenuHelper?.showSeasonContextMenu(
                        showTmdbId = tmdbShowId,
                        showTitle = contentItem?.title ?: "",
                        season = season,
                        isSeasonWatched = isSeasonWatched
                    )
                }
            )
            seasonRow.adapter = seasonAdapter
            seasonRow.setNumRows(1)
            seasonRow.setItemSpacing(0)
            seasonRow.setHasFixedSize(true)
            seasonRow.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)
            configureFixedFocusRow(seasonRow, itemAlignmentOffset = 30, windowAlignmentOffset = 80)
        }
        // No centering on season row focus; keep shelf hidden when moving away from episodes

        // Fetch show progress and playback data from Trakt, then determine "Next Up" episode
        viewLifecycleOwner.lifecycleScope.launch {
            Log.d(TAG, "Fetching show progress for TMDB ID: $tmdbShowId")
            // Fetch both show progress (watched status) and playback progress (partial progress) in parallel
            val progressDeferred = async(Dispatchers.IO) {
                traktSyncRepository.getShowProgress(tmdbShowId)
            }
            val playbackDeferred = async(Dispatchers.IO) {
                traktSyncRepository.getEpisodePlaybackProgress(tmdbShowId)
            }

            showProgress = progressDeferred.await()
            episodePlaybackProgress = playbackDeferred.await()

            // Initialize local set with remote watched episodes
            val remoteWatched = showProgress?.getWatchedEpisodeKeys() ?: emptySet()
            Log.d(TAG, "Received ${remoteWatched.size} watched episodes from Trakt: $remoteWatched")
            Log.d(TAG, "Received ${episodePlaybackProgress.size} in-progress episodes: $episodePlaybackProgress")
            localWatchedEpisodes.addAll(remoteWatched)
            Log.d(TAG, "Local watched episodes set now has ${localWatchedEpisodes.size} items")

            // Smart "Next Up" logic: use Trakt's next episode if available
            val nextEpisode = showProgress?.nextEpisode
            val nextUpSeasonNumber = nextEpisode?.season
            val nextUpEpisodeNumber = nextEpisode?.number

            // Determine initial season: use next up season if available, otherwise first season
            val initialSeasonNumber = nextUpSeasonNumber
                ?: seasonAdapter?.getSelectedSeason()?.seasonNumber
                ?: seasons.firstOrNull()?.seasonNumber

            if (nextEpisode != null) {
                Log.d(TAG, "Smart Next Up: S${nextUpSeasonNumber}E${nextUpEpisodeNumber}")
            } else {
                Log.d(TAG, "No Trakt progress, defaulting to first season")
            }

            // Select the correct season in the adapter
            if (nextUpSeasonNumber != null) {
                val seasonPosition = seasonAdapter?.getPositionForSeasonNumber(nextUpSeasonNumber) ?: -1
                if (seasonPosition >= 0) {
                    seasonAdapter?.setSelectedPosition(seasonPosition)
                    seasonRow.post {
                        seasonRow.scrollToPosition(seasonPosition)
                    }
                }
            }

            // Now load episodes with the fetched progress and optional focus episode
            if (initialSeasonNumber != null) {
                val selectedPosition = if (nextUpSeasonNumber != null) {
                    seasonAdapter?.getPositionForSeasonNumber(nextUpSeasonNumber).takeIf { it != null && it >= 0 }
                        ?: seasonAdapter?.selectedPosition
                        ?: 0
                } else {
                    seasonAdapter?.selectedPosition ?: 0
                }

                loadEpisodesForSeason(
                    showId = tmdbShowId,
                    seasonNumber = initialSeasonNumber,
                    selectedPosition = selectedPosition,
                    focusEpisodeNumber = nextUpEpisodeNumber
                )
            }
        }
    }

    private fun loadEpisodesForSeason(showId: Int, seasonNumber: Int, selectedPosition: Int, focusEpisodeNumber: Int? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val seasonDetails = withContext(Dispatchers.IO) {
                    rateLimiter.acquire()
                    tmdbApiService.getSeasonDetails(
                        showId = showId,
                        seasonNumber = seasonNumber,
                        apiKey = BuildConfig.TMDB_API_KEY
                    )
                }

                val episodes = seasonDetails.episodes
                    ?.sortedBy { it.episodeNumber ?: Int.MAX_VALUE }
                    .orEmpty()

                // Use local watched episodes set (includes both remote and local changes)
                val watchedEpisodes = localWatchedEpisodes.toSet()
                // Use cached episode playback progress for red progress bars
                val progress = episodePlaybackProgress

                episodeAdapter = com.test1.tv.ui.details.EpisodeAdapter(
                    episodes = episodes,
                    showTmdbId = showId,
                    watchedEpisodes = watchedEpisodes,
                    episodeProgress = progress,
                    onEpisodeFocused = { episode -> updateEpisodeShelf(episode) },
                    onEpisodeClick = { episode ->
                        val season = episode.seasonNumber ?: return@EpisodeAdapter
                        val ep = episode.episodeNumber ?: return@EpisodeAdapter
                        contentItem?.let { item ->
                            SourcesActivity.startForEpisode(requireContext(), item, season, ep)
                        }
                    },
                    onEpisodeLongPress = { episode, isWatched ->
                        episodeContextMenuHelper?.showEpisodeContextMenu(
                            showTmdbId = showId,
                            showTitle = contentItem?.title ?: "",
                            episode = episode,
                            isWatched = isWatched
                        )
                    }
                )
                episodeRow.adapter = episodeAdapter
                episodeRow.setNumRows(1)
                episodeRow.setItemSpacing(0)
                episodeRow.setHasFixedSize(true)
                episodeRow.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)
                configureFixedFocusRow(episodeRow, itemAlignmentOffset = 6)
                SmartRowScrollManager.attach(episodeRow)
                episodeRow.setOnKeyInterceptListener(scrollThrottler)

                episodeRow.visibility = if (episodes.isNotEmpty()) View.VISIBLE else View.GONE

                // Determine which episode to focus: use focusEpisodeNumber if provided, otherwise first episode
                val targetEpisodeNumber = focusEpisodeNumber ?: episodes.firstOrNull()?.episodeNumber ?: 1
                updatePlayButtonText(seasonNumber, targetEpisodeNumber)

                if (episodes.isEmpty()) {
                    restoreEpisodeShelf()
                }

        // Hook selection listener for episode info shelf
        episodeRow.setOnChildViewHolderSelectedListener(object : OnChildViewHolderSelectedListener() {
            override fun onChildViewHolderSelected(
                parent: RecyclerView,
                child: RecyclerView.ViewHolder?,
                position: Int,
                subposition: Int
            ) {
                if (child != null && episodeRow.hasFocus()) {
                    val episode = episodeAdapter?.getEpisode(position)
                    updateEpisodeShelf(episode)
                    episodeRow.post { scrollToViewCenter(episodeRow) }
                } else {
                    restoreEpisodeShelf()
                }
            }
        })
        episodeRow.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                episodeRow.post {
                    episodeRow.focusedChild?.let { focused ->
                        val pos = episodeRow.getChildAdapterPosition(focused)
                        val episode = episodeAdapter?.getEpisode(pos)
                        updateEpisodeShelf(episode)
                    }
                    scrollToViewCenter(episodeRow)
                }
            } else {
                restoreEpisodeShelf()
            }
        }

        // Anchor focus/keyline to reduce zig-zag navigation
        episodeRow.windowAlignment = BaseGridView.WINDOW_ALIGN_NO_EDGE
        episodeRow.windowAlignmentOffsetPercent = 1f
        episodeRow.itemAlignmentOffsetPercent = 0f
        episodeRow.isFocusSearchDisabled = false

                // Smart scroll to focus episode if specified
                if (focusEpisodeNumber != null && episodes.isNotEmpty()) {
                    val focusPosition = episodeAdapter?.getPositionForEpisodeNumber(focusEpisodeNumber) ?: -1
                    if (focusPosition >= 0) {
                        episodeRow.post {
                            episodeRow.scrollToPosition(focusPosition)
                            // Update episode shelf with focused episode info
                            val focusedEpisode = episodeAdapter?.getEpisode(focusPosition)
                            if (focusedEpisode != null) {
                                updateEpisodeShelf(focusedEpisode)
                            }
                        }
                        Log.d(TAG, "Smart focus: scrolling to episode $focusEpisodeNumber at position $focusPosition")
                    }
                }

                seasonRow.post {
                    seasonRow.layoutManager?.findViewByPosition(selectedPosition)?.requestFocus()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching season details", e)
            }
        }
    }

    private fun updatePlayButtonText(seasonNumber: Int?, episodeNumber: Int?) {
        if (seasonNumber == null || episodeNumber == null) {
            buttonPlay.text = getString(R.string.details_play)
            return
        }
        // Track current season/episode for Play button
        currentSeasonNumber = seasonNumber
        currentEpisodeNumber = episodeNumber
        buttonPlay.text = "${getString(R.string.details_play)} S$seasonNumber E$episodeNumber"
    }

    /**
     * Update Play button for movies - shows "Continue from XX:XX" if there's progress
     */
    private fun updatePlayButtonForMovie() {
        val progress = moviePlaybackProgress
        val runtime = movieRuntimeMinutes

        if (progress != null && runtime != null && runtime > 0) {
            // Calculate position from progress and runtime
            val durationMs = runtime * 60 * 1000L
            val formattedTime = progress.formatPosition(durationMs)
            buttonPlay.text = "Continue from $formattedTime"
            Log.d(TAG, "Movie has playback progress: ${(progress.progress * 100).toInt()}% ($formattedTime)")
        } else {
            buttonPlay.text = getString(R.string.details_play)
        }
    }

    private fun updateEpisodeShelf(episode: TMDBEpisode?) {
        if (episode == null) return
        // Update Play button with current episode
        episode.seasonNumber?.let { s ->
            episode.episodeNumber?.let { e ->
                updatePlayButtonText(s, e)
            }
        }
        val seasonEpisode = buildSeasonEpisodeLabel(episode.seasonNumber, episode.episodeNumber)
        val airDateText = formatAirDate(episode.airDate)
        val runtimeText = formatRuntime(episode.runtime)
        val baseTitle = when {
            !episode.name.isNullOrBlank() && seasonEpisode.isNotBlank() -> "${episode.name}"
            !episode.name.isNullOrBlank() -> episode.name
            else -> seasonEpisode
        }
        val suffix = listOfNotNull(airDateText, runtimeText)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" • ")
            ?.let { " • $it" }
            ?: ""
        val fullTitle = baseTitle + suffix
        val overviewText = episode.overview
            ?: showOverviewOriginal
            ?: ""
        TransitionManager.beginDelayedTransition(rowsContainer as ViewGroup)
        shelfEpisodeTitle.text = fullTitle
        shelfEpisodeOverview.text = overviewText
        episodeInfoShelf.visibility = View.VISIBLE
    }

    private fun restoreEpisodeShelf() {
        TransitionManager.beginDelayedTransition(rowsContainer as ViewGroup)
        episodeInfoShelf.visibility = View.GONE
    }

    private fun buildEpisodeMetadata(
        seasonNumber: Int?,
        episodeNumber: Int?,
        airDate: String?,
        runtimeMinutes: Int?
    ): String {
        val parts = mutableListOf<String>()
        if (seasonNumber != null && episodeNumber != null) {
            parts.add("S$seasonNumber E$episodeNumber")
        }
        formatAirDate(airDate)?.let { parts.add(it) }
        formatRuntime(runtimeMinutes)?.let { parts.add(it) }
        return parts.joinToString(" • ")
    }

    private fun buildSeasonEpisodeLabel(seasonNumber: Int?, episodeNumber: Int?): String {
        if (seasonNumber == null && episodeNumber == null) return ""
        return buildString {
            seasonNumber?.let { append("S$it") }
            if (episodeNumber != null) {
                if (isNotEmpty()) append(" ")
                append("E$episodeNumber")
            }
        }
    }

    private fun formatAirDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date: Date? = parser.parse(raw)
            date?.let {
                SimpleDateFormat("MMM d, yyyy", Locale.US).format(it)
            }
        } catch (_: Exception) {
            raw
        }
    }

    private fun formatRuntime(runtimeMinutes: Int?): String? {
        runtimeMinutes ?: return null
        if (runtimeMinutes <= 0) return null
        return if (runtimeMinutes >= 60) {
            val hours = runtimeMinutes / 60
            val mins = runtimeMinutes % 60
            if (mins == 0) "${hours}h" else "${hours}h ${mins}m"
        } else {
            "${runtimeMinutes}m"
        }
    }

    private fun configureFixedFocusRow(
        gridView: HorizontalGridView,
        itemAlignmentOffset: Int = 10,
        windowAlignmentOffset: Int = 144
    ) {
        gridView.setWindowAlignment(HorizontalGridView.WINDOW_ALIGN_LOW_EDGE)
        gridView.setWindowAlignmentOffset(windowAlignmentOffset)
        gridView.setWindowAlignmentOffsetPercent(HorizontalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED)
        gridView.setItemAlignmentOffset(itemAlignmentOffset)
        gridView.setItemAlignmentOffsetPercent(HorizontalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED)
    }

    private fun scrollToViewCenter(view: View) {
        val currentScrollY = detailsScroll.scrollY

        val viewLocation = IntArray(2)
        view.getLocationOnScreen(viewLocation)
        val viewY = viewLocation[1]

        val scrollLocation = IntArray(2)
        detailsScroll.getLocationOnScreen(scrollLocation)
        val scrollYOnScreen = scrollLocation[1]

        val relativeY = viewY - scrollYOnScreen + currentScrollY

        val screenHeight = detailsScroll.height
        val viewHeight = view.height
        val targetScrollY = relativeY - (screenHeight / 2) + (viewHeight / 2)

        detailsScroll.smoothScrollTo(0, targetScrollY)
    }

    private fun showEmptyStates() {
        castEmpty.visibility = View.VISIBLE
        castRow.visibility = View.GONE
        similarEmpty.visibility = View.VISIBLE
        similarRow.visibility = View.GONE
        collectionSection.visibility = View.GONE
        tvShowSection.visibility = View.GONE
        episodeRow.visibility = View.GONE
    }

    private fun showMissingContent() {
        title.text = getString(R.string.app_name)
        overview.text = getString(R.string.details_section_similar_empty)
    }

    private fun updateHeroLogo(logoUrl: String?) {
        heroLogoLoader.load(logoUrl)
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
        if (raw.isNullOrBlank() || raw.equals("N/A", ignoreCase = true)) return null
        val slashIndex = raw.indexOf("/")
        return if (slashIndex > 0) raw.substring(0, slashIndex).trim() else raw
    }

    private fun resolveImdbId(
        primary: String?,
        external: String?,
        trakt: String?
    ): String? = primary ?: external ?: trakt


    private fun launchSources() {
        val item = contentItem ?: return

        when (item.type) {
            ContentItem.ContentType.MOVIE -> {
                // Calculate resume position from playback progress
                val resumePositionMs = moviePlaybackProgress?.let { progress ->
                    movieRuntimeMinutes?.let { runtime ->
                        if (runtime > 0) {
                            progress.getPositionMs(runtime * 60 * 1000L)
                        } else 0L
                    }
                } ?: 0L

                SourcesActivity.startForMovie(requireContext(), item, resumePositionMs)
            }
            ContentItem.ContentType.TV_SHOW -> {
                // TODO: Add episode playback progress support
                SourcesActivity.startForEpisode(
                    requireContext(),
                    item,
                    currentSeasonNumber,
                    currentEpisodeNumber
                )
            }
        }
    }

    private fun openTrailer() {
        val key = trailerKey
        if (key.isNullOrBlank()) {
            Toast.makeText(requireContext(), "No trailer available", Toast.LENGTH_SHORT).show()
            return
        }

        // Open in YouTube app (WebView playback is blocked by YouTube)
        val youtubeAppIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("vnd.youtube:$key"))
        val webIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$key"))

        try {
            // Check if YouTube app can handle the intent
            if (youtubeAppIntent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(youtubeAppIntent)
            } else {
                // Fallback to web browser
                startActivity(webIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open trailer", e)
            // Last resort: try web intent
            try {
                startActivity(webIntent)
            } catch (e2: Exception) {
                Toast.makeText(requireContext(), "Could not open trailer", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun Movie.toContentItem(): ContentItem {
        return ContentItem(
            id = id.toInt(),
            tmdbId = id.toInt(),
            imdbId = null,
            title = title ?: "",
            overview = description,
            posterUrl = cardImageUrl,
            backdropUrl = backgroundImageUrl,
            logoUrl = null,
            year = null,
            rating = null,
            ratingPercentage = null,
            genres = null,
            type = ContentItem.ContentType.MOVIE,
            runtime = null,
            cast = null,
            certification = null,
            imdbRating = null,
            rottenTomatoesRating = null,
            traktRating = null
        )
    }

    /**
     * Toggle watched/unwatched status for the current item
     */
    private fun toggleWatchedStatus() {
        val item = contentItem ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            isWatched = !isWatched
            updateWatchedButton()

            val message = if (isWatched) "Marked as watched" else "Marked as unwatched"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

            // Sync with Trakt if authenticated
            val account = withContext(Dispatchers.IO) {
                traktAccountRepository.getAccount()
            }
            if (account != null) {
                withContext(Dispatchers.IO) {
                    try {
                        val success = if (item.type == ContentItem.ContentType.MOVIE) {
                            if (isWatched) {
                                traktSyncRepository.markMovieWatched(item.tmdbId)
                            } else {
                                traktSyncRepository.markMovieUnwatched(item.tmdbId)
                            }
                        } else {
                            if (isWatched) {
                                traktSyncRepository.markShowWatched(item.tmdbId)
                            } else {
                                traktSyncRepository.markShowUnwatched(item.tmdbId)
                            }
                        }
                        if (success) {
                            // Notify badge manager
                            watchedBadgeManager.notifyWatchedStateChanged(item.tmdbId, item.type, isWatched)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync watched status with Trakt", e)
                    }
                }
            }
        }
    }

    /**
     * Update the watched button icon and content description based on current state
     */
    private fun updateWatchedButton() {
        val iconRes = if (isWatched) R.drawable.ic_check_circle_24 else R.drawable.ic_check_circle_outline_24
        buttonWatched.icon = resources.getDrawable(iconRes, null)
        buttonWatched.contentDescription = if (isWatched) "Mark Unwatched" else "Mark Watched"
    }

    /**
     * Show the "More" options popup menu
     */
    private fun showMoreOptionsMenu() {
        val item = contentItem ?: return

        // Use the existing context menu system
        contextMenuHelper.showContextMenu(item)
    }

    companion object {
        private const val TAG = "DetailsFragment"
        private const val ARG_CONTENT_ITEM = "arg_content_item"
        private const val ARG_MOVIE = "arg_movie"
        private const val AMBIENT_ANIMATION_DURATION = 250L
        private val DEFAULT_AMBIENT_COLOR = Color.parseColor("#0A0F1F")

        fun newInstance(item: ContentItem): DetailsFragment {
            return DetailsFragment().apply {
                arguments = bundleOf(ARG_CONTENT_ITEM to item)
            }
        }

        fun newInstance(movie: Movie): DetailsFragment {
            return DetailsFragment().apply {
                arguments = bundleOf(ARG_MOVIE to movie)
            }
        }
    }

    private fun focusDetailsContent() {
        detailsScroll.requestFocus()
    }

    private fun handlePersonClick(person: TMDBCast) {
        Log.d(TAG, "Person clicked: ${person.name}")
        val intent = Intent(requireContext(), ActorDetailsActivity::class.java).apply {
            putExtra(ActorDetailsActivity.PERSON_ID, person.id)
            putExtra(ActorDetailsActivity.PERSON_NAME, person.name)
            contentItem?.let { putExtra(ActorDetailsActivity.ORIGIN_CONTENT, it) }
        }
        startActivity(intent)
    }

    private inline fun <reified T : Parcelable> Bundle?.parcelableCompat(key: String): T? {
        return this?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelable(key, T::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelable(key)
            }
        }
    }

    private inline fun <reified T : Parcelable> Intent?.parcelableExtraCompat(key: String): T? {
        return this?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(key, T::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableExtra(key)
            }
        }
    }

    private inline fun <reified T : Serializable> Bundle?.serializableCompat(key: String): T? {
        return this?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getSerializable(key, T::class.java)
            } else {
                @Suppress("DEPRECATION")
                getSerializable(key) as? T
            }
        }
    }

    private inline fun <reified T : Serializable> Intent?.serializableExtraCompat(key: String): T? {
        return this?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getSerializableExtra(key, T::class.java)
            } else {
                @Suppress("DEPRECATION")
                getSerializableExtra(key) as? T
            }
        }
    }
}
