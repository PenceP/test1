package com.test1.tv.data.repository

import android.content.Context
import com.google.gson.Gson
import com.test1.tv.data.model.home.HomeConfig
import java.io.BufferedReader
import java.io.InputStreamReader

class HomeConfigRepository(
    private val context: Context,
    private val gson: Gson = Gson()
)
{
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
