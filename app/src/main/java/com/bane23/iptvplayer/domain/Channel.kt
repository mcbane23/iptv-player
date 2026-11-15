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
)

/** Sve što plejeru treba da pusti kanal. */
data class StreamSource(
    /** Adrese koje se probaju redom (npr. Xtream `.ts`, pa `.m3u8`). */
    val urls: List<String>,
    val userAgent: String?,
    val referrer: String?,
)
