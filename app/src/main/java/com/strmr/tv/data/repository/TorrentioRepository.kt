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
 * Supports multiple debrid providers: Premiumize, Real-Debrid, and AllDebrid
 */
@Singleton
class TorrentioRepository @Inject constructor(
    private val torrentioApiService: TorrentioApiService,
    private val premiumizeRepository: PremiumizeRepository,
    private val realDebridRepository: RealDebridRepository,
    private val allDebridRepository: AllDebridRepository,
    private val linkFilterService: LinkFilterService
) {
    companion object {
        private const val TAG = "TorrentioRepository"
        private const val BATCH_SIZE = 100 // Cache check limit per request
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
     * Enrich streams with debrid cache status from all connected providers
     * Priority order: Premiumize > Real-Debrid > AllDebrid (first cached wins)
     */
    private suspend fun enrichWithCacheStatus(streams: List<StreamInfo>): List<StreamInfo> {
        // Determine which providers are available
        val hasPremiumize = premiumizeRepository.hasAccount()
        val hasRealDebrid = realDebridRepository.hasAccount()
        val hasAllDebrid = allDebridRepository.hasAccount()

        if (!hasPremiumize && !hasRealDebrid && !hasAllDebrid) {
            Log.d(TAG, "No debrid accounts configured, skipping cache check")
            return streams
        }

        Log.d(TAG, "Checking cache: PM=$hasPremiumize RD=$hasRealDebrid AD=$hasAllDebrid")

        // Get all unique info hashes
        val hashes = streams.map { it.infoHash }.distinct()

        // Maps to track which provider has each hash cached
        val premiumizeCacheMap = mutableMapOf<String, Boolean>()
        val realDebridCacheMap = mutableMapOf<String, Boolean>()
        val allDebridCacheMap = mutableMapOf<String, Boolean>()

        // Check Premiumize cache status
        if (hasPremiumize) {
            hashes.chunked(BATCH_SIZE).forEach { batch ->
                val result = premiumizeRepository.checkCacheStatus(batch)
                result.onSuccess { batchStatus ->
                    premiumizeCacheMap.putAll(batchStatus)
                }
            }
            Log.d(TAG, "Premiumize: ${premiumizeCacheMap.count { it.value }} cached")
        }

        // Check Real-Debrid cache status
        if (hasRealDebrid) {
            hashes.chunked(BATCH_SIZE).forEach { batch ->
                val result = realDebridRepository.checkCacheStatus(batch)
                result.onSuccess { batchStatus ->
                    realDebridCacheMap.putAll(batchStatus)
                }
            }
            Log.d(TAG, "Real-Debrid: ${realDebridCacheMap.count { it.value }} cached")
        }

        // Check AllDebrid cache status
        if (hasAllDebrid) {
            hashes.chunked(BATCH_SIZE).forEach { batch ->
                val result = allDebridRepository.checkCacheStatus(batch)
                result.onSuccess { batchStatus ->
                    allDebridCacheMap.putAll(batchStatus)
                }
            }
            Log.d(TAG, "AllDebrid: ${allDebridCacheMap.count { it.value }} cached")
        }

        // Enrich streams with cache status - priority: Premiumize > Real-Debrid > AllDebrid
        return streams.map { stream ->
            val hash = stream.infoHash
            when {
                premiumizeCacheMap[hash] == true -> stream.copy(
                    isCached = true,
                    debridProvider = DebridProvider.PREMIUMIZE
                )
                realDebridCacheMap[hash] == true -> stream.copy(
                    isCached = true,
                    debridProvider = DebridProvider.REAL_DEBRID
                )
                allDebridCacheMap[hash] == true -> stream.copy(
                    isCached = true,
                    debridProvider = DebridProvider.ALL_DEBRID
                )
                else -> {
                    // Not cached, assign the first available provider
                    val provider = when {
                        hasPremiumize -> DebridProvider.PREMIUMIZE
                        hasRealDebrid -> DebridProvider.REAL_DEBRID
                        hasAllDebrid -> DebridProvider.ALL_DEBRID
                        else -> null
                    }
                    stream.copy(isCached = false, debridProvider = provider)
                }
            }
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
            DebridProvider.REAL_DEBRID -> {
                realDebridRepository.resolveToDirectLink(stream.infoHash)
            }
            DebridProvider.ALL_DEBRID -> {
                allDebridRepository.resolveToDirectLink(stream.infoHash)
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
