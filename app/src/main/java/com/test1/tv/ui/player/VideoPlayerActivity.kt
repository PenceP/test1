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
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.test1.tv.R
import com.test1.tv.data.local.entity.PlayerSettings
import com.test1.tv.data.model.ContentItem
import com.test1.tv.data.repository.PlayerSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
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

        private const val LOGO_FADE_DURATION = 1500L
        private const val LOGO_DISPLAY_DURATION = 2000L

        fun start(
            context: Context,
            videoUrl: String,
            title: String,
            logoUrl: String? = null,
            contentItem: ContentItem? = null,
            season: Int = -1,
            episode: Int = -1
        ) {
            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_LOGO_URL, logoUrl)
                putExtra(EXTRA_CONTENT_ITEM, contentItem)
                putExtra(EXTRA_SEASON, season)
                putExtra(EXTRA_EPISODE, episode)
            }
            context.startActivity(intent)
        }
    }

    @Inject
    lateinit var playerSettingsRepository: PlayerSettingsRepository

    private lateinit var playerView: PlayerView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var loadingLogo: ImageView
    private lateinit var loadingText: TextView
    private lateinit var errorOverlay: FrameLayout
    private lateinit var errorMessage: TextView

    private var player: ExoPlayer? = null
    private var playerSettings: PlayerSettings = PlayerSettings.default()

    private var videoUrl: String = ""
    private var title: String = ""
    private var logoUrl: String? = null
    private var contentItem: ContentItem? = null
    private var season: Int = -1
    private var episode: Int = -1

    private val handler = Handler(Looper.getMainLooper())
    private var logoAnimator: AnimatorSet? = null
    private var isLogoAnimationRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        hideSystemUI()
        parseIntent()
        initializeViews()
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
    }

    private fun initializeViews() {
        playerView = findViewById(R.id.player_view)
        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingLogo = findViewById(R.id.loading_logo)
        loadingText = findViewById(R.id.loading_text)
        errorOverlay = findViewById(R.id.error_overlay)
        errorMessage = findViewById(R.id.error_message)

        // Set title in loading text
        loadingText.text = "Loading $title..."
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            playerSettings = playerSettingsRepository.getSettings()
        }
    }

    private fun showLoadingWithLogo() {
        loadingOverlay.visibility = View.VISIBLE
        errorOverlay.visibility = View.GONE

        // Load logo if available
        val logoToLoad = logoUrl ?: contentItem?.logoUrl
        if (!logoToLoad.isNullOrBlank()) {
            Glide.with(this)
                .load(logoToLoad)
                .into(loadingLogo)
            startLogoFadeAnimation()
        } else {
            // No logo, just show loading text
            loadingLogo.visibility = View.GONE
        }
    }

    private fun startLogoFadeAnimation() {
        if (isLogoAnimationRunning) return
        isLogoAnimationRunning = true

        // Continuous fade in/out animation
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
                        start() // Loop the animation
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

        player = ExoPlayer.Builder(this)
            .build()
            .also { exoPlayer ->
                playerView.player = exoPlayer

                // Set up media item
                val mediaItem = MediaItem.Builder()
                    .setUri(videoUrl)
                    .build()

                exoPlayer.setMediaItem(mediaItem)

                // Add listener for playback state
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                hideLoading()
                            }
                            Player.STATE_BUFFERING -> {
                                // Keep loading visible during buffering
                            }
                            Player.STATE_ENDED -> {
                                onPlaybackEnded()
                            }
                            Player.STATE_IDLE -> {
                                // Idle state
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        showError("Playback error: ${error.message}")
                    }
                })

                // Prepare and play
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
    }

    private fun hideLoading() {
        stopLogoAnimation()

        // Fade out the loading overlay
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                loadingOverlay.visibility = View.GONE
                loadingOverlay.alpha = 1f
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
        // TODO: Handle playback ended - show autoplay next episode if TV show
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                seekBackward()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                seekForward()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                releasePlayer()
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun seekForward() {
        player?.let {
            val seekAmount = getSeekAmount(true)
            val newPosition = (it.currentPosition + seekAmount).coerceAtMost(it.duration)
            it.seekTo(newPosition)
        }
    }

    private fun seekBackward() {
        player?.let {
            val seekAmount = getSeekAmount(false)
            val newPosition = (it.currentPosition - seekAmount).coerceAtLeast(0)
            it.seekTo(newPosition)
        }
    }

    private fun getSeekAmount(forward: Boolean): Long {
        // For now, use instant skip (10 seconds)
        // Adaptive skip will be implemented in a later sprint
        return 10_000L
    }

    private fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    private fun releasePlayer() {
        player?.let {
            it.release()
        }
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
        releasePlayer()
        handler.removeCallbacksAndMessages(null)
    }
}
