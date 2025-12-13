package com.strmr.tv.data.remote.api

import com.strmr.tv.data.remote.model.premiumize.PremiumizeDeviceCodeResponse
import com.strmr.tv.data.remote.model.premiumize.PremiumizeTokenResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Premiumize OAuth authentication service
 * Base URL: https://www.premiumize.me/
 *
 * This is separate from PremiumizeApiService because the OAuth endpoints
 * are at the root URL, not under /api/
 *
 * Client credentials are provided from BuildConfig (secrets.properties)
 */
interface PremiumizeAuthService {

    /**
     * Request a device code for OAuth device flow authentication
     * User will be shown a code to enter at verification_uri
     */
    @POST("token")
    @FormUrlEncoded
    suspend fun requestDeviceCode(
        @Field("client_id") clientId: String,
        @Field("response_type") responseType: String = "device_code"
    ): PremiumizeDeviceCodeResponse

    /**
     * Poll for access token after user has authorized the device code
     * Should be called at intervals specified by device code response
     */
    @POST("token")
    @FormUrlEncoded
    suspend fun pollForToken(
        @Field("client_id") clientId: String,
        @Field("code") deviceCode: String,
        @Field("grant_type") grantType: String = "device_code"
    ): PremiumizeTokenResponse
}
