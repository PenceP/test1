package com.strmr.tv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.strmr.tv.data.local.entity.RealDebridAccount

@Dao
interface RealDebridAccountDao {
    @Query("SELECT * FROM realdebrid_accounts WHERE providerId = :providerId LIMIT 1")
    suspend fun getAccount(providerId: String = "realdebrid"): RealDebridAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: RealDebridAccount)

    @Query("DELETE FROM realdebrid_accounts WHERE providerId = :providerId")
    suspend fun deleteAccount(providerId: String = "realdebrid")

    @Query("""
        UPDATE realdebrid_accounts
        SET accessToken = :accessToken,
            refreshToken = :refreshToken,
            tokenExpiresAt = :tokenExpiresAt
        WHERE providerId = :providerId
    """)
    suspend fun updateTokens(
        accessToken: String,
        refreshToken: String?,
        tokenExpiresAt: Long?,
        providerId: String = "realdebrid"
    )
}
