package com.test1.tv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.test1.tv.data.local.entity.ContinueWatchingEntity

@Dao
interface ContinueWatchingDao {
    @Query("SELECT * FROM continue_watching")
    suspend fun getAll(): List<ContinueWatchingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ContinueWatchingEntity>)

    @Query("DELETE FROM continue_watching")
    suspend fun clear()
}
