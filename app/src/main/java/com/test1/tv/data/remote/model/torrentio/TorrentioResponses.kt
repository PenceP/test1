package com.test1.tv.data.remote.model.torrentio

import com.google.gson.annotations.SerializedName

/**
 * Response from Torrentio stremio addon API
 * Example: https://torrentio.strem.fun/providers=yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentgalaxy,magnetdl/stream/movie/tt1234567.json
 */
data class TorrentioResponse(
    @SerializedName("streams")
    val streams: List<TorrentioStream>?
)

/**
 * Individual stream from Torrentio
 * Contains magnet/torrent info in various formats
 */
data class TorrentioStream(
    @SerializedName("name")
    val name: String?,

    @SerializedName("title")
    val title: String?,

    @SerializedName("infoHash")
    val infoHash: String?,

    @SerializedName("fileIdx")
    val fileIdx: Int?,

    @SerializedName("behaviorHints")
    val behaviorHints: BehaviorHints?
)

data class BehaviorHints(
    @SerializedName("bingeGroup")
    val bingeGroup: String?,

    @SerializedName("filename")
    val filename: String?
)
