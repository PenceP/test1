package com.strmr.tv.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.strmr.tv.BuildConfig
import com.strmr.tv.R
import com.strmr.tv.data.model.UpdateCheckResult
import com.strmr.tv.data.model.UpdateInfo
import com.strmr.tv.data.repository.UpdateRepository
import com.strmr.tv.ui.update.UpdateDialog
import com.strmr.tv.update.UpdateDownloadManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AboutFragment : Fragment() {

    @Inject lateinit var updateRepository: UpdateRepository
    @Inject lateinit var downloadManager: UpdateDownloadManager

    private lateinit var checkUpdateButton: MaterialButton
    private lateinit var updateProgress: ProgressBar
    private lateinit var updateStatusText: TextView

    private var updateDialog: UpdateDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        populateVersionInfo(view)
        setupCheckUpdateButton()
    }

    private fun initViews(view: View) {
        checkUpdateButton = view.findViewById(R.id.btn_check_update)
        updateProgress = view.findViewById(R.id.update_check_progress)
        updateStatusText = view.findViewById(R.id.update_status_text)
    }

    private fun populateVersionInfo(view: View) {
        view.findViewById<TextView>(R.id.version_name).text =
            getString(R.string.version_format, BuildConfig.VERSION_NAME)
        view.findViewById<TextView>(R.id.version_code).text =
            getString(R.string.build_format, BuildConfig.VERSION_CODE)
    }

    private fun setupCheckUpdateButton() {
        checkUpdateButton.setOnClickListener {
            checkForUpdates()
        }

        // Request focus for D-pad navigation
        checkUpdateButton.requestFocus()
    }

    private fun checkForUpdates() {
        // Show loading state
        checkUpdateButton.isEnabled = false
        updateProgress.visibility = View.VISIBLE
        updateStatusText.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val result = updateRepository.checkForUpdate()

            // Hide loading
            updateProgress.visibility = View.GONE
            checkUpdateButton.isEnabled = true

            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    showUpdateDialog(result.updateInfo)
                }

                is UpdateCheckResult.NoUpdateAvailable -> {
                    updateStatusText.visibility = View.VISIBLE
                    updateStatusText.text = getString(R.string.no_update_available)
                    updateStatusText.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.text_secondary)
                    )
                }

                is UpdateCheckResult.Error -> {
                    updateStatusText.visibility = View.VISIBLE
                    updateStatusText.text = getString(R.string.update_check_failed)
                    updateStatusText.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.error_color)
                    )

                    Toast.makeText(
                        requireContext(),
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        updateDialog = UpdateDialog(
            context = requireContext(),
            updateInfo = updateInfo,
            downloadManager = downloadManager,
            lifecycleOwner = viewLifecycleOwner,
            onExitApp = {
                requireActivity().finishAffinity()
            }
        ).also { dialog ->
            dialog.show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateDialog?.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        updateDialog?.dismiss()
        updateDialog = null
    }
}
