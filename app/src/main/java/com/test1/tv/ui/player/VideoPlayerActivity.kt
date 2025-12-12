package com.test1.tv.ui.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.ui.PlayerView
import com.test1.tv.Test1App
import com.bumptech.glide.Glide
import com.test1.tv.R
import com.test1.tv.data.local.entity.PlayerSettings
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.repository.PlayerSettingsRepository
import com.test1.tv.data.repository.TraktScrobbleRepository
import com.test1.tv.data.subtitle.SubtitleManager
import com.test1.tv.data.subtitle.SubtitleOption
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VideoPlayerActivity : FragmentActivity() {

    companion object {
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_LOGO_URL = "logo_url"
        const val EXTRA_CONTENT_ITEM = "content_item"
        const val EXTRA_SEASON = "season"
        const val EXTRA_EPISODE = "episode"
        const val EXTRA_RESUME_POSITION_MS = "resume_position_ms"

        private const val LOGO_FADE_DURATION = 1500L
        private const val LOGO_DISPLAY_DURATION = 2000L
        private const val CONTROLS_HIDE_DELAY = 5000L
        private const val PROGRESS_UPDATE_INTERVAL = 1000L

        fun start(
            context: Context,
            videoUrl: String,
            title: String,
            logoUrl: String? = null,
            contentItem: ContentItem? = null,
            season: Int = -1,
            episode: Int = -1,
            resumePositionMs: Long = 0L
        ) {
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_LOGO_URL, logoUrl)
                putExtra(EXTRA_CONTENT_ITEM, contentItem)
                putExtra(EXTRA_SEASON, season)
                putExtra(EXTRA_EPISODE, episode)
                putExtra(EXTRA_RESUME_POSITION_MS, resumePositionMs)
            }
            context.startActivity(intent)
        }
    }

    @Inject
    lateinit var playerSettingsRepository: PlayerSettingsRepository

    @Inject
    lateinit var traktScrobbleRepository: TraktScrobbleRepository

    @Inject
    lateinit var subtitleManager: SubtitleManager

    // Core views
    private lateinit var playerView: PlayerView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var loadingLogo: ImageView
    private lateinit var loadingText: TextView
    private lateinit var errorOverlay: FrameLayout
    private lateinit var errorMessage: TextView
    private lateinit var skipIndicator: TextView
    private lateinit var bufferingIndicator: ProgressBar

    // Custom controls
    private lateinit var controlsRoot: View
    private lateinit var topBar: LinearLayout
    private lateinit var bottomControls: LinearLayout
    private lateinit var videoTitle: TextView
    private lateinit var episodeInfo: TextView
    private lateinit var timeCurrent: TextView
    private lateinit var timeTotal: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnSubtitles: ImageButton
    private lateinit var btnAudio: ImageButton
    private lateinit var btnQuality: ImageButton
    private lateinit var centerPlayPause: ImageView

    private var player: ExoPlayer? = null
    private var playerSettings: PlayerSettings = PlayerSettings.default()

    private var videoUrl: String = ""
    private var title: String = ""
    private var logoUrl: String? = null
    private var contentItem: ContentItem? = null
    private var season: Int = -1
    private var episode: Int = -1
    private var resumePositionMs: Long = 0L
    private var hasResumed: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private var logoAnimator: AnimatorSet? = null
    private var isLogoAnimationRunning = false
    private var progressUpdateJob: Job? = null

    // Controls visibility
    private var areControlsVisible = false
    private val hideControlsRunnable = Runnable { hideControls() }

    // Seeking state
    private var isSeeking = false

    // Scrobbling
    private var scrobbleJob: Job? = null
    private var hasStartedScrobble = false
    private val scrobbleUpdateIntervalMs = 30_000L  // Update every 30 seconds

    // Adaptive skip tracking
    private var lastSkipTime: Long = 0
    private var currentSkipIndex: Int = 0
    private val adaptiveSkipAmounts = listOf(5_000L, 10_000L, 30_000L, 60_000L, 300_000L, 600_000L)
    private val skipResetDelay = 2000L

    // Subtitle state
    private var availableSubtitles: List<SubtitleOption> = emptyList()
    private var currentSubtitleSelection: SubtitleOption? = null
    private var subtitleSearchJob: Job? = null
    private var isLoadingSubtitles = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        hideSystemUI()
        parseIntent()
        initializeViews()
        setupCustomControls()
        loadSettings()
        showLoadingWithLogo()
        initializePlayer()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    private fun parseIntent() {
        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        logoUrl = intent.getStringExtra(EXTRA_LOGO_URL)
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

    private fun initializeViews() {
        playerView = findViewById(R.id.player_view)
        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingLogo = findViewById(R.id.loading_logo)
        loadingText = findViewById(R.id.loading_text)
        errorOverlay = findViewById(R.id.error_overlay)
        errorMessage = findViewById(R.id.error_message)
        skipIndicator = findViewById(R.id.skip_indicator)
        bufferingIndicator = findViewById(R.id.buffering_indicator)

        // Custom controls
        controlsRoot = findViewById(R.id.player_controls)
        topBar = controlsRoot.findViewById(R.id.top_bar)
        bottomControls = controlsRoot.findViewById(R.id.bottom_controls)
        videoTitle = controlsRoot.findViewById(R.id.video_title)
        episodeInfo = controlsRoot.findViewById(R.id.episode_info)
        timeCurrent = controlsRoot.findViewById(R.id.time_current)
        timeTotal = controlsRoot.findViewById(R.id.time_total)
        seekBar = controlsRoot.findViewById(R.id.seek_bar)
        btnPlayPause = controlsRoot.findViewById(R.id.btn_play_pause)
        btnSubtitles = controlsRoot.findViewById(R.id.btn_subtitles)
        btnAudio = controlsRoot.findViewById(R.id.btn_audio)
        btnQuality = controlsRoot.findViewById(R.id.btn_quality)
        centerPlayPause = controlsRoot.findViewById(R.id.center_play_pause)

        loadingText.text = "Loading $title..."
    }

    private fun setupCustomControls() {
        // Set title
        videoTitle.text = title

        // Set episode info if available
        if (season > 0 && episode > 0) {
            episodeInfo.visibility = View.VISIBLE
            episodeInfo.text = "S$season E$episode"
        } else {
            episodeInfo.visibility = View.GONE
        }

        // Play/Pause button
        btnPlayPause.setOnClickListener {
            togglePlayPause()
            resetControlsHideTimer()
        }

        // Subtitle button
        btnSubtitles.setOnClickListener {
            showSubtitleSelectionDialog()
            resetControlsHideTimer()
        }

        // Audio button
        btnAudio.setOnClickListener {
            showAudioTrackSelectionDialog()
            resetControlsHideTimer()
        }

        // Quality button
        btnQuality.setOnClickListener {
            showQualitySelectionDialog()
            resetControlsHideTimer()
        }

        // SeekBar listener
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player?.let {
                        val duration = it.duration
                        if (duration > 0) {
                            timeCurrent.text = formatTime((progress.toLong() * duration) / 1000)
                        }
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
                handler.removeCallbacks(hideControlsRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                seekBar?.let { sb ->
                    player?.let {
                        val duration = it.duration
                        if (duration > 0) {
                            val newPosition = (sb.progress.toLong() * duration) / 1000
                            it.seekTo(newPosition)
                        }
                    }
                }
                resetControlsHideTimer()
            }
        })

        // SeekBar key listener for D-pad scrubbing
        seekBar.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                player?.let { p ->
                    val duration = p.duration
                    if (duration <= 0) return@setOnKeyListener false

                    val seekStep = duration / 100 // 1% of total duration per press
                    val currentPos = p.currentPosition

                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            val newPos = (currentPos - seekStep).coerceAtLeast(0)
                            p.seekTo(newPos)
                            seekBar.progress = ((newPos * 1000) / duration).toInt()
                            timeCurrent.text = formatTime(newPos)
                            resetControlsHideTimer()
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val newPos = (currentPos + seekStep).coerceAtMost(duration)
                            p.seekTo(newPos)
                            seekBar.progress = ((newPos * 1000) / duration).toInt()
                            timeCurrent.text = formatTime(newPos)
                            resetControlsHideTimer()
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            false
        }

        // Initially hide controls
        topBar.visibility = View.GONE
        bottomControls.visibility = View.GONE
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            playerSettings = playerSettingsRepository.getSettings()
        }
    }

    private fun showLoadingWithLogo() {
        loadingOverlay.visibility = View.VISIBLE
        errorOverlay.visibility = View.GONE

        val logoToLoad = logoUrl ?: contentItem?.logoUrl
        if (!logoToLoad.isNullOrBlank()) {
            Glide.with(this)
                .load(logoToLoad)
                .into(loadingLogo)
            startLogoFadeAnimation()
        } else {
            loadingLogo.visibility = View.GONE
        }
    }

    private fun startLogoFadeAnimation() {
        if (isLogoAnimationRunning) return
        isLogoAnimationRunning = true

        val fadeIn = ObjectAnimator.ofFloat(loadingLogo, View.ALPHA, 0f, 1f).apply {
            duration = LOGO_FADE_DURATION
        }

        val fadeOut = ObjectAnimator.ofFloat(loadingLogo, View.ALPHA, 1f, 0f).apply {
            duration = LOGO_FADE_DURATION
            startDelay = LOGO_DISPLAY_DURATION
        }

        logoAnimator = AnimatorSet().apply {
            playSequentially(fadeIn, fadeOut)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isLogoAnimationRunning && loadingOverlay.visibility == View.VISIBLE) {
                        start()
                    }
                }
            })
            start()
        }
    }

    private fun stopLogoAnimation() {
        isLogoAnimationRunning = false
        logoAnimator?.cancel()
        logoAnimator = null
    }

    private fun initializePlayer() {
        if (videoUrl.isBlank()) {
            showError("No video URL provided")
            return
        }

        // Configure renderers factory with decoder settings
        val renderersFactory = createRenderersFactory()

        // Configure track selector with tunneling support
        val trackSelector = createTrackSelector()

        // Configure buffer/load control
        val loadControl = createLoadControl()

        // Configure media source factory with caching
        val mediaSourceFactory = createMediaSourceFactory()

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { exoPlayer ->
                playerView.player = exoPlayer

                val mediaItem = MediaItem.Builder()
                    .setUri(videoUrl)
                    .build()

                exoPlayer.setMediaItem(mediaItem)

                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                hideLoading()
                                bufferingIndicator.visibility = View.GONE
                                updateDuration()
                                startProgressUpdates()

                                // Seek to resume position if specified
                                if (!hasResumed && resumePositionMs > 0) {
                                    exoPlayer.seekTo(resumePositionMs)
                                    hasResumed = true
                                    android.util.Log.d("VideoPlayerActivity", "Resumed playback at ${resumePositionMs}ms")
                                }

                                // Start scrobbling when ready to play
                                if (exoPlayer.playWhenReady && !hasStartedScrobble) {
                                    startScrobbling()
                                }
                            }
                            Player.STATE_BUFFERING -> {
                                if (loadingOverlay.visibility != View.VISIBLE) {
                                    bufferingIndicator.visibility = View.VISIBLE
                                }
                            }
                            Player.STATE_ENDED -> {
                                onPlaybackEnded()
                            }
                            Player.STATE_IDLE -> {
                                // Idle
                            }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updatePlayPauseButton(isPlaying)

                        // Handle pause/resume for scrobbling
                        if (!isPlaying && exoPlayer.playbackState == Player.STATE_READY) {
                            // Paused - report to Trakt
                            reportScrobblePause()
                        } else if (isPlaying && hasStartedScrobble) {
                            // Resumed - restart scrobble updates
                            startScrobbleUpdates()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        showError("Playback error: ${error.message}")
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        // Update button states based on available tracks
                        updateTrackButtonStates(tracks)
                    }
                })

                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
    }

    /**
     * Creates RenderersFactory with decoder mode settings.
     * - AUTO: Use hardware decoders when available, fall back to software
     * - HW_ONLY: Prefer hardware decoders only
     * - SW_PREFER: Prefer software decoders (useful for problematic hardware)
     */
    private fun createRenderersFactory(): DefaultRenderersFactory {
        return DefaultRenderersFactory(this).apply {
            // Configure extension renderer mode based on settings
            val extensionMode = when {
                playerSettings.isSoftwarePreferred() ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            }
            setExtensionRendererMode(extensionMode)
            setEnableDecoderFallback(true)
        }
    }

    /**
     * Creates TrackSelector with tunneling support for 4K/HDR playback.
     * Tunneling bypasses Android's audio/video synchronization for better performance.
     */
    private fun createTrackSelector(): DefaultTrackSelector {
        return DefaultTrackSelector(this).apply {
            parameters = buildUponParameters()
                .setTunnelingEnabled(playerSettings.tunnelingEnabled)
                .setMaxVideoSize(3840, 2160) // Support up to 4K
                .setMaxVideoFrameRate(60)
                .build()
        }
    }

    /**
     * Creates LoadControl with custom buffer settings.
     * Optimized for streaming with reasonable startup time and smooth playback.
     */
    private fun createLoadControl(): DefaultLoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                playerSettings.minBufferMs,
                playerSettings.maxBufferMs,
                playerSettings.bufferForPlaybackMs,
                playerSettings.bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    /**
     * Creates MediaSourceFactory with caching support.
     * Caches video segments for improved seek performance and reduced bandwidth.
     */
    private fun createMediaSourceFactory(): DefaultMediaSourceFactory {
        // Create HTTP data source factory
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)

        // Wrap with cache data source for segment caching
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(Test1App.getMediaCache(application))
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return DefaultMediaSourceFactory(this)
            .setDataSourceFactory(cacheDataSourceFactory)
    }

    private fun updateDuration() {
        player?.let {
            if (it.duration > 0) {
                timeTotal.text = formatTime(it.duration)
                seekBar.max = 1000
            }
        }
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = lifecycleScope.launch {
            while (isActive) {
                updateProgress()
                delay(PROGRESS_UPDATE_INTERVAL)
            }
        }
    }

    private fun updateProgress() {
        if (isSeeking) return

        player?.let {
            val position = it.currentPosition
            val duration = it.duration
            val buffered = it.bufferedPosition

            if (duration > 0) {
                timeCurrent.text = formatTime(position)
                seekBar.progress = ((position * 1000) / duration).toInt()
                seekBar.secondaryProgress = ((buffered * 1000) / duration).toInt()
            }
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun updateTrackButtonStates(tracks: Tracks) {
        var hasSubtitles = false
        var hasMultipleAudio = false
        var hasMultipleVideo = false

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_TEXT -> hasSubtitles = true
                C.TRACK_TYPE_AUDIO -> if (group.length > 1) hasMultipleAudio = true
                C.TRACK_TYPE_VIDEO -> if (group.length > 1) hasMultipleVideo = true
            }
        }

        // Always show buttons but adjust alpha for availability
        btnSubtitles.alpha = if (hasSubtitles) 1.0f else 0.5f
        btnAudio.alpha = if (hasMultipleAudio) 1.0f else 0.5f
        btnQuality.alpha = if (hasMultipleVideo) 1.0f else 0.5f
    }

    private fun showControls() {
        if (areControlsVisible) {
            resetControlsHideTimer()
            return
        }

        areControlsVisible = true

        topBar.visibility = View.VISIBLE
        bottomControls.visibility = View.VISIBLE
        topBar.alpha = 0f
        bottomControls.alpha = 0f

        topBar.animate().alpha(1f).setDuration(200).start()
        bottomControls.animate()
            .alpha(1f)
            .setDuration(200)
            .withEndAction {
                // Request focus on seekbar when controls appear
                seekBar.requestFocus()
            }
            .start()

        resetControlsHideTimer()
    }

    private fun hideControls() {
        if (!areControlsVisible) return

        areControlsVisible = false

        topBar.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction { topBar.visibility = View.GONE }
            .start()

        bottomControls.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction { bottomControls.visibility = View.GONE }
            .start()
    }

    private fun toggleControls() {
        if (areControlsVisible) {
            hideControls()
        } else {
            showControls()
        }
    }

    private fun resetControlsHideTimer() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY)
    }

    private fun hideLoading() {
        stopLogoAnimation()

        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                loadingOverlay.visibility = View.GONE
                loadingOverlay.alpha = 1f
                showControls()
            }
            .start()
    }

    private fun showError(message: String) {
        stopLogoAnimation()
        loadingOverlay.visibility = View.GONE
        errorOverlay.visibility = View.VISIBLE
        errorMessage.text = message
    }

    private fun onPlaybackEnded() {
        // Stop scrobbling and save final progress
        stopScrobbling()

        // TODO: Handle playback ended - show autoplay next episode if TV show
        finish()
    }

    // ==================== Scrobbling & Progress Tracking ====================

    /**
     * Start scrobbling to Trakt when playback begins
     */
    private fun startScrobbling() {
        val item = contentItem ?: return
        hasStartedScrobble = true

        lifecycleScope.launch {
            traktScrobbleRepository.startWatching(
                contentItem = item,
                season = season.takeIf { it > 0 },
                episode = episode.takeIf { it > 0 }
            )
        }

        // Start periodic scrobble updates
        startScrobbleUpdates()
    }

    /**
     * Start periodic scrobble updates (every 30 seconds)
     */
    private fun startScrobbleUpdates() {
        scrobbleJob?.cancel()
        scrobbleJob = lifecycleScope.launch {
            while (isActive) {
                delay(scrobbleUpdateIntervalMs)
                reportScrobbleProgress()
            }
        }
    }

    /**
     * Report current progress to Trakt
     */
    private fun reportScrobbleProgress() {
        val item = contentItem ?: return
        val p = player ?: return

        lifecycleScope.launch {
            traktScrobbleRepository.updateProgress(
                contentItem = item,
                currentPositionMs = p.currentPosition,
                durationMs = p.duration,
                isPaused = false,
                season = season.takeIf { it > 0 },
                episode = episode.takeIf { it > 0 }
            )
        }
    }

    /**
     * Report pause to Trakt
     */
    private fun reportScrobblePause() {
        val item = contentItem ?: return
        val p = player ?: return

        // Cancel ongoing updates
        scrobbleJob?.cancel()

        lifecycleScope.launch {
            traktScrobbleRepository.updateProgress(
                contentItem = item,
                currentPositionMs = p.currentPosition,
                durationMs = p.duration,
                isPaused = true,
                season = season.takeIf { it > 0 },
                episode = episode.takeIf { it > 0 }
            )
        }
    }

    /**
     * Stop scrobbling when playback ends or user exits
     */
    private fun stopScrobbling() {
        scrobbleJob?.cancel()

        val item = contentItem ?: return
        val p = player ?: return

        // Capture position on main thread before launching background coroutine
        // ExoPlayer MUST be accessed from main thread only
        val currentPosition = p.currentPosition
        val duration = p.duration

        // Fire and forget - don't block exit
        CoroutineScope(Dispatchers.IO).launch {
            traktScrobbleRepository.stopWatching(
                contentItem = item,
                currentPositionMs = currentPosition,
                durationMs = duration,
                season = season.takeIf { it > 0 },
                episode = episode.takeIf { it > 0 }
            )
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                // If controls visible, let focus system handle navigation
                if (areControlsVisible) {
                    resetControlsHideTimer()
                    return super.onKeyDown(keyCode, event)
                }
                seekBackward()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // If controls visible, let focus system handle navigation
                if (areControlsVisible) {
                    resetControlsHideTimer()
                    return super.onKeyDown(keyCode, event)
                }
                seekForward()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (!areControlsVisible) {
                    showControls()
                    return true
                }
                // Let focused view handle center press
                resetControlsHideTimer()
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!areControlsVisible) {
                    showControls()
                    return true
                }
                // Let focus system handle up/down navigation
                resetControlsHideTimer()
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_BACK -> {
                if (areControlsVisible) {
                    hideControls()
                    return true
                }
                releasePlayer()
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun seekForward() {
        player?.let {
            val seekAmount = getSeekAmount(forward = true)
            val newPosition = (it.currentPosition + seekAmount).coerceAtMost(it.duration)
            it.seekTo(newPosition)
            showSkipIndicator("+${formatSkipTime(seekAmount)}")
        }
    }

    private fun seekBackward() {
        player?.let {
            val seekAmount = getSeekAmount(forward = false)
            val newPosition = (it.currentPosition - seekAmount).coerceAtLeast(0)
            it.seekTo(newPosition)
            showSkipIndicator("-${formatSkipTime(seekAmount)}")
        }
    }

    private fun getSeekAmount(forward: Boolean): Long {
        return if (playerSettings.isAdaptiveSkip()) {
            getAdaptiveSkipAmount()
        } else {
            10_000L
        }
    }

    private fun getAdaptiveSkipAmount(): Long {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastSkipTime > skipResetDelay) {
            currentSkipIndex = 0
        } else {
            if (currentSkipIndex < adaptiveSkipAmounts.size - 1) {
                currentSkipIndex++
            }
        }

        lastSkipTime = currentTime
        return adaptiveSkipAmounts[currentSkipIndex]
    }

    private fun showSkipIndicator(text: String) {
        handler.removeCallbacksAndMessages(skipIndicator)

        skipIndicator.text = text
        skipIndicator.alpha = 1f
        skipIndicator.visibility = View.VISIBLE

        handler.postDelayed({
            skipIndicator.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    skipIndicator.visibility = View.GONE
                }
                .start()
        }, 1000)
    }

    private fun formatSkipTime(millis: Long): String {
        val seconds = millis / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m"
            else -> "${seconds / 3600}h"
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    private fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
                showCenterPlayPauseIndicator(false)
            } else {
                it.play()
                showCenterPlayPauseIndicator(true)
            }
        }
    }

    private fun showCenterPlayPauseIndicator(isPlaying: Boolean) {
        centerPlayPause.setImageResource(if (isPlaying) R.drawable.ic_play else R.drawable.ic_pause)
        centerPlayPause.visibility = View.VISIBLE
        centerPlayPause.alpha = 0f

        centerPlayPause.animate()
            .alpha(1f)
            .setDuration(100)
            .withEndAction {
                centerPlayPause.animate()
                    .alpha(0f)
                    .setStartDelay(300)
                    .setDuration(200)
                    .withEndAction {
                        centerPlayPause.visibility = View.GONE
                    }
                    .start()
            }
            .start()
    }

    // Track Selection Dialogs

    // Active subtitle dialog reference for updates
    private var activeSubtitleDialog: SubtitleSelectionDialog? = null

    /**
     * Show enhanced subtitle selection dialog with embedded and external subtitles
     */
    private fun showSubtitleSelectionDialog() {
        val player = this.player ?: return

        // If we haven't loaded subtitles yet or need to refresh, load them
        if (availableSubtitles.isEmpty() && !isLoadingSubtitles) {
            loadAvailableSubtitles()
        }

        // Show the dialog with current data (may be loading)
        val dialog = SubtitleSelectionDialog(
            context = this,
            subtitleOptions = availableSubtitles,
            currentSelection = currentSubtitleSelection,
            isLoading = isLoadingSubtitles,
            onSubtitleSelected = { option ->
                applySubtitleSelection(option)
            }
        )
        activeSubtitleDialog = dialog
        dialog.setOnDismissListener { activeSubtitleDialog = null }
        dialog.show()
    }

    /**
     * Load available subtitles (embedded + external from OpenSubtitles)
     */
    private fun loadAvailableSubtitles() {
        val player = this.player ?: return

        subtitleSearchJob?.cancel()
        isLoadingSubtitles = true

        subtitleSearchJob = lifecycleScope.launch {
            val tracks = player.currentTracks

            availableSubtitles = subtitleManager.getAvailableSubtitles(
                tracks = tracks,
                contentItem = contentItem,
                season = season.takeIf { it > 0 },
                episode = episode.takeIf { it > 0 }
            )

            isLoadingSubtitles = false

            android.util.Log.d("VideoPlayerActivity", "Loaded ${availableSubtitles.size} subtitle options")

            // If dialog is still showing, dismiss it and show updated one
            activeSubtitleDialog?.let { dialog ->
                if (dialog.isShowing) {
                    dialog.dismiss()
                    showSubtitleSelectionDialog()
                }
            }
        }
    }

    /**
     * Apply the selected subtitle option
     */
    private fun applySubtitleSelection(option: SubtitleOption) {
        val player = this.player ?: return
        currentSubtitleSelection = option

        when (option) {
            is SubtitleOption.Off -> {
                // Disable all text tracks
                val params = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                player.trackSelectionParameters = params
                android.util.Log.d("VideoPlayerActivity", "Subtitles disabled")
            }

            is SubtitleOption.Embedded -> {
                // Enable embedded subtitle track
                val tracks = player.currentTracks
                val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }

                if (option.groupIndex < textGroups.size) {
                    val group = textGroups[option.groupIndex]
                    val override = TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)

                    val params = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(override)
                        .build()
                    player.trackSelectionParameters = params
                    android.util.Log.d("VideoPlayerActivity", "Selected embedded subtitle: ${option.label}")
                }
            }

            is SubtitleOption.External -> {
                // Download and apply external subtitle
                applyExternalSubtitle(option.subtitle)
            }
        }
    }

    /**
     * Download and apply an external subtitle file
     */
    private fun applyExternalSubtitle(subtitle: com.test1.tv.data.subtitle.SubtitleResult) {
        val player = this.player ?: return

        lifecycleScope.launch {
            // Show loading toast
            android.widget.Toast.makeText(
                this@VideoPlayerActivity,
                "Loading subtitle...",
                android.widget.Toast.LENGTH_SHORT
            ).show()

            subtitleManager.downloadSubtitle(subtitle)
                .onSuccess { file ->
                    // Create subtitle configuration
                    val subtitleConfig = subtitleManager.createSubtitleConfiguration(
                        subtitleFile = file,
                        language = subtitle.languageCode,
                        label = subtitle.language
                    )

                    // Rebuild media item with external subtitle
                    val currentPosition = player.currentPosition
                    val wasPlaying = player.isPlaying

                    val currentMediaItem = player.currentMediaItem ?: return@launch
                    val newMediaItem = currentMediaItem.buildUpon()
                        .setSubtitleConfigurations(listOf(subtitleConfig))
                        .build()

                    player.setMediaItem(newMediaItem)
                    player.prepare()
                    player.seekTo(currentPosition)
                    if (wasPlaying) player.play()

                    // Enable the subtitle track
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguage(subtitle.languageCode)
                        .build()

                    android.util.Log.d("VideoPlayerActivity", "Applied external subtitle: ${subtitle.fileName}")
                }
                .onFailure { error ->
                    // Provide user-friendly error message for common cases
                    val errorMessage = when {
                        error.message?.contains("503") == true ||
                        error.message?.contains("Service") == true ->
                            "OpenSubtitles is temporarily unavailable. Please try again later."
                        error.message?.contains("429") == true ->
                            "Too many requests. Please wait a moment and try again."
                        else -> "Failed to load subtitle: ${error.message}"
                    }
                    android.widget.Toast.makeText(
                        this@VideoPlayerActivity,
                        errorMessage,
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    android.util.Log.e("VideoPlayerActivity", "Failed to apply subtitle", error)
                }
        }
    }

    /**
     * Legacy method for simple track selection (used by audio and quality)
     */
    private fun showSubtitleSelectionDialogLegacy() {
        val player = this.player ?: return
        val tracks = player.currentTracks

        val subtitleTracks = mutableListOf<Pair<String, TrackSelectionOverride?>>()
        subtitleTracks.add("Off" to null)

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val label = format.label ?: format.language ?: "Track ${trackIndex + 1}"
                    val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                    subtitleTracks.add(label to override)
                }
            }
        }

        showTrackSelectionDialog("Subtitles", subtitleTracks, C.TRACK_TYPE_TEXT)
    }

    private fun showAudioTrackSelectionDialog() {
        val player = this.player ?: return
        val tracks = player.currentTracks

        val audioTracks = mutableListOf<Pair<String, TrackSelectionOverride?>>()

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val label = buildString {
                        append(format.label ?: format.language ?: "Track ${trackIndex + 1}")
                        format.channelCount.takeIf { it > 0 }?.let { append(" (${it}ch)") }
                    }
                    val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                    audioTracks.add(label to override)
                }
            }
        }

        if (audioTracks.isEmpty()) return
        showTrackSelectionDialog("Audio Track", audioTracks, C.TRACK_TYPE_AUDIO)
    }

    private fun showQualitySelectionDialog() {
        val player = this.player ?: return
        val tracks = player.currentTracks

        val videoTracks = mutableListOf<Pair<String, TrackSelectionOverride?>>()
        videoTracks.add("Auto" to null)

        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val label = "${format.height}p"
                    val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                    videoTracks.add(label to override)
                }
            }
        }

        showTrackSelectionDialog("Quality", videoTracks, C.TRACK_TYPE_VIDEO)
    }

    private fun showTrackSelectionDialog(
        title: String,
        tracks: List<Pair<String, TrackSelectionOverride?>>,
        trackType: Int
    ) {
        val player = this.player ?: return

        val labels = tracks.map { it.first }.toTypedArray()

        android.app.AlertDialog.Builder(this, R.style.ContextMenuDialogTheme)
            .setTitle(title)
            .setItems(labels) { _, which ->
                val selectedOverride = tracks[which].second

                val currentParams = player.trackSelectionParameters
                val newParams = if (selectedOverride != null) {
                    currentParams.buildUpon()
                        .setOverrideForType(selectedOverride)
                        .setTrackTypeDisabled(trackType, false)
                        .build()
                } else {
                    // "Off" for subtitles, "Auto" for video
                    if (trackType == C.TRACK_TYPE_TEXT) {
                        currentParams.buildUpon()
                            .setTrackTypeDisabled(trackType, true)
                            .build()
                    } else {
                        currentParams.buildUpon()
                            .clearOverridesOfType(trackType)
                            .build()
                    }
                }
                player.trackSelectionParameters = newParams
                resetControlsHideTimer()
            }
            .show()
    }

    private fun releasePlayer() {
        progressUpdateJob?.cancel()
        player?.release()
        player = null
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT > 23) {
            player?.playWhenReady = true
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        if (Build.VERSION.SDK_INT <= 23) {
            player?.playWhenReady = true
        }
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT <= 23) {
            player?.playWhenReady = false
        }
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT > 23) {
            player?.playWhenReady = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLogoAnimation()

        // Cancel subtitle search if running
        subtitleSearchJob?.cancel()

        // Stop scrobbling before releasing player
        if (hasStartedScrobble) {
            stopScrobbling()
        }

        releasePlayer()
        handler.removeCallbacksAndMessages(null)
    }
}
