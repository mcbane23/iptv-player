package com.bane23.iptvplayer.data.prefs

/**
 * Nedavni upiti zajedničke pretrage: najnoviji je prvi, bez ponavljanja (velika i mala slova se ne
 * razlikuju) i najviše [LIMIT]. Čuvaju se samo na uređaju, kao jedan tekst sa upitom u svakom redu.
 */
object RecentSearches {
    const val LIMIT = 8

    fun add(current: List<String>, query: String): List<String> {
        val clean = query.replace('\n', ' ').trim()
        if (clean.isEmpty()) return current
        return (listOf(clean) + current.filterNot { it.equals(clean, ignoreCase = true) }).take(LIMIT)
    }

    fun encode(list: List<String>): String = list.joinToString("\n")

    fun decode(text: String?): List<String> =
        text.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }.take(LIMIT)
}
