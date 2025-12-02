package com.test1.tv

import android.app.Application
import com.test1.tv.background.CatalogRefreshWorker
import com.test1.tv.background.TraktSyncWorker
import com.test1.tv.data.config.DefaultRowConfigs
import com.test1.tv.data.local.AppDatabase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class Test1App : Application() {

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
}
