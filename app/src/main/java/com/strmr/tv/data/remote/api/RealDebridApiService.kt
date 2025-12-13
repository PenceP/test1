package com.strmr.tv.data.remote.api

import com.strmr.tv.data.remote.model.realdebrid.RealDebridAddMagnetResponse
import com.strmr.tv.data.remote.model.realdebrid.RealDebridTorrentInfo
import com.strmr.tv.data.remote.model.realdebrid.RealDebridUnrestrictResponse
import com.strmr.tv.data.remote.model.realdebrid.RealDebridUserResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Real-Debrid API service
 * Base URL: https://api.real-debrid.com/rest/1.0/
 *
 * All endpoints require Authorization header with Bearer token
 */
interface RealDebridApiService {

    /**
     * Get user account information
     */
    @GET("user")
    suspend fun getUser(
        @Header("Authorization") authHeader: String
    ): RealDebridUserResponse

    /**
     * Check instant availability for torrent hashes
     * Returns map of hash -> availability info
     *
     * Note: This endpoint returns a dynamic JSON structure where keys are the hashes
     */
    @GET("torrents/instantAvailability/{hashes}")
    suspend fun checkInstantAvailability(
        @Header("Authorization") authHeader: String,
        @Path("hashes") hashes: String  // Comma-separated or slash-separated hashes
    ): Map<String, Any>  // Dynamic response structure

    /**
     * Add a magnet link to Real-Debrid
     */
    @POST("torrents/addMagnet")
    @FormUrlEncoded
    suspend fun addMagnet(
        @Header("Authorization") authHeader: String,
        @Field("magnet") magnet: String
    ): RealDebridAddMagnetResponse

    /**
     * Get torrent info by ID
     */
    @GET("torrents/info/{id}")
    suspend fun getTorrentInfo(
        @Header("Authorization") authHeader: String,
        @Path("id") id: String
    ): RealDebridTorrentInfo

    /**
     * Select files to download from a torrent
     * Use "all" to select all files
     */
    @POST("torrents/selectFiles/{id}")
    @FormUrlEncoded
    suspend fun selectFiles(
        @Header("Authorization") authHeader: String,
        @Path("id") id: String,
        @Field("files") files: String = "all"
    )

    /**
     * Unrestrict a hoster link to get direct download URL
     */
    @POST("unrestrict/link")
    @FormUrlEncoded
    suspend fun unrestrictLink(
        @Header("Authorization") authHeader: String,
        @Field("link") link: String,
        @Field("password") password: String? = null,
        @Field("remote") remote: Int = 0
    ): RealDebridUnrestrictResponse

    /**
     * Delete a torrent from your torrents list
     */
    @retrofit2.http.DELETE("torrents/delete/{id}")
    suspend fun deleteTorrent(
        @Header("Authorization") authHeader: String,
        @Path("id") id: String
    )
}
