package com.strmr.tv.data.repository

import android.util.Log
import com.strmr.tv.data.local.dao.AllDebridAccountDao
import com.strmr.tv.data.local.entity.AllDebridAccount
import com.strmr.tv.data.remote.api.AllDebridApiService
import com.strmr.tv.data.remote.model.alldebrid.AllDebridPinData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AllDebridRepository @Inject constructor(
    private val allDebridApiService: AllDebridApiService,
    private val accountDao: AllDebridAccountDao
) {
    companion object {
        private const val TAG = "AllDebridRepository"
        private const val PROVIDER_ID = "alldebrid"
    }

    /**
     * Get the stored AllDebrid account
     */
    suspend fun getAccount(): AllDebridAccount? = accountDao.getAccount(PROVIDER_ID)

    // ==================== PIN Authentication Flow ====================

    /**
     * Request a PIN for device authentication
     * User will need to enter this PIN at the provided URL
     */
    suspend fun requestPinCode(): Result<AllDebridPinData> {
        return try {
            val response = allDebridApiService.getPinCode()

            if (response.status == "success" && response.data != null) {
                Log.d(TAG, "PIN code requested: ${response.data.pin}")
                Result.success(response.data)
            } else {
                val errorMsg = response.error?.message ?: "Failed to get PIN code"
                Log.w(TAG, "PIN request failed: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request PIN code", e)
            Result.failure(e)
        }
    }

    /**
     * Poll for API key after user has entered the PIN
     * @return API key if authorized, null if still pending
     */
    suspend fun pollForApiKey(pin: String, check: String): Result<String?> {
        return try {
            val response = allDebridApiService.checkPinStatus(
                check = check,
                pin = pin
            )

            if (response.status == "success" && response.data != null) {
                val data = response.data
                if (data.activated && data.apikey != null) {
                    Log.d(TAG, "API key received successfully")
                    Result.success(data.apikey)
                } else {
                    // Not activated yet
                    Log.d(TAG, "PIN not activated yet, expires in ${data.expiresIn}s")
                    Result.success(null)
                }
            } else {
                val errorMsg = response.error?.message ?: "Unknown error"
                // Check if it's just a "not activated" error
                if (response.error?.code == "PIN_NOT_ACTIVATED") {
                    Result.success(null)
                } else {
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to poll for API key", e)
            Result.failure(e)
        }
    }

    /**
     * Save API key and fetch account info
     */
    suspend fun saveApiKey(apiKey: String): Result<AllDebridAccount> {
        return try {
            val authHeader = "Bearer $apiKey"
            val response = allDebridApiService.getUser(authHeader)

            if (response.status == "success" && response.data != null) {
                val userData = response.data.user
                val now = System.currentTimeMillis()

                val account = AllDebridAccount(
                    providerId = PROVIDER_ID,
                    apiKey = apiKey,
                    username = userData.username,
                    email = userData.email,
                    isPremium = userData.isPremium,
                    isSubscribed = userData.isSubscribed,
                    isTrial = userData.isTrial,
                    premiumUntil = userData.premiumUntil,
                    fidelityPoints = userData.fidelityPoints,
                    lastVerifiedAt = now,
                    createdAt = now
                )

                accountDao.upsert(account)
                Log.d(TAG, "AllDebrid account saved: ${userData.username}")
                Result.success(account)
            } else {
                val errorMsg = response.error?.message ?: "Failed to get user info"
                Log.w(TAG, "AllDebrid verification failed: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save API key", e)
            Result.failure(e)
        }
    }

    /**
     * Refresh account info from the API
     */
    suspend fun refreshAccountInfo(): Result<AllDebridAccount> {
        val account = accountDao.getAccount(PROVIDER_ID)
            ?: return Result.failure(Exception("No AllDebrid account configured"))

        return saveApiKey(account.apiKey)
    }

    // ==================== API Methods ====================

    /**
     * Check instant availability for torrent hashes
     * @param hashes List of info hashes to check
     * @return Map of hash to cached status (true if cached)
     */
    suspend fun checkCacheStatus(hashes: List<String>): Result<Map<String, Boolean>> {
        val account = accountDao.getAccount(PROVIDER_ID)
            ?: return Result.failure(Exception("No AllDebrid account configured"))

        return try {
            // AllDebrid expects magnets, so convert hashes to magnet URIs
            val magnets = hashes.map { "magnet:?xt=urn:btih:$it" }

            val response = allDebridApiService.checkInstantAvailability(
                authHeader = account.getAuthHeader(),
                magnets = magnets
            )

            if (response.status == "success" && response.data != null) {
                val cacheMap = mutableMapOf<String, Boolean>()
                response.data.magnets.forEach { magnetStatus ->
                    // Extract hash from magnet or use the returned hash
                    val hash = magnetStatus.hash ?: extractHashFromMagnet(magnetStatus.magnet)
                    if (hash != null) {
                        cacheMap[hash.uppercase()] = magnetStatus.instant
                    }
                }

                // Match back to original hashes
                val resultMap = hashes.associateWith { hash ->
                    cacheMap[hash.uppercase()] ?: false
                }

                Log.d(TAG, "Cache check: ${resultMap.count { it.value }} of ${hashes.size} cached")
                Result.success(resultMap)
            } else {
                Result.failure(Exception(response.error?.message ?: "Cache check failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check cache status", e)
            Result.failure(e)
        }
    }

    /**
     * Resolve a magnet link to a direct streaming URL
     */
    suspend fun resolveToDirectLink(magnetOrHash: String): Result<String> {
        val account = accountDao.getAccount(PROVIDER_ID)
            ?: return Result.failure(Exception("No AllDebrid account configured"))

        return try {
            val authHeader = account.getAuthHeader()

            // Convert hash to magnet if needed
            val magnet = if (magnetOrHash.startsWith("magnet:")) {
                magnetOrHash
            } else {
                "magnet:?xt=urn:btih:$magnetOrHash"
            }

            // Upload the magnet
            val uploadResponse = allDebridApiService.uploadMagnet(
                authHeader = authHeader,
                magnets = listOf(magnet)
            )

            if (uploadResponse.status != "success" || uploadResponse.data == null) {
                return Result.failure(Exception(uploadResponse.error?.message ?: "Failed to upload magnet"))
            }

            val uploadedMagnet = uploadResponse.data.magnets.firstOrNull()
                ?: return Result.failure(Exception("No magnet in response"))

            if (uploadedMagnet.error != null) {
                return Result.failure(Exception(uploadedMagnet.error.message))
            }

            // If ready, get the status to find links
            val magnetId = uploadedMagnet.id
                ?: return Result.failure(Exception("No magnet ID returned"))

            val statusResponse = allDebridApiService.getMagnetStatus(
                authHeader = authHeader,
                id = magnetId
            )

            if (statusResponse.status != "success" || statusResponse.data == null) {
                return Result.failure(Exception(statusResponse.error?.message ?: "Failed to get magnet status"))
            }

            val magnetDetail = statusResponse.data.magnets

            // Check if ready
            if (magnetDetail.status != "Ready") {
                return Result.failure(Exception("Magnet not ready, status: ${magnetDetail.status}"))
            }

            // Find the best video link
            val videoExtensions = listOf(".mkv", ".mp4", ".avi", ".mov", ".wmv", ".m4v", ".webm")
            val links = magnetDetail.links ?: return Result.failure(Exception("No links available"))

            // Find largest video file
            val bestLink = links
                .filter { link ->
                    videoExtensions.any { link.filename.lowercase().endsWith(it) }
                }
                .maxByOrNull { it.size }
                ?: links.maxByOrNull { it.size }
                ?: return Result.failure(Exception("No files found"))

            // Unlock the link
            val unlockResponse = allDebridApiService.unlockLink(
                authHeader = authHeader,
                link = bestLink.link
            )

            if (unlockResponse.status == "success" && unlockResponse.data != null) {
                Log.d(TAG, "Resolved direct link: ${unlockResponse.data.filename}")
                Result.success(unlockResponse.data.link)
            } else {
                Result.failure(Exception(unlockResponse.error?.message ?: "Failed to unlock link"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve direct link", e)
            Result.failure(e)
        }
    }

    /**
     * Clear/disconnect the AllDebrid account
     */
    suspend fun clearAccount() {
        accountDao.deleteAccount(PROVIDER_ID)
        Log.d(TAG, "AllDebrid account cleared")
    }

    /**
     * Check if an AllDebrid account is configured
     */
    suspend fun hasAccount(): Boolean = accountDao.getAccount(PROVIDER_ID) != null

    /**
     * Calculate days remaining on premium account
     */
    fun getDaysRemaining(account: AllDebridAccount): Int? {
        return account.getDaysRemaining()
    }

    private fun extractHashFromMagnet(magnet: String): String? {
        val regex = Regex("btih:([a-fA-F0-9]{40})", RegexOption.IGNORE_CASE)
        return regex.find(magnet)?.groupValues?.get(1)
    }
}
