package com.test1.tv.data.config

import com.test1.tv.R

/**
 * Static data for Collections, Directors, and Networks rows.
 * This provides the mapping between drawable images and Trakt list URLs.
 */

data class CollectionItem(
    val id: String,
    val name: String,
    val drawableRes: Int,
    val traktListUrl: String,
    val nameDisplayMode: String = "Hidden"
)

data class DirectorItem(
    val id: String,
    val name: String,
    val drawableRes: Int,
    val traktListUrl: String,
    val nameDisplayMode: String = "Hidden"
)

data class NetworkItem(
    val id: String,
    val name: String,
    val drawableRes: Int,
    val traktListUrl: String,
    val nameDisplayMode: String = "Hidden"
)

data class TraktListItem(
    val id: String,
    val name: String,
    val drawableRes: Int,
    val traktApiUrl: String,
    val type: String, // "movies" or "shows"
    val nameDisplayMode: String = "AlwaysVisible" // Currently controlled by tmdbId < 0
)

object StaticRowData {

    /**
     * Collections (Franchises) - 23 items
     * These appear in the "Franchises" row on the Home screen
     */
    val collections = listOf(
        CollectionItem(
            id = "007",
            name = "007",
            drawableRes = R.drawable.collection_007,
            traktListUrl = "https://trakt.tv/users/proturbo18/lists/007?sort=released,desc"
        ),
        CollectionItem(
            id = "avatar",
            name = "Avatar",
            drawableRes = R.drawable.collection_avatar,
            traktListUrl = "https://trakt.tv/users/totes30/lists/avatar?sort=released,asc"
        ),
        CollectionItem(
            id = "back-to-the-future",
            name = "Back to the Future",
            drawableRes = R.drawable.collection_back_to_the_future,
            traktListUrl = "https://trakt.tv/users/james-marsh/lists/back-to-the-future?sort=released,desc"
        ),
        CollectionItem(
            id = "breaking-bad",
            name = "Breaking Bad",
            drawableRes = R.drawable.collection_breaking_bad,
            traktListUrl = "https://trakt.tv/users/all11/lists/breaking-bad?sort=rank,asc"
        ),
        CollectionItem(
            id = "dc-comics",
            name = "DC Comics",
            drawableRes = R.drawable.collection_dc_comics,
            traktListUrl = "https://trakt.tv/users/ziggy73701/lists/dcu?sort=released,asc"
        ),
        CollectionItem(
            id = "dune",
            name = "Dune",
            drawableRes = R.drawable.collection_dune,
            traktListUrl = "https://trakt.tv/users/hriday-80ce1824-23e8-4fee-99e5-8edc661aed32/lists/dune?sort=released,asc"
        ),
        CollectionItem(
            id = "fast-and-furious",
            name = "Fast & Furious",
            drawableRes = R.drawable.collection_fast_and_furious,
            traktListUrl = "https://trakt.tv/users/janpesta/lists/fast-furious-the-fast-and-the-furious-copy?sort=released,desc"
        ),
        CollectionItem(
            id = "harry-potter",
            name = "Harry Potter",
            drawableRes = R.drawable.collection_harry_potter,
            traktListUrl = "https://trakt.tv/users/tvcinelover/lists/harry-potter?sort=rank,asc"
        ),
        CollectionItem(
            id = "indiana-jones",
            name = "Indiana Jones",
            drawableRes = R.drawable.collection_indiana_jones,
            traktListUrl = "https://trakt.tv/users/cinhd/lists/indiana-jones?sort=rank,asc"
        ),
        CollectionItem(
            id = "jurassic-park",
            name = "Jurassic Park",
            drawableRes = R.drawable.collection_jurassic_park,
            traktListUrl = "https://trakt.tv/users/foxbot/lists/jurassic-park?sort=rank,asc"
        ),
        CollectionItem(
            id = "john-wick",
            name = "John Wick",
            drawableRes = R.drawable.collection_john_wick,
            traktListUrl = "https://trakt.tv/users/drjenkins/lists/john-wick?sort=released,desc"
        ),
        CollectionItem(
            id = "matrix",
            name = "Matrix",
            drawableRes = R.drawable.collection_matrix,
            traktListUrl = "https://trakt.tv/users/julienicoletv/lists/matrix?sort=rank,asc"
        ),
        CollectionItem(
            id = "marvel",
            name = "Marvel",
            drawableRes = R.drawable.collection_marvel,
            traktListUrl = "https://trakt.tv/users/goingnineteen/lists/mcu?sort=rank,asc"
        ),
        CollectionItem(
            id = "mission-impossible",
            name = "Mission Impossible",
            drawableRes = R.drawable.collection_mission_impossible,
            traktListUrl = "https://trakt.tv/users/drjenkins/lists/mission-impossible?sort=released,desc"
        ),
        CollectionItem(
            id = "pirates-of-the-caribbean",
            name = "Pirates of the Caribbean",
            drawableRes = R.drawable.collection_pirates_of_the_caribbean,
            traktListUrl = "https://trakt.tv/users/drjenkins/lists/pirates-of-the-caribbean?sort=released,desc"
        ),
        CollectionItem(
            id = "rambo",
            name = "Rambo",
            drawableRes = R.drawable.collection_rambo,
            traktListUrl = "https://trakt.tv/users/arachn0id/lists/rambo?sort=released,desc"
        ),
        CollectionItem(
            id = "rocky",
            name = "Rocky",
            drawableRes = R.drawable.collection_rocky,
            traktListUrl = "https://trakt.tv/users/estemitad/lists/rocky?sort=rank,asc"
        ),
        CollectionItem(
            id = "star-trek",
            name = "Star Trek",
            drawableRes = R.drawable.collection_star_trek,
            traktListUrl = "https://trakt.tv/users/mazelon/lists/star-trek?sort=rank,asc"
        ),
        CollectionItem(
            id = "star-wars",
            name = "Star Wars",
            drawableRes = R.drawable.collection_star_wars,
            traktListUrl = "https://trakt.tv/users/turkeyfx/lists/star-wars?sort=rank,asc"
        ),
        CollectionItem(
            id = "the-hunger-games",
            name = "The Hunger Games",
            drawableRes = R.drawable.collection_the_hunger_games,
            traktListUrl = "https://trakt.tv/users/grelipe/lists/hunger-games?sort=released,desc"
        ),
        CollectionItem(
            id = "the-lord-of-the-rings",
            name = "The Lord of the Rings",
            drawableRes = R.drawable.collection_the_lord_of_the_rings,
            traktListUrl = "https://trakt.tv/users/monkeeman/lists/lord-of-the-rings?sort=rank,asc"
        ),
        CollectionItem(
            id = "transformers",
            name = "Transformers",
            drawableRes = R.drawable.collection_transformers,
            traktListUrl = "https://trakt.tv/users/drjenkins/lists/transformers?sort=released,desc"
        ),
        CollectionItem(
            id = "x-men",
            name = "X-Men",
            drawableRes = R.drawable.collection_x_men,
            traktListUrl = "https://trakt.tv/users/rdurdle/lists/xmen?sort=released,desc"
        )
    )

    /**
     * Directors - 10 items
     * These appear in the "Directors" row on the Home screen
     */
    val directors = listOf(
        DirectorItem(
            id = "nolan",
            name = "Christopher Nolan",
            drawableRes = R.drawable.director_nolan,
            traktListUrl = "https://trakt.tv/users/acox03/lists/christopher-nolan?sort=rank,asc"
        ),
        DirectorItem(
            id = "scorsese",
            name = "Martin Scorsese",
            drawableRes = R.drawable.director_scorsese,
            traktListUrl = "https://trakt.tv/users/viddywelly/lists/martin-scorsese?sort=rank,asc"
        ),
        DirectorItem(
            id = "spielberg",
            name = "Steven Spielberg",
            drawableRes = R.drawable.director_spielberg,
            traktListUrl = "https://trakt.tv/users/viddywelly/lists/steven-spielberg?sort=rank,asc"
        ),
        DirectorItem(
            id = "kubrick",
            name = "Stanley Kubrick",
            drawableRes = R.drawable.director_kubrick,
            traktListUrl = "https://trakt.tv/users/fuzzybeats/lists/stanley-kubrick?sort=released,asc"
        ),
        DirectorItem(
            id = "fincher",
            name = "David Fincher",
            drawableRes = R.drawable.director_fincher,
            traktListUrl = "https://trakt.tv/users/mawsa/lists/david-fincher?sort=rank,asc"
        ),
        DirectorItem(
            id = "hitchcock",
            name = "Alfred Hitchcock",
            drawableRes = R.drawable.director_hitchcock,
            traktListUrl = "https://trakt.tv/users/mawsa/lists/alfred-hitchcock?sort=rank,asc"
        ),
        DirectorItem(
            id = "pta",
            name = "Paul Thomas Anderson",
            drawableRes = R.drawable.director_pta,
            traktListUrl = "https://trakt.tv/users/pebao/lists/paul-thomas-anderson?sort=rank,asc"
        ),
        DirectorItem(
            id = "villeneuve",
            name = "Denis Villeneuve",
            drawableRes = R.drawable.director_villeneuve,
            traktListUrl = "https://trakt.tv/users/batherini/lists/denis-villeneuve?sort=rank,asc"
        ),
        DirectorItem(
            id = "depalma",
            name = "Brian De Palma",
            drawableRes = R.drawable.director_depalma,
            traktListUrl = "https://trakt.tv/users/viddywelly/lists/brian-de-palma?sort=rank,asc"
        ),
        DirectorItem(
            id = "carpenter",
            name = "John Carpenter",
            drawableRes = R.drawable.director_carpenter,
            traktListUrl = "https://trakt.tv/users/viddywelly/lists/john-carpenter?sort=rank,asc"
        )
    )

    /**
     * Networks - 6 items
     * These appear in the "Networks" row on the Home screen
     */
    val networks = listOf(
        NetworkItem(
            id = "Netflix",
            name = "Netflix",
            drawableRes = R.drawable.network_netflix,
            traktListUrl = "https://trakt.tv/users/lhuss13/lists/netflix-movies?sort=released,asc"
        ),
        NetworkItem(
            id = "DisneyPlus",
            name = "Disney+",
            drawableRes = R.drawable.network_disney_plus,
            traktListUrl = "https://trakt.tv/users/bonnno1/lists/disney-movies?sort=released,asc"
        ),
        NetworkItem(
            id = "HBOMax",
            name = "HBO Max",
            drawableRes = R.drawable.network_hbo_max,
            traktListUrl = "https://trakt.tv/users/alaxkouad/lists/hbo-max-movies?sort=released,asc"
        ),
        NetworkItem(
            id = "Hulu",
            name = "Hulu",
            drawableRes = R.drawable.network_hulu,
            traktListUrl = "https://trakt.tv/users/garycrawfordgc/lists/hulu-movies?sort=released,asc"
        ),
        NetworkItem(
            id = "AmazonPrime",
            name = "Amazon Prime",
            drawableRes = R.drawable.network_amazon_prime,
            traktListUrl = "https://trakt.tv/users/garycrawfordgc/lists/amazon-prime-movies?sort=released,asc"
        ),
        NetworkItem(
            id = "AppleTVPlus",
            name = "Apple TV+",
            drawableRes = R.drawable.network_apple_tv_plus,
            traktListUrl = "https://trakt.tv/users/shaunatkins11/lists/apple-tv-movies?sort=released,asc"
        )
    )

    /**
     * Trakt Lists (nested under Continue Watching)
     * These provide quick access to user's Trakt collections and watchlists
     */
    val traktQuickAccess = listOf(
        TraktListItem(
            id = "movie_collection",
            name = "Movie Collection",
            drawableRes = R.drawable.trakt2, // TODO: Replace with trakt_likedlist when added
            traktApiUrl = "https://api.trakt.tv/sync/collection/movies",
            type = "movies"
        ),
        TraktListItem(
            id = "movie_watchlist",
            name = "Movie Watchlist",
            drawableRes = R.drawable.ic_watchlist, // TODO: Replace with trakt_watchlist when added
            traktApiUrl = "https://api.trakt.tv/sync/watchlist/movies",
            type = "movies"
        ),
        TraktListItem(
            id = "tv_collection",
            name = "TV Collection",
            drawableRes = R.drawable.trakt2, // TODO: Replace with trakt_likedlist when added
            traktApiUrl = "https://api.trakt.tv/sync/collection/shows",
            type = "shows"
        ),
        TraktListItem(
            id = "tv_watchlist",
            name = "TV Watchlist",
            drawableRes = R.drawable.ic_watchlist, // TODO: Replace with trakt_watchlist when added
            traktApiUrl = "https://api.trakt.tv/sync/watchlist/shows",
            type = "shows"
        )
    )

    /**
     * Helper function to get items by row type
     */
    fun getItemsForRowType(rowType: String): List<Any> {
        return when (rowType) {
            "collections" -> collections
            "directors" -> directors
            "networks" -> networks
            "trakt_lists" -> traktQuickAccess
            else -> emptyList()
        }
    }

    /**
     * Helper function to get Trakt list URL for a specific item
     */
    fun getTraktListUrl(rowType: String, itemId: String): String? {
        return when (rowType) {
            "collections" -> collections.find { it.id == itemId }?.traktListUrl
            "directors" -> directors.find { it.id == itemId }?.traktListUrl
            "networks" -> networks.find { it.id == itemId }?.traktListUrl
            "trakt_lists" -> traktQuickAccess.find { it.id == itemId }?.traktApiUrl
            else -> null
        }
    }

    /**
     * Helper function to get drawable resource for a specific item
     */
    fun getDrawableRes(rowType: String, itemId: String): Int? {
        return when (rowType) {
            "collections" -> collections.find { it.id == itemId }?.drawableRes
            "directors" -> directors.find { it.id == itemId }?.drawableRes
            "networks" -> networks.find { it.id == itemId }?.drawableRes
            "trakt_lists" -> traktQuickAccess.find { it.id == itemId }?.drawableRes
            else -> null
        }
    }
}
