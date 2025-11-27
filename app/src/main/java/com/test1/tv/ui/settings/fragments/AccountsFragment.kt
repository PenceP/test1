package com.test1.tv.ui.settings.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.leanback.widget.VerticalGridView
import com.test1.tv.R
import com.test1.tv.ui.settings.adapter.SettingsAdapter
import com.test1.tv.ui.settings.model.AccountAction
import com.test1.tv.ui.settings.model.SettingsItem

class AccountsFragment : Fragment() {

    private lateinit var accountsList: VerticalGridView
    private lateinit var adapter: SettingsAdapter

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
    }

    private fun setupAccountsList() {
        val items = buildAccountItems()
        adapter = SettingsAdapter(items)
        accountsList.adapter = adapter
    }

    private fun buildAccountItems(): List<SettingsItem> {
        return listOf(
            // Trakt Account Card
            SettingsItem.AccountCard(
                id = "trakt",
                serviceName = "Trakt.tv",
                serviceDescription = "Sync your watch history",
                iconText = "T",
                iconBackgroundColor = Color.parseColor("#DC2626"), // Red
                isConnected = traktConnected,
                userName = if (traktConnected) "CinemaLover99" else null,
                additionalInfo = if (traktConnected) "1,402" else null,
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
                // Simulate authentication
                Toast.makeText(context, "Authenticating with Trakt...", Toast.LENGTH_SHORT).show()
                // In real app: start OAuth flow
                traktConnected = true
                refreshItems()
            }
            AccountAction.SYNC -> {
                Toast.makeText(context, "Syncing Trakt data...", Toast.LENGTH_SHORT).show()
                // Perform sync
            }
            AccountAction.LOGOUT -> {
                traktConnected = false
                refreshItems()
                Toast.makeText(context, "Logged out from Trakt", Toast.LENGTH_SHORT).show()
            }
            else -> {}
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
