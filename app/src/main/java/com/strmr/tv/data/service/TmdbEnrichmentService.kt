package com.strmr.tv.data.service

import com.strmr.tv.BuildConfig
import com.strmr.tv.data.local.dao.MediaDao
import com.strmr.tv.data.local.entity.MediaEnrichmentEntity
import com.strmr.tv.data.model.ContentItem
import com.strmr.tv.data.remote.api.TMDBApiService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbEnrichmentService @Inject constructor(
    private val tmdbApi: TMDBApiService,
    private val mediaDao: MediaDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<MediaEnrichmentEntity?>>()
    private val requestBuffer = Channel<EnrichmentRequest>(Channel.BUFFERED)
    private val batchLock = Mutex()

    enum class ContentType { MOVIE, TV_SHOW }

    private data class EnrichmentRequest(
        val tmdbId: Int,
        val type: ContentType,
        val deferred: CompletableDeferred<MediaEnrichmentEntity?>
    )

    init {
        // Process requests in batches
        scope.launch {
            val buffer = mutableListOf<EnrichmentRequest>()

            while (true) {
                // Collect requests for 75ms or until we have 15
                val request = if (buffer.isEmpty()) {
                    requestBuffer.receive() // Block until first request
                } else {
                    withTimeoutOrNull(75) { requestBuffer.receive() }
                }

                if (request != null) {
                    buffer.add(request)
                }

                // Process batch when timeout expires or batch is full
                if (request == null || buffer.size >= 15) {
                    if (buffer.isNotEmpty()) {
                        processBatch(buffer.toList())
                        buffer.clear()
                    }
                }
            }
        }
    }

    private suspend fun processBatch(requests: List<EnrichmentRequest>) = withContext(Dispatchers.IO) {
        // Deduplicate by tmdbId
        val uniqueRequests = requests.distinctBy { it.tmdbId }
        val tmdbIds = uniqueRequests.map { it.tmdbId }

        // Check cache first
        val cached = mediaDao.getEnrichmentsByTmdbIds(tmdbIds)
        val cachedMap = cached.associateBy { it.tmdbId }

        // Resolve cached immediately
        uniqueRequests.forEach { req ->
            cachedMap[req.tmdbId]?.let { enrichment ->
                req.deferred.complete(enrichment)
                pendingRequests.remove(req.tmdbId)
            }
        }

        // Fetch missing from API
        val missing = uniqueRequests.filter { it.tmdbId !in cachedMap }
        if (missing.isEmpty()) return@withContext

        // Batch fetch with rate limiting (5 concurrent)
        missing.chunked(5).forEach { batch ->
            coroutineScope {
                batch.map { req ->
                    async {
                        try {
                            val enrichment = when (req.type) {
                                ContentType.MOVIE -> fetchMovieEnrichment(req.tmdbId)
                                ContentType.TV_SHOW -> fetchShowEnrichment(req.tmdbId)
                            }
                            enrichment?.let { mediaDao.insertEnrichment(it) }
                            req.deferred.complete(enrichment)
                        } catch (e: Exception) {
                            req.deferred.complete(null)
                        } finally {
                            pendingRequests.remove(req.tmdbId)
                        }
                    }
                }.awaitAll()
            }
            delay(250) // Rate limit between batches
        }
    }

    private suspend fun fetchMovieEnrichment(tmdbId: Int): MediaEnrichmentEntity? {
        val details = tmdbApi.getMovieDetails(
            movieId = tmdbId,
            apiKey = BuildConfig.TMDB_API_KEY,
            appendToResponse = "images,credits,release_dates"
        )
        return MediaEnrichmentEntity(
            tmdbId = tmdbId,
            posterUrl = details.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            backdropUrl = details.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
            logoUrl = details.images?.logos?.firstOrNull()?.filePath?.let { "https://image.tmdb.org/t/p/w500$it" },
            genres = details.genres?.joinToString(", ") { it.name ?: "" },
            cast = details.credits?.cast?.take(5)?.joinToString(", ") { it.name ?: "" },
            runtime = details.runtime?.let { "${it / 60}h ${it % 60}m" },
            certification = details.releaseDates?.results
                ?.firstOrNull { it.iso31661 == "US" }
                ?.releaseDates?.firstOrNull()?.certification
        )
    }

    private suspend fun fetchShowEnrichment(tmdbId: Int): MediaEnrichmentEntity? {
        val details = tmdbApi.getShowDetails(
            showId = tmdbId,
            apiKey = BuildConfig.TMDB_API_KEY,
            appendToResponse = "images,credits,content_ratings"
        )
        return MediaEnrichmentEntity(
            tmdbId = tmdbId,
            posterUrl = details.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            backdropUrl = details.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
            logoUrl = details.images?.logos?.firstOrNull()?.filePath?.let { "https://image.tmdb.org/t/p/w500$it" },
            genres = details.genres?.joinToString(", ") { it.name ?: "" },
            cast = details.credits?.cast?.take(5)?.joinToString(", ") { it.name ?: "" },
            runtime = details.episodeRunTime?.firstOrNull()?.let { "${it}m" },
            certification = details.contentRatings?.results
                ?.firstOrNull { it.iso31661 == "US" }
                ?.rating
        )
    }

    /**
     * Request enrichment for a TMDB ID. Returns cached data if available,
     * otherwise queues for batch fetch.
     */
    suspend fun enrich(tmdbId: Int, type: ContentType): MediaEnrichmentEntity? {
        // Check if already pending
        pendingRequests[tmdbId]?.let { return it.await() }

        // Check cache synchronously
        mediaDao.getEnrichmentByTmdbId(tmdbId)?.let { return it }

        // Queue for batch fetch
        val deferred = CompletableDeferred<MediaEnrichmentEntity?>()
        pendingRequests[tmdbId] = deferred
        requestBuffer.send(EnrichmentRequest(tmdbId, type, deferred))
        return deferred.await()
    }

    /**
     * Pre-warm cache for a list of IDs (fire-and-forget)
     */
    fun preload(tmdbIds: List<Int>, type: ContentType) {
        scope.launch {
            tmdbIds.forEach { tmdbId ->
                if (pendingRequests[tmdbId] == null && mediaDao.getEnrichmentByTmdbId(tmdbId) == null) {
                    val deferred = CompletableDeferred<MediaEnrichmentEntity?>()
                    pendingRequests[tmdbId] = deferred
                    requestBuffer.send(EnrichmentRequest(tmdbId, type, deferred))
                }
            }
        }
    }

    fun close() {
        scope.cancel()
    }
}
