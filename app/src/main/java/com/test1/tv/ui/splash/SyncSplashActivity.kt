package com.test1.tv.ui.splash

import android.content.Intent
import android.os.Bundle
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

        progressBar = findViewById(R.id.sync_progress)
        statusText = findViewById(R.id.status_text)
        funMessageText = findViewById(R.id.fun_message_text)

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

            messageJob.cancel()

            startActivity(Intent(this@SyncSplashActivity, MainActivity::class.java))

            // Keep splash visible until home reports ready (first pages loaded)
            LaunchGate.homeReady.first { it }
            finish()
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
