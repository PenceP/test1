package com.strmr.tv.data.model.trakt

import com.google.gson.annotations.SerializedName

/**
 * Trakt Scrobble API Models
 *
 * Scrobbling is used to track what users are watching in real-time.
 * The flow is: start -> (pause/resume) -> stop
 * At 80%+ completion, Trakt auto-marks the item as watched.
 */

// ==================== SCROBBLE REQUEST MODELS ====================

data class TraktScrobbleRequest(
    @SerializedName("movie")
    val movie: TraktScrobbleMovie? = null,
    @SerializedName("show")
    val show: TraktScrobbleShow? = null,
    @SerializedName("episode")
    val episode: TraktScrobbleEpisode? = null,
    @SerializedName("progress")
    val progress: Double,  // 0.0 to 100.0
    @SerializedName("app_version")
    val appVersion: String? = null,
    @SerializedName("app_date")
    val appDate: String? = null
)

data class TraktScrobbleMovie(
    @SerializedName("ids")
    val ids: TraktScrobbleIds
)

data class TraktScrobbleShow(
    @SerializedName("ids")
    val ids: TraktScrobbleIds
)

data class TraktScrobbleEpisode(
    @SerializedName("season")
    val season: Int,
    @SerializedName("number")
    val number: Int
)

/**
 * IDs for scrobble - can use TMDB, IMDB, or Trakt IDs
 */
data class TraktScrobbleIds(
    @SerializedName("trakt")
    val trakt: Int? = null,
    @SerializedName("tmdb")
    val tmdb: Int? = null,
    @SerializedName("imdb")
    val imdb: String? = null,
    @SerializedName("slug")
    val slug: String? = null
)

// ==================== SCROBBLE RESPONSE MODELS ====================

data class TraktScrobbleResponse(
    @SerializedName("id")
    val id: Long?,
    @SerializedName("action")
    val action: String?,  // "start", "pause", "scrobble"
    @SerializedName("progress")
    val progress: Double?,
    @SerializedName("sharing")
    val sharing: TraktScrobbleSharing?,
    @SerializedName("movie")
    val movie: TraktMovie?,
    @SerializedName("show")
    val show: TraktShow?,
    @SerializedName("episode")
    val episode: TraktEpisode?
)

data class TraktScrobbleSharing(
    @SerializedName("twitter")
    val twitter: Boolean?,
    @SerializedName("tumblr")
    val tumblr: Boolean?
)
