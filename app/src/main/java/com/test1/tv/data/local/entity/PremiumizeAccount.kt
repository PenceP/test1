package com.test1.tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "premiumize_accounts")
data class PremiumizeAccount(
    @PrimaryKey val providerId: String = "premiumize",
    val apiKey: String,
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
)
