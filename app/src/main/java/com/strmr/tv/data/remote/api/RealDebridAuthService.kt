package com.strmr.tv.data.remote.api

import com.strmr.tv.data.remote.model.realdebrid.RealDebridCredentialsResponse
import com.strmr.tv.data.remote.model.realdebrid.RealDebridDeviceCodeResponse
import com.strmr.tv.data.remote.model.realdebrid.RealDebridTokenResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Real-Debrid OAuth authentication service
 * Base URL: https://api.real-debrid.com/oauth/v2/
 *
 * Real-Debrid uses a device code flow similar to other services but with
 * an additional credentials step for opensource applications.
 */
interface RealDebridAuthService {

    /**
     * Request a device code for OAuth device flow authentication
     * User will be shown a code to enter at verification_url
     *
     * For opensource apps, use new_credentials=yes to get app-specific credentials
     */
    @GET("device/code")
    suspend fun requestDeviceCode(
        @Query("client_id") clientId: String,
        @Query("new_credentials") newCredentials: String = "yes"
    ): RealDebridDeviceCodeResponse

    /**
     * Poll for app credentials after user has authorized
     * Returns client_id and client_secret bound to the user
     *
     * Note: Returns error "authorization_pending" while waiting
     */
    @GET("device/credentials")
    suspend fun pollForCredentials(
        @Query("client_id") clientId: String,
        @Query("code") deviceCode: String
    ): RealDebridCredentialsResponse

    /**
     * Exchange credentials for access token
     * Uses the client_id and client_secret from credentials step
     */
    @POST("token")
    @FormUrlEncoded
    suspend fun getToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") deviceCode: String,
        @Field("grant_type") grantType: String = "http://oauth.net/grant_type/device/1.0"
    ): RealDebridTokenResponse

    /**
     * Refresh an expired access token
     */
    @POST("token")
    @FormUrlEncoded
    suspend fun refreshToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token"
    ): RealDebridTokenResponse
}
