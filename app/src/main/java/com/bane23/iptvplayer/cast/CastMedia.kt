package com.bane23.iptvplayer.cast

/** Film ili epizoda koja se pušta na TV-u, da telefon može da zapamti dokle se stiglo. */
data class CastVodItem(
    val id: Long,
    val seriesId: Long?,
    val title: String?,
    val poster: String?,
    val extension: String?,
)

/**
 * Ono što se šalje na Chromecast. [urls] su adrese strima kako ih koristi plejer na telefonu;
 * [channelId] i [categoryId] služe daljinskom za sledeći i prethodni kanal.
 */
data class CastMedia(
    val title: String,
    val urls: List<String>,
    val live: Boolean,
    val positionMs: Long = 0,
    val channelId: Long? = null,
    val categoryId: Long? = null,
    val vod: CastVodItem? = null,
    /** Zaglavlja koja provajder traži; koristi ih samo slanje na našu aplikaciju na TV-u. */
    val userAgent: String? = null,
    val referrer: String? = null,
)

/** Izbor adrese i vrste sadržaja za Chromecast. Bez Android klasa, da može da se testira. */
object CastUrls {
    const val HLS = "application/x-mpegURL"
    const val DASH = "application/dash+xml"
    const val MP4 = "video/mp4"

    /**
     * Chromecast ne pušta sirov MPEG-TS, pa se za kanale bira HLS adresa ako postoji. Za filmove se
     * bira format koji Chromecast najsigurnije pušta.
     */
    fun pick(urls: List<String>): String? {
        val usable = urls.filter { it.startsWith("http://") || it.startsWith("https://") }
        return usable.firstOrNull { extension(it) == "m3u8" }
            ?: usable.firstOrNull { extension(it) == "mp4" }
            ?: usable.firstOrNull()
    }

    fun contentType(url: String, live: Boolean): String = when (extension(url)) {
        "m3u8" -> HLS
        "mpd" -> DASH
        "mp4", "m4v", "mov" -> MP4
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "ts" -> "video/mp2t"
        "avi" -> "video/x-msvideo"
        else -> if (live) HLS else MP4
    }

    /** Nastavak fajla iz adrese, bez upita i sidra, malim slovima. */
    fun extension(url: String): String {
        val path = url.substringBefore('#').substringBefore('?')
        val name = path.substringAfterLast('/')
        return if ('.' in name) name.substringAfterLast('.').lowercase() else ""
    }
}
