package com.strmr.tv.data.repository

import android.util.Log
import com.strmr.tv.BuildConfig
import com.strmr.tv.data.model.UpdateCheckResult
import com.strmr.tv.data.model.UpdateInfo
import com.strmr.tv.data.remote.api.GitHubApiService
import com.strmr.tv.util.VersionComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val gitHubApiService: GitHubApiService
) {
    companion object {
        private const val TAG = "UpdateRepository"
    }

    // Parse GITHUB_REPO "PenceP/strmr" into owner and repo
    private val githubOwner: String
        get() = BuildConfig.GITHUB_REPO.split("/").getOrNull(0) ?: "PenceP"

    private val githubRepo: String
        get() = BuildConfig.GITHUB_REPO.split("/").getOrNull(1) ?: "strmr"

    /**
     * Check for available updates from GitHub Releases
     */
    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for updates... Current version: ${BuildConfig.VERSION_NAME}")

            // Get latest release from GitHub
            val release = gitHubApiService.getLatestRelease(githubOwner, githubRepo)

            // Skip draft releases
            if (release.draft) {
                Log.d(TAG, "Latest release is a draft, skipping")
                return@withContext UpdateCheckResult.NoUpdateAvailable
            }

            // Find version.json asset
            val versionAsset = release.assets.find { it.name == "version.json" }
            if (versionAsset == null) {
                Log.w(TAG, "No version.json found in release assets")
                // Fallback: construct UpdateInfo from release data
                val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                if (apkAsset != null) {
                    val remoteVersion = release.tag_name.removePrefix("v")
                    return@withContext checkVersionAndReturn(
                        UpdateInfo(
                            version = remoteVersion,
                            versionCode = 0,
                            releaseDate = "",
                            apkUrl = apkAsset.browser_download_url,
                            releaseNotesUrl = "https://github.com/$githubOwner/$githubRepo/releases/tag/${release.tag_name}",
                            minAndroidSdk = 30
                        )
                    )
                }
                return@withContext UpdateCheckResult.NoUpdateAvailable
            }

            // Download and parse version.json
            val updateInfo = gitHubApiService.getVersionInfo(versionAsset.browser_download_url)

            return@withContext checkVersionAndReturn(updateInfo)

        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            return@withContext UpdateCheckResult.Error(
                message = "Failed to check for updates: ${e.message}",
                exception = e
            )
        }
    }

    private fun checkVersionAndReturn(updateInfo: UpdateInfo): UpdateCheckResult {
        val currentVersion = BuildConfig.VERSION_NAME
        val remoteVersion = updateInfo.version

        Log.d(TAG, "Comparing versions: current=$currentVersion, remote=$remoteVersion")

        return if (VersionComparator.isNewerVersion(currentVersion, remoteVersion)) {
            Log.i(TAG, "Update available: $remoteVersion")
            UpdateCheckResult.UpdateAvailable(updateInfo)
        } else {
            Log.d(TAG, "No update available")
            UpdateCheckResult.NoUpdateAvailable
        }
    }

    /**
     * Get current app version info
     */
    fun getCurrentVersion(): String = BuildConfig.VERSION_NAME
    fun getCurrentVersionCode(): Int = BuildConfig.VERSION_CODE
}
