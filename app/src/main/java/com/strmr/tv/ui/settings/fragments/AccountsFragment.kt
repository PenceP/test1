package com.strmr.tv.ui.settings.fragments

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.strmr.tv.R
import com.strmr.tv.data.local.entity.PremiumizeAccount
import com.strmr.tv.data.local.entity.TraktAccount
import com.strmr.tv.data.repository.PremiumizeRepository
import com.strmr.tv.ui.settings.adapter.SettingsAdapter
import com.strmr.tv.ui.settings.model.AccountAction
import com.strmr.tv.ui.settings.model.SettingsItem
import com.strmr.tv.ui.splash.SyncSplashActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AccountsFragment : Fragment() {

    private lateinit var accountsList: VerticalGridView
    private lateinit var adapter: SettingsAdapter

    @Inject lateinit var traktAuthRepository: com.strmr.tv.data.repository.TraktAuthRepository
    @Inject lateinit var accountRepository: com.strmr.tv.data.repository.TraktAccountRepository
    @Inject lateinit var traktUserItemDao: com.strmr.tv.data.local.dao.TraktUserItemDao
    @Inject lateinit var traktApiService: com.strmr.tv.data.remote.api.TraktApiService
    @Inject lateinit var premiumizeRepository: PremiumizeRepository

    private var traktAccount: TraktAccount? = null
    private var premiumizeAccount: PremiumizeAccount? = null

    // State
    private var traktConnected = false
    private var premiumizeConnected = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_accounts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        accountsList = view.findViewById(R.id.accounts_list)
        setupAccountsList()
        loadAccountState()
    }

    private fun setupAccountsList() {
        // Fix VerticalGridView alignment - align to top edge instead of center
        accountsList.windowAlignment = BaseGridView.WINDOW_ALIGN_LOW_EDGE
        accountsList.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
        accountsList.itemAlignmentOffsetPercent = BaseGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED

        val items = buildAccountItems()
        adapter = SettingsAdapter(items)
        accountsList.adapter = adapter
    }

    private fun loadAccountState() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // Load Trakt account
            val trakt = accountRepository.getAccount()
            traktAccount = trakt
            traktConnected = trakt != null

            // Load Premiumize account
            val premiumize = premiumizeRepository.getAccount()
            premiumizeAccount = premiumize
            premiumizeConnected = premiumize != null

            withContext(Dispatchers.Main) {
                refreshItems()
            }
        }
    }

    private fun buildAccountItems(): List<SettingsItem> {
        val moviesWatched = traktAccount?.statsMoviesWatched
        val showsWatched = traktAccount?.statsShowsWatched
        val minutesWatched = traktAccount?.statsMinutesWatched
        val hoursWatched = minutesWatched?.div(60)

        // Calculate Premiumize days remaining
        val premiumizeDaysRemaining = premiumizeAccount?.let {
            premiumizeRepository.getDaysRemaining(it)
        }

        return listOf(
            // Trakt Account Card
            SettingsItem.AccountCard(
                id = "trakt",
                serviceName = "Trakt.tv",
                serviceDescription = "Sync your watch history",
                iconText = "T",
                iconBackgroundColor = Color.parseColor("#DC2626"), // Red
                isConnected = traktConnected,
                userName = traktAccount?.userName,
                additionalInfo = when {
                    moviesWatched != null && showsWatched != null && hoursWatched != null ->
                        "${moviesWatched} movies · ${showsWatched} shows · ${hoursWatched}h"
                    moviesWatched != null && showsWatched != null ->
                        "${moviesWatched} movies · ${showsWatched} shows"
                    else -> null
                },
                onAction = { action ->
                    handleTraktAction(action)
                }
            ),

            // Premiumize Account Card (OAuth device code flow)
            SettingsItem.AccountCard(
                id = "premiumize",
                serviceName = "Premiumize",
                serviceDescription = "High-speed cloud downloader",
                iconText = "P",
                iconBackgroundColor = Color.parseColor("#2563EB"), // Blue
                isConnected = premiumizeConnected,
                userName = premiumizeAccount?.accountStatus?.replaceFirstChar { it.uppercase() },
                additionalInfo = premiumizeDaysRemaining?.let { "$it days remaining" },
                onAction = { action ->
                    handlePremiumizeAction(action)
                }
            )
        )
    }

    private fun handleTraktAction(action: AccountAction) {
        when (action) {
            AccountAction.AUTHENTICATE -> {
                requestTraktDeviceCode()
            }
            AccountAction.SYNC -> {
                startActivity(
                    Intent(requireContext(), SyncSplashActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            AccountAction.LOGOUT -> {
                traktConnected = false
                traktAccount = null
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    accountRepository.clearAccount()
                    traktUserItemDao.clearAll()
                    withContext(Dispatchers.Main) {
                        refreshItems()
                        Toast.makeText(context, "Logged out from Trakt", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else -> {}
        }
    }

    private fun requestTraktDeviceCode() {
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
                showTraktActivationDialog(code.userCode, code.verificationUrl, code.expiresIn)
                pollForDeviceToken(code.deviceCode, code.interval, code.expiresIn)
            }.onFailure {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.trakt_authorize_error),
                    Toast.LENGTH_LONG
                ).show()
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
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    Uri.parse(verificationUrl)
                )
                startActivity(intent)
            }
            .setNegativeButton(R.string.trakt_authorize_close, null)
            .show()
    }

    private fun pollForDeviceToken(deviceCode: String, intervalSeconds: Int, expiresIn: Int) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val expiresAt = startTime + expiresIn * 1000L
            val pollDelay = (intervalSeconds.coerceAtLeast(1)) * 1000L

            while (isActive && System.currentTimeMillis() < expiresAt) {
                val tokenResult = runCatching {
                    traktApiService.pollDeviceToken(
                        clientId = com.strmr.tv.BuildConfig.TRAKT_CLIENT_ID,
                        clientSecret = com.strmr.tv.BuildConfig.TRAKT_CLIENT_SECRET,
                        deviceCode = deviceCode
                    )
                }

                val token = tokenResult.getOrNull()
                if (token != null) {
                    onDeviceTokenReceived(token)
                    return@launch
                }

                delay(pollDelay.toLong())
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Trakt code expired. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun onDeviceTokenReceived(token: com.strmr.tv.data.model.trakt.TraktTokenResponse) {
        val authHeader = "Bearer ${token.accessToken}"
        val profile = runCatching {
            traktApiService.getUserProfile(
                authHeader = authHeader,
                clientId = com.strmr.tv.BuildConfig.TRAKT_CLIENT_ID
            )
        }.getOrNull()

        if (profile == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Failed to load Trakt profile.", Toast.LENGTH_LONG).show()
            }
            return
        }

        val account = accountRepository.saveDeviceToken(token, profile)
        traktAccount = account
        traktConnected = true

        withContext(Dispatchers.Main) {
            refreshItems()
            Toast.makeText(requireContext(), "Trakt authorized!", Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(requireContext(), SyncSplashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    // ==================== Premiumize OAuth Device Code Flow ====================

    private fun handlePremiumizeAction(action: AccountAction) {
        when (action) {
            AccountAction.AUTHENTICATE -> {
                requestPremiumizeDeviceCode()
            }
            AccountAction.LOGOUT -> {
                disconnectPremiumize()
            }
            else -> {}
        }
    }

    private fun requestPremiumizeDeviceCode() {
        viewLifecycleOwner.lifecycleScope.launch {
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle("Authorize Premiumize")
                .setMessage("Requesting authorization code...")
                .setCancelable(false)
                .show()

            val result = withContext(Dispatchers.IO) {
                premiumizeRepository.requestDeviceCode()
            }

            dialog.dismiss()

            result.onSuccess { code ->
                showPremiumizeActivationDialog(code.userCode, code.verificationUri, code.expiresIn)
                pollForPremiumizeToken(code.deviceCode, code.interval, code.expiresIn)
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    "Failed to get authorization code: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showPremiumizeActivationDialog(userCode: String, verificationUrl: String, expiresIn: Int) {
        val minutes = (expiresIn / 60).coerceAtLeast(1)
        val message = """
            Go to: $verificationUrl

            Enter code: ${userCode.uppercase(Locale.US)}

            This code expires in $minutes minutes.
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Authorize Premiumize")
            .setMessage(message)
            .setPositiveButton("Open Browser") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(verificationUrl))
                startActivity(intent)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun pollForPremiumizeToken(deviceCode: String, intervalSeconds: Int, expiresIn: Int) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val expiresAt = startTime + expiresIn * 1000L
            val pollDelay = (intervalSeconds.coerceAtLeast(5)) * 1000L

            while (isActive && System.currentTimeMillis() < expiresAt) {
                val tokenResult = premiumizeRepository.pollForToken(deviceCode)

                tokenResult.onSuccess { response ->
                    if (response != null && response.accessToken != null) {
                        // Token received - save it and update UI
                        onPremiumizeTokenReceived(response.accessToken)
                        return@launch
                    }
                    // null response means still pending - continue polling
                }.onFailure { error ->
                    // Access denied or other error
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "Authorization failed: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                delay(pollDelay)
            }

            // Expired
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    "Premiumize code expired. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun onPremiumizeTokenReceived(accessToken: String) {
        val result = premiumizeRepository.saveOAuthToken(accessToken)

        result.onSuccess { account ->
            premiumizeAccount = account
            premiumizeConnected = true

            withContext(Dispatchers.Main) {
                refreshItems()
                Toast.makeText(requireContext(), "Premiumize connected!", Toast.LENGTH_SHORT).show()
            }
        }.onFailure { error ->
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    "Failed to save account: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun disconnectPremiumize() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                premiumizeRepository.clearAccount()
            }

            premiumizeAccount = null
            premiumizeConnected = false

            refreshItems()
            Toast.makeText(context, "Disconnected from Premiumize", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshItems() {
        val newItems = buildAccountItems()
        adapter.submitList(newItems)
    }
}
