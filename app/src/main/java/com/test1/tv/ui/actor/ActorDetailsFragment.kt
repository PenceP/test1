package com.test1.tv.ui.actor

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.test1.tv.ActorDetailsActivity
import com.test1.tv.BuildConfig
import com.test1.tv.DetailsActivity
import com.test1.tv.R
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.remote.ApiClient
import com.test1.tv.databinding.FragmentActorDetailsBinding
import com.test1.tv.ui.HeroSectionHelper
import com.test1.tv.ui.adapter.ContentRow
import com.test1.tv.ui.adapter.ContentRowAdapter
import com.test1.tv.ui.adapter.RowPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ActorDetailsFragment : Fragment() {

    private var _binding: FragmentActorDetailsBinding? = null
    private val binding get() = _binding!!

    private var personId: Int = -1
    private var personName: String? = null

    private var ambientColorAnimator: ValueAnimator? = null
    private val argbEvaluator = ArgbEvaluator()
    private val ambientInterpolator = DecelerateInterpolator()
    private var currentAmbientColor: Int = DEFAULT_AMBIENT_COLOR

    private var rowsAdapter: ContentRowAdapter? = null
    private var heroUpdateJob: Job? = null

    companion object {
        private const val TAG = "ActorDetailsFragment"
        private const val AMBIENT_ANIMATION_DURATION = 150L
        private val DEFAULT_AMBIENT_COLOR = Color.parseColor("#0A0F1F")
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

        updateAmbientGradient(DEFAULT_AMBIENT_COLOR)
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
                    ApiClient.tmdbApiService.getPersonDetails(
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
                        // Optional: context menu
                    },
                    onRequestMore = { rowIndex ->
                        // No pagination for actor details
                    }
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
        Log.d(TAG, "Updating hero section with: ${item.title}")
        renderHero(item)
        loadBackdropAndPalette(item)
        heroUpdateJob?.cancel()
        heroUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            fetchDetailedHero(item)?.let { detailed ->
                renderHero(detailed)
                loadBackdropAndPalette(detailed)
            }
        }
    }

    private fun loadBackdropAndPalette(item: ContentItem) {
        val heroImageUrl = item.backdropUrl ?: item.posterUrl
        if (heroImageUrl.isNullOrBlank()) {
            binding.heroBackdrop.setImageResource(R.drawable.default_background)
            animateAmbientToColor(DEFAULT_AMBIENT_COLOR)
            return
        }

        Glide.with(this)
            .load(heroImageUrl)
            .thumbnail(0.2f)
            .transition(DrawableTransitionOptions.withCrossFade(200))
            .placeholder(R.drawable.default_background)
            .error(R.drawable.default_background)
            .override(1920, 1080)
            .into(binding.heroBackdrop)

        Glide.with(this)
            .asBitmap()
            .load(heroImageUrl)
            .override(150, 150)
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

    private suspend fun fetchDetailedHero(item: ContentItem): ContentItem? = withContext(Dispatchers.IO) {
        runCatching {
            when (item.type) {
                ContentItem.ContentType.MOVIE -> {
                    val details = ApiClient.tmdbApiService.getMovieDetails(
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
                    val details = ApiClient.tmdbApiService.getShowDetails(
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
        binding.heroOverview.text = item.overview ?: ""
        HeroSectionHelper.updateHeroMetadata(binding.heroMetadata, item)
        updateHeroLogo(item.logoUrl)
        HeroSectionHelper.updateGenres(binding.heroGenreText, item.genres)
        HeroSectionHelper.updateCast(binding.heroCast, item.cast)
    }

    private fun updateHeroLogo(logoUrl: String?) {
        binding.heroTitle.visibility = View.VISIBLE
        binding.heroLogo.visibility = View.GONE
        binding.heroLogo.setImageDrawable(null)
        binding.heroLogo.scaleX = 1f
        binding.heroLogo.scaleY = 1f

        if (logoUrl.isNullOrBlank()) {
            return
        }

        Glide.with(this)
            .load(logoUrl)
            .thumbnail(0.2f)
            .transition(DrawableTransitionOptions.withCrossFade(150))
            .override(600, 200)
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    binding.heroLogo.setImageDrawable(resource)
                    applyHeroLogoBounds(resource)
                    binding.heroLogo.visibility = View.VISIBLE
                    binding.heroTitle.visibility = View.GONE
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    _binding?.let {
                        it.heroLogo.setImageDrawable(placeholder)
                        it.heroLogo.scaleX = 1f
                        it.heroLogo.scaleY = 1f
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    _binding?.let {
                        it.heroLogo.visibility = View.GONE
                        it.heroTitle.visibility = View.VISIBLE
                        it.heroLogo.scaleX = 1f
                        it.heroLogo.scaleY = 1f
                    }
                }
            })
    }

    private fun applyHeroLogoBounds(resource: Drawable) {
        val intrinsicWidth = if (resource.intrinsicWidth > 0) resource.intrinsicWidth else binding.heroLogo.width
        val intrinsicHeight = if (resource.intrinsicHeight > 0) resource.intrinsicHeight else binding.heroLogo.height
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return

        val maxWidth = resources.getDimensionPixelSize(R.dimen.hero_logo_max_width)
        val maxHeight = resources.getDimensionPixelSize(R.dimen.hero_logo_max_height)
        val widthRatio = maxWidth.toFloat() / intrinsicWidth
        val heightRatio = maxHeight.toFloat() / intrinsicHeight
        val scale = min(widthRatio, heightRatio)

        val params = binding.heroLogo.layoutParams
        params.width = (intrinsicWidth * scale).roundToInt()
        params.height = (intrinsicHeight * scale).roundToInt()
        binding.heroLogo.layoutParams = params
        binding.heroLogo.scaleX = 1f
        binding.heroLogo.scaleY = 1f
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
        val widthCandidates = listOf(
            binding.heroBackdrop.width,
            binding.ambientBackgroundOverlay.width,
            resources.displayMetrics.widthPixels
        ).filter { it > 0 }
        val heightCandidates = listOf(
            binding.heroBackdrop.height,
            binding.ambientBackgroundOverlay.height,
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
        binding.ambientBackgroundOverlay.background = gradient
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

        // Cancel any pending hero update
        heroUpdateJob?.cancel()

        // Debounce hero section updates
        heroUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(250)
            updateHeroSection(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        heroUpdateJob?.cancel()
        heroUpdateJob = null
        ambientColorAnimator?.cancel()
        ambientColorAnimator = null
        currentAmbientColor = DEFAULT_AMBIENT_COLOR
    }
}
