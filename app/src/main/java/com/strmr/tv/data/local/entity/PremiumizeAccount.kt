package com.strmr.tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "premiumize_accounts")
data class PremiumizeAccount(
    @PrimaryKey val providerId: String = "premiumize",
    val apiKey: String? = null,           // Legacy API key (nullable for OAuth users)
    val accessToken: String? = null,       // OAuth access token
    val customerId: String?,
    val username: String?,
    val email: String?,
    val accountStatus: String?,
    val expiresAt: Long?,
    val pointsUsed: Double?,
    val pointsAvailable: Double?,
    val spaceLimitBytes: Long?,
    val spaceUsedBytes: Long?,
    val fairUsageLimitBytes: Long?,
    val fairUsageUsedBytes: Long?,
    val lastVerifiedAt: Long,
    val createdAt: Long
) {
    /**
     * Returns the authorization header value for API calls
     * Prefers OAuth token, falls back to API key if available
     */
    fun getAuthHeader(): String? {
        return when {
            accessToken != null -> "Bearer $accessToken"
            else -> null
        }
    }

    /**
     * Returns true if this account uses OAuth authentication
     */
    fun isOAuthAccount(): Boolean = accessToken != null

    /**
     * Returns true if this account has valid credentials (either OAuth or API key)
     */
    fun hasValidCredentials(): Boolean = accessToken != null || apiKey != null
}
