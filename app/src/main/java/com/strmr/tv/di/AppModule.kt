package com.strmr.tv.di

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.strmr.tv.BuildConfig
import com.strmr.tv.R
import com.strmr.tv.data.local.AppDatabase
import com.strmr.tv.data.local.MIGRATION_11_12
import com.strmr.tv.data.local.MIGRATION_12_13
import com.strmr.tv.data.local.MIGRATION_13_14
import com.strmr.tv.data.local.MIGRATION_14_15
import com.strmr.tv.data.local.MIGRATION_15_16
import com.strmr.tv.data.local.MIGRATION_16_17
import com.strmr.tv.data.local.MIGRATION_17_18
import com.strmr.tv.data.local.MIGRATION_18_19
import com.strmr.tv.data.local.MIGRATION_19_20
import com.strmr.tv.data.local.MIGRATION_20_21
import com.strmr.tv.data.remote.api.OMDbApiService
import com.strmr.tv.data.remote.api.RealDebridApiService
import com.strmr.tv.data.remote.api.RealDebridAuthService
import com.strmr.tv.data.remote.api.AllDebridApiService
import com.strmr.tv.data.remote.api.PremiumizeApiService
import com.strmr.tv.data.remote.api.PremiumizeAuthService
import com.strmr.tv.data.remote.api.TMDBApiService
import com.strmr.tv.data.remote.api.TorrentioApiService
import com.strmr.tv.data.remote.api.TraktApiService
import com.strmr.tv.data.remote.api.OpenSubtitlesApiService
import com.strmr.tv.data.remote.api.GitHubApiService
import com.strmr.tv.data.repository.TorrentioRepository
import com.strmr.tv.data.repository.UpdateRepository
import com.strmr.tv.update.UpdateDownloadManager
import com.strmr.tv.data.repository.CacheRepository
import com.strmr.tv.data.repository.ContinueWatchingRepository
import com.strmr.tv.data.repository.HomeConfigRepository
import com.strmr.tv.data.repository.SyncMetadataRepository
import com.strmr.tv.data.repository.LinkFilterPreferences
import com.strmr.tv.data.repository.PremiumizeRepository
import com.strmr.tv.data.repository.RealDebridRepository
import com.strmr.tv.data.repository.AllDebridRepository
import com.strmr.tv.data.service.LinkFilterService
import com.strmr.tv.data.repository.TraktAccountRepository
import com.strmr.tv.data.repository.TraktMediaRepository
import com.strmr.tv.data.repository.WatchStatusRepository
import com.strmr.tv.data.repository.PlayerSettingsRepository
import com.strmr.tv.ui.AccentColorCache
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
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

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PremiumizeRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PremiumizeAuthRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TorrentioRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenSubtitlesRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RealDebridRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RealDebridAuthRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AllDebridRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubRetrofit

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
            "strmr_tv_database"
        )
            .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21)
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

    @Provides
    fun provideMediaDao(database: AppDatabase) = database.mediaDao()

    @Provides
    fun provideRowConfigDao(database: AppDatabase) = database.rowConfigDao()

    @Provides
    fun provideSyncMetadataDao(database: AppDatabase) = database.syncMetadataDao()

    @Provides
    fun providePremiumizeAccountDao(database: AppDatabase) = database.premiumizeAccountDao()

    @Provides
    fun providePlayerSettingsDao(database: AppDatabase) = database.playerSettingsDao()

    @Provides
    fun providePlaybackProgressDao(database: AppDatabase) = database.playbackProgressDao()

    @Provides
    fun provideRealDebridAccountDao(database: AppDatabase) = database.realDebridAccountDao()

    @Provides
    fun provideAllDebridAccountDao(database: AppDatabase) = database.allDebridAccountDao()

    // Repository helpers
    @Provides
    @Singleton
    fun provideCacheRepository(cachedContentDao: com.strmr.tv.data.local.dao.CachedContentDao) =
        com.strmr.tv.data.repository.CacheRepository(cachedContentDao)

    @Provides
    @Singleton
    fun provideWatchStatusRepository(watchStatusDao: com.strmr.tv.data.local.dao.WatchStatusDao) =
        com.strmr.tv.data.repository.WatchStatusRepository(watchStatusDao)

    @Provides
    @Singleton
    fun provideAccentColorCache(): AccentColorCache = AccentColorCache()

    @Provides
    @Singleton
    fun provideTraktAccountRepository(
        traktApiService: TraktApiService,
        accountDao: com.strmr.tv.data.local.dao.TraktAccountDao
    ) = TraktAccountRepository(traktApiService, accountDao)

    @Provides
    @Singleton
    fun providePremiumizeRepository(
        premiumizeApiService: PremiumizeApiService,
        premiumizeAuthService: PremiumizeAuthService,
        premiumizeAccountDao: com.strmr.tv.data.local.dao.PremiumizeAccountDao
    ) = PremiumizeRepository(premiumizeApiService, premiumizeAuthService, premiumizeAccountDao)

    @Provides
    @Singleton
    fun provideRealDebridRepository(
        realDebridApiService: RealDebridApiService,
        realDebridAuthService: RealDebridAuthService,
        realDebridAccountDao: com.strmr.tv.data.local.dao.RealDebridAccountDao
    ) = RealDebridRepository(realDebridApiService, realDebridAuthService, realDebridAccountDao)

    @Provides
    @Singleton
    fun provideAllDebridRepository(
        allDebridApiService: AllDebridApiService,
        allDebridAccountDao: com.strmr.tv.data.local.dao.AllDebridAccountDao
    ) = AllDebridRepository(allDebridApiService, allDebridAccountDao)

    @Provides
    @Singleton
    fun provideContinueWatchingRepository(
        traktApiService: TraktApiService,
        tmdbApiService: TMDBApiService,
        accountRepository: TraktAccountRepository,
        syncMetadataRepository: SyncMetadataRepository
    ) = ContinueWatchingRepository(traktApiService, tmdbApiService, accountRepository, syncMetadataRepository)

    @Provides
    @Singleton
    fun provideContentLoaderUseCase(
        contentRepository: com.strmr.tv.data.repository.ContentRepository,
        mediaRepository: com.strmr.tv.data.repository.MediaRepository,
        continueWatchingRepository: ContinueWatchingRepository,
        traktApiService: TraktApiService,
        traktAccountRepository: com.strmr.tv.data.repository.TraktAccountRepository,
        tmdbApiService: TMDBApiService
    ): com.strmr.tv.domain.ContentLoaderUseCase =
        com.strmr.tv.domain.ContentLoaderUseCase(contentRepository, mediaRepository, continueWatchingRepository, traktApiService, traktAccountRepository, tmdbApiService)

    @Provides
    @Singleton
    fun provideTraktMediaRepository(
        traktUserItemDao: com.strmr.tv.data.local.dao.TraktUserItemDao,
        tmdbApiService: TMDBApiService,
        cacheRepository: CacheRepository,
        watchStatusRepository: WatchStatusRepository,
        traktSyncRepository: com.strmr.tv.data.repository.TraktSyncRepository
    ) = TraktMediaRepository(traktUserItemDao, tmdbApiService, cacheRepository, watchStatusRepository, traktSyncRepository)

    @Provides
    @Singleton
    fun provideHomeConfigRepository(@ApplicationContext context: Context, gson: Gson): HomeConfigRepository =
        HomeConfigRepository(context, gson)

    @Provides
    @Singleton
    fun provideScreenConfigRepository(
        rowConfigDao: com.strmr.tv.data.local.dao.RowConfigDao,
        traktAccountRepository: com.strmr.tv.data.repository.TraktAccountRepository,
        @ApplicationContext context: Context
    ): com.strmr.tv.data.repository.ScreenConfigRepository =
        com.strmr.tv.data.repository.ScreenConfigRepository(rowConfigDao, traktAccountRepository, context)

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideLinkFilterPreferences(@ApplicationContext context: Context): LinkFilterPreferences =
        LinkFilterPreferences(context)

    @Provides
    @Singleton
    fun provideLinkFilterService(linkFilterPreferences: LinkFilterPreferences): LinkFilterService =
        LinkFilterService(linkFilterPreferences)

    @Provides
    @Singleton
    fun providePlayerSettingsRepository(playerSettingsDao: com.strmr.tv.data.local.dao.PlayerSettingsDao): PlayerSettingsRepository =
        PlayerSettingsRepository(playerSettingsDao)

    // ==================== Network ====================

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val cacheSize = 50L * 1024L * 1024L // 50 MB
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDir, cacheSize)

        return OkHttpClient.Builder()
            .cache(cache)
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
    @PremiumizeRetrofit
    fun providePremiumizeRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://www.premiumize.me/api/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @PremiumizeAuthRetrofit
    fun providePremiumizeAuthRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://www.premiumize.me/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @TorrentioRetrofit
    fun provideTorrentioRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://torrentio.strem.fun/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @OpenSubtitlesRetrofit
    fun provideOpenSubtitlesRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(OpenSubtitlesApiService.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @RealDebridRetrofit
    fun provideRealDebridRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.real-debrid.com/rest/1.0/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @RealDebridAuthRetrofit
    fun provideRealDebridAuthRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.real-debrid.com/oauth/v2/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @AllDebridRetrofit
    fun provideAllDebridRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.alldebrid.com/v4/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @GitHubRetrofit
    fun provideGitHubRetrofit(client: OkHttpClient): Retrofit {
        val gitHubClient = client.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .addHeader("User-Agent", "STRMR-App/${BuildConfig.VERSION_NAME}")
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(gitHubClient)
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

    @Provides
    @Singleton
    fun providePremiumizeApiService(@PremiumizeRetrofit retrofit: Retrofit): PremiumizeApiService {
        return retrofit.create(PremiumizeApiService::class.java)
    }

    @Provides
    @Singleton
    fun providePremiumizeAuthService(@PremiumizeAuthRetrofit retrofit: Retrofit): PremiumizeAuthService {
        return retrofit.create(PremiumizeAuthService::class.java)
    }

    @Provides
    @Singleton
    fun provideTorrentioApiService(@TorrentioRetrofit retrofit: Retrofit): TorrentioApiService {
        return retrofit.create(TorrentioApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideOpenSubtitlesApiService(@OpenSubtitlesRetrofit retrofit: Retrofit): OpenSubtitlesApiService {
        return retrofit.create(OpenSubtitlesApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideRealDebridApiService(@RealDebridRetrofit retrofit: Retrofit): RealDebridApiService {
        return retrofit.create(RealDebridApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideRealDebridAuthService(@RealDebridAuthRetrofit retrofit: Retrofit): RealDebridAuthService {
        return retrofit.create(RealDebridAuthService::class.java)
    }

    @Provides
    @Singleton
    fun provideAllDebridApiService(@AllDebridRetrofit retrofit: Retrofit): AllDebridApiService {
        return retrofit.create(AllDebridApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideGitHubApiService(@GitHubRetrofit retrofit: Retrofit): GitHubApiService {
        return retrofit.create(GitHubApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideUpdateRepository(gitHubApiService: GitHubApiService): UpdateRepository {
        return UpdateRepository(gitHubApiService)
    }

    @Provides
    @Singleton
    fun provideUpdateDownloadManager(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): UpdateDownloadManager {
        return UpdateDownloadManager(context, okHttpClient)
    }

    @Provides
    @Singleton
    fun provideTorrentioRepository(
        torrentioApiService: TorrentioApiService,
        premiumizeRepository: PremiumizeRepository,
        realDebridRepository: RealDebridRepository,
        allDebridRepository: AllDebridRepository,
        linkFilterService: LinkFilterService
    ): TorrentioRepository =
        TorrentioRepository(
            torrentioApiService,
            premiumizeRepository,
            realDebridRepository,
            allDebridRepository,
            linkFilterService
        )

    // ==================== Utilities ====================

    @Provides
    @Singleton
    fun provideRateLimiter(): com.strmr.tv.data.remote.RateLimiter = com.strmr.tv.data.remote.RateLimiter()

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
