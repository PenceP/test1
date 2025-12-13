package com.strmr.tv.ui.update

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.strmr.tv.BuildConfig
import com.strmr.tv.R
import com.strmr.tv.data.model.UpdateInfo
import com.strmr.tv.update.DownloadState
import com.strmr.tv.update.UpdateDownloadManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class UpdateDialog(
    context: Context,
    private val updateInfo: UpdateInfo,
    private val downloadManager: UpdateDownloadManager,
    private val lifecycleOwner: LifecycleOwner,
    private val onExitApp: () -> Unit
) : Dialog(context, R.style.Theme_Strmr_Dialog_Fullscreen) {

    private lateinit var btnUpdate: MaterialButton
    private lateinit var btnExit: MaterialButton
    private lateinit var versionInfo: TextView
    private lateinit var progressSection: View
    private lateinit var downloadProgress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var buttonSection: View
    private lateinit var permissionWarning: TextView

    private var isDownloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_update_available)

        // Make dialog non-cancelable
        setCancelable(false)
        setCanceledOnTouchOutside(false)

        initViews()
        setupListeners()
        updateVersionInfo()
    }

    private fun initViews() {
        btnUpdate = findViewById(R.id.btn_update)
        btnExit = findViewById(R.id.btn_exit)
        versionInfo = findViewById(R.id.version_info)
        progressSection = findViewById(R.id.progress_section)
        downloadProgress = findViewById(R.id.download_progress)
        progressText = findViewById(R.id.progress_text)
        buttonSection = findViewById(R.id.button_section)
        permissionWarning = findViewById(R.id.permission_warning)

        // Focus on update button by default
        btnUpdate.requestFocus()
    }

    private fun setupListeners() {
        btnUpdate.setOnClickListener {
            if (!isDownloading) {
                checkPermissionAndDownload()
            }
        }

        btnExit.setOnClickListener {
            onExitApp()
        }
    }

    private fun updateVersionInfo() {
        val currentVersion = BuildConfig.VERSION_NAME
        val newVersion = updateInfo.version
        versionInfo.text = context.getString(
            R.string.update_version_info,
            currentVersion,
            newVersion
        )
    }

    private fun checkPermissionAndDownload() {
        if (!downloadManager.canInstallFromUnknownSources()) {
            // Show permission warning and open settings
            permissionWarning.visibility = View.VISIBLE
            btnUpdate.text = context.getString(R.string.open_settings)
            btnUpdate.setOnClickListener {
                context.startActivity(downloadManager.getUnknownSourcesSettingsIntent())
            }
            return
        }

        startDownload()
    }

    private fun startDownload() {
        isDownloading = true

        // Update UI for download state
        buttonSection.visibility = View.GONE
        progressSection.visibility = View.VISIBLE
        permissionWarning.visibility = View.GONE

        lifecycleOwner.lifecycleScope.launch {
            downloadManager.downloadUpdate(updateInfo).collectLatest { state ->
                when (state) {
                    is DownloadState.Idle -> {
                        progressText.text = context.getString(R.string.preparing_download)
                    }

                    is DownloadState.Downloading -> {
                        downloadProgress.progress = state.progress.coerceIn(0, 100)
                        val downloadedMb = state.downloadedBytes / (1024 * 1024)
                        val totalMb = state.totalBytes / (1024 * 1024)
                        progressText.text = if (state.progress >= 0) {
                            context.getString(R.string.downloading_progress, state.progress, downloadedMb, totalMb)
                        } else {
                            context.getString(R.string.downloading_size, downloadedMb)
                        }
                    }

                    is DownloadState.Downloaded -> {
                        progressText.text = context.getString(R.string.download_complete)
                        downloadManager.installApk(context, state.apkFile)
                        // Dialog stays visible - user will see install prompt
                    }

                    is DownloadState.Error -> {
                        isDownloading = false
                        progressSection.visibility = View.GONE
                        buttonSection.visibility = View.VISIBLE
                        btnUpdate.text = context.getString(R.string.retry_download)
                        btnUpdate.setOnClickListener { startDownload() }

                        // Show error
                        progressText.visibility = View.VISIBLE
                        progressText.text = state.message
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Block back button while downloading
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isDownloading) {
                return true // Consume event
            }
            // If not downloading, exit app
            onExitApp()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun onResume() {
        // Re-check permission when returning from settings
        if (permissionWarning.visibility == View.VISIBLE) {
            if (downloadManager.canInstallFromUnknownSources()) {
                permissionWarning.visibility = View.GONE
                btnUpdate.text = context.getString(R.string.download_and_install)
                btnUpdate.setOnClickListener { startDownload() }
            }
        }
    }
}
