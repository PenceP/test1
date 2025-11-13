package com.test1.tv.data.remote

import com.test1.tv.BuildConfig
import com.test1.tv.data.remote.api.TMDBApiService
import com.test1.tv.data.remote.api.TraktApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TRAKT_BASE_URL = "https://api.trakt.tv/"
    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"

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

    val traktApiService: TraktApiService by lazy {
        traktRetrofit.create(TraktApiService::class.java)
    }

    val tmdbApiService: TMDBApiService by lazy {
        tmdbRetrofit.create(TMDBApiService::class.java)
    }
}
