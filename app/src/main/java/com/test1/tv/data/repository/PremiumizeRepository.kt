package com.test1.tv.data.repository

import android.util.Log
import com.test1.tv.BuildConfig
import com.test1.tv.data.local.dao.PremiumizeAccountDao
import com.test1.tv.data.local.entity.PremiumizeAccount
import com.test1.tv.data.remote.api.PremiumizeApiService
import com.test1.tv.data.remote.api.PremiumizeAuthService
import com.test1.tv.data.remote.model.premiumize.PremiumizeDeviceCodeResponse
import com.test1.tv.data.remote.model.premiumize.PremiumizeDirectLinkResponse
import com.test1.tv.data.remote.model.premiumize.PremiumizeTokenResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumizeRepository @Inject constructor(
    private val premiumizeApiService: PremiumizeApiService,
    private val premiumizeAuthService: PremiumizeAuthService,
    private val accountDao: PremiumizeAccountDao
) {
    companion object {
        private const val TAG = "PremiumizeRepository"
        private const val PROVIDER_ID = "premiumize"
    }

    private val clientId: String
        get() = BuildConfig.PREMIUMIZE_CLIENT_ID

    /**
     * Get the stored Premiumize account
     */
    suspend fun getAccount(): PremiumizeAccount? = accountDao.getAccount(PROVIDER_ID)

    // ==================== OAuth Device Code Flow ====================

    /**
     * Request a device code for OAuth authentication
     * User will need to visit verification_uri and enter user_code
     */
    suspend fun requestDeviceCode(): Result<PremiumizeDeviceCodeResponse> {
        return try {
            val response = premiumizeAuthService.requestDeviceCode(clientId = clientId)
            Log.d(TAG, "Device code requested: ${response.userCode}")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request device code", e)
            Result.failure(e)
        }
    }

    /**
     * Poll for token after user has authorized the device code
     * @return PremiumizeTokenResponse if authorized, null if still pending, throws on error
     */
    suspend fun pollForToken(deviceCode: String): Result<PremiumizeTokenResponse?> {
        return try {
            val response = premiumizeAuthService.pollForToken(clientId = clientId, deviceCode = deviceCode)

            when {
                response.accessToken != null -> {
                    Log.d(TAG, "Token received successfully")
                    Result.success(response)
                }
                response.error == "authorization_pending" -> {
                    // User hasn't authorized yet - keep polling
                    Log.d(TAG, "Authorization pending, continuing to poll...")
                    Result.success(null)
                }
                response.error == "access_denied" -> {
                    Result.failure(Exception("Access denied by user"))
                }
                response.error != null -> {
                    Result.failure(Exception(response.errorDescription ?: response.error))
                }
                else -> {
                    // No token and no error - keep polling
                    Result.success(null)
                }
            }
        } catch (e: retrofit2.HttpException) {
            // Premiumize returns HTTP 400 for authorization_pending and other OAuth errors
            // We need to parse the error body to determine if we should keep polling
            val errorBody = e.response()?.errorBody()?.string()
            Log.d(TAG, "HTTP ${e.code()} error, body: $errorBody")

            when {
                errorBody?.contains("authorization_pending") == true -> {
                    // User hasn't authorized yet - keep polling
                    Log.d(TAG, "Authorization pending (from 400 response), continuing to poll...")
                    Result.success(null)
                }
                errorBody?.contains("access_denied") == true -> {
                    Result.failure(Exception("Access denied by user"))
                }
                errorBody?.contains("expired") == true -> {
                    Result.failure(Exception("Device code expired"))
                }
                else -> {
                    Log.e(TAG, "Failed to poll for token: HTTP ${e.code()}", e)
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to poll for token", e)
            Result.failure(e)
        }
    }

    /**
     * Save OAuth token and fetch account info
     */
    suspend fun saveOAuthToken(accessToken: String): Result<PremiumizeAccount> {
        return try {
            val authHeader = "Bearer $accessToken"
            val response = premiumizeApiService.getAccountInfoWithToken(authHeader)

            if (response.status == "success") {
                val now = System.currentTimeMillis()
                val account = PremiumizeAccount(
                    providerId = PROVIDER_ID,
                    apiKey = null,  // OAuth doesn't use API key
                    accessToken = accessToken,
                    customerId = response.customerId,
                    username = null,
                    email = null,
                    accountStatus = if (response.premiumUntil != null && response.premiumUntil > now / 1000) "premium" else "free",
                    expiresAt = response.premiumUntil?.times(1000),
                    pointsUsed = response.pointsUsed,
                    pointsAvailable = response.pointsAvailable,
                    spaceLimitBytes = null,
                    spaceUsedBytes = response.spaceUsed?.toLong(),
                    fairUsageLimitBytes = null,
                    fairUsageUsedBytes = response.fairUseUsed?.toLong(),
                    lastVerifiedAt = now,
                    createdAt = now
                )
                accountDao.upsert(account)
                Log.d(TAG, "Premiumize OAuth account saved: ${response.customerId}")
                Result.success(account)
            } else {
                val errorMsg = response.message ?: "Failed to get account info"
                Log.w(TAG, "Premiumize OAuth verification failed: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save OAuth token", e)
            Result.failure(e)
        }
    }

    // ==================== Legacy API Key Support ====================

    /**
     * Verify an API key and save the account if valid (legacy method)
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
                    accessToken = null,
                    customerId = response.customerId,
                    username = null,
                    email = null,
                    accountStatus = if (response.premiumUntil != null && response.premiumUntil > now / 1000) "premium" else "free",
                    expiresAt = response.premiumUntil?.times(1000),
                    pointsUsed = response.pointsUsed,
                    pointsAvailable = response.pointsAvailable,
                    spaceLimitBytes = null,
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

        return if (account.isOAuthAccount()) {
            saveOAuthToken(account.accessToken!!)
        } else if (account.apiKey != null) {
            verifyAndSaveApiKey(account.apiKey)
        } else {
            Result.failure(Exception("No valid credentials"))
        }
    }

    // ==================== API Methods (OAuth + Legacy Support) ====================

    /**
     * Check cache status for multiple torrent hashes
     * @param hashes List of info hashes to check
     * @return Map of hash to cached status (true if cached)
     */
    suspend fun checkCacheStatus(hashes: List<String>): Result<Map<String, Boolean>> {
        val account = accountDao.getAccount(PROVIDER_ID)
            ?: return Result.failure(Exception("No Premiumize account configured"))

        return try {
            val response = if (account.isOAuthAccount()) {
                premiumizeApiService.checkCacheWithToken(account.getAuthHeader()!!, hashes)
            } else {
                premiumizeApiService.checkCache(account.apiKey!!, hashes)
            }

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
            val response = if (account.isOAuthAccount()) {
                premiumizeApiService.getDirectLinkWithToken(account.getAuthHeader()!!, magnetOrHash)
            } else {
                premiumizeApiService.getDirectLink(account.apiKey!!, magnetOrHash)
            }

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

        // Video file extensions (playable by ExoPlayer)
        val videoExtensions = listOf(".mkv", ".mp4", ".avi", ".mov", ".wmv", ".m4v", ".webm", ".ts", ".m2ts")

        // Non-video extensions to explicitly exclude
        val excludeExtensions = listOf(".srt", ".sub", ".idx", ".ass", ".ssa", ".nfo", ".txt", ".jpg", ".jpeg", ".png", ".gif", ".exe", ".rar", ".zip", ".7z")

        // Filter to only video files
        val videoFiles = contents.filter { content ->
            val path = content.path?.lowercase() ?: ""
            val isVideo = videoExtensions.any { path.endsWith(it) }
            val isExcluded = excludeExtensions.any { path.endsWith(it) }
            isVideo && !isExcluded
        }

        Log.d(TAG, "selectBestLink: ${contents.size} total files, ${videoFiles.size} video files")

        // If fileIdx specified, check if it's a valid video file
        if (fileIdx != null && fileIdx < contents.size) {
            val content = contents[fileIdx]
            val path = content.path?.lowercase() ?: ""
            val isVideo = videoExtensions.any { path.endsWith(it) }
            if (isVideo) {
                Log.d(TAG, "Using specified fileIdx $fileIdx: ${content.path}")
                return content.streamLink ?: content.link
            }
            // If specified index is not a video, fall through to find best video
            Log.d(TAG, "Specified fileIdx $fileIdx is not a video file, finding best video instead")
        }

        // Find the largest video file (usually the main video)
        val bestFile = videoFiles.maxByOrNull { it.size ?: 0 }

        if (bestFile != null) {
            Log.d(TAG, "Selected best video: ${bestFile.path} (${bestFile.size} bytes)")
            return bestFile.streamLink ?: bestFile.link
        }

        // No video files found - don't fall back to non-video files
        Log.w(TAG, "No video files found in ${contents.size} files")
        return null
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
     * Returns null for OAuth accounts (they don't have API keys)
     */
    suspend fun getApiKeyMasked(): String? {
        val account = accountDao.getAccount(PROVIDER_ID) ?: return null
        val key = account.apiKey ?: return null
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
