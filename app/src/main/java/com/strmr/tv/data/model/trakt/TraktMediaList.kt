package com.strmr.tv.data.model.trakt

enum class TraktMediaList(
    val listType: String,
    val itemType: String,
    val cacheCategory: String,
    val displayTitle: String
) {
    MOVIE_COLLECTION(
        listType = "COLLECTION",
        itemType = "MOVIE",
        cacheCategory = "MY_TRAKT_MOVIE_COLLECTION",
        displayTitle = "Movie Collection"
    ),
    MOVIE_WATCHLIST(
        listType = "WATCHLIST",
        itemType = "MOVIE",
        cacheCategory = "MY_TRAKT_MOVIE_WATCHLIST",
        displayTitle = "Movie Watchlist"
    ),
    TV_COLLECTION(
        listType = "COLLECTION",
        itemType = "SHOW",
        cacheCategory = "MY_TRAKT_TV_COLLECTION",
        displayTitle = "TV Collection"
    ),
    TV_WATCHLIST(
        listType = "WATCHLIST",
        itemType = "SHOW",
        cacheCategory = "MY_TRAKT_TV_WATCHLIST",
        displayTitle = "TV Watchlist"
    );

    val isShow: Boolean get() = itemType == "SHOW"

    companion object {
        fun fromId(name: String?): TraktMediaList? = values().firstOrNull { it.name == name }
    }
}
