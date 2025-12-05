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
import com.test1.tv.ui.HeroSectionHelper
import com.test1.tv.ui.HeroBackgroundController
import com.test1.tv.ui.adapter.PersonAdapter
import com.test1.tv.ui.adapter.PosterAdapter
import com.test1.tv.ui.AccentColorCache
import java.io.Serializable
import javax.inject.Inject
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

    private lateinit var buttonThumbsUp: MaterialButton
    private lateinit var buttonThumbsDown: MaterialButton
    private lateinit var buttonPlay: MaterialButton
    private lateinit var buttonTrailer: MaterialButton
    private lateinit var buttonWatchlist: MaterialButton
    private lateinit var buttonWatched: MaterialButton
    private lateinit var buttonCollection: MaterialButton

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

    private var seasonAdapter: SeasonAdapter? = null
    private var episodeAdapter: EpisodeAdapter? = null
    @Inject lateinit var accentColorCache: AccentColorCache
    @Inject lateinit var rateLimiter: com.test1.tv.data.remote.RateLimiter
    @Inject lateinit var tmdbApiService: com.test1.tv.data.remote.api.TMDBApiService
    @Inject lateinit var traktApiService: com.test1.tv.data.remote.api.TraktApiService
    @Inject lateinit var traktSyncRepository: com.test1.tv.data.repository.TraktSyncRepository

    private var showTitleOriginal: String? = null
    private var showMetadataOriginal: CharSequence? = null
    private var showOverviewOriginal: String? = null

    private var isWatched = false
    private var isInCollection = false
    private var isItemWatched = false
    private var isItemInCollection = false
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

        buttonThumbsUp = view.findViewById(R.id.button_thumbs_up)
        buttonThumbsDown = view.findViewById(R.id.button_thumbs_down)
        buttonPlay = view.findViewById(R.id.button_play)
        buttonTrailer = view.findViewById(R.id.button_trailer)
        buttonWatchlist = view.findViewById(R.id.button_watchlist)
        buttonWatched = view.findViewById(R.id.button_watched)
        buttonCollection = view.findViewById(R.id.button_collection)
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

        buttonThumbsUp.setOnClickListener {
            currentRating = if (currentRating == 1) 0 else 1
            updateRatingButtons()
            val message = if (currentRating == 1) "Rated thumbs up" else "Rating removed"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        buttonThumbsDown.setOnClickListener {
            currentRating = if (currentRating == 2) 0 else 2
            updateRatingButtons()
            val message = if (currentRating == 2) "Rated thumbs down" else "Rating removed"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        buttonPlay.setOnClickListener {
            Toast.makeText(requireContext(), "Play not wired yet", Toast.LENGTH_SHORT).show()
        }

        buttonTrailer.setOnClickListener {
            Toast.makeText(requireContext(), "Trailer coming soon", Toast.LENGTH_SHORT).show()
        }

        buttonWatchlist.setOnClickListener {
            isWatched = !isWatched
            val message = if (isWatched) "Added to watchlist" else "Removed from watchlist"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        buttonWatched.setOnClickListener {
            contentItem?.let { item ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val isMovie = item.type == ContentItem.ContentType.MOVIE
                    val itemType = if (isMovie) "MOVIE" else "SHOW"

                    // Check current state
                    val currentlyWatched = withContext(Dispatchers.IO) {
                        traktSyncRepository.isInList(item.tmdbId, "HISTORY", itemType)
                    }

                    val success = withContext(Dispatchers.IO) {
                        if (currentlyWatched) {
                            if (isMovie) traktSyncRepository.markMovieUnwatched(item.tmdbId)
                            else traktSyncRepository.markShowUnwatched(item.tmdbId)
                        } else {
                            if (isMovie) traktSyncRepository.markMovieWatched(item.tmdbId)
                            else traktSyncRepository.markShowWatched(item.tmdbId)
                        }
                    }

                    if (success) {
                        withContext(Dispatchers.Main) {
                            isItemWatched = !currentlyWatched
                            updateButtonStates()
                            val message = if (isItemWatched) "Marked as watched" else "Marked as unwatched"
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        buttonCollection.setOnClickListener {
            contentItem?.let { item ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val isMovie = item.type == ContentItem.ContentType.MOVIE
                    val itemType = if (isMovie) "MOVIE" else "SHOW"

                    // Check current state
                    val currentlyInCollection = withContext(Dispatchers.IO) {
                        traktSyncRepository.isInList(item.tmdbId, "COLLECTION", itemType)
                    }

                    val success = withContext(Dispatchers.IO) {
                        if (currentlyInCollection) {
                            traktSyncRepository.removeFromCollection(item.tmdbId, isMovie)
                        } else {
                            traktSyncRepository.addToCollection(item.tmdbId, isMovie)
                        }
                    }

                    if (success) {
                        withContext(Dispatchers.Main) {
                            isItemInCollection = !currentlyInCollection
                            updateButtonStates()
                            val message = if (isItemInCollection) "Added to collection" else "Removed from collection"
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        // Setup button focus handlers
        setupButtons()

        updateRatingButtons()

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
            buttonWatchlist to "Watchlist",
            buttonThumbsUp to "Like",
            buttonThumbsDown to "Dislike"
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

        // Load item state (watched, collection status)
        loadItemState(item)

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

            withContext(Dispatchers.Main) {
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

        tvShowSection.visibility = View.VISIBLE
        seasonRow.visibility = View.VISIBLE

        if (seasonAdapter == null) {
            seasonAdapter = SeasonAdapter(
                seasons = seasons,
                showTmdbId = tmdbShowId,
                onSeasonClick = { season, position ->
                    season.seasonNumber?.let {
                        loadEpisodesForSeason(tmdbShowId, it, position)
                        seasonAdapter?.setSelectedPosition(position)
                    }
                },
                onSeasonLongPress = { season, position ->
                    showSeasonContextMenu(tmdbShowId, season)
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

        val initialSeasonNumber = seasonAdapter?.getSelectedSeason()?.seasonNumber
            ?: seasons.firstOrNull()?.seasonNumber

        if (initialSeasonNumber != null) {
            loadEpisodesForSeason(
                showId = tmdbShowId,
                seasonNumber = initialSeasonNumber,
                selectedPosition = seasonAdapter?.selectedPosition ?: 0
            )
        }
    }

    private fun loadEpisodesForSeason(showId: Int, seasonNumber: Int, selectedPosition: Int) {
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

                episodeAdapter = EpisodeAdapter(
                    episodes = episodes,
                    showTmdbId = showId,
                    onEpisodeFocused = { episode -> updateEpisodeShelf(episode) },
                    onEpisodeLongPress = { episode -> showEpisodeContextMenu(showId, episode) }
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
                val firstEpNumber = episodes.firstOrNull()?.episodeNumber
                updatePlayButtonText(seasonNumber, firstEpNumber ?: 1)
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
        buttonPlay.text = "${getString(R.string.details_play)} S$seasonNumber E$episodeNumber"
    }

    private fun updateEpisodeShelf(episode: TMDBEpisode?) {
        if (episode == null) return
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

    private fun updateRatingButtons() {
        // Reset all buttons to outline icons
        buttonThumbsUp.icon = resources.getDrawable(R.drawable.ic_thumb_up_outline, null)
        buttonThumbsDown.icon = resources.getDrawable(R.drawable.ic_thumb_down_outline, null)

        buttonThumbsUp.isSelected = false
        buttonThumbsDown.isSelected = false

        // Set the selected button to filled icon and mark as selected
        when (currentRating) {
            1 -> {
                buttonThumbsUp.icon = resources.getDrawable(R.drawable.ic_thumb_up, null)
                buttonThumbsUp.isSelected = true
            }
            2 -> {
                buttonThumbsDown.icon = resources.getDrawable(R.drawable.ic_thumb_down, null)
                buttonThumbsDown.isSelected = true
            }
        }
    }

    private fun updateButtonStates() {
        // Update Watched button icon
        buttonWatched.icon = if (isItemWatched) {
            resources.getDrawable(R.drawable.ic_eye_filled, null)
        } else {
            resources.getDrawable(R.drawable.ic_eye, null)
        }

        // Update Collection button icon
        buttonCollection.icon = if (isItemInCollection) {
            resources.getDrawable(R.drawable.ic_library_filled, null)
        } else {
            resources.getDrawable(R.drawable.ic_library, null)
        }
    }

    private fun loadItemState(item: ContentItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val isMovie = item.type == ContentItem.ContentType.MOVIE
            val itemType = if (isMovie) "MOVIE" else "SHOW"

            isItemWatched = withContext(Dispatchers.IO) {
                traktSyncRepository.isInList(item.tmdbId, "HISTORY", itemType)
            }
            isItemInCollection = withContext(Dispatchers.IO) {
                traktSyncRepository.isInList(item.tmdbId, "COLLECTION", itemType)
            }

            withContext(Dispatchers.Main) {
                updateButtonStates()
            }
        }
    }

    private fun showSeasonContextMenu(showTmdbId: Int, season: TMDBSeason) {
        val seasonNumber = season.seasonNumber ?: return

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Season $seasonNumber")
            .setItems(arrayOf(
                getString(R.string.action_mark_season_watched)
            )) { dialog, which ->
                when (which) {
                    0 -> {
                        viewLifecycleOwner.lifecycleScope.launch {
                            val success = withContext(Dispatchers.IO) {
                                traktSyncRepository.markSeasonWatched(showTmdbId, seasonNumber)
                            }
                            withContext(Dispatchers.Main) {
                                if (success) {
                                    Toast.makeText(
                                        requireContext(),
                                        "Season $seasonNumber marked as watched",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    // Refresh episode adapter to show watched badges
                                    episodeAdapter?.notifyDataSetChanged()
                                }
                            }
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dismiss_error, null)
            .show()
    }

    private fun showEpisodeContextMenu(showTmdbId: Int, episode: TMDBEpisode) {
        val seasonNumber = episode.seasonNumber ?: return
        val episodeNumber = episode.episodeNumber ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            // Check if episode is currently watched
            val isWatched = withContext(Dispatchers.IO) {
                traktSyncRepository.isInList(showTmdbId, "HISTORY", "SHOW")
                // Note: This is simplified - ideally we'd check episode-level watch status
            }

            withContext(Dispatchers.Main) {
                val title = "S${seasonNumber}E${episodeNumber}"
                val options = if (isWatched) {
                    arrayOf(getString(R.string.action_mark_episode_unwatched))
                } else {
                    arrayOf(getString(R.string.action_mark_episode_watched))
                }

                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(title)
                    .setItems(options) { dialog, which ->
                        when (which) {
                            0 -> {
                                viewLifecycleOwner.lifecycleScope.launch {
                                    val success = withContext(Dispatchers.IO) {
                                        if (isWatched) {
                                            traktSyncRepository.markEpisodeUnwatched(showTmdbId, seasonNumber, episodeNumber)
                                        } else {
                                            traktSyncRepository.markEpisodeWatched(showTmdbId, seasonNumber, episodeNumber)
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        if (success) {
                                            val message = if (isWatched) "Marked as unwatched" else "Marked as watched"
                                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                                            // Refresh episode adapter to show watched badges
                                            episodeAdapter?.notifyDataSetChanged()
                                        }
                                    }
                                }
                            }
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.dismiss_error, null)
                    .show()
            }
        }
    }

    private inner class SeasonAdapter(
        private val seasons: List<TMDBSeason>,
        private val showTmdbId: Int,
        private val onSeasonClick: (TMDBSeason, Int) -> Unit,
        private val onSeasonLongPress: (TMDBSeason, Int) -> Unit
    ) : RecyclerView.Adapter<SeasonAdapter.SeasonViewHolder>() {

        var selectedPosition: Int = 0
            private set

        inner class SeasonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val title: TextView = itemView.findViewById(R.id.season_title)

            fun bind(season: TMDBSeason, position: Int) {
                val label = season.seasonNumber?.let { "Season $it" }
                    ?: season.name?.takeIf { it.isNotBlank() }
                    ?: "Season"
                title.text = label
                itemView.isSelected = position == selectedPosition

                itemView.setOnClickListener {
                    val adapterPosition = bindingAdapterPosition
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        setSelectedPosition(adapterPosition)
                        onSeasonClick(season, adapterPosition)
                    }
                }

                // Add long-press handler
                itemView.isLongClickable = true
                itemView.setOnLongClickListener {
                    val adapterPosition = bindingAdapterPosition
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        onSeasonLongPress(season, adapterPosition)
                    }
                    true
                }

                itemView.setOnFocusChangeListener { view, hasFocus ->
                    view.isSelected = position == selectedPosition
                    if (hasFocus) {
                        view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120).start()
                    } else {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeasonViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_season_chip, parent, false)
            return SeasonViewHolder(view)
        }

        override fun onBindViewHolder(holder: SeasonViewHolder, position: Int) {
            holder.bind(seasons[position], position)
        }

        override fun getItemCount(): Int = seasons.size

        fun setSelectedPosition(newPosition: Int) {
            if (newPosition == selectedPosition) return
            val previous = selectedPosition
            selectedPosition = newPosition
            notifyItemChanged(previous)
            notifyItemChanged(newPosition)
        }

        fun getSelectedSeason(): TMDBSeason? = seasons.getOrNull(selectedPosition)
    }

    private inner class EpisodeAdapter(
        private val episodes: List<TMDBEpisode>,
        private val showTmdbId: Int,
        private val onEpisodeFocused: (TMDBEpisode?) -> Unit,
        private val onEpisodeLongPress: (TMDBEpisode) -> Unit
    ) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

        inner class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val episodeImage: ImageView = itemView.findViewById(R.id.episode_image)
            private val episodeTitle: TextView = itemView.findViewById(R.id.episode_title)
            private val focusOverlay: View = itemView.findViewById(R.id.episode_focus_overlay)

            fun bind(episode: TMDBEpisode) {
                Glide.with(itemView.context)
                    .load(episode.getStillUrl())
                    .transition(DrawableTransitionOptions.withCrossFade())
            .placeholder(R.drawable.default_background)
            .error(R.drawable.default_background)
                    .into(episodeImage)

                val seasonEpisode = buildSeasonEpisodeLabel(episode.seasonNumber, episode.episodeNumber)
                episodeTitle.text = seasonEpisode.ifBlank { "" }

                // Add long-press handler
                itemView.isLongClickable = true
                itemView.setOnLongClickListener {
                    onEpisodeLongPress(episode)
                    true
                }

                itemView.setOnFocusChangeListener { _, hasFocus ->
                    focusOverlay.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
                    if (hasFocus) {
                        itemView.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                        onEpisodeFocused(episode)
                    } else {
                        itemView.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_episode_card, parent, false)
            return EpisodeViewHolder(view)
        }

        override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
            holder.bind(episodes[position])
        }

        override fun getItemCount(): Int = episodes.size

        fun getEpisode(position: Int): TMDBEpisode? = episodes.getOrNull(position)
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
