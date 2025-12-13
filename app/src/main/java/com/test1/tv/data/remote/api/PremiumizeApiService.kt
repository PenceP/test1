package com.test1.tv.data.remote.api

import com.test1.tv.data.remote.model.premiumize.PremiumizeAccountResponse
import com.test1.tv.data.remote.model.premiumize.PremiumizeCacheCheckResponse
import com.test1.tv.data.remote.model.premiumize.PremiumizeDirectLinkResponse
import com.test1.tv.data.remote.model.premiumize.PremiumizeTransferResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface PremiumizeApiService {

    /**
     * Get account information to verify API key and retrieve account status
     */
    @GET("account/info")
    suspend fun getAccountInfo(
        @Query("apikey") apiKey: String
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
     * Create a transfer from a magnet link or torrent hash
     */
    @POST("transfer/create")
    @FormUrlEncoded
    suspend fun createTransfer(
        @Query("apikey") apiKey: String,
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
}
