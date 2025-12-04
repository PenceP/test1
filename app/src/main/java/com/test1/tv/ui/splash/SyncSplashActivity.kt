package com.test1.tv.ui.splash

import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.test1.tv.MainActivity
import com.test1.tv.R
import com.test1.tv.data.repository.TraktAccountRepository
import com.test1.tv.data.sync.TraktSyncManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

@AndroidEntryPoint
class SyncSplashActivity : FragmentActivity() {

    @Inject lateinit var traktSyncManager: TraktSyncManager
    @Inject lateinit var traktAccountRepository: TraktAccountRepository

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var funMessageText: TextView
    private lateinit var logoTrace: ImageView
    private lateinit var logoFill: ImageView
    private lateinit var bgHex: ImageView

    private val funMessages = listOf(
        "Hacking the mainframe...",
        "Bribing the API gods...",
        "Downloading more RAM...",
        "Calibrating flux capacitors...",
        "Reticulating splines...",
        "Feeding the hamsters...",
        "Convincing servers to cooperate...",
        "Teaching AI to appreciate cinema...",
        "Compiling your taste in movies...",
        "Polishing the posters..."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync_splash)
        LaunchGate.reset()

        // Bind Views
        progressBar = findViewById(R.id.sync_progress)
        statusText = findViewById(R.id.status_text)
        funMessageText = findViewById(R.id.fun_message_text)
        logoTrace = findViewById(R.id.logo_trace)
        logoFill = findViewById(R.id.logo_fill)
        bgHex = findViewById(R.id.bg_hex)

        // Start the Visual Animations
        startLogoAnimations()

        // Start the Logic / Data Loading
        lifecycleScope.launch {
            val messageJob = launch { rotateFunMessages() }

            val hasAccount = withContext(Dispatchers.IO) { traktAccountRepository.getAccount() != null }

            if (!hasAccount) {
                statusText.text = getString(R.string.sync_skip_no_trakt)
                progressBar.progress = 100
            } else {
                traktSyncManager.syncAllWatched { progress, status ->
                    withContext(Dispatchers.Main) {
                        progressBar.progress = (progress * 100).coerceIn(0f, 100f).toInt()
                        statusText.text = status
                    }
                }
            }

            // Small delay to ensure the cool logo animation has at least finished the "Drawing" phase
            // before we jump screens (optional, but looks nicer)
            delay(2000)

            messageJob.cancel()

            startActivity(Intent(this@SyncSplashActivity, MainActivity::class.java))

            // Keep splash visible until home reports ready (first pages loaded)
            LaunchGate.homeReady.first { it }
            finish()
        }
    }

    private fun startLogoAnimations() {
        // 1. Start Background Rotation
        bgHex.animate()
            .alpha(0.2f)
            .setDuration(1000)
            .start()
            
        // Rotate continuously
        val rotateAnim = android.animation.ObjectAnimator.ofFloat(bgHex, "rotation", 0f, 360f)
        rotateAnim.duration = 20000 // 20 seconds for full rotation
        rotateAnim.repeatCount = android.animation.ObjectAnimator.INFINITE
        rotateAnim.interpolator = android.view.animation.LinearInterpolator()
        rotateAnim.start()

        // 2. Trigger the "Trace" animation (AVD)
        val drawable = logoTrace.drawable
        if (drawable is Animatable) {
            drawable.start()
        }

        // 3. Sequence: Fade in the Fill after the trace is mostly done
        lifecycleScope.launch {
            delay(1800) // Wait for trace to near completion
            
            // Fade OUT the trace lines
            logoTrace.animate().alpha(0f).setDuration(500).start()
            
            // Fade IN the solid fill
            logoFill.alpha = 0f
            logoFill.scaleX = 0.9f
            logoFill.scaleY = 0.9f
            logoFill.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(800)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
                
            // Pulse effect on the fill
            delay(800)
            val pulseScaleX = android.animation.ObjectAnimator.ofFloat(logoFill, "scaleX", 1f, 1.05f, 1f)
            val pulseScaleY = android.animation.ObjectAnimator.ofFloat(logoFill, "scaleY", 1f, 1.05f, 1f)
            pulseScaleX.repeatCount = android.animation.ObjectAnimator.INFINITE
            pulseScaleY.repeatCount = android.animation.ObjectAnimator.INFINITE
            pulseScaleX.duration = 2000
            pulseScaleY.duration = 2000
            
            val animatorSet = android.animation.AnimatorSet()
            animatorSet.playTogether(pulseScaleX, pulseScaleY)
            animatorSet.start()
        }
    }

    private suspend fun rotateFunMessages() {
        while (coroutineContext.isActive) {
            delay(2500)
            funMessageText.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    funMessageText.text = funMessages.random()
                    funMessageText.animate().alpha(1f).setDuration(200).start()
                }
                .start()
        }
    }
}