package com.strmr.tv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.strmr.tv.data.local.dao.CachedContentDao
import com.strmr.tv.data.local.dao.TraktAccountDao
import com.strmr.tv.data.local.dao.TraktUserItemDao
import com.strmr.tv.data.local.dao.ContinueWatchingDao
import com.strmr.tv.data.local.dao.WatchStatusDao
import com.strmr.tv.data.local.dao.MediaDao
import com.strmr.tv.data.local.dao.RowConfigDao
import com.strmr.tv.data.local.dao.SyncMetadataDao
import com.strmr.tv.data.local.dao.PremiumizeAccountDao
import com.strmr.tv.data.local.dao.PlayerSettingsDao
import com.strmr.tv.data.local.dao.PlaybackProgressDao
import com.strmr.tv.data.local.dao.RealDebridAccountDao
import com.strmr.tv.data.local.dao.AllDebridAccountDao
import com.strmr.tv.data.local.entity.CachedContent
import com.strmr.tv.data.local.entity.TraktAccount
import com.strmr.tv.data.local.entity.TraktUserItem
import com.strmr.tv.data.local.entity.ContinueWatchingEntity
import com.strmr.tv.data.local.entity.WatchStatusEntity
import com.strmr.tv.data.local.entity.RowConfigEntity
import com.strmr.tv.data.local.entity.SyncMetadataEntity
import com.strmr.tv.data.local.entity.MediaEnrichmentEntity
import com.strmr.tv.data.local.entity.PremiumizeAccount
import com.strmr.tv.data.local.entity.PlayerSettings
import com.strmr.tv.data.local.entity.PlaybackProgress
import com.strmr.tv.data.local.entity.RealDebridAccount
import com.strmr.tv.data.local.entity.AllDebridAccount

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
        PlaybackProgress::class,
        RealDebridAccount::class,
        AllDebridAccount::class
    ],
    version = 21,
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
    abstract fun realDebridAccountDao(): RealDebridAccountDao
    abstract fun allDebridAccountDao(): AllDebridAccountDao
}
