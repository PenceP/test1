package com.strmr.tv.ui.settings.fragments

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.strmr.tv.R
import com.strmr.tv.data.local.entity.AllDebridAccount
import com.strmr.tv.data.local.entity.PremiumizeAccount
import com.strmr.tv.data.local.entity.RealDebridAccount
import com.strmr.tv.data.local.entity.TraktAccount
import com.strmr.tv.data.repository.AllDebridRepository
import com.strmr.tv.data.repository.PremiumizeRepository
import com.strmr.tv.data.repository.RealDebridRepository
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
    @Inject lateinit var realDebridRepository: RealDebridRepository
    @Inject lateinit var allDebridRepository: AllDebridRepository

    private var traktAccount: TraktAccount? = null
    private var premiumizeAccount: PremiumizeAccount? = null
    private var realDebridAccount: RealDebridAccount? = null
    private var allDebridAccount: AllDebridAccount? = null

    // State
    private var traktConnected = false
    private var premiumizeConnected = false
    private var realDebridConnected = false
    private var allDebridConnected = false

    // Active authorization dialogs (to dismiss on success)
    private var activeAuthDialog: AlertDialog? = null

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

            // Load Real-Debrid account
            val realDebrid = realDebridRepository.getAccount()
            realDebridAccount = realDebrid
            realDebridConnected = realDebrid != null

            // Load AllDebrid account
            val allDebrid = allDebridRepository.getAccount()
            allDebridAccount = allDebrid
            allDebridConnected = allDebrid != null

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

        // Calculate days remaining for debrid services
        val premiumizeDaysRemaining = premiumizeAccount?.let {
            premiumizeRepository.getDaysRemaining(it)
        }
        val realDebridDaysRemaining = realDebridAccount?.let {
            realDebridRepository.getDaysRemaining(it)
        }
        val allDebridDaysRemaining = allDebridAccount?.let {
            allDebridRepository.getDaysRemaining(it)
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

            // Premiumize Account Card
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
            ),

            // Real-Debrid Account Card
            SettingsItem.AccountCard(
                id = "realdebrid",
                serviceName = "Real-Debrid",
                serviceDescription = "Premium link generator",
                iconText = "RD",
                iconBackgroundColor = Color.parseColor("#16A34A"), // Green
                isConnected = realDebridConnected,
                userName = realDebridAccount?.username,
                additionalInfo = realDebridDaysRemaining?.let { "$it days remaining" },
                onAction = { action ->
                    handleRealDebridAction(action)
                }
            ),

            // AllDebrid Account Card
            SettingsItem.AccountCard(
                id = "alldebrid",
                serviceName = "AllDebrid",
                serviceDescription = "Universal link unlocker",
                iconText = "AD",
                iconBackgroundColor = Color.parseColor("#9333EA"), // Purple
                isConnected = allDebridConnected,
                userName = allDebridAccount?.username,
                additionalInfo = allDebridDaysRemaining?.let { "$it days remaining" },
                onAction = { action ->
                    handleAllDebridAction(action)
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

        activeAuthDialog?.dismiss()
        activeAuthDialog = MaterialAlertDialogBuilder(requireContext())
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
            .setOnDismissListener { activeAuthDialog = null }
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
            activeAuthDialog?.dismiss()
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

        activeAuthDialog?.dismiss()
        activeAuthDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Authorize Premiumize")
            .setMessage(message)
            .setPositiveButton("Open Browser") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(verificationUrl))
                startActivity(intent)
            }
            .setNegativeButton("Close", null)
            .setOnDismissListener { activeAuthDialog = null }
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
                activeAuthDialog?.dismiss()
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

    // ==================== Real-Debrid OAuth Device Code Flow ====================

    private fun handleRealDebridAction(action: AccountAction) {
        when (action) {
            AccountAction.AUTHENTICATE -> {
                requestRealDebridDeviceCode()
            }
            AccountAction.LOGOUT -> {
                disconnectRealDebrid()
            }
            else -> {}
        }
    }

    private fun requestRealDebridDeviceCode() {
        viewLifecycleOwner.lifecycleScope.launch {
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle("Authorize Real-Debrid")
                .setMessage("Requesting authorization code...")
                .setCancelable(false)
                .show()

            val result = withContext(Dispatchers.IO) {
                realDebridRepository.requestDeviceCode()
            }

            dialog.dismiss()

            result.onSuccess { code ->
                showRealDebridActivationDialog(code.userCode, code.verificationUrl, code.expiresIn)
                pollForRealDebridCredentials(code.deviceCode, code.interval, code.expiresIn)
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    "Failed to get authorization code: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showRealDebridActivationDialog(userCode: String, verificationUrl: String, expiresIn: Int) {
        val minutes = (expiresIn / 60).coerceAtLeast(1)
        val message = """
            Go to: $verificationUrl

            Enter code: ${userCode.uppercase(Locale.US)}

            This code expires in $minutes minutes.
        """.trimIndent()

        activeAuthDialog?.dismiss()
        activeAuthDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Authorize Real-Debrid")
            .setMessage(message)
            .setPositiveButton("Open Browser") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(verificationUrl))
                startActivity(intent)
            }
            .setNegativeButton("Close", null)
            .setOnDismissListener { activeAuthDialog = null }
            .show()
    }

    private fun pollForRealDebridCredentials(deviceCode: String, intervalSeconds: Int, expiresIn: Int) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val expiresAt = startTime + expiresIn * 1000L
            val pollDelay = (intervalSeconds.coerceAtLeast(5)) * 1000L

            while (isActive && System.currentTimeMillis() < expiresAt) {
                val credentialsResult = realDebridRepository.pollForCredentials(deviceCode)

                credentialsResult.onSuccess { credentials ->
                    if (credentials != null) {
                        // Credentials received - now get the token
                        // credentials is Pair<clientId, clientSecret>
                        val tokenResult = realDebridRepository.exchangeCredentialsForToken(
                            deviceCode = deviceCode,
                            clientId = credentials.first,
                            clientSecret = credentials.second
                        )

                        tokenResult.onSuccess { account ->
                            realDebridAccount = account
                            realDebridConnected = true

                            withContext(Dispatchers.Main) {
                                activeAuthDialog?.dismiss()
                                refreshItems()
                                Toast.makeText(requireContext(), "Real-Debrid connected!", Toast.LENGTH_SHORT).show()
                            }
                        }.onFailure { error ->
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    requireContext(),
                                    "Failed to get token: ${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        return@launch
                    }
                    // null credentials means still pending - continue polling
                }.onFailure { error ->
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
                    "Real-Debrid code expired. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun disconnectRealDebrid() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                realDebridRepository.clearAccount()
            }

            realDebridAccount = null
            realDebridConnected = false

            refreshItems()
            Toast.makeText(context, "Disconnected from Real-Debrid", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== AllDebrid PIN Authentication Flow ====================

    private fun handleAllDebridAction(action: AccountAction) {
        when (action) {
            AccountAction.AUTHENTICATE -> {
                requestAllDebridPinCode()
            }
            AccountAction.LOGOUT -> {
                disconnectAllDebrid()
            }
            else -> {}
        }
    }

    private fun requestAllDebridPinCode() {
        viewLifecycleOwner.lifecycleScope.launch {
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle("Authorize AllDebrid")
                .setMessage("Requesting authorization PIN...")
                .setCancelable(false)
                .show()

            val result = withContext(Dispatchers.IO) {
                allDebridRepository.requestPinCode()
            }

            dialog.dismiss()

            result.onSuccess { pinData ->
                showAllDebridActivationDialog(pinData.pin, pinData.userUrl, pinData.expiresIn)
                pollForAllDebridApiKey(pinData.pin, pinData.check, pinData.expiresIn)
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    "Failed to get authorization PIN: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showAllDebridActivationDialog(pin: String, userUrl: String, expiresIn: Int) {
        val minutes = (expiresIn / 60).coerceAtLeast(1)
        val message = """
            Go to: $userUrl

            Enter PIN: $pin

            This PIN expires in $minutes minutes.
        """.trimIndent()

        activeAuthDialog?.dismiss()
        activeAuthDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Authorize AllDebrid")
            .setMessage(message)
            .setPositiveButton("Open Browser") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(userUrl))
                startActivity(intent)
            }
            .setNegativeButton("Close", null)
            .setOnDismissListener { activeAuthDialog = null }
            .show()
    }

    private fun pollForAllDebridApiKey(pin: String, check: String, expiresIn: Int) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val expiresAt = startTime + expiresIn * 1000L
            val pollDelay = 5000L // AllDebrid recommends 5 second intervals

            while (isActive && System.currentTimeMillis() < expiresAt) {
                val apiKeyResult = allDebridRepository.pollForApiKey(pin, check)

                apiKeyResult.onSuccess { apiKey ->
                    if (apiKey != null) {
                        // API key received - save it and get account info
                        val saveResult = allDebridRepository.saveApiKey(apiKey)

                        saveResult.onSuccess { account ->
                            allDebridAccount = account
                            allDebridConnected = true

                            withContext(Dispatchers.Main) {
                                activeAuthDialog?.dismiss()
                                refreshItems()
                                Toast.makeText(requireContext(), "AllDebrid connected!", Toast.LENGTH_SHORT).show()
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
                        return@launch
                    }
                    // null apiKey means still pending - continue polling
                }.onFailure { error ->
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
                    "AllDebrid PIN expired. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun disconnectAllDebrid() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                allDebridRepository.clearAccount()
            }

            allDebridAccount = null
            allDebridConnected = false

            refreshItems()
            Toast.makeText(context, "Disconnected from AllDebrid", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshItems() {
        val newItems = buildAccountItems()
        adapter.submitList(newItems)
    }
}
