package com.bane23.iptvplayer.data.trakt

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Trakt API v2. Zaglavlja `trakt-api-version` i `trakt-api-key` dodaje klijent (vidi `AppModule`);
 * [auth] je `Bearer <token>` za pozive koji traže prijavu.
 */
interface TraktApi {
    @POST("oauth/device/code")
    suspend fun deviceCode(@Body body: DeviceCodeRequest): DeviceCode

    /** 200 kad je korisnik odobrio; 400 dok čeka, 404/409/410/418 kad kod više ne važi, 429 prečesto. */
    @POST("oauth/device/token")
    suspend fun deviceToken(@Body body: DeviceTokenRequest): Response<TokenResponse>

    @POST("oauth/token")
    suspend fun refreshToken(@Body body: RefreshTokenRequest): Response<TokenResponse>

    @POST("oauth/revoke")
    suspend fun revoke(@Body body: RevokeRequest): Response<Unit>

    @GET("users/settings")
    suspend fun userSettings(@Header("Authorization") auth: String): TraktUserSettings

    @GET("sync/watched/movies")
    suspend fun watchedMovies(@Header("Authorization") auth: String): List<WatchedMovie>

    @GET("sync/watched/shows")
    suspend fun watchedShows(@Header("Authorization") auth: String): List<WatchedShow>

    @POST("sync/history")
    suspend fun addHistory(@Header("Authorization") auth: String, @Body body: HistoryBody): Response<Unit>

    /** Pamti dokle se stiglo (za nastavak na drugom uređaju); ne označava kao odgledano. */
    @POST("scrobble/pause")
    suspend fun scrobblePause(@Header("Authorization") auth: String, @Body body: ScrobbleBody): Response<Unit>

    /** Započeti filmovi i epizode sa svih uređaja. */
    @GET("sync/playback")
    suspend fun playback(@Header("Authorization") auth: String): List<PlaybackItem>

    @POST("sync/history/remove")
    suspend fun removeHistory(@Header("Authorization") auth: String, @Body body: HistoryBody): Response<Unit>
}
