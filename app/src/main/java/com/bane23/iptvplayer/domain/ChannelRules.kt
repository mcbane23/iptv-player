package com.bane23.iptvplayer.domain

import java.text.Normalizer

/** Pravila za kanale iz Podešavanja. Sva su isključena dok ih korisnik ne uključi. */
data class ChannelRuleSettings(
    /** Kanal bez oznake kvaliteta ili sa oznakom SD se ne prikazuje kad isti postoji u HD, FHD ili 4K. */
    val hideSd: Boolean = false,
    /** Kanali sa vremenskim pomakom („+1”) se ne prikazuju. */
    val hidePlusOne: Boolean = false,
    /** Kanal sa potpuno istim nazivom u jednoj listi prikazuje se jednom. */
    val mergeSameName: Boolean = false,
) {
    val any: Boolean get() = hideSd || hidePlusOne || mergeSameName
}

/**
 * Koje kanale pravila sklanjaju iz spiskova. Sve se čita iz naziva kanala, jer liste kvalitet i
 * pomak ne šalju kao poseban podatak.
 */
object ChannelRules {
    /** [kept]: kanal je među omiljenima, pa ga pravila ne sklanjaju. Redosled je redosled u listi. */
    data class Row(val id: Long, val playlistId: Long, val name: String, val kept: Boolean = false)

    private const val EDGE_BEFORE = "(?<![\\p{L}\\p{N}])"
    private const val EDGE_AFTER = "(?![\\p{L}\\p{N}])"
    private val ultra = Regex("$EDGE_BEFORE(UHD|4K|8K|2160P)$EDGE_AFTER")
    private val full = Regex("$EDGE_BEFORE(FHD|FULL ?HD|1080P)$EDGE_AFTER")
    private val high = Regex("$EDGE_BEFORE(HD|720P)$EDGE_AFTER")
    private val tags = Regex("$EDGE_BEFORE(UHD|4K|8K|2160P|FHD|FULL ?HD|1080P|HD|720P|SD|576P|480P|HEVC|H\\.?265|H\\.?264)$EDGE_AFTER")
    private val separators = Regex("[^\\p{L}\\p{N}+]+")
    private val plusOne = Regex("(?<![\\p{L}\\p{N}+])\\+1$EDGE_AFTER")

    private fun upper(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFKC).uppercase()

    /** Kvalitet iz naziva: 0 = bez oznake ili SD, 1 = HD, 2 = FHD, 3 = 4K/UHD. */
    fun quality(name: String): Int {
        val text = upper(name)
        return when {
            ultra.containsMatchIn(text) -> 3
            full.containsMatchIn(text) -> 2
            high.containsMatchIn(text) -> 1
            else -> 0
        }
    }

    /** Naziv bez oznaka kvaliteta i kodeka, za poređenje „isti kanal u drugom kvalitetu”. */
    fun baseName(name: String): String = upper(name).replace(tags, " ").replace(separators, " ").trim()

    /** Kanal sa vremenskim pomakom: u nazivu stoji „+1” kao zasebna oznaka („RTL +1”, ali ne „Canal+ 1”). */
    fun isPlusOne(name: String): Boolean = plusOne.containsMatchIn(upper(name))

    /** ID-jevi kanala koje [rules] sklanjaju. */
    fun hidden(rows: List<Row>, rules: ChannelRuleSettings): Set<Long> {
        if (!rules.any) return emptySet()
        val hidden = HashSet<Long>()
        var shown = rows
        if (rules.hidePlusOne) {
            shown = shown.filter { row -> (row.kept || !isPlusOne(row.name)).also { stays -> if (!stays) hidden += row.id } }
        }
        if (rules.mergeSameName) {
            val seen = HashSet<Pair<Long, String>>()
            shown = shown.filter { row ->
                val first = seen.add(row.playlistId to row.name.trim().uppercase())
                (first || row.kept).also { stays -> if (!stays) hidden += row.id }
            }
        }
        if (rules.hideSd) {
            val best = HashMap<Pair<Long, String>, Int>()
            val keyed = shown.map { row -> Triple(row, row.playlistId to baseName(row.name), quality(row.name)) }
            for ((_, key, rank) in keyed) best[key] = maxOf(best[key] ?: 0, rank)
            for ((row, key, rank) in keyed) {
                if (rank == 0 && !row.kept && (best[key] ?: 0) > 0) hidden += row.id
            }
        }
        return hidden
    }
}
