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
import android.widget.ProgressBar
import android.widget.TextView
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.test1.tv.DetailsActivity
import com.test1.tv.R
import com.test1.tv.data.local.AppDatabase
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.remote.ApiClient
import com.test1.tv.data.repository.CacheRepository
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.ui.adapter.ContentRowAdapter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var contentRowsView: VerticalGridView
    private lateinit var loadingIndicator: ProgressBar

    // Hero section views
    private lateinit var ambientBackgroundOverlay: View
    private lateinit var heroBackdrop: ImageView
    private lateinit var heroLogo: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroMetadata: TextView
    private lateinit var heroGenreText: TextView
    private lateinit var heroOverview: TextView
    private lateinit var heroCast: TextView
    private lateinit var homeContentContainer: View
    private lateinit var comingSoonContainer: View
    private lateinit var comingSoonText: TextView

    private var ambientColorAnimator: ValueAnimator? = null
    private val argbEvaluator = ArgbEvaluator()
    private val ambientInterpolator = DecelerateInterpolator()
    private var currentAmbientColor: Int = DEFAULT_AMBIENT_COLOR

    // Navigation
    private lateinit var navSearch: MaterialButton
    private lateinit var navHome: MaterialButton
    private lateinit var navMovies: MaterialButton
    private lateinit var navTvShows: MaterialButton
    private lateinit var navSettings: MaterialButton
    private lateinit var navigationDockBackground: View
    private var lastFocusedNavButton: View? = null
    private var activeNavButton: MaterialButton? = null

    companion object {
        private const val TAG = "HomeFragment"
        private const val AMBIENT_ANIMATION_DURATION = 650L
        private val DEFAULT_AMBIENT_COLOR = Color.parseColor("#0A0F1F")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    private var rowsAdapter: ContentRowAdapter? = null
    private var hasRequestedInitialFocus = false
    private var heroUpdateJob: Job? = null

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

        // Hero section (now directly in main layout)
        ambientBackgroundOverlay = view.findViewById(R.id.ambient_background_overlay)
        heroBackdrop = view.findViewById(R.id.hero_backdrop)
        heroLogo = view.findViewById(R.id.hero_logo)
        heroTitle = view.findViewById(R.id.hero_title)
        heroMetadata = view.findViewById(R.id.hero_metadata)
        heroGenreText = view.findViewById(R.id.hero_genre_text)
        heroOverview = view.findViewById(R.id.hero_overview)
        heroCast = view.findViewById(R.id.hero_cast)
        homeContentContainer = view.findViewById(R.id.home_content_container)
        comingSoonContainer = view.findViewById(R.id.coming_soon_container)
        comingSoonText = view.findViewById(R.id.coming_soon_text)

        // Navigation
        navSearch = view.findViewById(R.id.nav_search)
        navHome = view.findViewById(R.id.nav_home)
        navMovies = view.findViewById(R.id.nav_movies)
        navTvShows = view.findViewById(R.id.nav_tv_shows)
        navSettings = view.findViewById(R.id.nav_settings)
        navigationDockBackground = view.findViewById(R.id.navigation_dock_background)

        updateAmbientGradient(DEFAULT_AMBIENT_COLOR)
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
        val navButtons = listOf(navSearch, navHome, navMovies, navTvShows, navSettings)
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

        lastFocusedNavButton = navHome
        navHome.requestFocus()
        setActiveNavButton(navHome)

        navSearch.setOnClickListener {
            setActiveNavButton(navSearch)
            showComingSoonPage("Search")
        }

        navHome.setOnClickListener {
            setActiveNavButton(navHome)
            showHomeContent()
        }

        navMovies.setOnClickListener {
            setActiveNavButton(navMovies)
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, com.test1.tv.ui.movies.MoviesFragment())
                .addToBackStack(null)
                .commit()
        }

        navTvShows.setOnClickListener {
            setActiveNavButton(navTvShows)
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, com.test1.tv.ui.tvshows.TvShowsFragment())
                .addToBackStack(null)
                .commit()
        }

        navSettings.setOnClickListener {
            setActiveNavButton(navSettings)
            showComingSoonPage("Settings")
        }
    }

    private fun applyNavigationDockEffects() {
        navigationDockBackground.alpha = 0.92f
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
        contentRowsView.setNumColumns(1)
        contentRowsView.setItemSpacing(60)

        // Enable smooth scrolling with fixed row heights
        contentRowsView.setHasFixedSize(true)
        contentRowsView.setFocusScrollStrategy(VerticalGridView.FOCUS_SCROLL_ALIGNED)

        // Set window alignment for fixed focus at top with proper offset
        contentRowsView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_LOW_EDGE)
        contentRowsView.setWindowAlignmentOffset(0)
        contentRowsView.setWindowAlignmentOffsetPercent(VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED)

        // Set item alignment to prevent rows from being cut off
        contentRowsView.setItemAlignmentOffset(0)
        contentRowsView.setItemAlignmentOffsetPercent(VerticalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED)
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

            if (contentRowsView.adapter !== rowsAdapter) {
                contentRowsView.adapter = rowsAdapter
            }

            rowsAdapter?.updateRows(rows)

            contentRowsView.post {
                if (!hasRequestedInitialFocus) {
                    hasRequestedInitialFocus = true
                    contentRowsView.requestFocus()
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

        // Load backdrop image with palette extraction
        val heroImageUrl = item.backdropUrl ?: item.posterUrl
        if (heroImageUrl.isNullOrBlank()) {
            heroBackdrop.setImageResource(R.drawable.default_background)
            animateAmbientToColor(DEFAULT_AMBIENT_COLOR)
        } else {
            Glide.with(this)
                .load(heroImageUrl)
                .thumbnail(0.2f)  // Load a 20% quality version first for instant display
                .transition(DrawableTransitionOptions.withCrossFade(200))  // Faster crossfade
                .placeholder(R.drawable.default_background)
                .error(R.drawable.default_background)
                .override(1920, 1080)  // Optimize for typical TV resolution
                .into(heroBackdrop)

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
        heroTitle.text = item.title
        heroOverview.text = item.overview ?: ""
        updateHeroMetadata(item)
        updateHeroLogo(item.logoUrl)
        updateGenres(item.genres)

        // Update cast
        val castText = item.cast?.let { formatCastList(it) }
        if (castText != null) {
            heroCast.text = castText
            heroCast.visibility = View.VISIBLE
        } else {
            heroCast.visibility = View.GONE
        }
    }

    private fun updateGenres(genres: String?) {
        if (genres.isNullOrBlank()) {
            heroGenreText.visibility = View.GONE
            heroGenreText.text = ""
            return
        }

        val formatted = genres.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (formatted.isEmpty()) {
            heroGenreText.visibility = View.GONE
            heroGenreText.text = ""
            return
        }

        heroGenreText.text = formatted.joinToString(" • ")
        heroGenreText.visibility = View.VISIBLE
    }

    private fun updateHeroLogo(logoUrl: String?) {
        heroTitle.visibility = View.VISIBLE
        heroLogo.visibility = View.GONE
        heroLogo.setImageDrawable(null)
        heroLogo.scaleX = 1f
        heroLogo.scaleY = 1f

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
                    heroLogo.setImageDrawable(resource)
                    applyHeroLogoBounds(resource)
                    heroLogo.visibility = View.VISIBLE
                    heroTitle.visibility = View.GONE
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    heroLogo.setImageDrawable(placeholder)
                    heroLogo.scaleX = 1f
                    heroLogo.scaleY = 1f
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    heroLogo.visibility = View.GONE
                    heroTitle.visibility = View.VISIBLE
                    heroLogo.scaleX = 1f
                    heroLogo.scaleY = 1f
                }
            })
    }

    private fun applyHeroLogoBounds(resource: Drawable) {
        val intrinsicWidth = if (resource.intrinsicWidth > 0) resource.intrinsicWidth else heroLogo.width
        val intrinsicHeight = if (resource.intrinsicHeight > 0) resource.intrinsicHeight else heroLogo.height
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return

        val maxWidth = resources.getDimensionPixelSize(R.dimen.hero_logo_max_width)
        val maxHeight = resources.getDimensionPixelSize(R.dimen.hero_logo_max_height)
        val widthRatio = maxWidth.toFloat() / intrinsicWidth
        val heightRatio = maxHeight.toFloat() / intrinsicHeight
        val scale = min(widthRatio, heightRatio)

        val params = heroLogo.layoutParams
        params.width = (intrinsicWidth * scale).roundToInt()
        params.height = (intrinsicHeight * scale).roundToInt()
        heroLogo.layoutParams = params
        heroLogo.scaleX = 1f
        heroLogo.scaleY = 1f
    }

    private fun updateHeroMetadata(item: ContentItem) {
        val metadata = buildMetadataLine(item)
        heroMetadata.text = metadata ?: ""
        heroMetadata.visibility = if (metadata.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun buildMetadataLine(item: ContentItem): CharSequence? {
        val parts = mutableListOf<String>()
        val matchScore = formatMatchScore(item)
        matchScore?.let { parts.add(it) }
        item.year?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        item.certification?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        formatRuntimeText(item.runtime)?.let { parts.add(it) }

        if (parts.isEmpty()) return null
        val joined = parts.joinToString(" • ")
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

    private fun formatMatchScore(item: ContentItem): String? {
        val fromPercentage = item.ratingPercentage?.takeIf { it in 1..100 }
        if (fromPercentage != null) {
            return String.format(Locale.US, "%d%% Match", fromPercentage)
        }

        val voteAverage = item.rating?.takeIf { it > 0 }
            ?.let { (it * 10).roundToInt().coerceAtMost(100) }
        if (voteAverage != null) {
            return String.format(Locale.US, "%d%% Match", voteAverage)
        }

        val trakt = item.traktRating?.takeIf { it > 0 }
            ?.let { (it * 10).roundToInt().coerceAtMost(100) }
        return trakt?.let { String.format(Locale.US, "%d%% Match", it) }
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
        if (!isAdded || !::ambientBackgroundOverlay.isInitialized) return
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
            heroBackdrop.width,
            ambientBackgroundOverlay.width,
            resources.displayMetrics.widthPixels
        ).filter { it > 0 }
        val heightCandidates = listOf(
            heroBackdrop.height,
            ambientBackgroundOverlay.height,
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
        ambientBackgroundOverlay.background = gradient
    }

    private fun formatRuntimeText(runtime: String?): String? {
        if (runtime.isNullOrBlank()) return null
        if (runtime.contains("h")) return runtime

        val minutes = runtime.filter { it.isDigit() }.toIntOrNull() ?: return runtime
        return if (minutes >= 60) {
            val hours = minutes / 60
            val remaining = minutes % 60
            if (remaining == 0) {
                "${hours}h"
            } else {
                "${hours}h ${remaining}m"
            }
        } else {
            "${minutes}m"
        }
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
        comingSoonText.text = "$pageName coming soon"
        comingSoonContainer.visibility = View.VISIBLE
        homeContentContainer.visibility = View.GONE
    }

    private fun showHomeContent() {
        comingSoonContainer.visibility = View.GONE
        homeContentContainer.visibility = View.VISIBLE
    }

    private fun focusPrimaryContent() {
        if (comingSoonContainer.visibility == View.VISIBLE) {
            comingSoonContainer.requestFocus()
        } else {
            contentRowsView.requestFocus()
        }
    }

    private fun focusNavigationBar() {
        (lastFocusedNavButton ?: navHome).requestFocus()
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

        // Debounce hero section updates - only update if user stays on item for 250ms
        // This prevents the hero section from queuing updates during fast scrolling
        heroUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(250)
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
        hasRequestedInitialFocus = false
        heroUpdateJob?.cancel()
        heroUpdateJob = null
        ambientColorAnimator?.cancel()
        ambientColorAnimator = null
        currentAmbientColor = DEFAULT_AMBIENT_COLOR
        viewModel.cleanupCache()
    }
}
