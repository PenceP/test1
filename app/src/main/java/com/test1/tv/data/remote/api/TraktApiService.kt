package com.test1.tv.data.remote.api

import com.test1.tv.data.model.trakt.TraktMovie
import com.test1.tv.data.model.trakt.TraktShow
import com.test1.tv.data.model.trakt.TraktTrendingMovie
import com.test1.tv.data.model.trakt.TraktTrendingShow
import com.test1.tv.data.model.trakt.TraktDeviceCodeResponse
import com.test1.tv.data.model.trakt.TraktTokenResponse
import com.test1.tv.data.model.trakt.TraktUser
import com.test1.tv.data.model.trakt.TraktUserStats
import com.test1.tv.data.model.trakt.TraktWatchlistItem
import com.test1.tv.data.model.trakt.TraktCollectionItem
import com.test1.tv.data.model.trakt.TraktHistoryItem
import com.test1.tv.data.model.trakt.TraktLastActivities
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded

interface TraktApiService {

    @FormUrlEncoded
    @POST("oauth/device/code")
    suspend fun createDeviceCode(
        @Field("client_id") clientId: String
    ): TraktDeviceCodeResponse

    @FormUrlEncoded
    @POST("oauth/device/token")
    suspend fun pollDeviceToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") deviceCode: String
    ): TraktTokenResponse

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun refreshToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("redirect_uri") redirectUri: String = "urn:ietf:wg:oauth:2.0:oob"
    ): TraktTokenResponse

    @GET("users/me")
    suspend fun getUserProfile(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String
    ): TraktUser

    @GET("users/{user_slug}/stats")
    suspend fun getUserStats(
        @Path("user_slug") userSlug: String,
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String
    ): TraktUserStats

    @GET("sync/last_activities")
    suspend fun getLastActivities(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String
    ): TraktLastActivities

    @GET("users/me/watchlist/movies")
    suspend fun getWatchlistMovies(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 200,
        @Query("extended") extended: String = "full"
    ): List<TraktWatchlistItem>

    @GET("users/me/watchlist/shows")
    suspend fun getWatchlistShows(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 200,
        @Query("extended") extended: String = "full"
    ): List<TraktWatchlistItem>

    @GET("users/me/collection/movies")
    suspend fun getCollectionMovies(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 500,
        @Query("extended") extended: String = "full"
    ): List<TraktCollectionItem>

    @GET("users/me/collection/shows")
    suspend fun getCollectionShows(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 500,
        @Query("extended") extended: String = "full"
    ): List<TraktCollectionItem>

    @GET("users/me/history/movies")
    suspend fun getHistoryMovies(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 200,
        @Query("extended") extended: String = "full"
    ): List<TraktHistoryItem>

    @GET("users/me/history/shows")
    suspend fun getHistoryShows(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 200,
        @Query("extended") extended: String = "full"
    ): List<TraktHistoryItem>

    @GET("movies/trending")
    suspend fun getTrendingMovies(
        @Header("Content-Type") contentType: String = "application/json",
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("limit") limit: Int = 40,
        @Query("page") page: Int = 1,
        @Query("extended") extended: String = "full"
    ): List<TraktTrendingMovie>

    @GET("movies/popular")
    suspend fun getPopularMovies(
        @Header("Content-Type") contentType: String = "application/json",
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("limit") limit: Int = 40,
        @Query("page") page: Int = 1,
        @Query("extended") extended: String = "full"
    ): List<TraktMovie>

    @GET("shows/trending")
    suspend fun getTrendingShows(
        @Header("Content-Type") contentType: String = "application/json",
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("limit") limit: Int = 40,
        @Query("page") page: Int = 1,
        @Query("extended") extended: String = "full"
    ): List<TraktTrendingShow>

    @GET("shows/popular")
    suspend fun getPopularShows(
        @Header("Content-Type") contentType: String = "application/json",
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("limit") limit: Int = 40,
        @Query("page") page: Int = 1,
        @Query("extended") extended: String = "full"
    ): List<TraktShow>

    @GET("movies/{movie_id}/related")
    suspend fun getRelatedMovies(
        @Path("movie_id") movieId: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("limit") limit: Int = 20,
        @Query("page") page: Int = 1,
        @Query("extended") extended: String = "full"
    ): List<TraktMovie>

    @GET("shows/{show_id}/related")
    suspend fun getRelatedShows(
        @Path("show_id") showId: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("limit") limit: Int = 20,
        @Query("page") page: Int = 1,
        @Query("extended") extended: String = "full"
    ): List<TraktShow>
}
