package com.test1.tv

import android.app.Application
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.test1.tv.background.CatalogRefreshWorker
import com.test1.tv.background.TraktSyncWorker
import com.test1.tv.data.config.DefaultRowConfigs
import com.test1.tv.data.local.AppDatabase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class Test1App : Application() {

    companion object {
        // Media cache size: 200MB for video segments
        private const val MEDIA_CACHE_SIZE = 200L * 1024L * 1024L

        /**
         * Singleton media cache for ExoPlayer.
         * CRITICAL: ExoPlayer requires only ONE SimpleCache instance per cache directory.
         * Multiple instances will cause crashes and data corruption.
         */
        @Volatile
        private var mediaCacheInstance: SimpleCache? = null

        fun getMediaCache(app: Application): SimpleCache {
            return mediaCacheInstance ?: synchronized(this) {
                mediaCacheInstance ?: createMediaCache(app).also { mediaCacheInstance = it }
            }
        }

        private fun createMediaCache(app: Application): SimpleCache {
            val cacheDir = File(app.cacheDir, "exoplayer_media_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(MEDIA_CACHE_SIZE)
            val databaseProvider = StandaloneDatabaseProvider(app)
            return SimpleCache(cacheDir, evictor, databaseProvider)
        }

        fun releaseMediaCache() {
            synchronized(this) {
                mediaCacheInstance?.release()
                mediaCacheInstance = null
            }
        }
    }

    @Inject
    lateinit var database: AppDatabase

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initializeDatabase()
        CatalogRefreshWorker.schedulePeriodic(this)
        CatalogRefreshWorker.enqueueImmediate(this)
        TraktSyncWorker.enqueue(this)
    }

    private fun initializeDatabase() {
        applicationScope.launch {
            val rowConfigDao = database.rowConfigDao()
            // Check if defaults are already initialized
            val existingRows = rowConfigDao.getRowCountForScreen("home")
            if (existingRows == 0) {
                // Insert default row configurations
                rowConfigDao.insertAll(DefaultRowConfigs.all)
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        releaseMediaCache()
    }
}
