package com.test1.tv.di

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.test1.tv.BuildConfig
import com.test1.tv.R
import com.test1.tv.data.local.AppDatabase
import com.test1.tv.data.remote.api.OMDbApiService
import com.test1.tv.data.remote.api.TMDBApiService
import com.test1.tv.data.remote.api.TraktApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TmdbRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TraktRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OmdbRetrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ==================== Database ====================

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "test1_tv_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideCachedContentDao(database: AppDatabase) = database.cachedContentDao()

    @Provides
    fun provideTraktAccountDao(database: AppDatabase) = database.traktAccountDao()

    @Provides
    fun provideTraktUserItemDao(database: AppDatabase) = database.traktUserItemDao()

    @Provides
    fun provideContinueWatchingDao(database: AppDatabase) = database.continueWatchingDao()

    @Provides
    fun provideWatchStatusDao(database: AppDatabase) = database.watchStatusDao()

    // ==================== Network ====================

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            })
            .build()
    }

    @Provides
    @Singleton
    @TmdbRetrofit
    fun provideTmdbRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @TraktRetrofit
    fun provideTraktRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.trakt.tv/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @OmdbRetrofit
    fun provideOmdbRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://www.omdbapi.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideTmdbApiService(@TmdbRetrofit retrofit: Retrofit): TMDBApiService {
        return retrofit.create(TMDBApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideTraktApiService(@TraktRetrofit retrofit: Retrofit): TraktApiService {
        return retrofit.create(TraktApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideOmdbApiService(@OmdbRetrofit retrofit: Retrofit): OMDbApiService {
        return retrofit.create(OMDbApiService::class.java)
    }

    // ==================== UI Performance ====================

    /**
     * CRITICAL PERFORMANCE FIX: Shared ViewPool for nested RecyclerViews
     * This allows poster views to be recycled across different rows,
     * dramatically reducing view inflation during scrolling.
     */
    @Provides
    @Singleton
    fun provideSharedViewPool(): RecyclerView.RecycledViewPool {
        return RecyclerView.RecycledViewPool().apply {
            setMaxRecycledViews(R.layout.item_poster, 40)
            setMaxRecycledViews(R.layout.item_poster_landscape, 25)
        }
    }
}
