package com.test1.tv.ui.settings.fragments

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.test1.tv.R
import com.test1.tv.background.TraktSyncWorker
import com.test1.tv.data.local.AppDatabase
import com.test1.tv.data.local.entity.TraktAccount
import com.test1.tv.data.remote.ApiClient
import com.test1.tv.data.repository.TraktAccountRepository
import com.test1.tv.data.repository.TraktAuthRepository
import com.test1.tv.ui.settings.adapter.SettingsAdapter
import com.test1.tv.ui.settings.model.AccountAction
import com.test1.tv.ui.settings.model.SettingsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AccountsFragment : Fragment() {

    private lateinit var accountsList: VerticalGridView
    private lateinit var adapter: SettingsAdapter
    private val traktAuthRepository = TraktAuthRepository(ApiClient.traktApiService)
    private val accountRepository by lazy {
        val db = AppDatabase.getDatabase(requireContext())
        TraktAccountRepository(ApiClient.traktApiService, db.traktAccountDao())
    }
    private val traktUserItemDao by lazy {
        AppDatabase.getDatabase(requireContext()).traktUserItemDao()
    }
    private var traktAccount: TraktAccount? = null

    // Example state (in real app, use ViewModel)
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
        val items = buildAccountItems()
        adapter = SettingsAdapter(items)
        accountsList.adapter = adapter
    }

    private fun loadAccountState() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val account = accountRepository.getAccount()
            traktAccount = account
            traktConnected = account != null
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

            // Premiumize Account Card
            SettingsItem.AccountCard(
                id = "premiumize",
                serviceName = "Premiumize",
                serviceDescription = "High-speed cloud downloader",
                iconText = "P",
                iconBackgroundColor = Color.parseColor("#2563EB"), // Blue
                isConnected = premiumizeConnected,
                userName = if (premiumizeConnected) "Premium (Yearly)" else null,
                additionalInfo = if (premiumizeConnected) "142 Days" else null,
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
                TraktSyncWorker.enqueue(requireContext())
                Toast.makeText(context, "Syncing Trakt data...", Toast.LENGTH_SHORT).show()
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
                    ApiClient.traktApiService.pollDeviceToken(
                        clientId = com.test1.tv.BuildConfig.TRAKT_CLIENT_ID,
                        clientSecret = com.test1.tv.BuildConfig.TRAKT_CLIENT_SECRET,
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

    private suspend fun onDeviceTokenReceived(token: com.test1.tv.data.model.trakt.TraktTokenResponse) {
        val authHeader = "Bearer ${token.accessToken}"
        val profile = runCatching {
            ApiClient.traktApiService.getUserProfile(
                authHeader = authHeader,
                clientId = com.test1.tv.BuildConfig.TRAKT_CLIENT_ID
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
            TraktSyncWorker.enqueue(requireContext())
        }
    }

    private fun handlePremiumizeAction(action: AccountAction) {
        when (action) {
            AccountAction.AUTHENTICATE, AccountAction.CONNECT -> {
                Toast.makeText(context, "Connecting to Premiumize...", Toast.LENGTH_SHORT).show()
                // In real app: validate API key
                premiumizeConnected = true
                refreshItems()
            }
            AccountAction.DISCONNECT -> {
                premiumizeConnected = false
                refreshItems()
                Toast.makeText(context, "Disconnected from Premiumize", Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }

    private fun refreshItems() {
        val newItems = buildAccountItems()
        adapter.updateItems(newItems)
    }
}
