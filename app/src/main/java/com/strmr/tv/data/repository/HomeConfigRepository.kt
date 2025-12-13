package com.strmr.tv.data.repository

import android.content.Context
import com.google.gson.Gson
import com.strmr.tv.data.model.home.HomeConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Deprecated(
    message = "Use ScreenConfigRepository instead. This will be removed in Phase 3.",
    replaceWith = ReplaceWith("ScreenConfigRepository")
)
@Singleton
class HomeConfigRepository @Inject constructor(
    private val context: Context,
    private val gson: Gson = Gson()
) {
    fun loadConfig(): HomeConfig? {
        return runCatching {
            context.assets.open(HOME_CONFIG_FILE).use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    gson.fromJson(reader, HomeConfig::class.java)
                }
            }
        }.getOrNull()
    }

    companion object {
        private const val HOME_CONFIG_FILE = "home_config.json"
    }
}
