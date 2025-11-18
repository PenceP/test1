package com.test1.tv.ui.details

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.TooltipCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.leanback.widget.HorizontalGridView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import android.widget.ImageButton
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.test1.tv.BuildConfig
import com.test1.tv.DetailsActivity
import com.test1.tv.Movie
import com.test1.tv.R
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.model.tmdb.TMDBCast
import com.test1.tv.data.model.tmdb.TMDBCollection
import com.test1.tv.data.model.tmdb.TMDBMovie
import com.test1.tv.data.model.tmdb.TMDBShow
import com.test1.tv.data.remote.ApiClient
import com.test1.tv.ui.adapter.PersonAdapter
import com.test1.tv.ui.adapter.PosterAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

class DetailsFragment : Fragment() {

    private var contentItem: ContentItem? = null

    private lateinit var backdrop: ImageView
    private lateinit var logo: ImageView
    private lateinit var title: TextView
    private lateinit var ratingBar: RatingBar
    private lateinit var year: TextView
    private lateinit var ratingBadge: TextView
    private lateinit var runtime: TextView
    private lateinit var overview: TextView
    private lateinit var castSummary: TextView
    private lateinit var genreGroup: ChipGroup

    private lateinit var buttonThumbsUp: ImageButton
    private lateinit var buttonThumbsSide: ImageButton
    private lateinit var buttonThumbsDown: ImageButton
    private lateinit var buttonPlay: MaterialButton
    private lateinit var buttonTrailer: MaterialButton
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

    private var isWatched = false
    private var isInCollection = false
    private var currentRating: Int = 0 // 0 = none, 1 = thumbs up, 2 = thumbs side, 3 = thumbs down

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
        bindNav(view)
        contentItem?.let { bindContent(it) } ?: showMissingContent()
    }

    private fun initViews(view: View) {
        backdrop = view.findViewById(R.id.details_backdrop)
        logo = view.findViewById(R.id.details_logo)
        title = view.findViewById(R.id.details_title)
        ratingBar = view.findViewById(R.id.details_rating_bar)
        year = view.findViewById(R.id.details_year)
        ratingBadge = view.findViewById(R.id.details_rating_label)
        runtime = view.findViewById(R.id.details_runtime)
        overview = view.findViewById(R.id.details_overview)
        castSummary = view.findViewById(R.id.details_cast_summary)
        genreGroup = view.findViewById(R.id.details_genre_group)

        buttonThumbsUp = view.findViewById(R.id.button_thumbs_up)
        buttonThumbsSide = view.findViewById(R.id.button_thumbs_side)
        buttonThumbsDown = view.findViewById(R.id.button_thumbs_down)
        buttonPlay = view.findViewById(R.id.button_play)
        buttonTrailer = view.findViewById(R.id.button_trailer)
        buttonWatched = view.findViewById(R.id.button_watched)
        buttonCollection = view.findViewById(R.id.button_collection)

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

        buttonThumbsUp.setOnClickListener {
            currentRating = if (currentRating == 1) 0 else 1
            updateRatingButtons()
            val message = if (currentRating == 1) "Rated thumbs up" else "Rating removed"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        buttonThumbsSide.setOnClickListener {
            currentRating = if (currentRating == 2) 0 else 2
            updateRatingButtons()
            val message = if (currentRating == 2) "Rated neutral" else "Rating removed"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        buttonThumbsDown.setOnClickListener {
            currentRating = if (currentRating == 3) 0 else 3
            updateRatingButtons()
            val message = if (currentRating == 3) "Rated thumbs down" else "Rating removed"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        buttonPlay.setOnClickListener {
            Toast.makeText(requireContext(), "Play not wired yet", Toast.LENGTH_SHORT).show()
        }

        buttonTrailer.setOnClickListener {
            Toast.makeText(requireContext(), "Trailer coming soon", Toast.LENGTH_SHORT).show()
        }

        buttonWatched.setOnClickListener {
            isWatched = !isWatched
            val message = if (isWatched) "Marked as watched" else "Unmarked as watched"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        buttonCollection.setOnClickListener {
            isInCollection = !isInCollection
            val message = if (isInCollection) "Added to collection" else "Removed from collection"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        updateRatingButtons()

        // Request focus on Play button by default
        buttonPlay.post {
            buttonPlay.requestFocus()
        }
    }

    private fun bindNav(root: View) {
        val navButtons = listOf(
            R.id.nav_search,
            R.id.nav_home,
            R.id.nav_movies,
            R.id.nav_tv_shows,
            R.id.nav_settings
        )

        navButtons.forEach { id ->
            root.findViewById<MaterialButton>(id)?.setOnClickListener {
                Toast.makeText(requireContext(), "Navigation coming soon", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindContent(item: ContentItem) {
        Glide.with(this)
            .load(item.backdropUrl)
            .transition(DrawableTransitionOptions.withCrossFade())
            .placeholder(R.drawable.default_background)
            .error(R.drawable.default_background)
            .into(backdrop)

        title.text = item.title
        overview.text = item.overview ?: getString(R.string.details_section_similar_empty)
        overview.visibility = View.VISIBLE

        year.text = item.year ?: ""
        val runtimeText = formatRuntimeText(item.runtime)
        runtime.text = runtimeText ?: ""
        runtime.visibility = if (runtimeText.isNullOrBlank()) View.GONE else View.VISIBLE

        updateHeroLogo(item.logoUrl)
        updateHeroRating(item.ratingPercentage)
        updateGenres(item.genres)
        updateCastSummary(item.cast)

        ratingBadge.text = item.certification ?: ""
        ratingBadge.visibility = if (item.certification.isNullOrBlank()) View.GONE else View.VISIBLE

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
                    ContentItem.ContentType.MOVIE -> fetchMovieDetails(item.tmdbId)
                    ContentItem.ContentType.TV_SHOW -> fetchShowDetails(item.tmdbId)
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
                        certification = null
                    )
                }
                populateSimilarRowWithItems(similarItems)

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
                certification = null
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
            onItemClick = { item ->
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
                certification = null
            )
        }

        val adapter = PosterAdapter(
            initialItems = collectionItems,
            onItemClick = { item ->
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

        collectionEmpty.visibility = View.GONE
        collectionRow.visibility = View.VISIBLE
        collectionSection.visibility = View.VISIBLE
    }

    private fun showEmptyStates() {
        castEmpty.visibility = View.VISIBLE
        castRow.visibility = View.GONE
        similarEmpty.visibility = View.VISIBLE
        similarRow.visibility = View.GONE
        collectionSection.visibility = View.GONE
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
                }
            })
    }

    private fun updateHeroRating(percentage: Int?) {
        if (percentage == null) {
            ratingBar.visibility = View.GONE
            ratingBar.rating = 0f
            return
        }

        ratingBar.visibility = View.VISIBLE
        ratingBar.rating = percentage.coerceIn(0, 100) / 20f
    }

    private fun updateGenres(genres: String?) {
        genreGroup.removeAllViews()
        if (genres.isNullOrBlank()) {
            genreGroup.visibility = View.GONE
            return
        }

        val parts = genres.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            genreGroup.visibility = View.GONE
            return
        }

        parts.forEach { label ->
            val chip = Chip(requireContext()).apply {
                text = label
                isClickable = false
                isFocusable = false
                setChipBackgroundColorResource(android.R.color.transparent)
                chipStrokeWidth = 1f
                setChipStrokeColorResource(android.R.color.white)
                chipStrokeColor = android.content.res.ColorStateList.valueOf(0x11FFFFFF)
                setChipBackgroundColor(android.content.res.ColorStateList.valueOf(0x11FFFFFF))
                setTextColor(resources.getColor(android.R.color.white, null))
                textSize = 12f
            }
            genreGroup.addView(chip)
        }
        genreGroup.visibility = View.VISIBLE
    }

    private fun updateCastSummary(cast: String?) {
        if (cast.isNullOrBlank()) {
            castSummary.visibility = View.GONE
        } else {
            castSummary.text = getString(R.string.details_cast_template, cast)
            castSummary.visibility = View.VISIBLE
        }

        castEmpty.visibility = View.VISIBLE
        castRow.visibility = View.GONE
    }

    private fun formatRuntimeText(runtime: String?): String? {
        if (runtime.isNullOrBlank()) return null
        if (runtime.contains("h")) return runtime

        val minutes = runtime.filter { it.isDigit() }.toIntOrNull() ?: return runtime
        return if (minutes >= 60) {
            val hours = minutes / 60
            val remaining = minutes % 60
            if (remaining == 0) "${hours}h" else "${hours}h ${remaining}m"
        } else {
            "${minutes}m"
        }
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
            certification = null
        )
    }

    private fun updateRatingButtons() {
        // Reset all buttons to outline icons
        buttonThumbsUp.setImageResource(R.drawable.ic_thumb_up_outline)
        buttonThumbsSide.setImageResource(R.drawable.ic_thumb_side_outline)
        buttonThumbsDown.setImageResource(R.drawable.ic_thumb_down_outline)

        buttonThumbsUp.isSelected = false
        buttonThumbsSide.isSelected = false
        buttonThumbsDown.isSelected = false

        // Set the selected button to filled icon and mark as selected
        when (currentRating) {
            1 -> {
                buttonThumbsUp.setImageResource(R.drawable.ic_thumb_up)
                buttonThumbsUp.isSelected = true
            }
            2 -> {
                buttonThumbsSide.setImageResource(R.drawable.ic_thumb_side)
                buttonThumbsSide.isSelected = true
            }
            3 -> {
                buttonThumbsDown.setImageResource(R.drawable.ic_thumb_down)
                buttonThumbsDown.isSelected = true
            }
        }
    }

    companion object {
        private const val TAG = "DetailsFragment"
        private const val ARG_CONTENT_ITEM = "arg_content_item"
        private const val ARG_MOVIE = "arg_movie"

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
}
