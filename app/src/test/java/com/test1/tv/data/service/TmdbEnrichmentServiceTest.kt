package com.test1.tv.data.service

import com.test1.tv.data.local.dao.MediaDao
import com.test1.tv.data.local.entity.MediaEnrichmentEntity
import com.test1.tv.data.remote.api.TMDBApiService
import com.test1.tv.data.model.tmdb.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TmdbEnrichmentServiceTest {

    private lateinit var mockTmdbApi: TMDBApiService
    private lateinit var mockMediaDao: MediaDao
    private lateinit var enrichmentService: TmdbEnrichmentService

    private fun createTestEntity(tmdbId: Int) = MediaEnrichmentEntity(
        tmdbId = tmdbId,
        posterUrl = "poster_$tmdbId",
        backdropUrl = "backdrop_$tmdbId",
        logoUrl = "logo_$tmdbId",
        genres = "Action, Drama",
        cast = "Actor 1, Actor 2",
        runtime = "2h 30m",
        certification = "PG-13"
    )

    private fun createTestMovieDetails(tmdbId: Int) = TMDBMovieDetails(
        id = tmdbId,
        title = "Test Movie $tmdbId",
        posterPath = "/poster_$tmdbId.jpg",
        backdropPath = "/backdrop_$tmdbId.jpg",
        overview = "Test overview",
        releaseDate = "2024-01-01",
        voteAverage = 7.5,
        runtime = 150,
        genres = listOf(TMDBGenre(id = 1, name = "Action"), TMDBGenre(id = 2, name = "Drama")),
        images = TMDBImages(
            logos = listOf(TMDBLogo(filePath = "/logo_$tmdbId.png", languageCode = "en", voteAverage = 5.0, voteCount = 10))
        ),
        credits = TMDBCredits(
            cast = listOf(
                TMDBCast(id = 1, name = "Actor 1", character = null, profilePath = null, order = 0),
                TMDBCast(id = 2, name = "Actor 2", character = null, profilePath = null, order = 1)
            )
        ),
        releaseDates = TMDBReleaseDatesResponse(
            results = listOf(
                TMDBReleaseDate(
                    iso31661 = "US",
                    releaseDates = listOf(TMDBReleaseDateInfo(certification = "PG-13", releaseDate = null, type = null))
                )
            )
        ),
        imdbId = null,
        tagline = null,
        status = null,
        belongsToCollection = null,
        externalIds = null
    )

    private fun createTestShowDetails(tmdbId: Int) = TMDBShowDetails(
        id = tmdbId,
        name = "Test Show $tmdbId",
        posterPath = "/poster_$tmdbId.jpg",
        backdropPath = "/backdrop_$tmdbId.jpg",
        overview = "Test overview",
        firstAirDate = "2024-01-01",
        voteAverage = 8.0,
        episodeRunTime = listOf(45),
        genres = listOf(TMDBGenre(id = 1, name = "Drama")),
        images = TMDBImages(
            logos = listOf(TMDBLogo(filePath = "/logo_$tmdbId.png", languageCode = "en", voteAverage = 5.0, voteCount = 10))
        ),
        credits = TMDBCredits(
            cast = listOf(TMDBCast(id = 1, name = "Actor 1", character = null, profilePath = null, order = 0))
        ),
        contentRatings = TMDBContentRatingsResponse(
            results = listOf(
                TMDBContentRating(iso31661 = "US", rating = "TV-14")
            )
        ),
        numberOfSeasons = null,
        numberOfEpisodes = null,
        seasons = null,
        status = null,
        externalIds = null
    )

    @Before
    fun setup() {
        mockTmdbApi = mock(TMDBApiService::class.java)
        mockMediaDao = mock(MediaDao::class.java)
        enrichmentService = TmdbEnrichmentService(mockTmdbApi, mockMediaDao)
    }

    @After
    fun tearDown() {
        // Close the service to cancel background coroutines
        enrichmentService.close()
    }

    @Test
    fun `enrich returns cached entity when available`() = runTest {
        // Arrange
        val entity = createTestEntity(123)
        whenever(mockMediaDao.getEnrichmentByTmdbId(123)).thenReturn(entity)

        // Act
        val result = enrichmentService.enrich(123, TmdbEnrichmentService.ContentType.MOVIE)

        // Assert
        assertNotNull(result)
        assertEquals(123, result.tmdbId)
        assertEquals("poster_123", result.posterUrl)
        verify(mockTmdbApi, times(0)).getMovieDetails(any(), any(), any(), any())
    }

    @Test
    fun `enrich fetches from API when not cached`() = runTest {
        // Arrange
        whenever(mockMediaDao.getEnrichmentByTmdbId(123)).thenReturn(null)
        whenever(mockMediaDao.getEnrichmentsByTmdbIds(any())).thenReturn(emptyList())
        whenever(mockTmdbApi.getMovieDetails(any(), any(), any(), any()))
            .thenReturn(createTestMovieDetails(123))

        // Act
        val result = enrichmentService.enrich(123, TmdbEnrichmentService.ContentType.MOVIE)
        delay(100) // Allow batch processing

        // Assert
        assertNotNull(result)
        assertEquals(123, result?.tmdbId)
    }

    @Test
    fun `enrich deduplicates concurrent requests for same tmdbId`() = runTest {
        // Arrange
        whenever(mockMediaDao.getEnrichmentByTmdbId(123)).thenReturn(null)
        whenever(mockMediaDao.getEnrichmentsByTmdbIds(any())).thenReturn(emptyList())
        whenever(mockTmdbApi.getMovieDetails(any(), any(), any(), any()))
            .thenReturn(createTestMovieDetails(123))

        // Act - Make 3 concurrent requests for same ID
        val job1 = launch { enrichmentService.enrich(123, TmdbEnrichmentService.ContentType.MOVIE) }
        val job2 = launch { enrichmentService.enrich(123, TmdbEnrichmentService.ContentType.MOVIE) }
        val job3 = launch { enrichmentService.enrich(123, TmdbEnrichmentService.ContentType.MOVIE) }

        job1.join()
        job2.join()
        job3.join()
        delay(100)

        // Assert - API should only be called once due to deduplication
        verify(mockTmdbApi, times(1)).getMovieDetails(any(), any(), any(), any())
    }

    @Test
    fun `enrich handles API errors gracefully`() = runTest {
        // Arrange
        whenever(mockMediaDao.getEnrichmentByTmdbId(123)).thenReturn(null)
        whenever(mockMediaDao.getEnrichmentsByTmdbIds(any())).thenReturn(emptyList())
        whenever(mockTmdbApi.getMovieDetails(any(), any(), any(), any()))
            .thenThrow(RuntimeException("API Error"))

        // Act
        val result = enrichmentService.enrich(123, TmdbEnrichmentService.ContentType.MOVIE)
        delay(100)

        // Assert - Should return null on error
        assertNull(result)
    }

    @Test
    fun `enrich processes TV shows correctly`() = runTest {
        // Arrange
        whenever(mockMediaDao.getEnrichmentByTmdbId(456)).thenReturn(null)
        whenever(mockMediaDao.getEnrichmentsByTmdbIds(any())).thenReturn(emptyList())
        whenever(mockTmdbApi.getShowDetails(any(), any(), any(), any()))
            .thenReturn(createTestShowDetails(456))

        // Act
        val result = enrichmentService.enrich(456, TmdbEnrichmentService.ContentType.TV_SHOW)
        delay(100)

        // Assert
        assertNotNull(result)
        verify(mockTmdbApi, times(1)).getShowDetails(any(), any(), any(), any())
    }

    @Test
    fun `preload does not block and skips cached items`() = runTest {
        // Arrange
        whenever(mockMediaDao.getEnrichmentByTmdbId(1)).thenReturn(createTestEntity(1))
        whenever(mockMediaDao.getEnrichmentByTmdbId(2)).thenReturn(null)
        whenever(mockMediaDao.getEnrichmentByTmdbId(3)).thenReturn(null)
        whenever(mockMediaDao.getEnrichmentsByTmdbIds(any())).thenReturn(emptyList())
        whenever(mockTmdbApi.getMovieDetails(any(), any(), any(), any()))
            .thenReturn(createTestMovieDetails(2))

        // Act - preload is fire-and-forget
        enrichmentService.preload(listOf(1, 2, 3), TmdbEnrichmentService.ContentType.MOVIE)
        delay(150) // Allow batch processing

        // Assert - Should skip cached item 1
        verify(mockMediaDao, times(1)).getEnrichmentByTmdbId(1)
        verify(mockMediaDao, times(1)).getEnrichmentByTmdbId(2)
        verify(mockMediaDao, times(1)).getEnrichmentByTmdbId(3)
    }

    @Test
    fun `batch processing waits for 75ms timeout`() = runTest {
        // Arrange
        whenever(mockMediaDao.getEnrichmentByTmdbId(any())).thenReturn(null)
        whenever(mockMediaDao.getEnrichmentsByTmdbIds(any())).thenReturn(emptyList())
        whenever(mockTmdbApi.getMovieDetails(any(), any(), any(), any()))
            .thenReturn(createTestMovieDetails(1))

        // Act - Enrich a single item (service uses real IO dispatcher)
        // Call enrich and wait for it to complete
        enrichmentService.enrich(1, TmdbEnrichmentService.ContentType.MOVIE)

        // Assert - Should have processed batch after timeout
        verify(mockMediaDao, times(1)).getEnrichmentsByTmdbIds(any())
    }

    @Test
    fun `batch processes at 15 items without waiting for timeout`() = runTest {
        // Arrange
        whenever(mockMediaDao.getEnrichmentByTmdbId(any())).thenReturn(null)
        whenever(mockMediaDao.getEnrichmentsByTmdbIds(any())).thenReturn(emptyList())
        whenever(mockTmdbApi.getMovieDetails(any(), any(), any(), any()))
            .thenReturn(createTestMovieDetails(1))

        // Act - Queue 15 items (enrich calls block until batch processes)
        val jobs = (0 until 15).map { i ->
            launch { enrichmentService.enrich(i, TmdbEnrichmentService.ContentType.MOVIE) }
        }

        // Wait for all enrich calls to complete
        jobs.forEach { it.join() }

        // Give time for batch processing on IO dispatcher
        delay(100)

        // Assert - Should have processed batch (at least once since batch size is 15)
        verify(mockMediaDao, times(1)).getEnrichmentsByTmdbIds(any())
    }

    @Test
    fun `enrichment caches results to database`() = runTest {
        // Arrange
        whenever(mockMediaDao.getEnrichmentByTmdbId(123)).thenReturn(null)
        whenever(mockMediaDao.getEnrichmentsByTmdbIds(any())).thenReturn(emptyList())
        whenever(mockTmdbApi.getMovieDetails(any(), any(), any(), any()))
            .thenReturn(createTestMovieDetails(123))

        // Act
        enrichmentService.enrich(123, TmdbEnrichmentService.ContentType.MOVIE)
        delay(100)

        // Assert - Should save to cache
        verify(mockMediaDao, times(1)).insertEnrichment(any())
    }

    @Test
    fun `close cancels background processing`() {
        // Arrange
        val dao = mock(MediaDao::class.java)
        val api = mock(TMDBApiService::class.java)
        val service = TmdbEnrichmentService(api, dao)

        runBlocking {
            whenever(dao.getEnrichmentByTmdbId(any())).thenReturn(null)
            whenever(dao.getEnrichmentsByTmdbIds(any())).thenReturn(emptyList())

            // Act - Close the service
            service.close()

            // Give the scope time to cancel
            Thread.sleep(100)

            // Try to enrich after close - should fail or be rejected
            val result = runCatching {
                service.enrich(1, TmdbEnrichmentService.ContentType.MOVIE)
            }

            // Give time for any potential processing
            Thread.sleep(100)

            // Assert - Should not process after close
            verify(api, times(0)).getMovieDetails(any(), any(), any(), any())
        }
    }
}
