package com.bane23.iptvplayer.player

/**
 * Pravila za karticu „Sledeća epizoda”: kada se pojavljuje pred kraj epizode i posle koliko epizoda
 * zaredom bez dodira daljinskog plejer pita „Još gledate?” umesto da sam pusti sledeću.
 */
object NextEpisode {
    /** Kartica se pojavljuje kad do kraja epizode ostane ovoliko. */
    const val CARD_MS = 20_000L

    /** Posle ovoliko epizoda koje su krenule same, sledeća čeka potvrdu. */
    const val STILL_WATCHING_AFTER = 3

    /**
     * Koliko sekundi je ostalo do kraja, zaokruženo naviše, ili null dok kartica još ne treba da
     * se vidi (epizoda nije pri kraju ili joj se trajanje ne zna).
     */
    fun secondsLeft(positionMs: Long, durationMs: Long): Int? {
        if (durationMs <= 0) return null
        val left = (durationMs - positionMs).coerceAtLeast(0)
        if (left > CARD_MS) return null
        return ((left + 999) / 1000).toInt()
    }

    /** Da li sledeća epizoda sme da krene sama, ili prvo treba pitati gledaoca. */
    fun needsConfirmation(autoAdvances: Int): Boolean = autoAdvances >= STILL_WATCHING_AFTER
}
