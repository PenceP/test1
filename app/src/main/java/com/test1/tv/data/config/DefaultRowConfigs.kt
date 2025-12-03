package com.test1.tv.data.config

import com.test1.tv.data.local.entity.RowConfigEntity

object DefaultRowConfigs {
    val homeRows = listOf(
        RowConfigEntity(
            id = "home_continue_watching",
            screenType = "home",
            title = "Continue Watching",
            rowType = "continue_watching",
            contentType = null,
            presentation = "landscape",
            dataSourceUrl = null,
            defaultPosition = 0,
            position = 0,
            enabled = true,
            requiresAuth = true,
            pageSize = 20,
            isSystemRow = true
        ),
        RowConfigEntity(
            id = "home_my_trakt",
            screenType = "home",
            title = "My Trakt Lists",
            rowType = "my_trakt",
            contentType = null,
            presentation = "landscape",
            dataSourceUrl = null,
            defaultPosition = 1,
            position = 1,
            enabled = true,
            requiresAuth = true,
            pageSize = 20,
            isSystemRow = false
        ),
        RowConfigEntity(
            id = "home_networks",
            screenType = "home",
            title = "Networks",
            rowType = "networks",
            contentType = null,
            presentation = "landscape",
            dataSourceUrl = null,
            defaultPosition = 2,
            position = 2,
            enabled = true,
            requiresAuth = false,
            pageSize = 20,
            isSystemRow = false
        ),
        RowConfigEntity(
            id = "home_franchises",
            screenType = "home",
            title = "Franchises",
            rowType = "collections",
            contentType = null,
            presentation = "portrait",
            dataSourceUrl = null,
            defaultPosition = 3,
            position = 3,
            enabled = true,
            requiresAuth = false,
            pageSize = 20,
            isSystemRow = false
        ),
        RowConfigEntity(
            id = "home_directors",
            screenType = "home",
            title = "Directors",
            rowType = "directors",
            contentType = null,
            presentation = "landscape",
            dataSourceUrl = null,
            defaultPosition = 4,
            position = 4,
            enabled = true,
            requiresAuth = false,
            pageSize = 20,
            isSystemRow = false
        ),
        RowConfigEntity(
            id = "home_trending_movies",
            screenType = "home",
            title = "Trending Movies",
            rowType = "trending",
            contentType = "movies",
            presentation = "portrait",
            dataSourceUrl = null,
            defaultPosition = 5,
            position = 5,
            enabled = true,
            requiresAuth = false,
            pageSize = 20,
            isSystemRow = false
        ),
        RowConfigEntity(
            id = "home_trending_shows",
            screenType = "home",
            title = "Trending Shows",
            rowType = "trending",
            contentType = "shows",
            presentation = "landscape",
            dataSourceUrl = null,
            defaultPosition = 6,
            position = 6,
            enabled = true,
            requiresAuth = false,
            pageSize = 20,
            isSystemRow = false
        )
    )

    val moviesRows = listOf(
        RowConfigEntity(
            id = "movies_trending",
            screenType = "movies",
            title = "Trending Movies",
            rowType = "trending",
            contentType = "movies",
            presentation = "portrait",
            dataSourceUrl = null,
            defaultPosition = 0,
            position = 0,
            enabled = true,
            requiresAuth = false,
            pageSize = 20,
            isSystemRow = true
        ),
        RowConfigEntity(
            id = "movies_popular",
            screenType = "movies",
            title = "Popular Movies",
            rowType = "popular",
            contentType = "movies",
            presentation = "portrait",
            dataSourceUrl = null,
            defaultPosition = 1,
            position = 1,
            enabled = true,
            requiresAuth = false,
            pageSize = 20,
            isSystemRow = false
        ),
        RowConfigEntity(
            id = "movies_4k",
            screenType = "movies",
            title = "Latest 4K Releases",
            rowType = "4k_releases",
            contentType = "movies",
            presentation = "portrait",
            dataSourceUrl = null,
            defaultPosition = 2,
            position = 2,
            enabled = true,
            requiresAuth = false,
            pageSize = 20,
            isSystemRow = false
        )
    )

    val tvShowsRows = listOf(
        RowConfigEntity(
            id = "tvshows_trending",
            screenType = "tvshows",
            title = "Trending Shows",
            rowType = "trending",
            contentType = "shows",
            presentation = "landscape",
            dataSourceUrl = null,
            defaultPosition = 0,
            position = 0,
            enabled = true,
            requiresAuth = false,
            pageSize = 20,
            isSystemRow = true
        ),
        RowConfigEntity(
            id = "tvshows_popular",
            screenType = "tvshows",
            title = "Popular Shows",
            rowType = "popular",
            contentType = "shows",
            presentation = "landscape",
            dataSourceUrl = null,
            defaultPosition = 1,
            position = 1,
            enabled = true,
            requiresAuth = false,
            pageSize = 20,
            isSystemRow = false
        )
    )

    val all = homeRows + moviesRows + tvShowsRows
}
