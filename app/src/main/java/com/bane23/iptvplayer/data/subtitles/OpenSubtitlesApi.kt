package com.bane23.iptvplayer.data.subtitles

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * OpenSubtitles REST API (api.opensubtitles.com/api/v1). Zaglavlja `Api-Key` i `User-Agent` dodaje
 * klijent (vidi `AppModule`); [auth] je `Bearer <token>` kad je korisnik prijavljen, inače null.
 * Parametri pretrage su poređani po abecedi, kako API traži (inače preusmerava zahtev).
 */
interface OpenSubtitlesApi {
    @GET("subtitles")
    suspend fun search(
        @Query("episode_number") episode: Int? = null,
        @Query("languages") languages: String? = null,
        @Query("order_by") orderBy: String? = "download_count",
        @Query("parent_tmdb_id") parentTmdbId: Long? = null,
        @Query("query") query: String? = null,
        @Query("season_number") season: Int? = null,
        @Query("tmdb_id") tmdbId: Long? = null,
        @Query("type") type: String? = null,
        @Query("year") year: Int? = null,
    ): OsSearchResponse

    /** 200 sa tokenom; 401 kad korisničko ime ili lozinka nisu tačni. */
    @POST("login")
    suspend fun login(@Body body: OsLoginRequest): Response<OsLoginResponse>

    @DELETE("logout")
    suspend fun logout(@Header("Authorization") auth: String): Response<Unit>

    /** 200 sa privremenim linkom; 401 kad token više ne važi, 406 kad je dnevni broj preuzimanja potrošen. */
    @POST("download")
    suspend fun download(@Header("Authorization") auth: String?, @Body body: OsDownloadRequest): Response<OsDownloadResponse>
}

val OpenSubtitlesJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
}

@Serializable
data class OsSearchResponse(val data: List<OsSubtitle> = emptyList())

@Serializable
data class OsSubtitle(val id: String? = null, val attributes: OsAttributes? = null)

@Serializable
data class OsAttributes(
    val language: String? = null,
    @SerialName("download_count") val downloadCount: Long? = null,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean? = null,
    @SerialName("ai_translated") val aiTranslated: Boolean? = null,
    @SerialName("machine_translated") val machineTranslated: Boolean? = null,
    val release: String? = null,
    val files: List<OsFile> = emptyList(),
)

@Serializable
data class OsFile(
    @SerialName("file_id") val fileId: Long? = null,
    @SerialName("file_name") val fileName: String? = null,
)

@Serializable
data class OsLoginRequest(val username: String, val password: String)

@Serializable
data class OsLoginResponse(val token: String? = null, val user: OsUser? = null)

@Serializable
data class OsUser(@SerialName("allowed_downloads") val allowedDownloads: Int? = null)

@Serializable
data class OsDownloadRequest(
    @SerialName("file_id") val fileId: Long,
    @SerialName("sub_format") val format: String = "srt",
)

@Serializable
data class OsDownloadResponse(
    val link: String? = null,
    val remaining: Int? = null,
)
