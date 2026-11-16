package com.bane23.iptvplayer.data.trakt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON za Trakt API: nepoznata polja se preskaču, a null vrednosti se ne šalju. */
val TraktJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

@Serializable
data class DeviceCodeRequest(@SerialName("client_id") val clientId: String)

/** Kod koji korisnik unosi na trakt.tv/activate. */
@Serializable
data class DeviceCode(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_url") val verificationUrl: String,
    @SerialName("expires_in") val expiresIn: Long,
    val interval: Long = 5,
)

@Serializable
data class DeviceTokenRequest(
    val code: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
    @SerialName("redirect_uri") val redirectUri: String = "urn:ietf:wg:oauth:2.0:oob",
    @SerialName("grant_type") val grantType: String = "refresh_token",
)

@Serializable
data class RevokeRequest(
    val token: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class TraktUserSettings(val user: TraktUser? = null)

@Serializable
data class TraktUser(val username: String? = null, val name: String? = null)

@Serializable
data class TraktIds(val tmdb: Long? = null)

/** Film ili serija u zahtevu ili odgovoru; od podataka je potreban samo TMDB ID. */
@Serializable
data class TraktMedia(val ids: TraktIds = TraktIds())

@Serializable
data class WatchedMovie(
    @SerialName("last_watched_at") val lastWatchedAt: String? = null,
    val movie: TraktMedia? = null,
)

@Serializable
data class WatchedShow(
    val show: TraktMedia? = null,
    val seasons: List<WatchedSeason> = emptyList(),
)

@Serializable
data class WatchedSeason(val number: Int, val episodes: List<WatchedEpisode> = emptyList())

@Serializable
data class WatchedEpisode(
    val number: Int,
    @SerialName("last_watched_at") val lastWatchedAt: String? = null,
)

/** Telo za `sync/history` (dodavanje) i `sync/history/remove` (uklanjanje). */
@Serializable
data class HistoryBody(
    val movies: List<HistoryMovie> = emptyList(),
    val shows: List<HistoryShow> = emptyList(),
)

@Serializable
data class HistoryMovie(
    val ids: TraktIds,
    @SerialName("watched_at") val watchedAt: String? = null,
)

@Serializable
data class HistoryShow(val ids: TraktIds, val seasons: List<HistorySeason>)

@Serializable
data class HistorySeason(val number: Int, val episodes: List<HistoryEpisode>)

@Serializable
data class HistoryEpisode(
    val number: Int,
    @SerialName("watched_at") val watchedAt: String? = null,
)
