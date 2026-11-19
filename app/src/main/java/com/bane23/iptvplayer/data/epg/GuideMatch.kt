package com.bane23.iptvplayer.data.epg

/** Kanal iz vodiča: njegov ID (malim slovima) i naziv za prikaz. */
data class GuideChannel(val key: String, val name: String)

/**
 * Traženje kanala iz vodiča po nazivu, za ručno povezivanje kanala liste sa programom. Nazivi se
 * porede bez oznake kvaliteta („HD”, „FHD”…), oznake države ispred naziva („RS: ”) i interpunkcije,
 * jer se baš po tome nazivi u listi i u vodiču najčešće razlikuju.
 */
object GuideMatch {

    /** Naziv sveden na reči po kojima se poredi. */
    fun normalize(name: String): String = words(name).joinToString(" ")

    /** Najviše [limit] kanala iz [channels] najsličnijih upitu [query], najsličniji prvi. Prazan upit daje početak spiska po abecedi. */
    fun rank(query: String, channels: List<GuideChannel>, limit: Int = 20): List<GuideChannel> {
        val wanted = words(query)
        if (wanted.isEmpty()) return channels.sortedBy { it.name.lowercase() }.take(limit)
        val wantedText = wanted.joinToString(" ")
        val wantedCompact = wanted.joinToString("")
        return channels
            .mapNotNull { channel ->
                val byName = score(wanted, wantedText, words(channel.name))
                // ID kanala u vodiču često liči na naziv („maks.rs”, „vesti24.rs”), pa i on pomaže, ali
                // vredi manje od naziva.
                val keyWords = words(channel.key)
                val byKey = if (wantedCompact in keyWords) KEY_MATCH else score(wanted, wantedText, keyWords) - KEY_PENALTY
                val best = maxOf(byName, byKey)
                if (best > 0) Scored(channel, best) else null
            }
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.channel.name.length }.thenBy { it.channel.name.lowercase() })
            .take(limit)
            .map { it.channel }
    }

    private class Scored(val channel: GuideChannel, val score: Int)

    private fun score(wanted: List<String>, wantedText: String, other: List<String>): Int {
        if (other.isEmpty()) return 0
        val otherText = other.joinToString(" ")
        return when {
            otherText == wantedText -> EXACT
            wanted.size == other.size && wanted.toSet() == other.toSet() -> SAME_WORDS
            otherText.startsWith(wantedText) || wantedText.startsWith(otherText) -> PREFIX
            wantedText in otherText || otherText in wantedText -> CONTAINS
            else -> {
                val common = wanted.count { it in other }
                if (common == 0) 0 else WORDS * common / maxOf(wanted.size, other.size)
            }
        }
    }

    private fun words(name: String): List<String> {
        val withoutCountry = COUNTRY_PREFIX.replace(name.trim().lowercase(), "")
        return SEPARATORS.split(withoutCountry).filter { it.isNotEmpty() && it !in QUALITY }
    }

    private const val EXACT = 100
    private const val SAME_WORDS = 90
    private const val PREFIX = 80
    private const val KEY_MATCH = 70
    private const val CONTAINS = 60
    private const val WORDS = 50
    private const val KEY_PENALTY = 5

    /** Oznaka države ispred naziva: „RS: ”, „|RS| ”, „[RS] ”, „(RS) ”. */
    private val COUNTRY_PREFIX = Regex("""^(?:[\[(|]\s*\p{L}{2,3}\s*[\])|]|\p{L}{2,3}\s*[:|])\s*""")
    private val SEPARATORS = Regex("""[^\p{L}\p{N}]+""")
    private val QUALITY = setOf("hd", "fhd", "uhd", "sd", "4k", "8k", "hevc", "h264", "h265", "hq", "raw")
}
