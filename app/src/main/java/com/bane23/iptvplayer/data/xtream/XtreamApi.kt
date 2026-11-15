package com.bane23.iptvplayer.data.xtream

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

/** Xtream Codes `player_api.php`. Adresa zavisi od liste, pa se uvek prosleđuje cela (vidi [XtreamUrls]). */
interface XtreamApi {
    @GET
    suspend fun account(@Url url: String): XtreamAccountResponse

    @GET
    suspend fun liveCategories(@Url url: String): List<XtreamCategory>

    /** Spisak kanala može biti veoma velik, pa se čita strimovano umesto da se ceo učita u memoriju. */
    @Streaming
    @GET
    suspend fun liveStreams(@Url url: String): ResponseBody
}
