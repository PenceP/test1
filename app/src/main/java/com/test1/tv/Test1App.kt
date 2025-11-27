package com.test1.tv

import android.app.Application
import com.test1.tv.background.CatalogRefreshWorker
import com.test1.tv.background.TraktSyncWorker

class Test1App : Application() {
    override fun onCreate() {
        super.onCreate()
        CatalogRefreshWorker.schedulePeriodic(this)
        CatalogRefreshWorker.enqueueImmediate(this)
        TraktSyncWorker.enqueue(this)
    }
}
