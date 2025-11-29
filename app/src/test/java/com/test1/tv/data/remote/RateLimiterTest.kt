package com.test1.tv.data.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RateLimiterTest {

    @Test
    fun `acquire waits for refill when tokens exhausted`() = runTest {
        val limiter = RateLimiter()

        // Drain tokens
        val drainJobs = (1..40).map { async { limiter.acquire() } }
        drainJobs.forEach { it.await() }

        val start = currentTime
        val next = async { limiter.acquire() }
        advanceTimeBy(5_000)
        assertTrue("Should still be waiting before refill", !next.isCompleted)
        advanceTimeBy(5_100) // total ~10s
        next.await()
        val elapsed = currentTime - start
        assertTrue("Should wait roughly one refill window", elapsed >= 10_000)
    }

    @Test
    fun `tryAcquire succeeds while tokens remain and fails after`() = runTest {
        val limiter = RateLimiter()
        repeat(40) {
            val success = limiter.tryAcquire()
            assertTrue("Should succeed while tokens available", success)
        }
        val exhausted = limiter.tryAcquire()
        assertTrue("Should fail after tokens exhausted", !exhausted)
    }
}
