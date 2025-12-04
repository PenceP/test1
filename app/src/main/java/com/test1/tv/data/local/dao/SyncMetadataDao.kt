package com.test1.tv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.test1.tv.data.local.entity.SyncMetadataEntity

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE `key` = :key")
    suspend fun get(key: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: SyncMetadataEntity)

    @Query("SELECT lastSyncedAt FROM sync_metadata WHERE `key` = :key")
    suspend fun getLastSyncTime(key: String): Long?

    @Query("DELETE FROM sync_metadata WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM sync_metadata")
    suspend fun clearAll()
}
