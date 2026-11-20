package com.bane23.iptvplayer.data.cast

/**
 * Adrese koje telefon proba kad traži TV u kućnoj mreži bez oglašavanja (mDNS): neki ruteri ne
 * propuštaju oglašavanje između uređaja, a obična veza ka adresi radi. Proba se samo deo mreže u
 * kom je telefon (isti prvi brojevi adrese, poslednji od 1 do 254), bez adrese samog telefona.
 */
object LanScan {
    /** Port na kom TV sluša kad je slobodan, da telefon zna gde da pita. */
    const val PORT = 47823

    private const val FIRST_HOST = 1
    private const val LAST_HOST = 254

    fun hosts(ownAddress: String): List<String> {
        val parts = ownAddress.split('.')
        if (parts.size != 4) return emptyList()
        val numbers = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return emptyList() }
        if (!isHomeNetwork(numbers)) return emptyList()
        val prefix = numbers.take(3).joinToString(".")
        return (FIRST_HOST..LAST_HOST).filter { it != numbers[3] }.map { "$prefix.$it" }
    }

    /** Samo privatne mreže (10.x, 172.16-31.x, 192.168.x): adrese na internetu se nikad ne probaju. */
    private fun isHomeNetwork(n: List<Int>): Boolean = when {
        n[0] == 10 -> true
        n[0] == 172 -> n[1] in 16..31
        n[0] == 192 -> n[1] == 168
        else -> false
    }
}
