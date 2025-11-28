package com.test1.tv.data.model.continuewatching

import java.time.Instant

enum class MediaType {
    MOVIE,
    SHOW,
    EPISODE,
    OTHER
}

data class LibraryItemState(
    val timeOffsetSeconds: Long,
    val runtimeSeconds: Long?,
    val lastUpdated: Instant
)

data class LibraryItem(
    val id: String,
    val type: MediaType,
    val removed: Boolean = false,
    val temp: Boolean = false,
    val state: LibraryItemState
)

data class EpisodeNotification(
    val id: String,
    val libraryItemId: String,
    val showTitle: String,
    val season: Int,
    val episode: Int,
    val videoReleased: Instant
)

data class ContinueWatchingItem(
    val libraryItem: LibraryItem,
    val notificationsCount: Int,
    val sortKey: Instant
)

fun LibraryItem.isInContinueWatching(): Boolean {
    val hasStarted = state.timeOffsetSeconds > 0L
    val notFinished = state.runtimeSeconds?.let { runtime ->
        if (runtime <= 0L) {
            true
        } else {
            val progress = state.timeOffsetSeconds.toDouble() / runtime.toDouble()
            progress < 0.95
        }
    } ?: true

    return type != MediaType.OTHER &&
        (!removed || temp) &&
        hasStarted &&
        notFinished
}

fun buildContinueWatchingRow(
    libraryItems: List<LibraryItem>,
    notificationsByItemId: Map<String, List<EpisodeNotification>>,
    maxItems: Int
): List<ContinueWatchingItem> {
    val candidates = libraryItems.mapNotNull { item ->
        val notifications = notificationsByItemId[item.id].orEmpty()
        val notificationsCount = notifications.size

        if (item.isInContinueWatching() || notificationsCount > 0) {
            val sortKey = notifications.maxOfOrNull { it.videoReleased } ?: item.state.lastUpdated
            ContinueWatchingItem(
                libraryItem = item,
                notificationsCount = notificationsCount,
                sortKey = sortKey
            )
        } else {
            null
        }
    }

    return candidates
        .sortedByDescending { it.sortKey }
        .take(maxItems)
}
