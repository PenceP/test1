package com.strmr.tv.data.remote.api

import com.strmr.tv.data.model.UpdateInfo
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

interface GitHubApiService {

    /**
     * Get the latest release from GitHub
     */
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease

    /**
     * Download version.json from release assets
     */
    @GET
    suspend fun getVersionInfo(@Url url: String): UpdateInfo
}

data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val body: String?,
    val prerelease: Boolean,
    val draft: Boolean,
    val assets: List<GitHubAsset>
)

data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long,
    val content_type: String
)
