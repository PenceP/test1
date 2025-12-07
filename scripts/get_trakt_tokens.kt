#!/usr/bin/env kotlin

/**
 * Trakt Token Generator Script
 *
 * Run this script to get access and refresh tokens for testing:
 *   kotlinc -script get_trakt_tokens.kt
 *
 * Or with kotlin installed:
 *   kotlin get_trakt_tokens.kt
 *
 * You'll need to set your TRAKT_CLIENT_ID and TRAKT_CLIENT_SECRET below.
 */

@file:DependsOn("com.squareup.okhttp3:okhttp:4.12.0")
@file:DependsOn("com.google.code.gson:gson:2.10.1")

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

// ============ CONFIGURE THESE ============
val CLIENT_ID = "YOUR_TRAKT_CLIENT_ID"
val CLIENT_SECRET = "YOUR_TRAKT_CLIENT_SECRET"
// =========================================

data class DeviceCodeResponse(
    @SerializedName("device_code") val deviceCode: String,
    @SerializedName("user_code") val userCode: String,
    @SerializedName("verification_url") val verificationUrl: String,
    @SerializedName("expires_in") val expiresIn: Int,
    val interval: Int
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Long,
    @SerializedName("created_at") val createdAt: Long,
    val scope: String
)

val client = OkHttpClient()
val gson = Gson()
val JSON = "application/json".toMediaType()

fun main() {
    println("=== Trakt Token Generator ===\n")

    if (CLIENT_ID == "YOUR_TRAKT_CLIENT_ID") {
        println("ERROR: Please edit this script and set your TRAKT_CLIENT_ID and TRAKT_CLIENT_SECRET")
        return
    }

    // Step 1: Get device code
    println("Step 1: Requesting device code...")
    val deviceCodeBody = """{"client_id":"$CLIENT_ID"}""".toRequestBody(JSON)
    val deviceCodeRequest = Request.Builder()
        .url("https://api.trakt.tv/oauth/device/code")
        .post(deviceCodeBody)
        .header("Content-Type", "application/json")
        .build()

    val deviceCodeResponse = client.newCall(deviceCodeRequest).execute()
    if (!deviceCodeResponse.isSuccessful) {
        println("ERROR: Failed to get device code: ${deviceCodeResponse.code}")
        return
    }

    val deviceCode = gson.fromJson(deviceCodeResponse.body?.string(), DeviceCodeResponse::class.java)

    println("\n" + "=".repeat(50))
    println("Go to: ${deviceCode.verificationUrl}")
    println("Enter code: ${deviceCode.userCode}")
    println("=".repeat(50))
    println("\nWaiting for authorization (expires in ${deviceCode.expiresIn / 60} minutes)...")

    // Step 2: Poll for token
    val startTime = System.currentTimeMillis()
    val expiresAt = startTime + deviceCode.expiresIn * 1000L
    val pollDelay = (deviceCode.interval * 1000L).coerceAtLeast(5000L)

    while (System.currentTimeMillis() < expiresAt) {
        Thread.sleep(pollDelay)
        print(".")

        val tokenBody = """
            {
                "code": "${deviceCode.deviceCode}",
                "client_id": "$CLIENT_ID",
                "client_secret": "$CLIENT_SECRET"
            }
        """.trimIndent().toRequestBody(JSON)

        val tokenRequest = Request.Builder()
            .url("https://api.trakt.tv/oauth/device/token")
            .post(tokenBody)
            .header("Content-Type", "application/json")
            .build()

        val tokenResponse = client.newCall(tokenRequest).execute()

        when (tokenResponse.code) {
            200 -> {
                val token = gson.fromJson(tokenResponse.body?.string(), TokenResponse::class.java)
                println("\n\n=== SUCCESS! ===\n")
                println("Add these to your secrets.properties:\n")
                println("TRAKT_TEST_ACCESS_TOKEN=${token.accessToken}")
                println("TRAKT_TEST_REFRESH_TOKEN=${token.refreshToken}")
                println("\nToken expires in ${token.expiresIn / 86400} days")
                return
            }
            400 -> {
                // Pending - user hasn't authorized yet
                continue
            }
            404, 409, 410, 418 -> {
                println("\nERROR: Authorization failed (code ${tokenResponse.code})")
                return
            }
            429 -> {
                println("\nRate limited, waiting longer...")
                Thread.sleep(10000)
            }
        }
    }

    println("\nERROR: Authorization expired. Please try again.")
}

main()
