package com.test1.tv.ui.home

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.SoundEffectConstants
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.test1.tv.MainActivity
import com.test1.tv.DetailsActivity
import com.test1.tv.R
import com.test1.tv.data.local.AppDatabase
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.remote.ApiClient
import com.test1.tv.data.repository.CacheRepository
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.databinding.FragmentHomeBinding
import com.test1.tv.ui.HeroSectionHelper
import com.test1.tv.ui.adapter.ContentRowAdapter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: HomeViewModel

    private var ambientColorAnimator: ValueAnimator? = null
    private val argbEvaluator = ArgbEvaluator()
    private val ambientInterpolator = DecelerateInterpolator()
    private var currentAmbientColor: Int = DEFAULT_AMBIENT_COLOR

    // Navigation
    private var lastFocusedNavButton: View? = null
    private var activeNavButton: MaterialButton? = null

    companion object {
        private const val TAG = "HomeFragment"
        private const val HERO_IMAGE_REQUEST_TAG = "hero_image"
        private const val AMBIENT_ANIMATION_DURATION = 250L
        private const val HERO_UPDATE_DEBOUNCE_MS = 400L  // Increased from 250ms
        private val DEFAULT_AMBIENT_COLOR = Color.parseColor("#0A0F1F")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var rowsAdapter: ContentRowAdapter? = null
    private var hasRequestedInitialFocus = false
    private var heroUpdateJob: Job? = null
    private var heroEnrichmentJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateAmbientGradient(DEFAULT_AMBIENT_COLOR)
        setupViewModel()
        setupNavigation()
        setupContentRows()
        observeViewModel()
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
        val navButtons = with(binding) { listOf(navSearch, navHome, navMovies, navTvShows, navSettings) }
        applyNavigationDockEffects()

        navButtons.forEach { button ->
            button.stateListAnimator = null
            button.setOnFocusChangeListener { view, hasFocus ->
                view.animateNavFocusState(hasFocus)
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

        lastFocusedNavButton = binding.navHome
        binding.navHome.requestFocus()
        setActiveNavButton(binding.navHome)

        binding.navSearch.setOnClickListener {
            setActiveNavButton(binding.navSearch)
            (activity as? MainActivity)?.navigateToSection(MainActivity.Section.SEARCH)
        }

        binding.navHome.setOnClickListener {
            setActiveNavButton(binding.navHome)
            showHomeContent()
        }

        binding.navMovies.setOnClickListener {
            setActiveNavButton(binding.navMovies)
            (activity as? MainActivity)?.navigateToSection(MainActivity.Section.MOVIES)
        }

        binding.navTvShows.setOnClickListener {
            setActiveNavButton(binding.navTvShows)
            (activity as? MainActivity)?.navigateToSection(MainActivity.Section.TV_SHOWS)
        }

        binding.navSettings.setOnClickListener {
            setActiveNavButton(binding.navSettings)
            val intent = Intent(requireContext(), com.test1.tv.ui.settings.SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun applyNavigationDockEffects() {
        binding.navigationDockBackground.alpha = 0.92f
    }

    private fun setActiveNavButton(button: MaterialButton) {
        // Clear activated state from all nav buttons
        with(binding) { listOf(navSearch, navHome, navMovies, navTvShows, navSettings) }.forEach {
            it.isActivated = false
        }
        // Set the current button as activated
        button.isActivated = true
        activeNavButton = button
    }

    private fun View.animateNavFocusState(hasFocus: Boolean) {
        val targetScale = if (hasFocus) 1.15f else 1f
        animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(140)
            .start()
        ViewCompat.setElevation(this, if (hasFocus) 12f else 0f)
        if (hasFocus) {
            playSoundEffect(SoundEffectConstants.CLICK)
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

    private fun observeViewModel() {
        // Observe content rows
        viewModel.contentRows.observe(viewLifecycleOwner) { rows ->
            Log.d(TAG, "Content rows updated: ${rows.size} rows")

            if (rowsAdapter == null) {
                rowsAdapter = ContentRowAdapter(
                    initialRows = rows,
                    onItemClick = { item, imageView ->
                        handleItemClick(item, imageView)
                    },
                    onItemFocused = { item, rowIndex, itemIndex ->
                        handleItemFocused(item, rowIndex, itemIndex)
                    },
                    onNavigateToNavBar = {
                        focusNavigationBar()
                    },
                    onItemLongPress = { item ->
                        showItemContextMenu(item)
                    },
                    onRequestMore = { rowIndex ->
                        viewModel.requestNextPage(rowIndex)
                    }
                )
            }

            if (binding.contentRows.adapter !== rowsAdapter) {
                binding.contentRows.adapter = rowsAdapter
            }

            rowsAdapter?.updateRows(rows)

            binding.contentRows.post {
                if (!hasRequestedInitialFocus) {
                    hasRequestedInitialFocus = true
                    binding.contentRows.requestFocus()
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
            binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
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

        // Load backdrop image with palette extraction
        val heroImageUrl = item.backdropUrl ?: item.posterUrl
        if (heroImageUrl.isNullOrBlank()) {
            binding.heroBackdrop.setImageResource(R.drawable.default_background)
            animateAmbientToColor(DEFAULT_AMBIENT_COLOR)
        } else {
            Glide.with(this)
                .load(heroImageUrl)
                .thumbnail(0.2f)  // Load a 20% quality version first for instant display
                .transition(DrawableTransitionOptions.withCrossFade(200))  // Faster crossfade
                .placeholder(R.drawable.default_background)
                .error(R.drawable.default_background)
                .override(1920, 1080)  // Optimize for typical TV resolution
                .signature(ObjectKey(HERO_IMAGE_REQUEST_TAG + "_" + item.id))  // Unique signature
                .into(binding.heroBackdrop)

            // Load a smaller version for palette extraction to improve performance
            Glide.with(this)
                .asBitmap()
                .load(heroImageUrl)
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

        // Update text content
        binding.heroTitle.text = item.title
        binding.heroOverview.text = item.overview ?: ""
        HeroSectionHelper.updateHeroMetadata(binding.heroMetadata, item)
        updateHeroLogo(item.logoUrl)
        HeroSectionHelper.updateGenres(binding.heroGenreText, item.genres)
        HeroSectionHelper.updateCast(binding.heroCast, item.cast)
        ensureHeroExtras(item)
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
            .thumbnail(0.2f)  // Load thumbnail first
            .transition(DrawableTransitionOptions.withCrossFade(150))  // Faster transition
            .override(600, 200)  // Reasonable size for logos
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    _binding?.let {
                        it.heroLogo.setImageDrawable(resource)
                        applyHeroLogoBounds(resource)
                        it.heroLogo.visibility = View.VISIBLE
                        it.heroTitle.visibility = View.GONE
                    }
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
                    HeroSectionHelper.updateGenres(binding.heroGenreText, enrichedItem.genres)
                    HeroSectionHelper.updateCast(binding.heroCast, enrichedItem.cast)
                }
            }
        }
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

    private fun updateHeroMetadata(item: ContentItem) {
        val metadata = buildMetadataLine(item)
        binding.heroMetadata.text = metadata ?: ""
        binding.heroMetadata.visibility = if (metadata.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun buildMetadataLine(item: ContentItem): CharSequence? {
        val parts = mutableListOf<String>()
        val matchScore = HeroSectionHelper.formatMatchScore(item)
        matchScore?.let { parts.add(it) }
        item.year?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        item.certification?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        HeroSectionHelper.formatRuntimeText(item.runtime)?.let { parts.add(it) }

        if (parts.isEmpty()) return null
        val joined = parts.joinToString("  •  ")
        if (matchScore == null) {
            return joined
        }

        val styled = SpannableString(joined)
        val highlightColor = ContextCompat.getColor(requireContext(), R.color.match_score_highlight)
        styled.setSpan(
            ForegroundColorSpan(highlightColor),
            0,
            matchScore.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        styled.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            matchScore.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return styled
    }

    private fun extractPaletteFromDrawable(drawable: Drawable?) {
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: run {
            animateAmbientToColor(DEFAULT_AMBIENT_COLOR)
            return
        }
        extractPaletteFromBitmap(bitmap)
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

    private fun formatCastList(raw: String): String? {
        val names = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (names.isEmpty()) return null
        val limited = names.take(4).joinToString(", ")
        return getString(R.string.details_cast_template, limited)
    }

    private fun showComingSoonPage(pageName: String) {
        binding.comingSoonText.text = "$pageName coming soon"
        binding.comingSoonContainer.visibility = View.VISIBLE
        binding.homeContentContainer.visibility = View.GONE
    }



    private fun showHomeContent() {
        binding.comingSoonContainer.visibility = View.GONE
        binding.homeContentContainer.visibility = View.VISIBLE
    }

    private fun focusPrimaryContent() {
        if (binding.comingSoonContainer.visibility == View.VISIBLE) {
            binding.comingSoonContainer.requestFocus()
        } else {
            binding.contentRows.requestFocus()
        }
    }

    private fun focusNavigationBar() {
        (lastFocusedNavButton ?: binding.navHome).requestFocus()
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

        // Cancel any in-progress Glide requests to prevent out-of-sync updates
        Glide.with(this).clear(binding.heroBackdrop)

        // Debounce hero section updates - only update if user stays on item for 500ms
        // This prevents the hero section from updating during fast scrolling
        heroUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(HERO_UPDATE_DEBOUNCE_MS)
            viewModel.updateHeroContent(item)
        }
    }

    private fun showItemContextMenu(item: ContentItem) {
        val options = arrayOf(
            getString(R.string.action_play_immediately),
            getString(R.string.action_mark_watched),
            getString(R.string.action_add_to_list)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.title)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> Toast.makeText(
                        requireContext(),
                        getString(R.string.action_play_immediately_feedback, item.title),
                        Toast.LENGTH_SHORT
                    ).show()
                    1 -> Toast.makeText(
                        requireContext(),
                        getString(R.string.action_mark_watched_feedback, item.title),
                        Toast.LENGTH_SHORT
                    ).show()
                    2 -> Toast.makeText(
                        requireContext(),
                        getString(R.string.action_add_to_list_feedback, item.title),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dismiss_error, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        heroUpdateJob?.cancel()
        heroUpdateJob = null
        ambientColorAnimator?.cancel()
        ambientColorAnimator = null
        currentAmbientColor = DEFAULT_AMBIENT_COLOR
        viewModel.cleanupCache()
    }
}
