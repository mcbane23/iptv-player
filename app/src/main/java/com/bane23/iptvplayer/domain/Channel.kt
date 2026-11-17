package com.bane23.iptvplayer.domain

data class Category(
    val id: Long,
    val name: String,
    val channelCount: Int,
    val hidden: Boolean = false,
)

/**
 * Posebne grupe kanala koje nisu kategorije iz liste. Koriste se na mestu ID-ja kategorije
 * (pravi ID-jevi su uvek pozitivni).
 */
object ChannelGroups {
    const val FAVORITES = -1L
    const val RECENT = -2L

    /** „Svi kanali”: u upitima je to kategorija null, a u izboru na ekranu ova vrednost. */
    const val ALL = 0L

    fun isVirtual(categoryId: Long?): Boolean = categoryId != null && categoryId < 0
}

data class Channel(
    val id: Long,
    val playlistId: Long,
    val categoryId: Long?,
    val name: String,
    val logo: String?,
    val number: Int?,
    /** Redosled u listi; služi za prelazak na sledeći i prethodni kanal. */
    val sortOrder: Int,
    val favorite: Boolean = false,
    /** Naslov emisije koja je trenutno na programu, ako vodič postoji. */
    val nowTitle: String? = null,
    /** Početak i kraj te emisije (ms), za traku koliko je odmakla. */
    val nowStartMs: Long? = null,
    val nowStopMs: Long? = null,
    /** Koliko dana unazad kanal ima arhivu (catch-up), ili null ako je nema. */
    val archiveDays: Int? = null,
    /** Ključ kanala koji ostaje isti i posle osvežavanja liste. */
    val stableKey: String = "",
) {
    /** Koliki deo tekuće emisije je prošao u trenutku [nowMs] (0 do 1), ili null bez vodiča. */
    fun nowProgress(nowMs: Long): Float? {
        val start = nowStartMs ?: return null
        val stop = nowStopMs ?: return null
        if (stop <= start) return null
        return ((nowMs - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
    }
}

/** Emisija iz TV vodiča. */
data class Program(
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String?,
)

/**
 * Emisija koja je prošla i može da se pogleda iz arhive. [startToken] je početak po satu Xtream
 * servera, potreban za link arhive; za M3U liste je null.
 */
data class ArchiveProgram(
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String?,
    val startToken: String? = null,
)

/** Šta je na kanalu sada i šta sledi. */
data class NowNext(val now: Program? = null, val next: Program? = null)

/** Sve što plejeru treba da pusti kanal. */
data class StreamSource(
    /** Adrese koje se probaju redom (npr. Xtream `.ts`, pa `.m3u8`). */
    val urls: List<String>,
    val userAgent: String?,
    val referrer: String?,
)
