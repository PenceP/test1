package com.test1.tv.data.remote.api

import com.test1.tv.data.remote.model.omdb.OMDbTitleResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OMDbApiService {

    @GET(".")
    suspend fun getTitleDetails(
        @Query("i") imdbId: String,
        @Query("apikey") apiKey: String
    ): OMDbTitleResponse
}
