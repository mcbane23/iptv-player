package com.bane23.iptvplayer.data.xtream

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Sastavljanje adresa za Xtream Codes API. */
object XtreamUrls {

    /**
     * Sređuje adresu servera koju je korisnik uneo: dodaje `http://` ako nema šemu i
     * odbacuje putanju (`/player_api.php`, `/get.php`...). Vraća null ako adresa nije ispravna.
     */
    fun normalizeServer(input: String): HttpUrl? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        return url.newBuilder().encodedPath("/").query(null).fragment(null).build()
    }

    /** Podaci za Xtream prijavu izvučeni iz M3U linka oblika `get.php?username=..&password=..`. */
    data class Credentials(val server: HttpUrl, val username: String, val password: String)

    /**
     * Mnogi provajderi daju M3U link koji je u stvari Xtream nalog (`/get.php?username=..&password=..`).
     * Takav link se prepoznaje ovde, da bi se lista dodala preko Xtream API-ja: on radi i kad je
     * izvoz M3U fajla na serveru isključen, a donosi i podatke o nalogu i arhivi.
     * Vraća null ako link nije tog oblika.
     */
    fun parseGetPhpLink(link: String): Credentials? {
        val url = link.trim().toHttpUrlOrNull() ?: return null
        if (!url.pathSegments.lastOrNull().equals("get.php", ignoreCase = true)) return null
        val username = url.queryParameter("username")?.takeIf { it.isNotEmpty() } ?: return null
        val password = url.queryParameter("password")?.takeIf { it.isNotEmpty() } ?: return null
        val server = url.newBuilder().encodedPath("/").query(null).fragment(null).build()
        return Credentials(server, username, password)
    }

    fun playerApi(server: HttpUrl, username: String, password: String, action: String? = null): String {
        val builder = server.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)
        if (action != null) builder.addQueryParameter("action", action)
        return builder.build().toString()
    }

    /** Link ka TV vodiču (XMLTV) Xtream naloga. */
    fun xmltv(server: HttpUrl, username: String, password: String): String = server.newBuilder()
        .addPathSegment("xmltv.php")
        .addQueryParameter("username", username)
        .addQueryParameter("password", password)
        .build()
        .toString()

    /** Link za gledanje kanala uživo. */
    fun liveStream(
        server: HttpUrl,
        username: String,
        password: String,
        streamId: Long,
        extension: String = "ts",
    ): String = server.newBuilder()
        .addPathSegment("live")
        .addPathSegment(username)
        .addPathSegment(password)
        .addPathSegment("$streamId.$extension")
        .build()
        .toString()

    /** Link za gledanje filma. */
    fun movieStream(server: HttpUrl, username: String, password: String, movieId: Long, extension: String): String =
        vodStream(server, "movie", username, password, movieId, extension)

    /** Link za gledanje epizode serije. */
    fun episodeStream(
        server: HttpUrl,
        username: String,
        password: String,
        episodeId: Long,
        extension: String,
    ): String = vodStream(server, "series", username, password, episodeId, extension)

    private fun vodStream(
        server: HttpUrl,
        kind: String,
        username: String,
        password: String,
        id: Long,
        extension: String,
    ): String = server.newBuilder()
        .addPathSegment(kind)
        .addPathSegment(username)
        .addPathSegment(password)
        .addPathSegment("$id.$extension")
        .build()
        .toString()

    /** `player_api.php` sa akcijom i jednim dodatnim parametrom (npr. `category_id`, `vod_id`). */
    fun playerApi(
        server: HttpUrl,
        username: String,
        password: String,
        action: String,
        parameter: String,
        value: String,
    ): String = server.newBuilder()
        .addPathSegment("player_api.php")
        .addQueryParameter("username", username)
        .addQueryParameter("password", password)
        .addQueryParameter("action", action)
        .addQueryParameter(parameter, value)
        .build()
        .toString()

    const val ACTION_VOD_CATEGORIES = "get_vod_categories"
    const val ACTION_VOD_STREAMS = "get_vod_streams"
    const val ACTION_VOD_INFO = "get_vod_info"
    const val ACTION_SERIES_CATEGORIES = "get_series_categories"
    const val ACTION_SERIES = "get_series"
    const val ACTION_SERIES_INFO = "get_series_info"
    const val ACTION_LIVE_CATEGORIES = "get_live_categories"
    const val ACTION_LIVE_STREAMS = "get_live_streams"
}
