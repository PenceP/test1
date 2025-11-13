package com.test1.tv.data.remote.api

import com.test1.tv.data.model.tmdb.TMDBMovieDetails
import com.test1.tv.data.model.tmdb.TMDBShowDetails
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TMDBApiService {

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,videos"
    ): TMDBMovieDetails

    @GET("tv/{tv_id}")
    suspend fun getShowDetails(
        @Path("tv_id") showId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,videos"
    ): TMDBShowDetails
}
