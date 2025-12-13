package com.strmr.tv.data.model.home

import com.google.gson.annotations.SerializedName

enum class PosterOrientation {
    @SerializedName("landscape")
    LANDSCAPE,
    @SerializedName("portrait")
    PORTRAIT,
    @SerializedName("square")
    SQUARE
}

enum class HomeRowType {
    @SerializedName("trakt_list")
    TRAKT_LIST,
    @SerializedName("collection")
    COLLECTION,
    @SerializedName("continue_watching")
    CONTINUE_WATCHING
}

data class HomeConfig(
    val home: HomeSection
)

data class HomeSection(
    val rows: List<HomeRow>
)

data class HomeRow(
    val id: String,
    val type: HomeRowType?,
    val title: String,
    val poster_orientation: PosterOrientation? = null,
    val requires_trakt: Boolean? = null,
    val trakt_list: TraktListConfig? = null,
    val items: List<CollectionItemConfig>? = null
)

data class TraktListConfig(
    val kind: String,
    val list_type: String,
    val user: String? = null,
    val slug: String,
    val url: String? = null
)

data class CollectionItemConfig(
    val id: String,
    val label: String,
    val image_url: String,
    val trakt_list: TraktListConfig
)
