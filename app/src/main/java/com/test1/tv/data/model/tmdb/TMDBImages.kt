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
    val logoList = this?.logos.orEmpty().filter { !it.filePath.isNullOrBlank() }
    if (logoList.isEmpty()) return null

    val prioritized = logoList.sortedWith(
        compareByDescending<TMDBLogo> { it.languageCode == "en" }
            .thenByDescending { it.voteAverage ?: 0.0 }
            .thenByDescending { it.voteCount ?: 0 }
    )

    val logo = prioritized.first()
    return logo.filePath?.let { "https://image.tmdb.org/t/p/w500$it" }
}
