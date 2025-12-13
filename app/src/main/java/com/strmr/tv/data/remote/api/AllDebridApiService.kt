package com.strmr.tv.data.remote.api

import com.strmr.tv.data.remote.model.alldebrid.AllDebridInstantData
import com.strmr.tv.data.remote.model.alldebrid.AllDebridMagnetStatusData
import com.strmr.tv.data.remote.model.alldebrid.AllDebridMagnetUploadData
import com.strmr.tv.data.remote.model.alldebrid.AllDebridPinCheckData
import com.strmr.tv.data.remote.model.alldebrid.AllDebridPinData
import com.strmr.tv.data.remote.model.alldebrid.AllDebridResponse
import com.strmr.tv.data.remote.model.alldebrid.AllDebridUnlockData
import com.strmr.tv.data.remote.model.alldebrid.AllDebridUserData
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * AllDebrid API service
 * Base URL: https://api.alldebrid.com/v4/
 *
 * AllDebrid uses a PIN-based authentication flow.
 * All responses are wrapped in AllDebridResponse<T>.
 * Agent parameter is required for all requests.
 */
interface AllDebridApiService {

    companion object {
        const val AGENT = "STRMR"  // App name for API identification
    }

    // ==================== PIN Authentication ====================

    /**
     * Get a PIN for device authentication
     * User must enter this PIN on AllDebrid website
     */
    @GET("pin/get")
    suspend fun getPinCode(
        @Query("agent") agent: String = AGENT
    ): AllDebridResponse<AllDebridPinData>

    /**
     * Check if user has entered the PIN on website
     * Poll this endpoint until activated=true
     */
    @GET("pin/check")
    suspend fun checkPinStatus(
        @Query("agent") agent: String = AGENT,
        @Query("check") check: String,
        @Query("pin") pin: String
    ): AllDebridResponse<AllDebridPinCheckData>

    // ==================== User Info ====================

    /**
     * Get user account information
     */
    @GET("user")
    suspend fun getUser(
        @Header("Authorization") authHeader: String,
        @Query("agent") agent: String = AGENT
    ): AllDebridResponse<AllDebridUserData>

    // ==================== Link Operations ====================

    /**
     * Unlock/unrestrict a link to get direct download URL
     */
    @POST("link/unlock")
    @FormUrlEncoded
    suspend fun unlockLink(
        @Header("Authorization") authHeader: String,
        @Field("link") link: String,
        @Field("password") password: String? = null,
        @Query("agent") agent: String = AGENT
    ): AllDebridResponse<AllDebridUnlockData>

    // ==================== Magnet/Torrent Operations ====================

    /**
     * Check instant availability for magnets/hashes
     * Returns which magnets are cached and ready for instant download
     */
    @GET("magnet/instant")
    suspend fun checkInstantAvailability(
        @Header("Authorization") authHeader: String,
        @Query("magnets[]") magnets: List<String>,
        @Query("agent") agent: String = AGENT
    ): AllDebridResponse<AllDebridInstantData>

    /**
     * Upload magnet links to AllDebrid
     */
    @POST("magnet/upload")
    @FormUrlEncoded
    suspend fun uploadMagnet(
        @Header("Authorization") authHeader: String,
        @Field("magnets[]") magnets: List<String>,
        @Query("agent") agent: String = AGENT
    ): AllDebridResponse<AllDebridMagnetUploadData>

    /**
     * Get status of a magnet by ID
     */
    @GET("magnet/status")
    suspend fun getMagnetStatus(
        @Header("Authorization") authHeader: String,
        @Query("id") id: Long,
        @Query("agent") agent: String = AGENT
    ): AllDebridResponse<AllDebridMagnetStatusData>

    /**
     * Delete a magnet from your list
     */
    @GET("magnet/delete")
    suspend fun deleteMagnet(
        @Header("Authorization") authHeader: String,
        @Query("id") id: Long,
        @Query("agent") agent: String = AGENT
    ): AllDebridResponse<Any>
}
