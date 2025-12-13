package com.strmr.tv.ui.sources

import android.content.Context
import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.strmr.tv.BuildConfig
import com.strmr.tv.R
import com.strmr.tv.data.model.ContentItem
import com.strmr.tv.data.model.StreamInfo
import com.strmr.tv.data.remote.api.TMDBApiService
import com.strmr.tv.data.repository.ScrapeResult
import com.strmr.tv.data.repository.TorrentioRepository
import com.strmr.tv.ui.sources.adapter.SourcesAdapter
import com.strmr.tv.ui.sources.model.SourceItem
import com.strmr.tv.ui.player.VideoPlayerActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SourcesActivity : FragmentActivity() {

    companion object {
        const val EXTRA_CONTENT_ITEM = "content_item"
        const val EXTRA_SEASON = "season"
        const val EXTRA_EPISODE = "episode"
        const val EXTRA_RESUME_POSITION_MS = "resume_position_ms"

        /**
         * Start SourcesActivity for a movie
         */
        fun startForMovie(context: Context, contentItem: ContentItem, resumePositionMs: Long = 0L) {
            val intent = Intent(context, SourcesActivity::class.java).apply {
                putExtra(EXTRA_CONTENT_ITEM, contentItem)
                putExtra(EXTRA_RESUME_POSITION_MS, resumePositionMs)
            }
            context.startActivity(intent)
        }

        /**
         * Start SourcesActivity for a TV episode
         */
        fun startForEpisode(context: Context, contentItem: ContentItem, season: Int, episode: Int, resumePositionMs: Long = 0L) {
            val intent = Intent(context, SourcesActivity::class.java).apply {
                putExtra(EXTRA_CONTENT_ITEM, contentItem)
                putExtra(EXTRA_SEASON, season)
                putExtra(EXTRA_EPISODE, episode)
                putExtra(EXTRA_RESUME_POSITION_MS, resumePositionMs)
            }
            context.startActivity(intent)
        }
    }

    @Inject
    lateinit var torrentioRepository: TorrentioRepository

    @Inject
    lateinit var tmdbApiService: TMDBApiService

    private lateinit var backgroundImage: ImageView
    private lateinit var posterImage: ImageView
    private lateinit var titleText: TextView
    private lateinit var sourcesHeader: TextView
    private lateinit var sourcesCount: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var sourcesList: VerticalGridView

    private lateinit var adapter: SourcesAdapter

    private var contentItem: ContentItem? = null
    private var season: Int = -1
    private var episode: Int = -1
    private var resumePositionMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sources)

        initializeViews()
        parseIntent()
        setupAdapter()
        setupBlurEffect()
        displayContentInfo()
        loadSources()
    }

    private fun initializeViews() {
        backgroundImage = findViewById(R.id.background_image)
        posterImage = findViewById(R.id.poster_image)
        titleText = findViewById(R.id.title_text)
        sourcesHeader = findViewById(R.id.sources_header)
        sourcesCount = findViewById(R.id.sources_count)
        loadingIndicator = findViewById(R.id.loading_indicator)
        errorText = findViewById(R.id.error_text)
        sourcesList = findViewById(R.id.sources_list)
    }

    private fun parseIntent() {
        contentItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CONTENT_ITEM, ContentItem::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_CONTENT_ITEM)
        }
        season = intent.getIntExtra(EXTRA_SEASON, -1)
        episode = intent.getIntExtra(EXTRA_EPISODE, -1)
        resumePositionMs = intent.getLongExtra(EXTRA_RESUME_POSITION_MS, 0L)
    }

    private fun setupAdapter() {
        adapter = SourcesAdapter { sourceItem ->
            onSourceSelected(sourceItem)
        }
        sourcesList.adapter = adapter
    }

    private fun setupBlurEffect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurEffect = RenderEffect.createBlurEffect(
                25f, 25f, Shader.TileMode.CLAMP
            )
            backgroundImage.setRenderEffect(blurEffect)
        }
    }

    private fun displayContentInfo() {
        val item = contentItem ?: return

        // Load poster
        Glide.with(this)
            .load(item.posterUrl)
            .placeholder(R.drawable.default_background)
            .error(R.drawable.default_background)
            .into(posterImage)

        // Load backdrop as background
        Glide.with(this)
            .load(item.backdropUrl ?: item.posterUrl)
            .placeholder(R.drawable.default_background)
            .error(R.drawable.default_background)
            .into(backgroundImage)

        // Set title
        val titleWithYear = if (item.year != null) {
            "${item.title} (${item.year})"
        } else {
            item.title
        }

        // Add episode info if applicable
        val displayTitle = if (season > 0 && episode > 0) {
            "$titleWithYear - S${season}E${episode}"
        } else {
            titleWithYear
        }
        titleText.text = displayTitle
    }

    private fun loadSources() {
        val item = contentItem ?: run {
            showError("No content information provided")
            return
        }

        showLoading()

        lifecycleScope.launch {
            // Try to get IMDB ID, fetching from TMDB if needed
            val imdbId = withContext(Dispatchers.IO) {
                resolveImdbId(item)
            }

            if (imdbId.isNullOrBlank()) {
                showError("No IMDB ID available for scraping")
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                val runtime = item.runtime?.toIntOrNull()
                if (season > 0 && episode > 0) {
                    torrentioRepository.scrapeEpisode(imdbId, season, episode, runtime)
                } else {
                    torrentioRepository.scrapeMovie(imdbId, runtime)
                }
            }

            displayResults(result)
        }
    }

    /**
     * Resolve IMDB ID from content item, falling back to TMDB API lookup if needed
     */
    private suspend fun resolveImdbId(item: ContentItem): String? {
        // First try the item's own IMDB ID
        if (!item.imdbId.isNullOrBlank()) {
            return item.imdbId
        }

        // Fall back to TMDB API lookup using tmdbId
        return try {
            when (item.type) {
                ContentItem.ContentType.MOVIE -> {
                    val details = tmdbApiService.getMovieDetails(
                        movieId = item.tmdbId,
                        apiKey = BuildConfig.TMDB_API_KEY
                    )
                    // Try imdbId field first, then external_ids
                    details.imdbId ?: details.externalIds?.imdbId
                }
                ContentItem.ContentType.TV_SHOW -> {
                    val details = tmdbApiService.getShowDetails(
                        showId = item.tmdbId,
                        apiKey = BuildConfig.TMDB_API_KEY
                    )
                    details.externalIds?.imdbId
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SourcesActivity", "Failed to fetch IMDB ID from TMDB", e)
            null
        }
    }

    private fun displayResults(result: ScrapeResult) {
        hideLoading()

        if (result.hasError && !result.hasStreams) {
            showError(result.error ?: "Failed to load sources")
            return
        }

        // Build the list with positions and separator
        val items = mutableListOf<SourceItem>()

        // Add passed streams with positions
        result.streams.forEachIndexed { index, stream ->
            items.add(SourceItem.Stream(stream, index + 1))
        }

        // Add separator and filtered-out streams if any
        if (result.filteredOutStreams.isNotEmpty()) {
            items.add(SourceItem.Separator("Filtered Out (${result.filteredOutStreams.size})"))
            result.filteredOutStreams.forEachIndexed { index, stream ->
                items.add(SourceItem.Stream(stream, result.streams.size + index + 1))
            }
        }

        adapter.submitList(items)

        // Update count text
        val countText = when {
            result.streams.isEmpty() && result.filteredOutStreams.isEmpty() ->
                "No sources found"
            result.streams.isEmpty() ->
                "${result.filteredOutStreams.size} sources (all filtered)"
            result.filteredOutStreams.isEmpty() ->
                "${result.streams.size} sources"
            else ->
                "${result.streams.size} sources (${result.filteredOutStreams.size} filtered)"
        }
        sourcesCount.text = countText

        // Auto-select if enabled and there are streams
        if (result.hasStreams) {
            val autoSelect = torrentioRepository.getAutoselectStream(result)
            if (autoSelect != null) {
                // Could auto-play here or highlight the selected item
                // For now, just focus the first item
                sourcesList.post {
                    sourcesList.requestFocus()
                }
            }
        }
    }

    private fun onSourceSelected(sourceItem: SourceItem.Stream) {
        lifecycleScope.launch {
            showLoading()

            val result = withContext(Dispatchers.IO) {
                torrentioRepository.resolveStream(sourceItem.streamInfo)
            }

            hideLoading()

            result.onSuccess { directUrl ->
                // Launch video player with the direct URL
                playVideo(directUrl, sourceItem.streamInfo)
            }.onFailure { error ->
                Toast.makeText(
                    this@SourcesActivity,
                    "Failed to resolve link: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun playVideo(url: String, streamInfo: StreamInfo) {
        val item = contentItem ?: return

        // Build display title
        val displayTitle = if (season > 0 && episode > 0) {
            "${item.title} - S${season}E${episode}"
        } else {
            item.title
        }

        // Launch video player
        VideoPlayerActivity.start(
            context = this,
            videoUrl = url,
            title = displayTitle,
            logoUrl = item.logoUrl,
            contentItem = item,
            season = season,
            episode = episode,
            resumePositionMs = resumePositionMs
        )
    }

    private fun showLoading() {
        loadingIndicator.visibility = View.VISIBLE
        errorText.visibility = View.GONE
    }

    private fun hideLoading() {
        loadingIndicator.visibility = View.GONE
    }

    private fun showError(message: String) {
        loadingIndicator.visibility = View.GONE
        errorText.visibility = View.VISIBLE
        errorText.text = message
        sourcesCount.text = "Error"
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
