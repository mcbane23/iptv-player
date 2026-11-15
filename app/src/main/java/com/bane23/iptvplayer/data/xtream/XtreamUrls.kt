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

    fun playerApi(server: HttpUrl, username: String, password: String, action: String? = null): String {
        val builder = server.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)
        if (action != null) builder.addQueryParameter("action", action)
        return builder.build().toString()
    }

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

    const val ACTION_LIVE_CATEGORIES = "get_live_categories"
    const val ACTION_LIVE_STREAMS = "get_live_streams"
}
