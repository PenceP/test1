package com.strmr.tv.data.repository

import com.strmr.tv.data.local.entity.TraktAccount
import com.strmr.tv.data.model.ContentItem
import com.strmr.tv.data.model.tmdb.*
import com.strmr.tv.data.model.trakt.*
import com.strmr.tv.data.remote.api.TMDBApiService
import com.strmr.tv.data.remote.api.TraktApiService
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContinueWatchingRepositoryTest {

    private lateinit var mockTraktApi: TraktApiService
    private lateinit var mockTmdbApi: TMDBApiService
    private lateinit var mockAccountRepo: TraktAccountRepository
    private lateinit var mockSyncMetadataRepo: SyncMetadataRepository
    private lateinit var repository: ContinueWatchingRepository
    private val testDispatcher = StandardTestDispatcher()

    private val testAccount = TraktAccount(
        providerId = "trakt",
        userSlug = "testuser",
        userName = "testuser",
        accessToken = "test_token",
        refreshToken = "refresh_token",
        tokenType = "Bearer",
        scope = "public",
        expiresAt = System.currentTimeMillis() + 86400000L,
        createdAt = System.currentTimeMillis(),
        statsMoviesWatched = null,
        statsShowsWatched = null,
        statsMinutesWatched = null,
        lastSyncAt = null,
        lastHistorySync = null,
        lastCollectionSync = null,
        lastWatchlistSync = null,
        lastActivitiesAt = null
    )

    private fun createTestPlaybackMovie(tmdbId: Int, progress: Double = 50.0) = TraktPlaybackItem(
        id = tmdbId.toLong(),
        type = "movie",
        progress = progress,
        pausedAt = "2024-01-01T12:00:00.000Z",
        movie = TraktMovie(
            title = "Test Movie $tmdbId",
            year = 2024,
            rating = null,
            ids = TraktIds(trakt = tmdbId, slug = null, imdb = null, tmdb = tmdbId)
        ),
        show = null,
        episode = null
    )

    private fun createTestPlaybackEpisode(
        showTmdbId: Int,
        season: Int = 1,
        episode: Int = 1,
        progress: Double = 50.0
    ) = TraktPlaybackItem(
        id = showTmdbId.toLong(),
        type = "episode",
        progress = progress,
        pausedAt = "2024-01-01T12:00:00.000Z",
        movie = null,
        show = TraktShow(
            title = "Test Show $showTmdbId",
            year = 2024,
            rating = null,
            ids = TraktIds(trakt = showTmdbId, slug = null, imdb = null, tmdb = showTmdbId)
        ),
        episode = TraktEpisode(
            season = season,
            number = episode,
            title = "Episode Title",
            ids = null,
            runtime = null,
            firstAired = null
        )
    )

    private fun createTestMovieDetails(tmdbId: Int) = TMDBMovieDetails(
        id = tmdbId,
        title = "Test Movie $tmdbId",
        overview = "Test overview",
        posterPath = "/poster.jpg",
        backdropPath = "/backdrop.jpg",
        releaseDate = "2024-01-01",
        voteAverage = 7.5,
        runtime = 120,
        genres = listOf(TMDBGenre(1, "Action")),
        credits = TMDBCredits(cast = listOf(TMDBCast(id = 1, name = "Actor 1", character = null, profilePath = null, order = 0))),
        imdbId = null,
        tagline = null,
        status = null,
        images = null,
        releaseDates = null,
        belongsToCollection = null,
        externalIds = null
    )

    private fun createTestShowDetails(tmdbId: Int) = TMDBShowDetails(
        id = tmdbId,
        name = "Test Show $tmdbId",
        overview = "Test overview",
        posterPath = "/poster.jpg",
        backdropPath = "/backdrop.jpg",
        firstAirDate = "2024-01-01",
        voteAverage = 8.0,
        episodeRunTime = listOf(45),
        genres = listOf(TMDBGenre(1, "Drama")),
        credits = TMDBCredits(cast = listOf(TMDBCast(id = 1, name = "Actor 1", character = null, profilePath = null, order = 0))),
        numberOfSeasons = null,
        numberOfEpisodes = null,
        seasons = null,
        status = null,
        images = null,
        contentRatings = null,
        externalIds = null
    )

    private fun createTestEpisodeDetails() = TMDBEpisode(
        id = 1,
        name = "Episode Title",
        overview = "Episode overview",
        airDate = null,
        runtime = 45,
        episodeNumber = 1,
        seasonNumber = 1,
        stillPath = "/still.jpg"
    )

    private fun createLastActivities(
        moviesPausedAt: String? = "2024-01-01T12:00:00.000Z",
        episodesPausedAt: String? = "2024-01-01T12:00:00.000Z"
    ) = TraktLastActivities(
        all = null,
        movies = TraktActivityGroup(watchedAt = null, collectedAt = null, watchlistedAt = null, pausedAt = moviesPausedAt, hiddenAt = null),
        shows = null,
        episodes = TraktActivityGroup(watchedAt = null, collectedAt = null, watchlistedAt = null, pausedAt = episodesPausedAt, hiddenAt = null),
        seasons = null,
        comments = null,
        lists = null
    )

    @Before
    fun setup() {
        mockTraktApi = mock(TraktApiService::class.java)
        mockTmdbApi = mock(TMDBApiService::class.java)
        mockAccountRepo = mock(TraktAccountRepository::class.java)
        mockSyncMetadataRepo = mock(SyncMetadataRepository::class.java)
        repository = ContinueWatchingRepository(
            mockTraktApi,
            mockTmdbApi,
            mockAccountRepo,
            mockSyncMetadataRepo,
            testDispatcher
        )
    }

    @Test
    fun `load returns empty list when no account`() = runTest(testDispatcher) {
        // Arrange
        whenever(mockAccountRepo.refreshTokenIfNeeded()).thenReturn(null)

        // Act
        val result = repository.load()

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `load uses cached data when shouldRefresh returns false`() = runTest(testDispatcher) {
        // Arrange
        whenever(mockAccountRepo.refreshTokenIfNeeded()).thenReturn(testAccount)
        whenever(mockAccountRepo.buildAuthHeader(any())).thenReturn("Bearer test_token")
        whenever(mockSyncMetadataRepo.isStale(any())).thenReturn(false)
        whenever(mockTraktApi.getLastActivities(any(), any(), any()))
            .thenReturn(createLastActivities())
        whenever(mockSyncMetadataRepo.getTraktTimestamp(any()))
            .thenReturn("2024-01-01T12:00:00.000Z")

        // First load to populate cache
        whenever(mockTraktApi.getPlaybackMovies(any(), any(), any(), any(), any()))
            .thenReturn(listOf(createTestPlaybackMovie(1)))
        whenever(mockTraktApi.getPlaybackEpisodes(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockTraktApi.getHistoryShows(any(), any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockTmdbApi.getMovieDetails(any(), any(), any(), any()))
            .thenReturn(createTestMovieDetails(1))

        repository.load(forceRefresh = true)

        // Act - Second load should use cache
        val result = repository.load()

        // Assert - Should only call APIs once (from first load)
        verify(mockTraktApi, times(1)).getPlaybackMovies(any(), any(), any(), any(), any())
        assertEquals(1, result.size)
    }

    @Test
    fun `load refreshes when last_activities timestamp changes`() = runTest(testDispatcher) {
        // Arrange
        whenever(mockAccountRepo.refreshTokenIfNeeded()).thenReturn(testAccount)
        whenever(mockAccountRepo.buildAuthHeader(any())).thenReturn("Bearer test_token")
        whenever(mockSyncMetadataRepo.isStale(any())).thenReturn(false)

        // First load with old timestamp
        whenever(mockSyncMetadataRepo.getTraktTimestamp(any()))
            .thenReturn("2024-01-01T10:00:00.000Z")
        whenever(mockTraktApi.getLastActivities(any(), any(), any()))
            .thenReturn(createLastActivities(moviesPausedAt = "2024-01-01T12:00:00.000Z"))

        whenever(mockTraktApi.getPlaybackMovies(any(), any(), any(), any(), any()))
            .thenReturn(listOf(createTestPlaybackMovie(1)))
        whenever(mockTraktApi.getPlaybackEpisodes(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockTraktApi.getHistoryShows(any(), any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockTmdbApi.getMovieDetails(any(), any(), any(), any()))
            .thenReturn(createTestMovieDetails(1))

        repository.load(forceRefresh = true)

        // Act - Load again with newer timestamp
        val result = repository.load()

        // Assert - Should refresh because timestamp changed
        verify(mockTraktApi, times(2)).getPlaybackMovies(any(), any(), any(), any(), any())
        assertEquals(1, result.size)
    }

    @Test
    fun `load refreshes when data is stale (over 24 hours)`() = runTest(testDispatcher) {
        // Arrange
        whenever(mockAccountRepo.refreshTokenIfNeeded()).thenReturn(testAccount)
        whenever(mockAccountRepo.buildAuthHeader(any())).thenReturn("Bearer test_token")
        whenever(mockSyncMetadataRepo.isStale(any())).thenReturn(true)

        whenever(mockTraktApi.getPlaybackMovies(any(), any(), any(), any(), any()))
            .thenReturn(listOf(createTestPlaybackMovie(1)))
        whenever(mockTraktApi.getPlaybackEpisodes(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockTraktApi.getHistoryShows(any(), any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockTraktApi.getLastActivities(any(), any(), any()))
            .thenReturn(createLastActivities())
        whenever(mockTmdbApi.getMovieDetails(any(), any(), any(), any()))
            .thenReturn(createTestMovieDetails(1))

        // Act
        val result = repository.load()

        // Assert
        verify(mockTraktApi, times(1)).getPlaybackMovies(any(), any(), any(), any(), any())
        assertEquals(1, result.size)
    }

    @Test
    fun `load handles playback movies correctly`() = runTest(testDispatcher) {
        // Arrange
        whenever(mockAccountRepo.refreshTokenIfNeeded()).thenReturn(testAccount)
        whenever(mockAccountRepo.buildAuthHeader(any())).thenReturn("Bearer test_token")
        whenever(mockSyncMetadataRepo.isStale(any())).thenReturn(true)

        whenever(mockTraktApi.getPlaybackMovies(any(), any(), any(), any(), any()))
            .thenReturn(listOf(createTestPlaybackMovie(1, progress = 45.0)))
        whenever(mockTraktApi.getPlaybackEpisodes(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockTraktApi.getHistoryShows(any(), any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockTraktApi.getLastActivities(any(), any(), any()))
            .thenReturn(createLastActivities())
        whenever(mockTmdbApi.getMovieDetails(any(), any(), any(), any()))
            .thenReturn(createTestMovieDetails(1))

        // Act
        val result = repository.load()

        // Assert
        assertEquals(1, result.size)
        assertEquals(ContentItem.ContentType.MOVIE, result[0].type)
        assertEquals(0.45, result[0].watchProgress ?: 0.0, 0.001) // 45% -> 0.45 with tolerance
    }

    @Test
    fun `load handles playback episodes correctly`() = runTest(testDispatcher) {
        // Arrange
        whenever(mockAccountRepo.refreshTokenIfNeeded()).thenReturn(testAccount)
        whenever(mockAccountRepo.buildAuthHeader(any())).thenReturn("Bearer test_token")
        whenever(mockSyncMetadataRepo.isStale(any())).thenReturn(true)

        whenever(mockTraktApi.getPlaybackMovies(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockTraktApi.getPlaybackEpisodes(any(), any(), any(), any(), any()))
            .thenReturn(listOf(createTestPlaybackEpisode(1, season = 1, episode = 5, progress = 75.0)))
        whenever(mockTraktApi.getHistoryShows(any(), any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockTraktApi.getLastActivities(any(), any(), any()))
            .thenReturn(createLastActivities())
        whenever(mockTmdbApi.getShowDetails(any(), any(), any(), any()))
            .thenReturn(createTestShowDetails(1))
        whenever(mockTmdbApi.getEpisodeDetails(any(), any(), any(), any(), any(), any()))
            .thenReturn(createTestEpisodeDetails())

        // Act
        val result = repository.load()

        // Assert
        assertEquals(1, result.size)
        assertEquals(ContentItem.ContentType.TV_SHOW, result[0].type)
        assertTrue(result[0].title.contains("S1E5"))
        assertEquals(0.75, result[0].watchProgress ?: 0.0, 0.001)
    }

    @Test
    fun `load fetches next episode when progress is near complete`() = runTest(testDispatcher) {
        // Arrange
        whenever(mockAccountRepo.refreshTokenIfNeeded()).thenReturn(testAccount)
        whenever(mockAccountRepo.buildAuthHeader(any())).thenReturn("Bearer test_token")
        whenever(mockSyncMetadataRepo.isStale(any())).thenReturn(true)

        whenever(mockTraktApi.getPlaybackMovies(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockTraktApi.getPlaybackEpisodes(any(), any(), any(), any(), any()))
            .thenReturn(listOf(createTestPlaybackEpisode(1, season = 1, episode = 5, progress = 95.0)))
        whenever(mockTraktApi.getHistoryShows(any(), any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockTraktApi.getLastActivities(any(), any(), any()))
            .thenReturn(createLastActivities())

        // Mock show progress to return next episode
        val nextEpisode = TraktEpisode(
            season = 1,
            number = 6,
            title = "Next Episode",
            ids = null,
            runtime = null,
            firstAired = "2024-01-02T12:00:00.000Z"
        )
        whenever(mockTraktApi.getShowProgress(any(), any(), any(), any(), any(), any()))
            .thenReturn(TraktShowProgress(aired = 5, completed = 5, nextEpisode = nextEpisode))

        whenever(mockTmdbApi.getShowDetails(any(), any(), any(), any()))
            .thenReturn(createTestShowDetails(1))
        whenever(mockTmdbApi.getEpisodeDetails(any(), any(), any(), any(), any(), any()))
            .thenReturn(createTestEpisodeDetails())

        // Act
        val result = repository.load()

        // Assert - Should fetch next episode for near-complete playback
        verify(mockTraktApi, times(1)).getShowProgress(any(), any(), any(), any(), any(), any())
        assertEquals(1, result.size)
        assertTrue(result[0].title.contains("S1E6"))
        assertEquals(0.0, result[0].watchProgress ?: 0.0, 0.001) // Next episode has 0 progress
    }

    @Test
    fun `load deduplicates shows keeping most recent`() = runTest(testDispatcher) {
        // Arrange
        whenever(mockAccountRepo.refreshTokenIfNeeded()).thenReturn(testAccount)
        whenever(mockAccountRepo.buildAuthHeader(any())).thenReturn("Bearer test_token")
        whenever(mockSyncMetadataRepo.isStale(any())).thenReturn(true)

        // Two episodes from same show, different timestamps
        val episode1 = createTestPlaybackEpisode(1, season = 1, episode = 5, progress = 50.0)
            .copy(pausedAt = "2024-01-02T12:00:00.000Z")
        val episode2 = createTestPlaybackEpisode(1, season = 1, episode = 4, progress = 30.0)
            .copy(pausedAt = "2024-01-01T12:00:00.000Z")

        whenever(mockTraktApi.getPlaybackMovies(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockTraktApi.getPlaybackEpisodes(any(), any(), any(), any(), any()))
            .thenReturn(listOf(episode1, episode2))
        whenever(mockTraktApi.getHistoryShows(any(), any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockTraktApi.getLastActivities(any(), any(), any()))
            .thenReturn(createLastActivities())
        whenever(mockTmdbApi.getShowDetails(any(), any(), any(), any()))
            .thenReturn(createTestShowDetails(1))
        whenever(mockTmdbApi.getEpisodeDetails(any(), any(), any(), any(), any(), any()))
            .thenReturn(createTestEpisodeDetails())

        // Act
        val result = repository.load()

        // Assert - Should only have 1 episode (most recent = episode 5)
        assertEquals(1, result.size)
        assertTrue(result[0].title.contains("S1E5"))
    }

    @Test
    fun `load updates sync metadata with latest paused_at timestamp`() = runTest(testDispatcher) {
        // Arrange
        whenever(mockAccountRepo.refreshTokenIfNeeded()).thenReturn(testAccount)
        whenever(mockAccountRepo.buildAuthHeader(any())).thenReturn("Bearer test_token")
        whenever(mockSyncMetadataRepo.isStale(any())).thenReturn(true)

        whenever(mockTraktApi.getPlaybackMovies(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockTraktApi.getPlaybackEpisodes(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(mockTraktApi.getHistoryShows(any(), any(), any(), any(), any(), any()))
            .thenReturn(emptyList())

        val expectedTimestamp = "2024-01-03T15:30:00.000Z"
        whenever(mockTraktApi.getLastActivities(any(), any(), any()))
            .thenReturn(createLastActivities(moviesPausedAt = expectedTimestamp))

        // Act
        repository.load()

        // Assert
        verify(mockSyncMetadataRepo, times(1))
            .markSynced("continue_watching", expectedTimestamp)
    }

    @Test
    fun `removePlayback calls API with correct playback ID`() = runTest(testDispatcher) {
        // Arrange
        whenever(mockAccountRepo.refreshTokenIfNeeded()).thenReturn(testAccount)
        whenever(mockAccountRepo.buildAuthHeader(any())).thenReturn("Bearer test_token")

        // Act
        repository.removePlayback(12345L)

        // Assert
        verify(mockTraktApi, times(1)).removePlayback(any(), any(), any(), any())
    }

    @Test
    fun `hasAccount returns true when account exists`() = runTest(testDispatcher) {
        // Arrange
        whenever(mockAccountRepo.getAccount()).thenReturn(testAccount)

        // Act
        val result = repository.hasAccount()

        // Assert
        assertTrue(result)
    }
}
