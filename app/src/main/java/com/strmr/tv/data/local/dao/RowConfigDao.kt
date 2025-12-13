package com.strmr.tv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.strmr.tv.data.local.entity.RowConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RowConfigDao {

    @Query("""
        SELECT * FROM row_config
        WHERE screenType = :screen AND enabled = 1
        ORDER BY position ASC
    """)
    fun getEnabledRowsForScreen(screen: String): Flow<List<RowConfigEntity>>

    @Query("SELECT * FROM row_config WHERE screenType = :screen ORDER BY position ASC")
    fun getAllRowsForScreen(screen: String): Flow<List<RowConfigEntity>>

    @Query("SELECT * FROM row_config WHERE id = :rowId LIMIT 1")
    suspend fun getRowById(rowId: String): RowConfigEntity?

    @Update
    suspend fun updateRow(row: RowConfigEntity)

    @Query("UPDATE row_config SET enabled = :enabled WHERE id = :rowId")
    suspend fun setRowEnabled(rowId: String, enabled: Boolean)

    @Query("UPDATE row_config SET position = :newPos WHERE id = :rowId")
    suspend fun updateRowPosition(rowId: String, newPos: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<RowConfigEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: RowConfigEntity)

    @Query("UPDATE row_config SET position = defaultPosition, enabled = 1 WHERE screenType = :screen")
    suspend fun resetScreenToDefaults(screen: String)

    @Query("DELETE FROM row_config WHERE id = :rowId AND isSystemRow = 0")
    suspend fun deleteNonSystemRow(rowId: String)

    @Query("SELECT COUNT(*) FROM row_config WHERE screenType = :screen")
    suspend fun getRowCountForScreen(screen: String): Int
}
