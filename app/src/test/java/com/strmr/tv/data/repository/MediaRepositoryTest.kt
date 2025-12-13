package com.strmr.tv.data.repository

import com.strmr.tv.BuildConfig
import com.strmr.tv.data.Resource
import com.strmr.tv.data.local.MediaContentEntity
import com.strmr.tv.data.local.dao.MediaDao
import com.strmr.tv.data.local.MediaImageEntity
import com.strmr.tv.data.local.MediaWithImages
import com.strmr.tv.data.model.ContentItem
import com.strmr.tv.data.model.trakt.TraktMovie
import com.strmr.tv.data.model.trakt.TraktTrendingMovie
import com.strmr.tv.data.model.trakt.TraktIds
import com.strmr.tv.data.remote.RateLimiter
import com.strmr.tv.data.remote.api.TMDBApiService
import com.strmr.tv.data.remote.api.TraktApiService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MediaRepositoryTest {

    private val mediaDao: MediaDao = mock()
    private val traktApi: TraktApiService = mock()
    private val tmdbApi: TMDBApiService = mock()
    private val rateLimiter = RateLimiter()

    private lateinit var repo: MediaRepository

    @Before
    fun setup() {
        repo = MediaRepository(mediaDao, traktApi, tmdbApi, rateLimiter)
    }

    @Test
    fun `returns cached data when cache is fresh`() = runTest {
        val cached = listOf(
            MediaWithImages(
                MediaContentEntity(
                    tmdbId = 1,
                    imdbId = "tt1",
                    title = "Cached Movie",
                    overview = null,
                    year = "2023",
                    runtime = 120,
                    certification = "PG",
                    contentType = "movie",
                    category = "trending_movies",
                    position = 0,
                    cachedAt = System.currentTimeMillis()
                ),
                MediaImageEntity(tmdbId = 1, posterUrl = "p", backdropUrl = "b", logoUrl = null),
                null,
                null
            )
        )
        whenever(mediaDao.getMediaByCategory("trending_movies")).thenReturn(kotlinx.coroutines.flow.flowOf(cached))
        whenever(mediaDao.getCachedCount(any(), any())).thenReturn(1)

        val result = repo.getTrendingMovies().drop(1).first() // skip initial Loading
        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertEquals(1, data.size)
        assertEquals("Cached Movie", data.first().title)
    }

    @Test
    fun `falls back to cached data on network error`() = runTest {
        val cached = listOf(
            MediaWithImages(
                MediaContentEntity(
                    tmdbId = 2,
                    imdbId = "tt2",
                    title = "Cached Error Movie",
                    overview = null,
                    year = "2022",
                    runtime = 90,
                    certification = "PG-13",
                    contentType = "movie",
                    category = "trending_movies",
                    position = 0,
                    cachedAt = 0L
                ),
                MediaImageEntity(tmdbId = 2, posterUrl = "p2", backdropUrl = "b2", logoUrl = null),
                null,
                null
            )
        )
        whenever(mediaDao.getMediaByCategory("trending_movies")).thenReturn(kotlinx.coroutines.flow.flowOf(cached))
        whenever(mediaDao.getCachedCount(any(), any())).thenReturn(0)

        whenever(traktApi.getTrendingMovies(any(), any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("network"))

        val result = repo.getTrendingMovies().drop(1).first() // skip initial Loading
        assertTrue(result is Resource.Error)
        val cachedData = (result as Resource.Error).cachedData
        assertEquals(1, cachedData?.size)
        assertEquals("Cached Error Movie", cachedData?.first()?.title)
    }
}
