package com.bane23.iptvplayer.data.prefs

/** Posle koliko minuta bez tastera se pali čuvar ekrana na TV-u. */
object ScreensaverTimeout {
    const val OFF = 0
    const val DEFAULT = 5

    /** Vrednosti koje se nude u Podešavanjima, redom. */
    val choices: List<Int> = listOf(OFF, 2, DEFAULT, 10, 15)

    /** Sačuvana vrednost, ili podrazumevana kad ništa nije sačuvano ili vrednost više nije u ponudi. */
    fun fromStored(minutes: Int?): Int = minutes?.takeIf { it in choices } ?: DEFAULT
}
