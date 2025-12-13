package com.strmr.tv.data.remote.api

import com.strmr.tv.data.model.trakt.TraktMovie
import com.strmr.tv.data.model.trakt.TraktShow
import com.strmr.tv.data.model.trakt.TraktTrendingMovie
import com.strmr.tv.data.model.trakt.TraktTrendingShow
import com.strmr.tv.data.model.trakt.TraktDeviceCodeResponse
import com.strmr.tv.data.model.trakt.TraktTokenResponse
import com.strmr.tv.data.model.trakt.TraktUser
import com.strmr.tv.data.model.trakt.TraktUserStats
import com.strmr.tv.data.model.trakt.TraktWatchlistItem
import com.strmr.tv.data.model.trakt.TraktCollectionItem
import com.strmr.tv.data.model.trakt.TraktHistoryItem
import com.strmr.tv.data.model.trakt.TraktLastActivities
import com.strmr.tv.data.model.trakt.TraktWatchedMovie
import com.strmr.tv.data.model.trakt.TraktWatchedShow
import com.strmr.tv.data.model.trakt.TraktListItem
import com.strmr.tv.data.model.trakt.TraktShowProgress
import com.strmr.tv.data.model.trakt.TraktUserList
import com.strmr.tv.data.model.trakt.TraktLikedList
import com.strmr.tv.data.model.trakt.TraktPlaybackItem
import com.strmr.tv.data.model.trakt.RemovePlaybackRequest
import com.strmr.tv.data.model.trakt.TraktSyncRequest
import com.strmr.tv.data.model.trakt.TraktSyncResponse
import com.strmr.tv.data.model.trakt.TraktRatingRequest
import com.strmr.tv.data.model.trakt.TraktScrobbleRequest
import com.strmr.tv.data.model.trakt.TraktScrobbleResponse
import com.strmr.tv.data.model.trakt.TraktSearchResult
import retrofit2.http.Body
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

    @GET("users/me/lists")
    suspend fun getUserLists(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String
    ): List<TraktUserList>

    @GET("users/me/likes/lists")
    suspend fun getLikedLists(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): List<TraktLikedList>

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

    @GET("sync/watched/movies")
    suspend fun getWatchedMovies(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("extended") extended: String = "full",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 1000
    ): List<TraktWatchedMovie>

    @GET("sync/watched/shows")
    suspend fun getWatchedShows(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("extended") extended: String = "full",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 1000
    ): List<TraktWatchedShow>

    @GET("shows/{id}/progress/watched")
    suspend fun getShowProgress(
        @Path("id") showId: String,
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("hidden") hidden: Boolean = false,
        @Query("specials") specials: Boolean = false
    ): TraktShowProgress

    @GET("sync/playback/movies")
    suspend fun getPlaybackMovies(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): List<TraktPlaybackItem>

    @GET("sync/playback/episodes")
    suspend fun getPlaybackEpisodes(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): List<TraktPlaybackItem>

    @GET("sync/playback")
    suspend fun getPlayback(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("limit") limit: Int? = null
    ): List<TraktPlaybackItem>

    @POST("sync/playback/remove")
    suspend fun removePlayback(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Body body: com.strmr.tv.data.model.trakt.RemovePlaybackRequest
    )

    @GET("users/{user}/lists/{list}/items/movies")
    suspend fun getListMovies(
        @Path("user") user: String,
        @Path("list") list: String,
        @Header("Authorization") authHeader: String? = null,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("extended") extended: String = "full",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 40
    ): List<TraktListItem>

    @GET("users/{user}/lists/{list}/items/shows")
    suspend fun getListShows(
        @Path("user") user: String,
        @Path("list") list: String,
        @Header("Authorization") authHeader: String? = null,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("extended") extended: String = "full",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 40
    ): List<TraktListItem>

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

    // ==================== SYNC ACTIONS ====================

    /**
     * Add items to watched history (mark as watched)
     */
    @POST("sync/history")
    suspend fun addToHistory(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Body body: TraktSyncRequest
    ): TraktSyncResponse

    /**
     * Remove items from watched history (mark as unwatched)
     */
    @POST("sync/history/remove")
    suspend fun removeFromHistory(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Body body: TraktSyncRequest
    ): TraktSyncResponse

    /**
     * Add items to collection
     */
    @POST("sync/collection")
    suspend fun addToCollection(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Body body: TraktSyncRequest
    ): TraktSyncResponse

    /**
     * Remove items from collection
     */
    @POST("sync/collection/remove")
    suspend fun removeFromCollection(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Body body: TraktSyncRequest
    ): TraktSyncResponse

    /**
     * Add items to watchlist
     */
    @POST("sync/watchlist")
    suspend fun addToWatchlist(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Body body: TraktSyncRequest
    ): TraktSyncResponse

    /**
     * Remove items from watchlist
     */
    @POST("sync/watchlist/remove")
    suspend fun removeFromWatchlist(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Body body: TraktSyncRequest
    ): TraktSyncResponse

    /**
     * Rate an item
     */
    @POST("sync/ratings")
    suspend fun addRating(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Body body: TraktRatingRequest
    ): TraktSyncResponse

    /**
     * Remove rating from item
     */
    @POST("sync/ratings/remove")
    suspend fun removeRating(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Body body: TraktSyncRequest
    ): TraktSyncResponse

    /**
     * Lookup a show by TMDB ID to get Trakt ID
     */
    @GET("search/tmdb/{id}")
    suspend fun lookupByTmdbId(
        @Path("id") tmdbId: Int,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Query("type") type: String = "show"
    ): List<TraktSearchResult>

    // ==================== SCROBBLE API ====================

    /**
     * Start watching - call when playback begins
     * Tells Trakt what the user is currently watching
     */
    @POST("scrobble/start")
    suspend fun startScrobble(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Body body: TraktScrobbleRequest
    ): TraktScrobbleResponse

    /**
     * Pause watching - call when playback is paused
     */
    @POST("scrobble/pause")
    suspend fun pauseScrobble(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Body body: TraktScrobbleRequest
    ): TraktScrobbleResponse

    /**
     * Stop watching - call when playback ends or user exits
     * If progress >= 80%, Trakt will auto-mark as watched (scrobble)
     */
    @POST("scrobble/stop")
    suspend fun stopScrobble(
        @Header("Authorization") authHeader: String,
        @Header("trakt-api-version") apiVersion: String = "2",
        @Header("trakt-api-key") clientId: String,
        @Body body: TraktScrobbleRequest
    ): TraktScrobbleResponse
}
