package com.strmr.tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "realdebrid_accounts")
data class RealDebridAccount(
    @PrimaryKey val providerId: String = "realdebrid",
    val accessToken: String,
    val refreshToken: String?,
    val clientId: String,        // User-bound client ID from credentials step
    val clientSecret: String,    // User-bound client secret from credentials step
    val tokenExpiresAt: Long?,   // Timestamp when access token expires
    val userId: Int?,
    val username: String?,
    val email: String?,
    val accountType: String?,    // "premium" or "free"
    val premiumDaysRemaining: Int?,
    val expiresAt: Long?,        // Premium expiration timestamp
    val points: Int?,
    val lastVerifiedAt: Long,
    val createdAt: Long
) {
    /**
     * Returns the authorization header value for API calls
     */
    fun getAuthHeader(): String = "Bearer $accessToken"

    /**
     * Check if the access token has expired
     */
    fun isTokenExpired(): Boolean {
        val expiresAt = tokenExpiresAt ?: return false
        return System.currentTimeMillis() >= expiresAt
    }

    /**
     * Check if this is a premium account
     */
    fun isPremium(): Boolean = accountType == "premium"
}
