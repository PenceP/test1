package com.strmr.tv.data.remote

import com.strmr.tv.BuildConfig
import com.strmr.tv.data.remote.api.OMDbApiService
import com.strmr.tv.data.remote.api.TMDBApiService
import com.strmr.tv.data.remote.api.TraktApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TRAKT_BASE_URL = "https://api.trakt.tv/"
    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
    private const val OMDB_BASE_URL = "https://www.omdbapi.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val traktRetrofit = Retrofit.Builder()
        .baseUrl(TRAKT_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val tmdbRetrofit = Retrofit.Builder()
        .baseUrl(TMDB_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val omdbRetrofit = Retrofit.Builder()
        .baseUrl(OMDB_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val traktApiService: TraktApiService by lazy {
        traktRetrofit.create(TraktApiService::class.java)
    }

    val tmdbApiService: TMDBApiService by lazy {
        tmdbRetrofit.create(TMDBApiService::class.java)
    }

    val omdbApiService: OMDbApiService by lazy {
        omdbRetrofit.create(OMDbApiService::class.java)
    }
}
