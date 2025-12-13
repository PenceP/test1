package com.strmr.tv.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token bucket rate limiter for API calls.
 *
 * TMDB allows 40 requests per 10 seconds.
 * This implementation ensures we never exceed that limit.
 */
@Singleton
class RateLimiter @Inject constructor() {

    companion object {
        private const val MAX_TOKENS = 40
        private const val REFILL_PERIOD_MS = 10_000L  // 10 seconds
        private const val WAIT_INTERVAL_MS = 250L
    }

    private val mutex = Mutex()
    private var availableTokens = MAX_TOKENS
    private var lastRefillTime = System.currentTimeMillis()

    /**
     * Acquires a token before making an API call.
     * Suspends if no tokens are available.
     */
    suspend fun acquire() {
        while (true) {
            val acquired = mutex.withLock {
                refillTokens()
                if (availableTokens > 0) {
                    availableTokens--
                    true
                } else {
                    false
                }
            }
            if (acquired) return
            delay(WAIT_INTERVAL_MS)
        }
    }

    /**
     * Tries to acquire a token without waiting.
     * @return true if token was acquired, false otherwise
     */
    suspend fun tryAcquire(): Boolean {
        return mutex.withLock {
            refillTokens()
            if (availableTokens > 0) {
                availableTokens--
                true
            } else {
                false
            }
        }
    }

    private fun refillTokens() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRefillTime
        if (elapsed >= REFILL_PERIOD_MS) {
            availableTokens = MAX_TOKENS
            lastRefillTime = now
        }
    }

    /**
     * Returns current available tokens (for debugging/monitoring)
     */
    suspend fun availableTokens(): Int = mutex.withLock {
        refillTokens()
        availableTokens
    }
}
