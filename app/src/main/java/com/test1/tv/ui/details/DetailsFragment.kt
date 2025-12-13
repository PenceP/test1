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
import androidx.lifecycle.ViewModelProvider
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
import com.test1.tv.data.local.AppDatabase
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.model.tmdb.TMDBCast
import com.test1.tv.data.model.tmdb.TMDBCollection
import com.test1.tv.data.model.tmdb.TMDBMovie
import com.test1.tv.data.model.tmdb.TMDBEpisode
import com.test1.tv.data.model.tmdb.TMDBSeason
import com.test1.tv.data.model.tmdb.TMDBShow
import com.test1.tv.data.remote.ApiClient
import com.test1.tv.data.repository.CacheRepository
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.databinding.FragmentDetailsBinding
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
import com.test1.tv.ui.ScrollThrottler
import android.transition.TransitionManager

class DetailsFragment : Fragment() {

    private var _binding: FragmentDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: DetailsViewModel

    private var contentItem: ContentItem? = null

    private val scrollThrottler = ScrollThrottler(throttleMs = 120L)

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
        _binding = FragmentDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewModel()
        setupButtons()
        updateRatingButtons()

        contentItem?.let {
            bindContent(it)
            viewModel.loadDetails(it)
        } ?: showMissingContent()

        observeViewModel()

        // Request focus on Play button by default
        binding.buttonPlay.post {
            binding.buttonPlay.requestFocus()
        }
    }

    private fun setupViewModel() {
        val database = AppDatabase.getDatabase(requireContext())
        val cacheRepository = CacheRepository(database.cachedContentDao())
        val contentRepository = ContentRepository(
            traktApiService = ApiClient.traktApiService,
            tmdbApiService = ApiClient.tmdbApiService,
            omdbApiService = ApiClient.omdbApiService,
            cacheRepository = cacheRepository
        )
        val factory = DetailsViewModelFactory(contentRepository)
        viewModel = ViewModelProvider(this, factory)[DetailsViewModel::class.java]
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // You might want to show a progress bar
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
        }

        viewModel.cast.observe(viewLifecycleOwner) { cast ->
            populateCastRow(cast)
        }

        viewModel.similarContent.observe(viewLifecycleOwner) { similar ->
            populateSimilarRowWithItems(similar)
        }

        viewModel.collection.observe(viewLifecycleOwner) { collection ->
            populateCollectionRow(collection.first, collection.second)
        }

        viewModel.seasons.observe(viewLifecycleOwner) { seasons ->
            contentItem?.let {
                populateSeasonsAndEpisodes(it.tmdbId, seasons)
            }
        }

        viewModel.episodes.observe(viewLifecycleOwner) { episodes ->
            episodeAdapter = EpisodeAdapter(
                episodes = episodes,
                onEpisodeFocused = { episode -> updateEpisodeShelf(episode) }
            )
            binding.episodeRow.adapter = episodeAdapter
            binding.episodeRow.setNumRows(1)
            binding.episodeRow.setItemSpacing(0)
            binding.episodeRow.setHasFixedSize(true)
            binding.episodeRow.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)
            configureFixedFocusRow(binding.episodeRow, itemAlignmentOffset = 6)
            RowScrollPauser.attach(binding.episodeRow)
            binding.episodeRow.setOnKeyInterceptListener(scrollThrottler)

            binding.episodeRow.visibility = if (episodes.isNotEmpty()) View.VISIBLE else View.GONE
            val firstEpNumber = episodes.firstOrNull()?.episodeNumber
            updatePlayButtonText(episodes.firstOrNull()?.seasonNumber, firstEpNumber ?: 1)
            if (episodes.isEmpty()) {
                restoreEpisodeShelf()
            }

            // Hook selection listener for episode info shelf
            binding.episodeRow.setOnChildViewHolderSelectedListener(object : OnChildViewHolderSelectedListener() {
                override fun onChildViewHolderSelected(
                    parent: RecyclerView,
                    child: RecyclerView.ViewHolder?,
                    position: Int,
                    subposition: Int
                ) {
                    if (child != null && binding.episodeRow.hasFocus()) {
                        val episode = episodeAdapter?.getEpisode(position)
                        updateEpisodeShelf(episode)
                        binding.episodeRow.post { scrollToViewCenter(binding.episodeRow) }
                    } else {
                        restoreEpisodeShelf()
                    }
                }
            })
            binding.episodeRow.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    binding.episodeRow.post {
                        binding.episodeRow.focusedChild?.let { focused ->
                            val pos = binding.episodeRow.getChildAdapterPosition(focused)
                            val episode = episodeAdapter?.getEpisode(pos)
                            updateEpisodeShelf(episode)
                        }
                        scrollToViewCenter(binding.episodeRow)
                    }
                } else {
                    restoreEpisodeShelf()
                }
            }

            // Anchor focus/keyline to reduce zig-zag navigation
            binding.episodeRow.windowAlignment = BaseGridView.WINDOW_ALIGN_NO_EDGE
            binding.episodeRow.windowAlignmentOffsetPercent = 1f
            binding.episodeRow.itemAlignmentOffsetPercent = 0f
            binding.episodeRow.isFocusSearchDisabled = false
        }
    }


    private fun setupButtons() {
        // Setup Play button (no expanding, just scale animation)
        setupPlayButtonFocus(binding.buttonPlay)

        // Setup secondary buttons with expanding pill animation
        val secondaryButtons = with(binding) {
            listOf(
                buttonTrailer to "Trailer",
                buttonWatchlist to "Watchlist",
                buttonThumbsUp to "Like",
                buttonThumbsDown to "Dislike"
            )
        }


        secondaryButtons.forEach { (button, labelText) ->
            setupExpandingPillButton(button, labelText)
        }

        binding.buttonThumbsUp.setOnClickListener {
            currentRating = if (currentRating == 1) 0 else 1
            updateRatingButtons()
            val message = if (currentRating == 1) "Rated thumbs up" else "Rating removed"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        binding.buttonThumbsDown.setOnClickListener {
            currentRating = if (currentRating == 2) 0 else 2
            updateRatingButtons()
            val message = if (currentRating == 2) "Rated thumbs down" else "Rating removed"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        binding.buttonPlay.setOnClickListener {
            Toast.makeText(requireContext(), "Play not wired yet", Toast.LENGTH_SHORT).show()
        }

        binding.buttonTrailer.setOnClickListener {
            Toast.makeText(requireContext(), "Trailer coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.buttonWatchlist.setOnClickListener {
            isWatched = !isWatched
            val message = if (isWatched) "Added to watchlist" else "Removed from watchlist"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
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
        _binding = null
    }

    private fun bindContent(item: ContentItem) {
        // Load backdrop image
        if (item.backdropUrl.isNullOrBlank()) {
            binding.detailsBackdrop.setImageResource(R.drawable.default_background)
            animateAmbientToColor(DEFAULT_AMBIENT_COLOR)
        } else {
            Glide.with(this)
                .load(item.backdropUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.default_background)
                .error(R.drawable.default_background)
                .into(binding.detailsBackdrop)

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

        binding.detailsTitle.text = item.title
        showTitleOriginal = item.title
        showOverviewOriginal = item.overview ?: getString(R.string.details_section_similar_empty)
        showMetadataOriginal = HeroSectionHelper.buildMetadataLine(item)
        binding.detailsOverview.text = item.overview ?: getString(R.string.details_section_similar_empty)
        binding.detailsOverview.visibility = View.VISIBLE

        updateHeroLogo(item.logoUrl)
        HeroSectionHelper.updateHeroMetadata(binding.detailsMetadata, item)
        HeroSectionHelper.updateGenres(binding.detailsGenreText, item.genres)
        HeroSectionHelper.updateCast(binding.detailsCast, item.cast)

        // Show row sections
        showRowSections()
    }

    private fun showRowSections() {
        // Show People section (cast/crew)
        binding.castSection.visibility = View.VISIBLE

        // Show Similar section
        binding.similarSection.visibility = View.VISIBLE

        // Hide Collection section for now (will show when collection data is available)
        binding.collectionSection.visibility = View.GONE
    }

    private fun populateCastRow(cast: List<TMDBCast>?) {
        if (cast.isNullOrEmpty()) {
            binding.castEmptyText.visibility = View.VISIBLE
            binding.castRow.visibility = View.GONE
            return
        }

        val sortedCast = cast.sortedBy { it.order ?: Int.MAX_VALUE }.take(20)
        val adapter = PersonAdapter(sortedCast)

        binding.castRow.adapter = adapter
        binding.castRow.setNumRows(1)
        binding.castRow.setItemSpacing(0)
        binding.castRow.setHasFixedSize(true)
        binding.castRow.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)
        configureFixedFocusRow(binding.castRow)
        RowScrollPauser.attach(binding.castRow)
        binding.castRow.setOnKeyInterceptListener(scrollThrottler)

        binding.castEmptyText.visibility = View.GONE
        binding.castRow.visibility = View.VISIBLE
    }

    private fun populateSimilarRowWithItems(similarItems: List<ContentItem>?) {
        if (similarItems.isNullOrEmpty()) {
            binding.similarEmptyText.visibility = View.VISIBLE
            binding.similarRow.visibility = View.GONE
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

        binding.similarRow.adapter = adapter
        binding.similarRow.setNumRows(1)
        binding.similarRow.setItemSpacing(0)
        binding.similarRow.setHasFixedSize(true)
        binding.similarRow.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)
        configureFixedFocusRow(binding.similarRow)
        RowScrollPauser.attach(binding.similarRow)
        binding.similarRow.setOnKeyInterceptListener(scrollThrottler)

        binding.similarEmptyText.visibility = View.GONE
        binding.similarRow.visibility = View.VISIBLE
    }

    private fun populateCollectionRow(collectionName: String, movies: List<ContentItem>) {
        if (movies.isNullOrEmpty()) {
            binding.collectionSection.visibility = View.GONE
            return
        }

        binding.collectionSectionTitle.text = collectionName

        val adapter = PosterAdapter(
            initialItems = movies,
            onItemClick = { item, _ ->
                // TODO: Navigate to details of clicked item
                Toast.makeText(requireContext(), "Clicked: ${item.title}", Toast.LENGTH_SHORT).show()
            },
            onItemFocused = { _, _ -> },
            onNavigateToNavBar = { },
            onNearEnd = {}
        )

        binding.collectionRow.adapter = adapter
        binding.collectionRow.setNumRows(1)
        binding.collectionRow.setItemSpacing(0)
        binding.collectionRow.setHasFixedSize(true)
        binding.collectionRow.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)
        configureFixedFocusRow(binding.collectionRow)
        RowScrollPauser.attach(binding.collectionRow)
        binding.collectionRow.setOnKeyInterceptListener(scrollThrottler)

        binding.collectionEmptyText.visibility = View.GONE
        binding.collectionRow.visibility = View.VISIBLE
        binding.collectionSection.visibility = View.VISIBLE
    }

    private fun populateSeasonsAndEpisodes(tmdbShowId: Int, seasons: List<TMDBSeason>) {
        if (seasons.isEmpty()) {
            binding.tvShowSection.visibility = View.GONE
            return
        }

        binding.tvShowSection.visibility = View.VISIBLE
        binding.seasonRow.visibility = View.VISIBLE

        if (seasonAdapter == null) {
            seasonAdapter = SeasonAdapter(
                seasons = seasons,
                onSeasonClick = { season, position ->
                    season.seasonNumber?.let {
                        viewModel.loadEpisodesForSeason(tmdbShowId, it)
                        seasonAdapter?.setSelectedPosition(position)
                    }
                }
            )
            binding.seasonRow.adapter = seasonAdapter
            binding.seasonRow.setNumRows(1)
            binding.seasonRow.setItemSpacing(0)
            binding.seasonRow.setHasFixedSize(true)
            binding.seasonRow.setFocusScrollStrategy(HorizontalGridView.FOCUS_SCROLL_ALIGNED)
            configureFixedFocusRow(binding.seasonRow, itemAlignmentOffset = 30, windowAlignmentOffset = 80)
        }
        // No centering on season row focus; keep shelf hidden when moving away from episodes

        val initialSeasonNumber = seasonAdapter?.getSelectedSeason()?.seasonNumber
            ?: seasons.firstOrNull()?.seasonNumber

        if (initialSeasonNumber != null) {
            viewModel.loadEpisodesForSeason(
                showId = tmdbShowId,
                seasonNumber = initialSeasonNumber,
            )
        }
    }


    private fun updatePlayButtonText(seasonNumber: Int?, episodeNumber: Int?) {
        if (seasonNumber == null || episodeNumber == null) {
            binding.buttonPlay.text = getString(R.string.details_play)
            return
        }
        binding.buttonPlay.text = "${getString(R.string.details_play)} S$seasonNumber E$episodeNumber"
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
        TransitionManager.beginDelayedTransition(binding.rowsContainer as ViewGroup)
        binding.shelfEpisodeTitle.text = fullTitle
        binding.shelfEpisodeOverview.text = overviewText
        binding.episodeInfoShelf.visibility = View.VISIBLE
    }

    private fun restoreEpisodeShelf() {
        TransitionManager.beginDelayedTransition(binding.rowsContainer as ViewGroup)
        binding.episodeInfoShelf.visibility = View.GONE
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
        val currentScrollY = binding.detailsScroll.scrollY

        val viewLocation = IntArray(2)
        view.getLocationOnScreen(viewLocation)
        val viewY = viewLocation[1]

        val scrollLocation = IntArray(2)
        binding.detailsScroll.getLocationOnScreen(scrollLocation)
        val scrollYOnScreen = scrollLocation[1]

        val relativeY = viewY - scrollYOnScreen + currentScrollY

        val screenHeight = binding.detailsScroll.height
        val viewHeight = view.height
        val targetScrollY = relativeY - (screenHeight / 2) + (viewHeight / 2)

        binding.detailsScroll.smoothScrollTo(0, targetScrollY)
    }

    private fun showMissingContent() {
        binding.detailsTitle.text = getString(R.string.app_name)
        binding.detailsOverview.text = getString(R.string.details_section_similar_empty)
    }

    private fun updateHeroLogo(logoUrl: String?) {
        binding.detailsLogo.visibility = View.GONE
        binding.detailsLogo.setImageDrawable(null)
        if (logoUrl.isNullOrEmpty()) {
            binding.detailsTitle.visibility = View.VISIBLE
            return
        } else {
            binding.detailsTitle.visibility = View.GONE
        }

        Glide.with(this)
            .load(logoUrl)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(object : CustomTarget<Drawable>() {
                override fun onLoadCleared(placeholder: Drawable?) {
                    binding.detailsLogo.setImageDrawable(null)
                    binding.detailsLogo.visibility = View.GONE
                    binding.detailsTitle.visibility = View.VISIBLE
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    binding.detailsLogo.setImageDrawable(null)
                    binding.detailsLogo.visibility = View.GONE
                    binding.detailsTitle.visibility = View.VISIBLE
                }

                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    binding.detailsLogo.setImageDrawable(resource)
                    binding.detailsLogo.visibility = View.VISIBLE
                    binding.detailsTitle.visibility = View.GONE
                    applyHeroLogoBounds(resource)
                }
            })
    }

    private fun applyHeroLogoBounds(resource: Drawable) {
        val intrinsicWidth = if (resource.intrinsicWidth > 0) resource.intrinsicWidth else binding.detailsLogo.width
        val intrinsicHeight = if (resource.intrinsicHeight > 0) resource.intrinsicHeight else binding.detailsLogo.height
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return

        val maxWidth = resources.getDimensionPixelSize(R.dimen.hero_logo_max_width)
        val maxHeight = resources.getDimensionPixelSize(R.dimen.hero_logo_max_height)
        val widthRatio = maxWidth.toFloat() / intrinsicWidth
        val heightRatio = maxHeight.toFloat() / intrinsicHeight
        val scale = min(widthRatio, heightRatio)

        val params = binding.detailsLogo.layoutParams
        params.width = (intrinsicWidth * scale).roundToInt()
        params.height = (intrinsicHeight * scale).roundToInt()
        binding.detailsLogo.layoutParams = params
        binding.detailsLogo.scaleX = 1f
        binding.detailsLogo.scaleY = 1f
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
        binding.buttonThumbsUp.icon = resources.getDrawable(R.drawable.ic_thumb_up_outline, null)
        binding.buttonThumbsDown.icon = resources.getDrawable(R.drawable.ic_thumb_down_outline, null)

        binding.buttonThumbsUp.isSelected = false
        binding.buttonThumbsDown.isSelected = false

        // Set the selected button to filled icon and mark as selected
        when (currentRating) {
            1 -> {
                binding.buttonThumbsUp.icon = resources.getDrawable(R.drawable.ic_thumb_up, null)
                binding.buttonThumbsUp.isSelected = true
            }
            2 -> {
                binding.buttonThumbsDown.icon = resources.getDrawable(R.drawable.ic_thumb_down, null)
                binding.buttonThumbsDown.isSelected = true
            }
        }
    }

    private fun focusDetailsContent() {
        binding.detailsScroll.requestFocus()
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
        if (!isAdded || _binding == null) return
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
        val widthCandidates = with(binding) {
            listOf(
                detailsBackdrop.width,
                ambientBackgroundOverlay.width,
                resources.displayMetrics.widthPixels
            ).filter { it > 0 }
        }
        val heightCandidates = with(binding) {
            listOf(
                detailsBackdrop.height,
                ambientBackgroundOverlay.height,
                resources.displayMetrics.heightPixels
            ).filter { it > 0 }
        }

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
        binding.ambientBackgroundOverlay.background = gradient
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
}
