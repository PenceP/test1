package com.test1.tv.data.repository

import com.test1.tv.data.model.ContentItem

object WatchStatusProvider {
    @Volatile
    private var repo: WatchStatusRepository? = null

    fun set(repository: WatchStatusRepository) {
        repo = repository
    }

    fun getProgress(tmdbId: Int, type: ContentItem.ContentType): Double? {
        return repo?.getProgress(tmdbId, type)
    }

    /**
     * Set the progress for an item (used for immediate badge updates)
     */
    fun setProgress(tmdbId: Int, type: ContentItem.ContentType, progress: Double) {
        repo?.setProgressSync(tmdbId, type, progress)
    }

    /**
     * Remove progress for an item (mark as unwatched)
     */
    fun removeProgress(tmdbId: Int, type: ContentItem.ContentType) {
        repo?.removeProgressSync(tmdbId, type)
    }
}
