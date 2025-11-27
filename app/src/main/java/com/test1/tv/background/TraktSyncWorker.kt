package com.test1.tv.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.test1.tv.ContentRepositoryProvider
import com.test1.tv.data.local.AppDatabase
import com.test1.tv.data.remote.ApiClient
import com.test1.tv.data.repository.TraktAccountRepository
import com.test1.tv.data.repository.TraktSyncRepository

class TraktSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val accountRepository = TraktAccountRepository(
            traktApiService = ApiClient.traktApiService,
            accountDao = db.traktAccountDao()
        )
        val account = accountRepository.getAccount() ?: return Result.success()

        val syncRepository = TraktSyncRepository(
            traktApiService = ApiClient.traktApiService,
            accountRepository = accountRepository,
            userItemDao = db.traktUserItemDao()
        )

        return try {
            val success = syncRepository.syncAll()
            if (success) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val ONE_TIME_WORK_NAME = "trakt_sync_worker"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<TraktSyncWorker>()
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
