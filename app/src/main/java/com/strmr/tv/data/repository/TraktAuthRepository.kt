package com.strmr.tv.data.repository

import com.strmr.tv.BuildConfig
import com.strmr.tv.data.model.trakt.TraktDeviceCodeResponse
import com.strmr.tv.data.remote.api.TraktApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktAuthRepository @Inject constructor(
    private val traktApiService: TraktApiService
) {
    suspend fun createDeviceCode(): TraktDeviceCodeResponse {
        return traktApiService.createDeviceCode(
            clientId = BuildConfig.TRAKT_CLIENT_ID
        )
    }
}
