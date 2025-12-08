package com.test1.tv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.test1.tv.data.local.entity.PlayerSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerSettingsDao {

    @Query("SELECT * FROM player_settings WHERE id = 1")
    suspend fun getSettings(): PlayerSettings?

    @Query("SELECT * FROM player_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<PlayerSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: PlayerSettings)

    @Update
    suspend fun update(settings: PlayerSettings)

    @Query("UPDATE player_settings SET skipMode = :mode WHERE id = 1")
    suspend fun updateSkipMode(mode: String)

    @Query("UPDATE player_settings SET subtitleDelayMs = :delayMs WHERE id = 1")
    suspend fun updateSubtitleDelay(delayMs: Long)

    @Query("UPDATE player_settings SET audioDelayMs = :delayMs WHERE id = 1")
    suspend fun updateAudioDelay(delayMs: Long)

    @Query("UPDATE player_settings SET autoplayNextEpisode = :enabled WHERE id = 1")
    suspend fun updateAutoplayNextEpisode(enabled: Boolean)

    @Query("UPDATE player_settings SET defaultSubtitleLanguage = :language WHERE id = 1")
    suspend fun updateDefaultSubtitleLanguage(language: String?)

    @Query("UPDATE player_settings SET defaultAudioLanguage = :language WHERE id = 1")
    suspend fun updateDefaultAudioLanguage(language: String?)

    @Query("UPDATE player_settings SET autoplayCountdownSeconds = :seconds WHERE id = 1")
    suspend fun updateAutoplayCountdown(seconds: Int)
}
