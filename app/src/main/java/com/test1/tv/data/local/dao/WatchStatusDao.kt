package com.test1.tv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.test1.tv.data.local.entity.WatchStatusEntity

@Dao
interface WatchStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WatchStatusEntity>)

    @Query("SELECT * FROM watch_status WHERE key = :key LIMIT 1")
    suspend fun getByKey(key: String): WatchStatusEntity?

    @Query("SELECT * FROM watch_status")
    suspend fun getAll(): List<WatchStatusEntity>

    @Query("DELETE FROM watch_status")
    suspend fun clear()
}
