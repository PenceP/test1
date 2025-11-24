package com.test1.tv.ui.details

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.leanback.widget.HorizontalGridView
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import android.widget.ImageButton
import com.google.android.material.button.MaterialButton
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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
import com.test1.tv.data.model.tmdb.TMDBShow
import com.test1.tv.data.remote.ApiClient
import com.test1.tv.ui.HeroSectionHelper
import com.test1.tv.ui.adapter.PersonAdapter
import com.test1.tv.ui.adapter.PosterAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.util.Locale
import androidx.core.widget.NestedScrollView
import java.util.Date
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.leanback.widget.BaseGridView
import com.test1.tv.ui.RowScrollPauser
import android.transition.TransitionManager

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

    private var seasonAdapter: SeasonAdapter? = null
    private var episodeAdapter: EpisodeAdapter? = null

    private var showTitleOriginal: String? = null
    private var showMetadataOriginal: CharSequence? = null
    private var showOverviewOriginal: String? = null

    private var isWatched = false
    private var isInCollection = false
    private var currentRating: Int = 0 // 0 = none, 1 = thumbs up, 2 = thumbs down

    // Ambient gradient animation
    private var ambientColorAnimator: ValueAnimator? = null
    private val argbEvaluator = ArgbEvaluator()
    private val ambientInterpolator = DecelerateInterpolator()
    private var currentAmbientColor: Int = DEFAULT_AMBIENT_COLOR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contentItem = arguments?.getParcelable(ARG_CONTENT_ITEM)
            ?: activity?.intent?.getParcelableExtra(DetailsActivity.CONTENT_ITEM)

        if (contentItem == null) {
            val legacyMovie = arguments?.getSerializable(ARG_MOVIE) as? Movie
                ?: activity?.intent?.getSerializableExtra(DetailsActivity.MOVIE) as? Movie
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
    }

    private fun bindContent(item: ContentItem) {
        // Load backdrop image
        if (item.backdropUrl.isNullOrBlank()) {
            backdrop.setImageResource(R.drawable.default_background)
            animateAmbientToColor(DEFAULT_AMBIENT_COLOR)
        } else {
            Glide.with(this)
                .load(item.backdropUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.default_background)
                .error(R.drawable.default_background)
                .into(backdrop)

            // Load a smaller version for palette extraction to improve performance
            Glide.with(this)
                .asBitmap()
                .load(item.backdropUrl)
                .override(150, 150)  // Small size is sufficient for color extraction
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        extractPaletteFromBitmap(resource)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) = Unit

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        animateAmbientToColor(DEFAULT_AMBIENT_COLOR)
                    }
                })
        }

        title.text = item.title
        showTitleOriginal = item.title
        showOverviewOriginal = item.overview ?: getString(R.string.details_section_similar_empty)
        showMetadataOriginal = HeroSectionHelper.buildMetadataLine(item)
        overview.text = item.overview ?: getString(R.string.details_section_similar_empty)
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
        CoroutineScope(Dispatchers.Main).launch {
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
            val movieDetails = ApiClient.tmdbApiService.getMovieDetails(
                movieId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY
            )

            // Fetch similar movies
            val similarMovies = ApiClient.tmdbApiService.getSimilarMovies(
                movieId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY
            )

            // Fetch collection if exists
            val collectionMovies = movieDetails.belongsToCollection?.let { collection ->
                ApiClient.tmdbApiService.getCollectionDetails(
                    collectionId = collection.id,
                    apiKey = BuildConfig.TMDB_API_KEY
                )
            }

            withContext(Dispatchers.Main) {
                // Populate People row
                populateCastRow(movieDetails.credits?.cast)

                // Populate Similar row
                populateSimilarRow(similarMovies.results)

                // Populate Collection row if exists
                if (collectionMovies != null && movieDetails.belongsToCollection != null) {
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
            val showDetails = ApiClient.tmdbApiService.getShowDetails(
                showId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY
            )

            // Fetch similar shows
            val similarShows = ApiClient.tmdbApiService.getSimilarShows(
                showId = tmdbId,
                apiKey = BuildConfig.TMDB_API_KEY
            )

            withContext(Dispatchers.Main) {
                // Populate People row
                populateCastRow(showDetails.credits?.cast)

                // Populate Similar row (convert TMDBShow to ContentItem)
                val similarItems = similarShows.results?.map { show ->
                    ContentItem(
                        id = show.id,
                        tmdbId = show.id,
                        title = show.name,
                        overview = show.overview,
                        posterUrl = show.getPosterUrl(),
                        backdropUrl = show.getBackdropUrl(),
                        logoUrl = null,
                        year = show.firstAirDate?.take(4),
                        rating = show.voteAverage,
                        ratingPercentage = show.voteAverage?.times(10)?.toInt(),
                        genres = null,
                        type = ContentItem.ContentType.TV_SHOW,
                        runtime = null,
                        cast = null,
                        certification = null,
                        imdbRating = null,
                        rottenTomatoesRating = null,
                        traktRating = null
                    )
                }
                populateSimilarRowWithItems(similarItems)

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

    private fun populateCastRow(cast: List<TMDBCast>?) {
        if (cast.isNullOrEmpty()) {
            castEmpty.visibility = View.VISIBLE
            castRow.visibility = View.GONE
            return
        }

        val sortedCast = cast.sortedBy { it.order ?: Int.MAX_VALUE }.take(20)
        val adapter = PersonAdapter(sortedCast)

        castRow.adapter = adapter
        castRow.setNumRows(1)
        castRow.setItemSpacing(0)
        castRow.setHasFixedSize(true)
        castRow.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)
        configureFixedFocusRow(castRow)
        RowScrollPauser.attach(castRow)

        castEmpty.visibility = View.GONE
        castRow.visibility = View.VISIBLE
    }

    private fun populateSimilarRow(similar: List<TMDBMovie>?) {
        if (similar.isNullOrEmpty()) {
            similarEmpty.visibility = View.VISIBLE
            similarRow.visibility = View.GONE
            return
        }

        val similarItems = similar.take(20).map { movie ->
            ContentItem(
                id = movie.id,
                tmdbId = movie.id,
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

        populateSimilarRowWithItems(similarItems)
    }

    private fun populateSimilarRowWithItems(similarItems: List<ContentItem>?) {
        if (similarItems.isNullOrEmpty()) {
            similarEmpty.visibility = View.VISIBLE
            similarRow.visibility = View.GONE
            return
        }

        val adapter = PosterAdapter(
            initialItems = similarItems.take(20),
            onItemClick = { item, _ ->
                // TODO: Navigate to details of clicked item
                Toast.makeText(requireContext(), "Clicked: ${item.title}", Toast.LENGTH_SHORT).show()
            },
            onItemFocused = { _, _ -> },
            onNavigateToNavBar = { },
            onNearEnd = {}
        )

        similarRow.adapter = adapter
        similarRow.setNumRows(1)
        similarRow.setItemSpacing(0)
        similarRow.setHasFixedSize(true)
        similarRow.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)
        configureFixedFocusRow(similarRow)
        RowScrollPauser.attach(similarRow)

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
            initialItems = collectionItems,
            onItemClick = { item, _ ->
                // TODO: Navigate to details of clicked item
                Toast.makeText(requireContext(), "Clicked: ${item.title}", Toast.LENGTH_SHORT).show()
            },
            onItemFocused = { _, _ -> },
            onNavigateToNavBar = { },
            onNearEnd = {}
        )

        collectionRow.adapter = adapter
        collectionRow.setNumRows(1)
        collectionRow.setItemSpacing(0)
        collectionRow.setHasFixedSize(true)
        collectionRow.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)
        configureFixedFocusRow(collectionRow)
        RowScrollPauser.attach(collectionRow)

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
                onSeasonClick = { season, position ->
                    season.seasonNumber?.let {
                        loadEpisodesForSeason(tmdbShowId, it, position)
                        seasonAdapter?.setSelectedPosition(position)
                    }
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
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val seasonDetails = withContext(Dispatchers.IO) {
                    ApiClient.tmdbApiService.getSeasonDetails(
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
                    onEpisodeFocused = { episode -> updateEpisodeShelf(episode) }
                )
                episodeRow.adapter = episodeAdapter
                episodeRow.setNumRows(1)
                episodeRow.setItemSpacing(0)
                episodeRow.setHasFixedSize(true)
                episodeRow.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)
                configureFixedFocusRow(episodeRow)
                RowScrollPauser.attach(episodeRow)

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
            !episode.name.isNullOrBlank() && seasonEpisode.isNotBlank() -> "$seasonEpisode - ${episode.name}"
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
        itemAlignmentOffset: Int = 60,
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
        logo.visibility = View.GONE
        logo.setImageDrawable(null)
        if (logoUrl.isNullOrEmpty()) {
            title.visibility = View.VISIBLE
            return
        } else {
            title.visibility = View.GONE
        }

        Glide.with(this)
            .load(logoUrl)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(object : CustomTarget<Drawable>() {
                override fun onLoadCleared(placeholder: Drawable?) {
                    logo.setImageDrawable(null)
                    logo.visibility = View.GONE
                    title.visibility = View.VISIBLE
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    logo.setImageDrawable(null)
                    logo.visibility = View.GONE
                    title.visibility = View.VISIBLE
                }

                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    logo.setImageDrawable(resource)
                    logo.visibility = View.VISIBLE
                    title.visibility = View.GONE
                    applyHeroLogoBounds(resource)
                }
            })
    }

    private fun applyHeroLogoBounds(resource: Drawable) {
        val intrinsicWidth = if (resource.intrinsicWidth > 0) resource.intrinsicWidth else logo.width
        val intrinsicHeight = if (resource.intrinsicHeight > 0) resource.intrinsicHeight else logo.height
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return

        val maxWidth = resources.getDimensionPixelSize(R.dimen.hero_logo_max_width)
        val maxHeight = resources.getDimensionPixelSize(R.dimen.hero_logo_max_height)
        val widthRatio = maxWidth.toFloat() / intrinsicWidth
        val heightRatio = maxHeight.toFloat() / intrinsicHeight
        val scale = min(widthRatio, heightRatio)

        val params = logo.layoutParams
        params.width = (intrinsicWidth * scale).roundToInt()
        params.height = (intrinsicHeight * scale).roundToInt()
        logo.layoutParams = params
        logo.scaleX = 1f
        logo.scaleY = 1f
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


    private fun Movie.toContentItem(): ContentItem {
        return ContentItem(
            id = id.toInt(),
            tmdbId = id.toInt(),
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

    private inner class SeasonAdapter(
        private val seasons: List<TMDBSeason>,
        private val onSeasonClick: (TMDBSeason, Int) -> Unit
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
        private val onEpisodeFocused: (TMDBEpisode?) -> Unit
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
        private const val AMBIENT_ANIMATION_DURATION = 650L
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

    private fun extractPaletteFromBitmap(bitmap: Bitmap) {
        Palette.from(bitmap).generate { palette ->
            if (palette == null) {
                animateAmbientToColor(DEFAULT_AMBIENT_COLOR)
            } else {
                animateAmbientFromPalette(palette)
            }
        }
    }

    private fun animateAmbientFromPalette(palette: Palette) {
        val swatchColor = palette.vibrantSwatch?.rgb
            ?: palette.darkVibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: DEFAULT_AMBIENT_COLOR
        val deepColor = ColorUtils.blendARGB(swatchColor, Color.BLACK, 0.55f)
        animateAmbientToColor(deepColor)
    }

    private fun animateAmbientToColor(targetColor: Int) {
        if (!isAdded || !::ambientOverlay.isInitialized) return
        ambientColorAnimator?.cancel()
        val startColor = currentAmbientColor
        if (startColor == targetColor) {
            updateAmbientGradient(targetColor)
            return
        }

        ambientColorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = AMBIENT_ANIMATION_DURATION
            interpolator = ambientInterpolator
            addUpdateListener { animator ->
                val blended = argbEvaluator.evaluate(
                    animator.animatedFraction,
                    startColor,
                    targetColor
                ) as Int
                updateAmbientGradient(blended)
            }
            start()
        }
    }

    private fun updateAmbientGradient(color: Int) {
        currentAmbientColor = color
        val widthCandidates = listOf(
            backdrop.width,
            ambientOverlay.width,
            resources.displayMetrics.widthPixels
        ).filter { it > 0 }
        val heightCandidates = listOf(
            backdrop.height,
            ambientOverlay.height,
            resources.displayMetrics.heightPixels
        ).filter { it > 0 }

        val width = widthCandidates.maxOrNull() ?: resources.displayMetrics.widthPixels
        val height = heightCandidates.maxOrNull() ?: resources.displayMetrics.heightPixels
        val radius = max(width, height).toFloat() * 0.95f

        val gradient = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = radius
            setGradientCenter(0.32f, 0.28f)
            colors = intArrayOf(
                ColorUtils.setAlphaComponent(color, 220),
                ColorUtils.setAlphaComponent(color, 120),
                ColorUtils.setAlphaComponent(color, 10)
            )
        }
        ambientOverlay.background = gradient
    }
}
