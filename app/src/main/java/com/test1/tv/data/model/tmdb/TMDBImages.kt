package com.test1.tv.data.model.tmdb

import com.google.gson.annotations.SerializedName

/**
 * Container for TMDB image responses. Currently only logos are needed,
 * but the structure allows expansion later.
 */
data class TMDBImages(
    @SerializedName("logos")
    val logos: List<TMDBLogo>?
)

data class TMDBLogo(
    @SerializedName("file_path")
    val filePath: String?,
    @SerializedName("iso_639_1")
    val languageCode: String?,
    @SerializedName("vote_average")
    val voteAverage: Double?,
    @SerializedName("vote_count")
    val voteCount: Int?
)

internal fun TMDBImages?.getPreferredLogoUrl(): String? {
    val logos = this?.logos.orEmpty().filter { !it.filePath.isNullOrBlank() }
    if (logos.isEmpty()) return null

    val rasterLogos = logos.filterNot { it.filePath!!.endsWith(".svg", ignoreCase = true) }
    val candidates = if (rasterLogos.isNotEmpty()) rasterLogos else logos

    val prioritized = candidates.sortedWith(
        compareByDescending<TMDBLogo> { it.languageCode == "en" }
            .thenByDescending { it.voteAverage ?: 0.0 }
            .thenByDescending { it.voteCount ?: 0 }
    )

    val logo = prioritized.firstOrNull() ?: return null
    return logo.filePath?.let { "https://image.tmdb.org/t/p/w500$it" }
}
