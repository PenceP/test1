package com.test1.tv.ui.home

import android.content.Intent
import android.net.Uri
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.SoundEffectConstants
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.test1.tv.MainActivity
import com.test1.tv.DetailsActivity
import com.test1.tv.R
import com.test1.tv.BuildConfig
import com.test1.tv.data.model.ContentItem
import dagger.hilt.android.AndroidEntryPoint
import com.test1.tv.TraktMediaActivity
import com.test1.tv.TraktListActivity
import com.test1.tv.data.model.trakt.TraktMediaList
import com.test1.tv.data.repository.TraktAuthRepository
import com.test1.tv.data.repository.TraktAccountRepository
import com.test1.tv.databinding.FragmentHomeBinding
import com.test1.tv.ui.HeroSectionHelper
import com.test1.tv.ui.HeroSyncManager
import com.test1.tv.ui.AccentColorCache
import com.test1.tv.ui.RowsScreenDelegate
import com.test1.tv.ui.HeroExtrasLoader
import com.test1.tv.ui.HeroBackgroundController
import com.test1.tv.ui.HeroLogoLoader
import com.test1.tv.ui.RowPrefetchManager
import com.test1.tv.ui.splash.LaunchGate
import com.test1.tv.ui.splash.SyncSplashActivity
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    @Inject lateinit var sharedViewPool: RecyclerView.RecycledViewPool

    private lateinit var heroBackgroundController: HeroBackgroundController

    companion object {
        private const val TAG = "HomeFragment"
        private val DEFAULT_AMBIENT_COLOR = android.graphics.Color.parseColor("#0A0F1F")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    private lateinit var rowsDelegate: RowsScreenDelegate
    private var heroEnrichmentJob: Job? = null
    private lateinit var heroSyncManager: HeroSyncManager
    private lateinit var heroLogoLoader: HeroLogoLoader
    @Inject lateinit var rowPrefetchManager: RowPrefetchManager
    @Inject lateinit var accentColorCache: AccentColorCache
    @Inject lateinit var traktAuthRepository: TraktAuthRepository
    @Inject lateinit var traktAccountRepository: TraktAccountRepository
    @Inject lateinit var traktApiService: com.test1.tv.data.remote.api.TraktApiService
    @Inject lateinit var tmdbApiService: com.test1.tv.data.remote.api.TMDBApiService
    private var resumedOnce = false
    private var authDialog: androidx.appcompat.app.AlertDialog? = null
    private var awaitingPostAuthRestart = false

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
        heroSyncManager = HeroSyncManager(viewLifecycleOwner) { content ->
            updateHeroSection(content)
        }
        applyNavigationDockEffects()
        rowsDelegate = RowsScreenDelegate(
            fragment = this,
            lifecycleOwner = viewLifecycleOwner,
            navButtons = RowsScreenDelegate.NavButtons(
                search = binding.navSearch,
                home = binding.navHome,
                movies = binding.navMovies,
                tvShows = binding.navTvShows,
                settings = binding.navSettings
            ),
            defaultSection = RowsScreenDelegate.NavTarget.HOME,
            contentRowsView = binding.contentRows,
            loadingIndicator = binding.loadingIndicator,
            sharedViewPool = sharedViewPool,
            rowPrefetchManager = rowPrefetchManager,
            accentColorCache = accentColorCache,
            heroSyncManager = heroSyncManager,
            onItemClick = { item, posterView -> handleItemClick(item, posterView) },
            onItemLongPress = { item -> showItemContextMenu(item) },
            onRequestMore = { rowIndex -> viewModel.requestNextPage(rowIndex) },
            onNavigate = { section ->
                when (section) {
                    RowsScreenDelegate.NavTarget.SEARCH -> (activity as? MainActivity)?.navigateToSection(MainActivity.Section.SEARCH)
                    RowsScreenDelegate.NavTarget.HOME -> showHomeContent()
                    RowsScreenDelegate.NavTarget.MOVIES -> (activity as? MainActivity)?.navigateToSection(MainActivity.Section.MOVIES)
                    RowsScreenDelegate.NavTarget.TV_SHOWS -> (activity as? MainActivity)?.navigateToSection(MainActivity.Section.TV_SHOWS)
                    RowsScreenDelegate.NavTarget.SETTINGS -> {
                        val intent = Intent(requireContext(), com.test1.tv.ui.settings.SettingsActivity::class.java)
                        startActivity(intent)
                    }
                }
            },
            navFocusEffect = { view, hasFocus -> view.animateNavFocusState(hasFocus) },
            coroutineScope = viewLifecycleOwner.lifecycleScope
        )
        rowsDelegate.bind(
            contentRows = viewModel.contentRows,
            rowAppendEvents = viewModel.rowAppendEvents,
            isLoading = viewModel.isLoading,
            error = viewModel.error,
            heroContent = viewModel.heroContent
        )

        viewModel.refreshComplete.observe(viewLifecycleOwner) { done ->
            if (done && awaitingPostAuthRestart) {
                awaitingPostAuthRestart = false
                restartAppClean()
            }
            if (done) {
                LaunchGate.markHomeReady()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!resumedOnce) {
            resumedOnce = true
            viewModel.refreshAfterAuth()
        }
    }

    private fun applyNavigationDockEffects() {
        binding.navigationDockBackground.alpha = 0.92f
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

    private fun updateHeroSection(item: ContentItem) {
        Log.d(TAG, "Updating hero section with: ${item.title}")

        val heroImageUrl = item.backdropUrl ?: item.posterUrl
        heroBackgroundController.updateBackdrop(
            backdropUrl = heroImageUrl,
            fallbackDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.default_background)
        )

        // Update text content
        binding.heroTitle.text = item.title
        val overview = HeroSectionHelper.buildOverviewText(item)
        binding.heroOverview.text = overview ?: ""
        HeroSectionHelper.updateHeroMetadata(binding.heroMetadata, item)
        updateHeroLogo(item.logoUrl)
        HeroSectionHelper.updateGenres(binding.heroGenreText, item.genres)
        HeroSectionHelper.updateCast(binding.heroCast, item.cast)
        ensureHeroExtras(item)
    }

    private fun updateHeroLogo(logoUrl: String?) {
        heroLogoLoader.load(logoUrl)
    }

    private fun ensureHeroExtras(item: ContentItem) {
        if (!item.cast.isNullOrBlank() && !item.genres.isNullOrBlank()) return
        heroEnrichmentJob = HeroExtrasLoader.load(
            scope = viewLifecycleOwner.lifecycleScope,
            existingJob = heroEnrichmentJob,
            item = item,
            tmdbApiService = tmdbApiService
        ) { enrichedItem ->
            HeroSectionHelper.updateGenres(binding.heroGenreText, enrichedItem.genres)
            HeroSectionHelper.updateCast(binding.heroCast, enrichedItem.cast)
        }
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

    private fun parseTraktListUrl(url: String): Pair<String, String>? {
        // Pattern: https://trakt.tv/users/{username}/lists/{list-id} (stop at ? or end)
        val pattern = """https://trakt\.tv/users/([^/]+)/lists/([^/?]+)""".toRegex()
        val matchResult = pattern.find(url)
        return matchResult?.let {
            val username = it.groupValues[1]
            val listId = it.groupValues[2]
            Pair(username, listId)
        }
    }

    private fun handleItemClick(item: ContentItem, posterView: ImageView) {
        Log.d(TAG, "Item clicked: ${item.title}")
        Log.d(TAG, "Item tmdbId: ${item.tmdbId}, imdbId: ${item.imdbId}")

        // Handle collections, directors, and networks with Trakt list URLs
        if (item.tmdbId == -1 && item.imdbId?.startsWith("https://trakt.tv/users/") == true) {
            Log.d(TAG, "Matched Trakt list URL pattern, parsing...")
            parseTraktListUrl(item.imdbId)?.let { (username, listId) ->
                Log.d(TAG, "Parsed username: $username, listId: $listId")
                val intent = TraktListActivity.newIntent(
                    context = requireContext(),
                    username = username,
                    listId = listId,
                    title = item.title,
                    traktUrl = item.imdbId
                )
                startActivity(intent)
            } ?: run {
                // Fallback to browser if parsing fails
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.imdbId))
                startActivity(intent)
            }
            return
        }

        if (item.tmdbId == -1 && item.title.contains("trakt", ignoreCase = true)) {
            promptTraktAuth()
            return
        }

        TraktMediaList.fromId(item.imdbId)?.let { category ->
            startActivity(TraktMediaActivity.newIntent(requireContext(), category))
            return
        }

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

    private fun promptTraktAuth() {
        // Avoid launching work when the view is already gone (e.g., fast navigation away).
        if (!isAdded || view == null) return

        viewLifecycleOwner.lifecycleScope.launch {
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.trakt_authorize_title))
                .setMessage(getString(R.string.trakt_authorize_loading))
                .setCancelable(false)
                .show()

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    traktAuthRepository.createDeviceCode()
                }
            }

            dialog.dismiss()

            result.onSuccess { code ->
                if (!isAdded || view == null) return@onSuccess
                showTraktActivationDialog(code.userCode, code.verificationUrl, code.expiresIn)
                pollForDeviceToken(code.deviceCode, code.interval, code.expiresIn)
            }.onFailure {
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.trakt_authorize_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showTraktActivationDialog(userCode: String, verificationUrl: String, expiresIn: Int) {
        val minutes = (expiresIn / 60).coerceAtLeast(1)
        val message = getString(
            R.string.trakt_authorize_message,
            verificationUrl,
            userCode.uppercase(Locale.US),
            minutes
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.trakt_authorize_title))
            .setMessage(message)
            .setPositiveButton(R.string.trakt_authorize_open) { _, _ ->
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(verificationUrl)
                )
                startActivity(intent)
            }
            .setNegativeButton(R.string.trakt_authorize_close, null)
            .show().also { authDialog = it }
    }

    private fun pollForDeviceToken(deviceCode: String, intervalSeconds: Int, expiresIn: Int) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val expiresAt = startTime + expiresIn * 1000L
            val pollDelay = (intervalSeconds.coerceAtLeast(1)) * 1000L

            while (isActive && System.currentTimeMillis() < expiresAt) {
                val tokenResult = runCatching {
                    traktApiService.pollDeviceToken(
                        clientId = BuildConfig.TRAKT_CLIENT_ID,
                        clientSecret = BuildConfig.TRAKT_CLIENT_SECRET,
                        deviceCode = deviceCode
                    )
                }

                val token = tokenResult.getOrNull()
                if (token != null) {
                    saveTraktAccount(token)
                    withContext(Dispatchers.Main) {
                        handlePostAuthRefresh()
                    }
                    return@launch
                }

                delay(pollDelay.toLong())
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Trakt code expired. Please try again.", Toast.LENGTH_LONG).show()
                authDialog?.dismiss()
            }
        }
    }

    private fun handlePostAuthRefresh() {
        if (!isAdded || view == null) return
        Toast.makeText(requireContext(), "Trakt authorized!", Toast.LENGTH_SHORT).show()
        authDialog?.dismiss()
        awaitingPostAuthRestart = false
        // Launch sync splash in a fresh task; existing activity will be cleared by flags.
        startActivity(
            Intent(requireContext(), SyncSplashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun restartAppClean() {
        val context = requireContext().applicationContext
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launcher != null) {
            launcher.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launcher)
            requireActivity().finish()
        } else {
            // Fallback to activity recreate if launcher can't be resolved.
            requireActivity().recreate()
        }
    }

    private suspend fun saveTraktAccount(token: com.test1.tv.data.model.trakt.TraktTokenResponse) {
        val authHeader = "Bearer ${token.accessToken}"
        val profile = runCatching {
            traktApiService.getUserProfile(
                authHeader = authHeader,
                clientId = BuildConfig.TRAKT_CLIENT_ID
            )
        }.getOrNull()

        if (profile != null) {
            traktAccountRepository.saveDeviceToken(token, profile)
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
        viewModel.cleanupCache()
        heroLogoLoader.cancel()
    }
}
