package com.bane23.iptvplayer.data.xtream

import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Xtream Codes `player_api.php`. Adresa zavisi od liste, pa se uvek prosleđuje cela (vidi [XtreamUrls]).
 * Kad je `userAgent` null, šalje se podrazumevani.
 */
interface XtreamApi {
    @GET
    suspend fun account(@Url url: String, @Header("User-Agent") userAgent: String? = null): XtreamAccountResponse

    @GET
    suspend fun liveCategories(@Url url: String, @Header("User-Agent") userAgent: String? = null): List<XtreamCategory>

    /** Spisak kanala može biti veoma velik, pa se čita strimovano umesto da se ceo učita u memoriju. */
    @Streaming
    @GET
    suspend fun liveStreams(@Url url: String, @Header("User-Agent") userAgent: String? = null): ResponseBody

    /** Kategorije filmova ili serija (isti oblik kao kategorije kanala). */
    @GET
    suspend fun categories(@Url url: String, @Header("User-Agent") userAgent: String? = null): List<XtreamCategory>

    @GET
    suspend fun vodStreams(@Url url: String, @Header("User-Agent") userAgent: String? = null): List<XtreamVodStream>

    @GET
    suspend fun series(@Url url: String, @Header("User-Agent") userAgent: String? = null): List<XtreamSeries>

    /** Detalji filma ili serije. Oblik odgovora se razlikuje od panela do panela, vidi [XtreamVodParser]. */
    @GET
    suspend fun details(@Url url: String, @Header("User-Agent") userAgent: String? = null): JsonElement
}
