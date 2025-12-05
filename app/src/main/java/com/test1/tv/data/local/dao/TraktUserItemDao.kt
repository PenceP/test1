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

    @Query("""
        SELECT * FROM trakt_user_items
        WHERE listType = :listType AND itemType = :itemType
        ORDER BY updatedAt DESC
    """)
    suspend fun getItems(listType: String, itemType: String): List<TraktUserItem>

    @Query("DELETE FROM trakt_user_items")
    suspend fun clearAll()

    @Query("""
        SELECT * FROM trakt_user_items
        WHERE tmdbId = :tmdbId AND listType = :listType AND itemType = :itemType
        LIMIT 1
    """)
    suspend fun findByTmdbId(tmdbId: Int, listType: String, itemType: String): TraktUserItem?

    @Query("DELETE FROM trakt_user_items WHERE tmdbId = :tmdbId AND listType = :listType")
    suspend fun deleteByTmdbId(tmdbId: Int, listType: String)

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM trakt_user_items
            WHERE tmdbId = :tmdbId AND listType = :listType
        )
    """)
    suspend fun existsByTmdbId(tmdbId: Int, listType: String): Boolean
}
