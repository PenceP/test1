package com.test1.tv

import android.content.Context
import com.test1.tv.data.local.AppDatabase
import com.test1.tv.data.repository.CacheRepository
import com.test1.tv.data.repository.ContentRepository
import com.test1.tv.data.remote.ApiClient

object ContentRepositoryProvider {

    fun provide(context: Context): ContentRepository {
        val database = AppDatabase.getDatabase(context)
        val cacheRepository = CacheRepository(database.cachedContentDao())
        return ContentRepository(
            traktApiService = ApiClient.traktApiService,
            tmdbApiService = ApiClient.tmdbApiService,
            cacheRepository = cacheRepository
        )
    }
}
