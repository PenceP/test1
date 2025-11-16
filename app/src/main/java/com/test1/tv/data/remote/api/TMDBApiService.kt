package com.test1.tv.data.remote.api

import com.test1.tv.data.model.tmdb.TMDBCollectionDetails
import com.test1.tv.data.model.tmdb.TMDBMovieDetails
import com.test1.tv.data.model.tmdb.TMDBMovieListResponse
import com.test1.tv.data.model.tmdb.TMDBShowDetails
import com.test1.tv.data.model.tmdb.TMDBShowListResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TMDBApiService {

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,videos,images,release_dates",
        @Query("include_image_language") includeImageLanguage: String = "en,null"
    ): TMDBMovieDetails

    @GET("tv/{tv_id}")
    suspend fun getShowDetails(
        @Path("tv_id") showId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,videos,images,content_ratings",
        @Query("include_image_language") includeImageLanguage: String = "en,null"
    ): TMDBShowDetails

    @GET("collection/{collection_id}")
    suspend fun getCollectionDetails(
        @Path("collection_id") collectionId: Int,
        @Query("api_key") apiKey: String
    ): TMDBCollectionDetails

    @GET("movie/{movie_id}/similar")
    suspend fun getSimilarMovies(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TMDBMovieListResponse

    @GET("tv/{tv_id}/similar")
    suspend fun getSimilarShows(
        @Path("tv_id") showId: Int,
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TMDBShowListResponse
}
