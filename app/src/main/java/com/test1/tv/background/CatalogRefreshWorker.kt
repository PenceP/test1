package com.test1.tv.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.test1.tv.ContentRepositoryProvider
import com.test1.tv.data.model.ContentItem
import java.util.concurrent.TimeUnit

class CatalogRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = ContentRepositoryProvider.provide(applicationContext)
        return try {
            refreshCategory(repository::getTrendingMovies)
            refreshCategory(repository::getPopularMovies)
            refreshCategory(repository::getTrendingShows)
            refreshCategory(repository::getPopularShows)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun refreshCategory(
        fetcher: suspend (Boolean) -> kotlin.Result<List<ContentItem>>
    ) {
        fetcher(true)
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "catalog_refresh_periodic"
        private const val ONE_TIME_WORK_NAME = "catalog_refresh_one_time"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<CatalogRefreshWorker>(
                24, TimeUnit.HOURS,
                3, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<CatalogRefreshWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
