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
import com.test1.tv.data.local.entity.CachedContent
import com.test1.tv.data.local.entity.TraktAccount
import com.test1.tv.data.local.entity.TraktUserItem
import com.test1.tv.data.local.entity.ContinueWatchingEntity
import com.test1.tv.data.local.entity.WatchStatusEntity

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
        WatchProgressEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cachedContentDao(): CachedContentDao
    abstract fun traktAccountDao(): TraktAccountDao
    abstract fun traktUserItemDao(): TraktUserItemDao
    abstract fun continueWatchingDao(): ContinueWatchingDao
    abstract fun watchStatusDao(): WatchStatusDao
    abstract fun mediaDao(): MediaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "test1_tv_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
