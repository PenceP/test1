package com.test1.tv.data.repository

import com.test1.tv.data.local.dao.PlayerSettingsDao
import com.test1.tv.data.local.entity.PlayerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerSettingsRepository @Inject constructor(
    private val playerSettingsDao: PlayerSettingsDao
) {
    // Cached settings for quick access
    @Volatile
    private var cachedSettings: PlayerSettings? = null

    fun getSettingsFlow(): Flow<PlayerSettings?> = playerSettingsDao.getSettingsFlow()

    suspend fun getSettings(): PlayerSettings = withContext(Dispatchers.IO) {
        cachedSettings ?: playerSettingsDao.getSettings() ?: PlayerSettings.default().also {
            playerSettingsDao.insert(it)
            cachedSettings = it
        }
    }

    suspend fun updateSkipMode(mode: String) = withContext(Dispatchers.IO) {
        ensureSettingsExist()
        playerSettingsDao.updateSkipMode(mode)
        cachedSettings = cachedSettings?.copy(skipMode = mode)
    }

    suspend fun updateSubtitleDelay(delayMs: Long) = withContext(Dispatchers.IO) {
        val clampedDelay = delayMs.coerceIn(-PlayerSettings.MAX_DELAY_MS, PlayerSettings.MAX_DELAY_MS)
        ensureSettingsExist()
        playerSettingsDao.updateSubtitleDelay(clampedDelay)
        cachedSettings = cachedSettings?.copy(subtitleDelayMs = clampedDelay)
    }

    suspend fun updateAudioDelay(delayMs: Long) = withContext(Dispatchers.IO) {
        val clampedDelay = delayMs.coerceIn(-PlayerSettings.MAX_DELAY_MS, PlayerSettings.MAX_DELAY_MS)
        ensureSettingsExist()
        playerSettingsDao.updateAudioDelay(clampedDelay)
        cachedSettings = cachedSettings?.copy(audioDelayMs = clampedDelay)
    }

    suspend fun updateAutoplayNextEpisode(enabled: Boolean) = withContext(Dispatchers.IO) {
        ensureSettingsExist()
        playerSettingsDao.updateAutoplayNextEpisode(enabled)
        cachedSettings = cachedSettings?.copy(autoplayNextEpisode = enabled)
    }

    suspend fun updateDefaultSubtitleLanguage(language: String?) = withContext(Dispatchers.IO) {
        ensureSettingsExist()
        playerSettingsDao.updateDefaultSubtitleLanguage(language)
        cachedSettings = cachedSettings?.copy(defaultSubtitleLanguage = language)
    }

    suspend fun updateDefaultAudioLanguage(language: String?) = withContext(Dispatchers.IO) {
        ensureSettingsExist()
        playerSettingsDao.updateDefaultAudioLanguage(language)
        cachedSettings = cachedSettings?.copy(defaultAudioLanguage = language)
    }

    suspend fun updateAutoplayCountdown(seconds: Int) = withContext(Dispatchers.IO) {
        val clampedSeconds = seconds.coerceIn(5, 30)
        ensureSettingsExist()
        playerSettingsDao.updateAutoplayCountdown(clampedSeconds)
        cachedSettings = cachedSettings?.copy(autoplayCountdownSeconds = clampedSeconds)
    }

    suspend fun updateDecoderMode(mode: String) = withContext(Dispatchers.IO) {
        ensureSettingsExist()
        playerSettingsDao.updateDecoderMode(mode)
        cachedSettings = cachedSettings?.copy(decoderMode = mode)
    }

    suspend fun updateTunnelingEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        ensureSettingsExist()
        playerSettingsDao.updateTunnelingEnabled(enabled)
        cachedSettings = cachedSettings?.copy(tunnelingEnabled = enabled)
    }

    suspend fun updateSettings(settings: PlayerSettings) = withContext(Dispatchers.IO) {
        playerSettingsDao.insert(settings)
        cachedSettings = settings
    }

    private suspend fun ensureSettingsExist() {
        if (cachedSettings == null) {
            cachedSettings = playerSettingsDao.getSettings()
            if (cachedSettings == null) {
                val default = PlayerSettings.default()
                playerSettingsDao.insert(default)
                cachedSettings = default
            }
        }
    }

    // Quick sync access for settings (non-suspending)
    fun getCachedSettings(): PlayerSettings = cachedSettings ?: PlayerSettings.default()

    suspend fun preload() = withContext(Dispatchers.IO) {
        cachedSettings = playerSettingsDao.getSettings() ?: PlayerSettings.default().also {
            playerSettingsDao.insert(it)
        }
    }
}
