package com.test1.tv.data.remote.api

import com.test1.tv.data.model.trakt.TraktMovie
import com.test1.tv.data.model.trakt.TraktShow
import com.test1.tv.data.model.trakt.TraktTrendingMovie
import com.test1.tv.data.model.trakt.TraktTrendingShow
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface TraktApiService {

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
