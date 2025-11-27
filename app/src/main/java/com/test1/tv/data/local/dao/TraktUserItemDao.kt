package com.test1.tv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.test1.tv.data.local.entity.TraktUserItem

@Dao
interface TraktUserItemDao {
    @Query("DELETE FROM trakt_user_items WHERE listType = :listType")
    suspend fun clearList(listType: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TraktUserItem>)

    @Query("SELECT COUNT(*) FROM trakt_user_items WHERE listType = :listType")
    suspend fun countByList(listType: String): Int

    @Query("DELETE FROM trakt_user_items")
    suspend fun clearAll()
}
