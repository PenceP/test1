package com.strmr.tv.data.repository

import android.util.Log
import com.strmr.tv.data.model.DebridProvider
import com.strmr.tv.data.model.StreamInfo
import com.strmr.tv.data.remote.api.TorrentioApiService
import com.strmr.tv.data.service.FilterResult
import com.strmr.tv.data.service.LinkFilterService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for scraping streams from Torrentio and enriching them with debrid cache status
 */
@Singleton
class TorrentioRepository @Inject constructor(
    private val torrentioApiService: TorrentioApiService,
    private val premiumizeRepository: PremiumizeRepository,
    private val linkFilterService: LinkFilterService
) {
    companion object {
        private const val TAG = "TorrentioRepository"
        private const val BATCH_SIZE = 100 // Premiumize cache check limit per request
    }

    /**
     * Scrape streams for a movie and enrich with cache status
     * @param imdbId IMDB ID (e.g., "tt1234567")
     * @param runtimeMinutes Movie runtime for bitrate estimation
     * @return ScrapeResult with filtered and sorted streams
     */
    suspend fun scrapeMovie(imdbId: String, runtimeMinutes: Int?): ScrapeResult {
        return try {
            Log.d(TAG, "Scraping movie: $imdbId")
            val response = torrentioApiService.getMovieStreams(imdbId = imdbId)

            val streams = response.streams?.mapNotNull { stream ->
                val infoHash = stream.infoHash ?: return@mapNotNull null
                val title = stream.title ?: stream.name ?: return@mapNotNull null
                StreamInfo.parseFromTorrentio(infoHash, title, stream.fileIdx)
            } ?: emptyList()

            Log.d(TAG, "Found ${streams.size} streams for movie $imdbId")

            processStreams(streams, runtimeMinutes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scrape movie $imdbId", e)
            ScrapeResult(
                streams = emptyList(),
                filteredOutStreams = emptyList(),
                error = e.message ?: "Failed to scrape streams"
            )
        }
    }

    /**
     * Scrape streams for a TV episode and enrich with cache status
     * @param imdbId Show IMDB ID (e.g., "tt1234567")
     * @param season Season number
     * @param episode Episode number
     * @param runtimeMinutes Episode runtime for bitrate estimation
     * @return ScrapeResult with filtered and sorted streams
     */
    suspend fun scrapeEpisode(
        imdbId: String,
        season: Int,
        episode: Int,
        runtimeMinutes: Int?
    ): ScrapeResult {
        return try {
            Log.d(TAG, "Scraping episode: $imdbId S${season}E${episode}")
            val response = torrentioApiService.getEpisodeStreams(
                imdbId = imdbId,
                season = season,
                episode = episode
            )

            val streams = response.streams?.mapNotNull { stream ->
                val infoHash = stream.infoHash ?: return@mapNotNull null
                val title = stream.title ?: stream.name ?: return@mapNotNull null
                StreamInfo.parseFromTorrentio(infoHash, title, stream.fileIdx)
            } ?: emptyList()

            Log.d(TAG, "Found ${streams.size} streams for $imdbId S${season}E${episode}")

            processStreams(streams, runtimeMinutes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scrape episode $imdbId S${season}E${episode}", e)
            ScrapeResult(
                streams = emptyList(),
                filteredOutStreams = emptyList(),
                error = e.message ?: "Failed to scrape streams"
            )
        }
    }

    /**
     * Process streams: check cache status, apply filters, sort
     */
    private suspend fun processStreams(
        streams: List<StreamInfo>,
        runtimeMinutes: Int?
    ): ScrapeResult {
        if (streams.isEmpty()) {
            return ScrapeResult(
                streams = emptyList(),
                filteredOutStreams = emptyList(),
                error = null
            )
        }

        // Check Premiumize cache status if account is configured
        val enrichedStreams = enrichWithCacheStatus(streams)

        // Apply filtering and sorting
        val filterResult = linkFilterService.filterAndSort(enrichedStreams, runtimeMinutes)

        return ScrapeResult(
            streams = filterResult.passedStreams,
            filteredOutStreams = filterResult.filteredOutStreams,
            error = null
        )
    }

    /**
     * Enrich streams with debrid cache status
     */
    private suspend fun enrichWithCacheStatus(streams: List<StreamInfo>): List<StreamInfo> {
        // Check if Premiumize account exists
        if (!premiumizeRepository.hasAccount()) {
            Log.d(TAG, "No Premiumize account, skipping cache check")
            return streams
        }

        // Get all unique info hashes
        val hashes = streams.map { it.infoHash }.distinct()

        // Check cache status in batches
        val cacheStatusMap = mutableMapOf<String, Boolean>()
        hashes.chunked(BATCH_SIZE).forEach { batch ->
            val result = premiumizeRepository.checkCacheStatus(batch)
            result.onSuccess { batchStatus ->
                cacheStatusMap.putAll(batchStatus)
            }
        }

        // Enrich streams with cache status
        return streams.map { stream ->
            val isCached = cacheStatusMap[stream.infoHash] == true
            stream.copy(
                isCached = isCached,
                debridProvider = DebridProvider.PREMIUMIZE
            )
        }
    }

    /**
     * Get the autoselected stream based on filter preferences
     */
    fun getAutoselectStream(scrapeResult: ScrapeResult): StreamInfo? {
        return linkFilterService.getAutoselectStream(
            FilterResult(scrapeResult.streams, scrapeResult.filteredOutStreams)
        )
    }

    /**
     * Resolve a stream to a direct playable URL
     */
    suspend fun resolveStream(stream: StreamInfo): Result<String> {
        return when (stream.debridProvider) {
            DebridProvider.PREMIUMIZE -> {
                val magnetUri = "magnet:?xt=urn:btih:${stream.infoHash}"
                premiumizeRepository.resolveToDirectLink(magnetUri, stream.fileIdx)
            }
            DebridProvider.REAL_DEBRID, DebridProvider.ALL_DEBRID -> {
                // TODO: Implement other debrid providers
                Result.failure(Exception("${stream.debridProvider?.displayName} not yet implemented"))
            }
            null -> {
                Result.failure(Exception("No debrid provider available"))
            }
        }
    }
}

/**
 * Result of a scrape operation
 */
data class ScrapeResult(
    val streams: List<StreamInfo>,
    val filteredOutStreams: List<StreamInfo>,
    val error: String?
) {
    val totalCount: Int get() = streams.size + filteredOutStreams.size
    val hasStreams: Boolean get() = streams.isNotEmpty()
    val hasError: Boolean get() = error != null
}
