package com.test1.tv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.test1.tv.data.local.dao.CachedContentDao
import com.test1.tv.data.local.dao.TraktAccountDao
import com.test1.tv.data.local.dao.TraktUserItemDao
import com.test1.tv.data.local.dao.ContinueWatchingDao
import com.test1.tv.data.local.dao.WatchStatusDao
import com.test1.tv.data.local.dao.MediaDao
import com.test1.tv.data.local.dao.RowConfigDao
import com.test1.tv.data.local.dao.SyncMetadataDao
import com.test1.tv.data.local.dao.PremiumizeAccountDao
import com.test1.tv.data.local.dao.PlayerSettingsDao
import com.test1.tv.data.local.dao.PlaybackProgressDao
import com.test1.tv.data.local.entity.CachedContent
import com.test1.tv.data.local.entity.TraktAccount
import com.test1.tv.data.local.entity.TraktUserItem
import com.test1.tv.data.local.entity.ContinueWatchingEntity
import com.test1.tv.data.local.entity.WatchStatusEntity
import com.test1.tv.data.local.entity.RowConfigEntity
import com.test1.tv.data.local.entity.SyncMetadataEntity
import com.test1.tv.data.local.entity.MediaEnrichmentEntity
import com.test1.tv.data.local.entity.PremiumizeAccount
import com.test1.tv.data.local.entity.PlayerSettings
import com.test1.tv.data.local.entity.PlaybackProgress

@Database(
    entities = [
        CachedContent::class,
        TraktAccount::class,
        TraktUserItem::class,
        ContinueWatchingEntity::class,
        WatchStatusEntity::class,
        MediaContentEntity::class,
        MediaImageEntity::class,
        MediaRatingEntity::class,
        WatchProgressEntity::class,
        RowConfigEntity::class,
        SyncMetadataEntity::class,
        MediaEnrichmentEntity::class,
        PremiumizeAccount::class,
        PlayerSettings::class,
        PlaybackProgress::class
    ],
    version = 19,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cachedContentDao(): CachedContentDao
    abstract fun traktAccountDao(): TraktAccountDao
    abstract fun traktUserItemDao(): TraktUserItemDao
    abstract fun continueWatchingDao(): ContinueWatchingDao
    abstract fun watchStatusDao(): WatchStatusDao
    abstract fun mediaDao(): MediaDao
    abstract fun rowConfigDao(): RowConfigDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun premiumizeAccountDao(): PremiumizeAccountDao
    abstract fun playerSettingsDao(): PlayerSettingsDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
}
