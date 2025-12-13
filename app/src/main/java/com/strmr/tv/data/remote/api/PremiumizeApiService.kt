package com.strmr.tv.data.remote.api

import com.strmr.tv.data.remote.model.premiumize.PremiumizeAccountResponse
import com.strmr.tv.data.remote.model.premiumize.PremiumizeCacheCheckResponse
import com.strmr.tv.data.remote.model.premiumize.PremiumizeDeviceCodeResponse
import com.strmr.tv.data.remote.model.premiumize.PremiumizeDirectLinkResponse
import com.strmr.tv.data.remote.model.premiumize.PremiumizeTokenResponse
import com.strmr.tv.data.remote.model.premiumize.PremiumizeTransferResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface PremiumizeApiService {

    /**
     * Get account information to verify API key and retrieve account status
     * Legacy method - uses API key authentication
     */
    @GET("account/info")
    suspend fun getAccountInfo(
        @Query("apikey") apiKey: String
    ): PremiumizeAccountResponse

    /**
     * Get account information using OAuth Bearer token
     */
    @GET("account/info")
    suspend fun getAccountInfoWithToken(
        @Header("Authorization") authHeader: String
    ): PremiumizeAccountResponse

    /**
     * Check if items are cached on Premiumize
     * @param hashes List of torrent info hashes to check
     */
    @POST("cache/check")
    @FormUrlEncoded
    suspend fun checkCache(
        @Query("apikey") apiKey: String,
        @Field("items[]") hashes: List<String>
    ): PremiumizeCacheCheckResponse

    /**
     * Check if items are cached using OAuth Bearer token
     */
    @POST("cache/check")
    @FormUrlEncoded
    suspend fun checkCacheWithToken(
        @Header("Authorization") authHeader: String,
        @Field("items[]") hashes: List<String>
    ): PremiumizeCacheCheckResponse

    /**
     * Create a transfer from a magnet link or torrent hash
     */
    @POST("transfer/create")
    @FormUrlEncoded
    suspend fun createTransfer(
        @Query("apikey") apiKey: String,
        @Field("src") magnetOrHash: String
    ): PremiumizeTransferResponse

    /**
     * Create a transfer using OAuth Bearer token
     */
    @POST("transfer/create")
    @FormUrlEncoded
    suspend fun createTransferWithToken(
        @Header("Authorization") authHeader: String,
        @Field("src") magnetOrHash: String
    ): PremiumizeTransferResponse

    /**
     * Get direct download link for a magnet/hash
     * This is the main method used for instant streaming of cached content
     */
    @POST("transfer/directdl")
    @FormUrlEncoded
    suspend fun getDirectLink(
        @Query("apikey") apiKey: String,
        @Field("src") magnetOrHash: String
    ): PremiumizeDirectLinkResponse

    /**
     * Get direct download link using OAuth Bearer token
     */
    @POST("transfer/directdl")
    @FormUrlEncoded
    suspend fun getDirectLinkWithToken(
        @Header("Authorization") authHeader: String,
        @Field("src") magnetOrHash: String
    ): PremiumizeDirectLinkResponse
}
