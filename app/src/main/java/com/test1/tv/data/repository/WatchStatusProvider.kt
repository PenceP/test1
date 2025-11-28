package com.test1.tv.data.repository

object WatchStatusProvider {
    @Volatile
    private var repo: WatchStatusRepository? = null

    fun set(repository: WatchStatusRepository) {
        repo = repository
    }

    fun getProgress(tmdbId: Int, type: com.test1.tv.data.model.ContentItem.ContentType): Double? {
        return repo?.getProgress(tmdbId, type)
    }
}
