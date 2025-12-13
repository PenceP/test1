package com.strmr.tv.data.repository

import android.util.Log
import com.strmr.tv.data.local.dao.RealDebridAccountDao
import com.strmr.tv.data.local.entity.RealDebridAccount
import com.strmr.tv.data.remote.api.RealDebridApiService
import com.strmr.tv.data.remote.api.RealDebridAuthService
import com.strmr.tv.data.remote.model.realdebrid.RealDebridDeviceCodeResponse
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealDebridRepository @Inject constructor(
    private val realDebridApiService: RealDebridApiService,
    private val realDebridAuthService: RealDebridAuthService,
    private val accountDao: RealDebridAccountDao
) {
    companion object {
        private const val TAG = "RealDebridRepository"
        private const val PROVIDER_ID = "realdebrid"
        // Real-Debrid opensource client ID for device code flow
        const val OPENSOURCE_CLIENT_ID = "X245A4XAIBGVM"
    }

    /**
     * Get the stored Real-Debrid account
     */
    suspend fun getAccount(): RealDebridAccount? = accountDao.getAccount(PROVIDER_ID)

    // ==================== OAuth Device Code Flow ====================

    /**
     * Request a device code for OAuth authentication
     * User will need to visit verification_url and enter user_code
     */
    suspend fun requestDeviceCode(): Result<RealDebridDeviceCodeResponse> {
        return try {
            val response = realDebridAuthService.requestDeviceCode(
                clientId = OPENSOURCE_CLIENT_ID,
                newCredentials = "yes"
            )
            Log.d(TAG, "Device code requested: ${response.userCode}")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request device code", e)
            Result.failure(e)
        }
    }

    /**
     * Poll for credentials after user has authorized the device code
     * Real-Debrid returns user-bound client_id and client_secret
     *
     * @return Pair of (clientId, clientSecret) if authorized, null if still pending
     */
    suspend fun pollForCredentials(deviceCode: String): Result<Pair<String, String>?> {
        return try {
            val response = realDebridAuthService.pollForCredentials(
                clientId = OPENSOURCE_CLIENT_ID,
                deviceCode = deviceCode
            )

            when {
                response.clientId != null && response.clientSecret != null -> {
                    Log.d(TAG, "Credentials received successfully")
                    Result.success(Pair(response.clientId, response.clientSecret))
                }
                response.error == "authorization_pending" -> {
                    Log.d(TAG, "Authorization pending, continuing to poll...")
                    Result.success(null)
                }
                response.error != null -> {
                    Result.failure(Exception(response.error))
                }
                else -> {
                    Result.success(null)
                }
            }
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.d(TAG, "HTTP ${e.code()} error, body: $errorBody")

            when {
                errorBody?.contains("authorization_pending") == true -> {
                    Log.d(TAG, "Authorization pending (from error response)")
                    Result.success(null)
                }
                else -> {
                    Log.e(TAG, "Failed to poll for credentials: HTTP ${e.code()}", e)
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to poll for credentials", e)
            Result.failure(e)
        }
    }

    /**
     * Exchange credentials for access token and save account
     */
    suspend fun exchangeCredentialsForToken(
        deviceCode: String,
        clientId: String,
        clientSecret: String
    ): Result<RealDebridAccount> {
        return try {
            val tokenResponse = realDebridAuthService.getToken(
                clientId = clientId,
                clientSecret = clientSecret,
                deviceCode = deviceCode
            )

            if (tokenResponse.accessToken != null) {
                val expiresAt = tokenResponse.expiresIn?.let {
                    System.currentTimeMillis() + (it * 1000L)
                }

                // Fetch user info
                val authHeader = "Bearer ${tokenResponse.accessToken}"
                val userResponse = realDebridApiService.getUser(authHeader)

                val premiumExpiration = userResponse.expiration?.let {
                    try {
                        Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(it)).toEpochMilli()
                    } catch (e: Exception) {
                        null
                    }
                }

                val now = System.currentTimeMillis()
                val account = RealDebridAccount(
                    providerId = PROVIDER_ID,
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    tokenExpiresAt = expiresAt,
                    userId = userResponse.id,
                    username = userResponse.username,
                    email = userResponse.email,
                    accountType = userResponse.type,
                    premiumDaysRemaining = userResponse.premium,
                    expiresAt = premiumExpiration,
                    points = userResponse.points,
                    lastVerifiedAt = now,
                    createdAt = now
                )

                accountDao.upsert(account)
                Log.d(TAG, "Real-Debrid account saved: ${userResponse.username}")
                Result.success(account)
            } else {
                val errorMsg = tokenResponse.errorDescription ?: tokenResponse.error ?: "Failed to get token"
                Log.w(TAG, "Token exchange failed: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to exchange credentials for token", e)
            Result.failure(e)
        }
    }

    /**
     * Refresh the access token using refresh token
     */
    suspend fun refreshAccessToken(): Result<RealDebridAccount> {
        val account = accountDao.getAccount(PROVIDER_ID)
            ?: return Result.failure(Exception("No Real-Debrid account configured"))

        val refreshToken = account.refreshToken
            ?: return Result.failure(Exception("No refresh token available"))

        return try {
            val tokenResponse = realDebridAuthService.refreshToken(
                clientId = account.clientId,
                clientSecret = account.clientSecret,
                refreshToken = refreshToken
            )

            if (tokenResponse.accessToken != null) {
                val expiresAt = tokenResponse.expiresIn?.let {
                    System.currentTimeMillis() + (it * 1000L)
                }

                accountDao.updateTokens(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken ?: refreshToken,
                    tokenExpiresAt = expiresAt
                )

                val updatedAccount = accountDao.getAccount(PROVIDER_ID)!!
                Log.d(TAG, "Access token refreshed successfully")
                Result.success(updatedAccount)
            } else {
                Result.failure(Exception(tokenResponse.errorDescription ?: "Failed to refresh token"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh access token", e)
            Result.failure(e)
        }
    }

    // ==================== API Methods ====================

    /**
     * Get a valid auth header, refreshing token if needed
     */
    private suspend fun getValidAuthHeader(): String? {
        var account = accountDao.getAccount(PROVIDER_ID) ?: return null

        if (account.isTokenExpired()) {
            refreshAccessToken().onSuccess { account = it }
                .onFailure { return null }
        }

        return account.getAuthHeader()
    }

    /**
     * Check instant availability for torrent hashes
     * @param hashes List of info hashes to check
     * @return Map of hash to cached status (true if cached)
     */
    suspend fun checkCacheStatus(hashes: List<String>): Result<Map<String, Boolean>> {
        val authHeader = getValidAuthHeader()
            ?: return Result.failure(Exception("No Real-Debrid account configured"))

        return try {
            // Real-Debrid expects hashes separated by /
            val hashesPath = hashes.joinToString("/")
            val response = realDebridApiService.checkInstantAvailability(authHeader, hashesPath)

            // Parse the dynamic response
            val cacheMap = mutableMapOf<String, Boolean>()
            for (hash in hashes) {
                val hashLower = hash.lowercase()
                val hashData = response[hashLower]
                // If the hash key exists and has data, it's cached
                cacheMap[hash] = hashData != null && hashData.toString() != "{}"
            }

            Log.d(TAG, "Cache check: ${cacheMap.count { it.value }} of ${hashes.size} cached")
            Result.success(cacheMap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check cache status", e)
            Result.failure(e)
        }
    }

    /**
     * Resolve a magnet link to a direct streaming URL
     * For Real-Debrid, we need to:
     * 1. Add the magnet
     * 2. Select files (all)
     * 3. Get the generated link
     * 4. Unrestrict the link
     */
    suspend fun resolveToDirectLink(magnetOrHash: String): Result<String> {
        val authHeader = getValidAuthHeader()
            ?: return Result.failure(Exception("No Real-Debrid account configured"))

        return try {
            // Convert hash to magnet if needed
            val magnet = if (magnetOrHash.startsWith("magnet:")) {
                magnetOrHash
            } else {
                "magnet:?xt=urn:btih:$magnetOrHash"
            }

            // 1. Add magnet
            val addResponse = realDebridApiService.addMagnet(authHeader, magnet)
            if (addResponse.error != null) {
                return Result.failure(Exception(addResponse.error))
            }
            val torrentId = addResponse.id

            // 2. Select all files
            realDebridApiService.selectFiles(authHeader, torrentId)

            // 3. Get torrent info to get the links
            val torrentInfo = realDebridApiService.getTorrentInfo(authHeader, torrentId)

            // Wait for torrent to be ready if needed (cached torrents are instant)
            if (torrentInfo.status != "downloaded" && torrentInfo.links.isNullOrEmpty()) {
                // For non-cached, this would need to poll - for now, fail
                return Result.failure(Exception("Torrent not cached, status: ${torrentInfo.status}"))
            }

            val links = torrentInfo.links
            if (links.isNullOrEmpty()) {
                return Result.failure(Exception("No links available"))
            }

            // Find the largest video file link
            val videoExtensions = listOf(".mkv", ".mp4", ".avi", ".mov", ".wmv", ".m4v", ".webm")
            val videoLink = links.firstOrNull { link ->
                videoExtensions.any { link.lowercase().contains(it) }
            } ?: links.first()

            // 4. Unrestrict the link
            val unrestrictResponse = realDebridApiService.unrestrictLink(authHeader, videoLink)
            if (unrestrictResponse.error != null) {
                return Result.failure(Exception(unrestrictResponse.error))
            }

            Log.d(TAG, "Resolved direct link: ${unrestrictResponse.filename}")
            Result.success(unrestrictResponse.download)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve direct link", e)
            Result.failure(e)
        }
    }

    /**
     * Clear/disconnect the Real-Debrid account
     */
    suspend fun clearAccount() {
        accountDao.deleteAccount(PROVIDER_ID)
        Log.d(TAG, "Real-Debrid account cleared")
    }

    /**
     * Check if a Real-Debrid account is configured
     */
    suspend fun hasAccount(): Boolean = accountDao.getAccount(PROVIDER_ID) != null

    /**
     * Calculate days remaining on premium account
     */
    fun getDaysRemaining(account: RealDebridAccount): Int? {
        return account.premiumDaysRemaining
    }
}
