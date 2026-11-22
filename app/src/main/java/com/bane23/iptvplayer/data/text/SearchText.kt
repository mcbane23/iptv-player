package com.bane23.iptvplayer.data.text

import java.text.Normalizer

/**
 * Poređenje naziva u pretrazi: mala slova, bez kvačica, „đ” kao „dj”, i nalaženje naziva kad je u
 * upitu jedno slovo pogrešno, ispušteno ili suvišno.
 */
object SearchText {
    /** Kraći upiti traže tačno poklapanje: sa jednom greškom bi odgovarali skoro svakom nazivu. */
    const val MIN_NEAR_LENGTH = 5

    private val marks = Regex("\\p{Mn}+")

    fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase().replace("đ", "dj"), Normalizer.Form.NFD).replace(marks, "")

    /**
     * Da li se [needle] nalazi negde u [text] uz najviše jednu grešku (zamenjeno, ispušteno ili
     * suvišno slovo). Oba teksta treba da su već prošla kroz [normalize].
     */
    fun near(text: String, needle: String): Boolean {
        val n = needle.length
        if (n < MIN_NEAR_LENGTH || text.length < n - 1) return false
        // Približno traženje podniza: red tabele rastojanja po slovu teksta, početak može biti bilo gde.
        var previous = IntArray(n + 1) { it }
        var current = IntArray(n + 1)
        for (c in text) {
            current[0] = 0
            for (j in 1..n) {
                val swap = previous[j - 1] + if (needle[j - 1] == c) 0 else 1
                current[j] = minOf(swap, previous[j] + 1, current[j - 1] + 1)
            }
            if (current[n] <= 1) return true
            val spare = previous
            previous = current
            current = spare
        }
        return false
    }

    /**
     * Rezultati pretrage: prvo stavke čiji naziv sadrži [needle], a iza njih one koje ga sadrže uz
     * jednu grešku; najviše [limit]. [items] su parovi (normalizovan naziv, stavka).
     */
    fun <T> search(items: List<Pair<String, T>>, needle: String, limit: Int): List<T> {
        val exact = ArrayList<T>()
        val close = ArrayList<T>()
        for ((name, item) in items) {
            if (needle in name) {
                exact += item
                if (exact.size >= limit) return exact
            } else if (close.size < limit && near(name, needle)) {
                close += item
            }
        }
        return exact + close.take(limit - exact.size)
    }
}
