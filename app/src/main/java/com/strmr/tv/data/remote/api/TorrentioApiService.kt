package com.strmr.tv.data.remote.api

import com.strmr.tv.data.remote.model.torrentio.TorrentioResponse
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Torrentio Stremio addon API service
 * Base URL: https://torrentio.strem.fun/
 *
 * Providers configured: yts, eztv, rarbg, 1337x, thepiratebay, kickasstorrents, torrentgalaxy, magnetdl
 */
interface TorrentioApiService {

    companion object {
        const val PROVIDERS = "yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentgalaxy,magnetdl"
    }

    /**
     * Get streams for a movie by IMDB ID
     * Example: /providers=yts,.../stream/movie/tt1234567.json
     */
    @GET("providers={providers}/stream/movie/{imdb_id}.json")
    suspend fun getMovieStreams(
        @Path("providers") providers: String = PROVIDERS,
        @Path("imdb_id") imdbId: String
    ): TorrentioResponse

    /**
     * Get streams for a TV show episode by IMDB ID
     * Example: /providers=yts,.../stream/series/tt1234567:1:1.json (season:episode)
     */
    @GET("providers={providers}/stream/series/{imdb_id}:{season}:{episode}.json")
    suspend fun getEpisodeStreams(
        @Path("providers") providers: String = PROVIDERS,
        @Path("imdb_id") imdbId: String,
        @Path("season") season: Int,
        @Path("episode") episode: Int
    ): TorrentioResponse
}
