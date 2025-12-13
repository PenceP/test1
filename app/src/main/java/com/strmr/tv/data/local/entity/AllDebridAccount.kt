package com.strmr.tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alldebrid_accounts")
data class AllDebridAccount(
    @PrimaryKey val providerId: String = "alldebrid",
    val apiKey: String,          // AllDebrid uses API keys (called apikey in their docs)
    val username: String?,
    val email: String?,
    val isPremium: Boolean,
    val isSubscribed: Boolean?,
    val isTrial: Boolean?,
    val premiumUntil: Long?,     // Unix timestamp when premium expires
    val fidelityPoints: Int?,
    val lastVerifiedAt: Long,
    val createdAt: Long
) {
    /**
     * Returns the authorization header value for API calls
     */
    fun getAuthHeader(): String = "Bearer $apiKey"

    /**
     * Calculate days remaining on premium account
     */
    fun getDaysRemaining(): Int? {
        val until = premiumUntil ?: return null
        val now = System.currentTimeMillis() / 1000  // Convert to seconds
        if (until <= now) return 0
        return ((until - now) / (60 * 60 * 24)).toInt()
    }
}
