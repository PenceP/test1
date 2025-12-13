package com.strmr.tv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.strmr.tv.data.local.entity.PremiumizeAccount

@Dao
interface PremiumizeAccountDao {
    @Query("SELECT * FROM premiumize_accounts WHERE providerId = :providerId LIMIT 1")
    suspend fun getAccount(providerId: String = "premiumize"): PremiumizeAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: PremiumizeAccount)

    @Query(
        """
        UPDATE premiumize_accounts
        SET username = :username,
            email = :email,
            customerId = :customerId,
            accountStatus = :accountStatus,
            expiresAt = :expiresAt,
            pointsUsed = :pointsUsed,
            pointsAvailable = :pointsAvailable,
            spaceLimitBytes = :spaceLimitBytes,
            spaceUsedBytes = :spaceUsedBytes,
            fairUsageLimitBytes = :fairUsageLimitBytes,
            fairUsageUsedBytes = :fairUsageUsedBytes,
            lastVerifiedAt = :lastVerifiedAt
        WHERE providerId = :providerId
        """
    )
    suspend fun updateAccountInfo(
        username: String?,
        email: String?,
        customerId: String?,
        accountStatus: String?,
        expiresAt: Long?,
        pointsUsed: Double?,
        pointsAvailable: Double?,
        spaceLimitBytes: Long?,
        spaceUsedBytes: Long?,
        fairUsageLimitBytes: Long?,
        fairUsageUsedBytes: Long?,
        lastVerifiedAt: Long,
        providerId: String = "premiumize"
    )

    @Query("DELETE FROM premiumize_accounts WHERE providerId = :providerId")
    suspend fun deleteAccount(providerId: String = "premiumize")
}
