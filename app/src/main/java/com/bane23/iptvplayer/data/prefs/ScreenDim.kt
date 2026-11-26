package com.bane23.iptvplayer.data.prefs

/**
 * Zatamnjenje na TV-u: koliko procenata crnog sloja ide preko cele aplikacije. TV ne dozvoljava
 * aplikaciji da menja osvetljenje ekrana, pa se slika tamni slojem, za gledanje uveče.
 */
object ScreenDim {
    const val OFF = 0

    /** Vrednosti koje se nude, redom. Najviše 80%, da slika nikad ne ode potpuno u crno. */
    val choices: List<Int> = listOf(OFF, 20, 40, 60, 80)

    /** Sačuvana vrednost, ili isključeno kad ništa nije sačuvano ili vrednost više nije u ponudi. */
    fun fromStored(percent: Int?): Int = percent?.takeIf { it in choices } ?: OFF
}
