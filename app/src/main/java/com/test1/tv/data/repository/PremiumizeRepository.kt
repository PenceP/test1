package com.test1.tv.data.repository

import android.util.Log
import com.test1.tv.data.local.dao.PremiumizeAccountDao
import com.test1.tv.data.local.entity.PremiumizeAccount
import com.test1.tv.data.remote.api.PremiumizeApiService
import com.test1.tv.data.remote.model.premiumize.PremiumizeDirectLinkResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumizeRepository @Inject constructor(
    private val premiumizeApiService: PremiumizeApiService,
    private val accountDao: PremiumizeAccountDao
) {
    companion object {
        private const val TAG = "PremiumizeRepository"
        private const val PROVIDER_ID = "premiumize"
    }

    /**
     * Get the stored Premiumize account
     */
    suspend fun getAccount(): PremiumizeAccount? = accountDao.getAccount(PROVIDER_ID)

    /**
     * Verify an API key and save the account if valid
     * @return Result with the account on success, or an error message on failure
     */
    suspend fun verifyAndSaveApiKey(apiKey: String): Result<PremiumizeAccount> {
        return try {
            val response = premiumizeApiService.getAccountInfo(apiKey)

            if (response.status == "success") {
                val now = System.currentTimeMillis()
                val account = PremiumizeAccount(
                    providerId = PROVIDER_ID,
                    apiKey = apiKey,
                    customerId = response.customerId,
                    username = null, // Premiumize doesn't return username in account/info
                    email = null,
                    accountStatus = if (response.premiumUntil != null && response.premiumUntil > now / 1000) "premium" else "free",
                    expiresAt = response.premiumUntil?.times(1000), // Convert to millis
                    pointsUsed = response.pointsUsed,
                    pointsAvailable = response.pointsAvailable,
                    spaceLimitBytes = null, // Not provided directly
                    spaceUsedBytes = response.spaceUsed?.toLong(),
                    fairUsageLimitBytes = null,
                    fairUsageUsedBytes = response.fairUseUsed?.toLong(),
                    lastVerifiedAt = now,
                    createdAt = now
                )
                accountDao.upsert(account)
                Log.d(TAG, "Premiumize account verified and saved: ${response.customerId}")
                Result.success(account)
            } else {
                val errorMsg = response.message ?: "Invalid API key"
                Log.w(TAG, "Premiumize verification failed: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify Premiumize API key", e)
            Result.failure(e)
        }
    }

    /**
     * Refresh account info from the API
     */
    suspend fun refreshAccountInfo(): Result<PremiumizeAccount> {
        val account = accountDao.getAccount(PROVIDER_ID)
            ?: return Result.failure(Exception("No Premiumize account configured"))

        return verifyAndSaveApiKey(account.apiKey)
    }

    /**
     * Check cache status for multiple torrent hashes
     * @param hashes List of info hashes to check
     * @return Map of hash to cached status (true if cached)
     */
    suspend fun checkCacheStatus(hashes: List<String>): Result<Map<String, Boolean>> {
        val account = accountDao.getAccount(PROVIDER_ID)
            ?: return Result.failure(Exception("No Premiumize account configured"))

        return try {
            val response = premiumizeApiService.checkCache(account.apiKey, hashes)

            if (response.status == "success" && response.response != null) {
                val cacheMap = hashes.zip(response.response).toMap()
                Log.d(TAG, "Cache check: ${cacheMap.count { it.value }} of ${hashes.size} cached")
                Result.success(cacheMap)
            } else {
                Result.failure(Exception(response.message ?: "Cache check failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check cache status", e)
            Result.failure(e)
        }
    }

    /**
     * Resolve a magnet link or hash to a direct streaming URL
     * @param magnetOrHash Either a magnet URI or info hash
     * @param fileIdx Optional file index within the torrent (for multi-file torrents)
     * @return Direct streaming URL
     */
    suspend fun resolveToDirectLink(magnetOrHash: String, fileIdx: Int? = null): Result<String> {
        val account = accountDao.getAccount(PROVIDER_ID)
            ?: return Result.failure(Exception("No Premiumize account configured"))

        return try {
            val response = premiumizeApiService.getDirectLink(account.apiKey, magnetOrHash)

            if (response.status == "success") {
                val link = selectBestLink(response, fileIdx)
                if (link != null) {
                    Log.d(TAG, "Resolved direct link successfully")
                    Result.success(link)
                } else {
                    Result.failure(Exception("No playable content found"))
                }
            } else {
                Result.failure(Exception(response.message ?: "Failed to resolve link"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve direct link", e)
            Result.failure(e)
        }
    }

    /**
     * Select the best streaming link from the response
     */
    private fun selectBestLink(response: PremiumizeDirectLinkResponse, fileIdx: Int?): String? {
        val contents = response.content

        // If no content array, check for single file response
        if (contents.isNullOrEmpty()) {
            return null
        }

        // If fileIdx specified, use that specific file
        if (fileIdx != null && fileIdx < contents.size) {
            val content = contents[fileIdx]
            return content.streamLink ?: content.link
        }

        // Otherwise, find the largest video file
        val videoExtensions = listOf(".mkv", ".mp4", ".avi", ".mov", ".wmv", ".m4v")
        val videoFiles = contents.filter { content ->
            val path = content.path?.lowercase() ?: ""
            videoExtensions.any { path.endsWith(it) }
        }

        // Sort by size and return the largest (usually the main video)
        val bestFile = videoFiles.maxByOrNull { it.size ?: 0 }
            ?: contents.maxByOrNull { it.size ?: 0 }

        return bestFile?.streamLink ?: bestFile?.link
    }

    /**
     * Clear/disconnect the Premiumize account
     */
    suspend fun clearAccount() {
        accountDao.deleteAccount(PROVIDER_ID)
        Log.d(TAG, "Premiumize account cleared")
    }

    /**
     * Check if a Premiumize account is configured
     */
    suspend fun hasAccount(): Boolean = accountDao.getAccount(PROVIDER_ID) != null

    /**
     * Get the stored API key (for display purposes - masked)
     */
    suspend fun getApiKeyMasked(): String? {
        val account = accountDao.getAccount(PROVIDER_ID) ?: return null
        val key = account.apiKey
        return if (key.length > 8) {
            "${key.take(4)}****${key.takeLast(4)}"
        } else {
            "****"
        }
    }

    /**
     * Calculate days remaining on premium account
     */
    fun getDaysRemaining(account: PremiumizeAccount): Int? {
        val expiresAt = account.expiresAt ?: return null
        val now = System.currentTimeMillis()
        if (expiresAt <= now) return 0
        return ((expiresAt - now) / (1000 * 60 * 60 * 24)).toInt()
    }
}
