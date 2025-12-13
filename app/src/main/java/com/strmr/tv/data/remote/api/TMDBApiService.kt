package com.strmr.tv.data.remote.api

import com.strmr.tv.data.model.tmdb.TMDBCollectionDetails
import com.strmr.tv.data.model.tmdb.TMDBMovieDetails
import com.strmr.tv.data.model.tmdb.TMDBMovieListResponse
import com.strmr.tv.data.model.tmdb.TMDBPersonDetails
import com.strmr.tv.data.model.tmdb.TMDBSeasonDetails
import com.strmr.tv.data.model.tmdb.TMDBShowDetails
import com.strmr.tv.data.model.tmdb.TMDBShowListResponse
import com.strmr.tv.data.model.tmdb.TMDBSearchResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TMDBApiService {

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,videos,images,release_dates,external_ids",
        @Query("include_image_language") includeImageLanguage: String = "en,null"
    ): TMDBMovieDetails

    @GET("tv/{tv_id}")
    suspend fun getShowDetails(
        @Path("tv_id") showId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,videos,images,content_ratings,external_ids",
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

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getSeasonDetails(
        @Path("tv_id") showId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String
    ): TMDBSeasonDetails

    @GET("tv/{tv_id}/season/{season_number}/episode/{episode_number}")
    suspend fun getEpisodeDetails(
        @Path("tv_id") showId: Int,
        @Path("season_number") seasonNumber: Int,
        @Path("episode_number") episodeNumber: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "images,external_ids",
        @Query("include_image_language") includeImageLanguage: String = "en,null"
    ): com.strmr.tv.data.model.tmdb.TMDBEpisode

    @GET("person/{person_id}")
    suspend fun getPersonDetails(
        @Path("person_id") personId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "movie_credits,tv_credits"
    ): TMDBPersonDetails

    @GET("search/multi")
    suspend fun multiSearch(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false
    ): TMDBSearchResponse
}
