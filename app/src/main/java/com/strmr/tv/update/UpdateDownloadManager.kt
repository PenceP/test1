package com.strmr.tv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.strmr.tv.BuildConfig
import com.strmr.tv.data.model.UpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class Downloaded(val apkFile: File) : DownloadState()
    data class Error(val message: String, val exception: Throwable? = null) : DownloadState()
}

@Singleton
class UpdateDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "UpdateDownloadManager"
        private const val UPDATES_DIR = "updates"
        private const val BUFFER_SIZE = 8192
    }

    /**
     * Download the APK update with progress reporting
     */
    fun downloadUpdate(updateInfo: UpdateInfo): Flow<DownloadState> = flow {
        emit(DownloadState.Idle)

        try {
            // Prepare download directory
            val updatesDir = File(context.getExternalFilesDir(null), UPDATES_DIR)
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }

            // Clean up old APK files
            updatesDir.listFiles()?.filter { it.extension == "apk" }?.forEach { it.delete() }

            val apkFile = File(updatesDir, "STRMR-${updateInfo.version}.apk")

            Log.d(TAG, "Starting download: ${updateInfo.apkUrl}")
            Log.d(TAG, "Destination: ${apkFile.absolutePath}")

            val request = Request.Builder()
                .url(updateInfo.apkUrl)
                .addHeader("User-Agent", "STRMR-App/${BuildConfig.VERSION_NAME}")
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw Exception("Download failed: HTTP ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { inputStream ->
                FileOutputStream(apkFile).use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt()
                        } else {
                            -1 // Indeterminate
                        }

                        emit(DownloadState.Downloading(progress, downloadedBytes, totalBytes))
                    }
                }
            }

            Log.d(TAG, "Download complete: ${apkFile.absolutePath}")
            emit(DownloadState.Downloaded(apkFile))

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            emit(DownloadState.Error("Download failed: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Install the downloaded APK
     */
    fun installApk(context: Context, apkFile: File) {
        Log.d(TAG, "Installing APK: ${apkFile.absolutePath}")

        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
    }

    /**
     * Check if the app can install from unknown sources
     */
    fun canInstallFromUnknownSources(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.INSTALL_NON_MARKET_APPS,
                0
            ) == 1
        }
    }

    /**
     * Get intent to open unknown sources settings
     */
    fun getUnknownSourcesSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }
}
