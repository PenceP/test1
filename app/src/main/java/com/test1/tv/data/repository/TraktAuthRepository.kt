package com.test1.tv.data.repository

import com.test1.tv.BuildConfig
import com.test1.tv.data.model.trakt.TraktDeviceCodeResponse
import com.test1.tv.data.remote.api.TraktApiService

class TraktAuthRepository(
    private val traktApiService: TraktApiService
) {
    suspend fun createDeviceCode(): TraktDeviceCodeResponse {
        return traktApiService.createDeviceCode(
            clientId = BuildConfig.TRAKT_CLIENT_ID
        )
    }
}
