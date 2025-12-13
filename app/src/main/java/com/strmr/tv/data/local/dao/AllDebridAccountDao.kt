package com.strmr.tv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.strmr.tv.data.local.entity.AllDebridAccount

@Dao
interface AllDebridAccountDao {
    @Query("SELECT * FROM alldebrid_accounts WHERE providerId = :providerId LIMIT 1")
    suspend fun getAccount(providerId: String = "alldebrid"): AllDebridAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AllDebridAccount)

    @Query("DELETE FROM alldebrid_accounts WHERE providerId = :providerId")
    suspend fun deleteAccount(providerId: String = "alldebrid")
}
