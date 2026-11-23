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
 * Filter kanala u Uživo: važi u svim grupama dok ga korisnik ne poništi. Za razliku od pravila,
 * sklanja i omiljene kanale koji mu ne odgovaraju.
 */
data class ChannelFilter(
    /** Najmanji kvalitet iz naziva: 0 = svi, 1 = HD i bolje, 2 = FHD i bolje, 3 = samo 4K. */
    val minQuality: Int = 0,
    /** Oznaka zemlje ispred naziva kanala ili kategorije („RS”); null znači sve. */
    val country: String? = null,
) {
    val count: Int get() = (if (minQuality > 0) 1 else 0) + (if (country != null) 1 else 0)
    val active: Boolean get() = count > 0

    /**
     * Da li filter sklanja kanal kvaliteta [quality] iz zemlje [channelCountry] (ISO oznaka ili null).
     * Isto pravilo kao naredba `ChannelDao.applyFilter`; koristi se pri uvozu liste, da kanali budu
     * upisani već filtrirani.
     */
    fun hides(quality: Int, channelCountry: String?): Boolean {
        val wanted = ChannelRules.countryCode(country)
        return (minQuality > 0 && quality < minQuality) || (wanted != null && channelCountry != wanted)
    }
}

/**
 * Koje kanale pravila sklanjaju iz spiskova. Sve se čita iz naziva kanala, jer liste kvalitet i
 * pomak ne šalju kao poseban podatak.
 */
object ChannelRules {
    /** [kept]: kanal je među omiljenima, pa ga pravila ne sklanjaju. Redosled je redosled u listi. */
    data class Row(
        val id: Long,
        val playlistId: Long,
        val name: String,
        val kept: Boolean = false,
        /** Naziv kategorije kanala, za oznaku zemlje kad je nema u nazivu kanala. */
        val category: String? = null,
    )

    private val countryPrefix = Regex("^[\\s\\[(|*#•★-]*([A-Z]{2,3})\\s*(?:[:|\\])•★]|\\s[-–]\\s)")

    /** Najmanje kanala sa istom oznakom da bi se ona ponudila kao zemlja. */
    const val MIN_COUNTRY_CHANNELS = 5

    /** Oznake zemalja po ISO 3166 (dva slova), bez onih koje u listama kanala gotovo nikad nisu zemlja. */
    private val isoCountries: Set<String> = java.util.Locale.getISOCountries().toSet() - setOf("LA", "NA", "TV", "FM", "IO", "PM", "AM", "AQ")

    /** Oznake sa tri slova („SWE”, „NOR”) i uobičajene oznake van standarda, svedene na dva slova. */
    private val countryAliases: Map<String, String> = buildMap {
        for (code in java.util.Locale.getISOCountries()) {
            val three = runCatching { java.util.Locale("", code).isO3Country }.getOrNull()
            if (!three.isNullOrEmpty() && code in isoCountries) put(three, code)
        }
        put("UK", "GB")
        put("SR", "RS")
        put("SRB", "RS")
        put("GER", "DE")
        put("CRO", "HR")
        put("SLO", "SI")
        put("BIH", "BA")
        put("MNE", "ME")
        put("MKD", "MK")
        put("NED", "NL")
        put("SUI", "CH")
        put("POR", "PT")
        put("GRE", "GR")
        put("DEN", "DK")
        put("BUL", "BG")
        put("USA", "US")
    }

    /**
     * Oznaka svedena na ISO oznaku zemlje sa dva slova, ili null kad nije zemlja (naziv paketa, lige
     * ili jezika: „SKY”, „NBA”, „LAT”).
     */
    fun countryCode(tag: String?): String? {
        val text = tag?.trim()?.uppercase() ?: return null
        return countryAliases[text] ?: text.takeIf { it in isoCountries }
    }

    /** Oznaka od dva ili tri velika slova ispred teksta („NL - …”, „[EN] …”), i kad nije zemlja. */
    fun prefixTag(text: String?): String? = text?.let { countryPrefix.find(it)?.groupValues?.get(1) }

    private fun prefix(text: String?): String? = prefixTag(text)?.let { countryCode(it) }

    /**
     * ISO oznaka zemlje ispred naziva kanala („RS: Prvi kanal”, „[RS] …”, „RS | …”), a ako je nema,
     * ispred naziva kategorije. Oznake koje nisu zemlje se ne računaju.
     */
    fun country(name: String, category: String? = null): String? = prefix(name) ?: prefix(category)

    /** Zemlje koje lista zaista koristi, po oznaci. Ekran ih ređa po nazivu na jeziku aplikacije. */
    fun countries(rows: List<Row>): List<String> =
        rows.mapNotNull { country(it.name, it.category) }.groupingBy { it }.eachCount()
            .filter { it.value >= MIN_COUNTRY_CHANNELS }.keys.sorted()

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

    private val variantTags = Regex("$EDGE_BEFORE(RAW|VIP)$EDGE_AFTER")
    private val leadingCode = Regex("^\\s*([A-Z]{2,3})\\s+")

    /**
     * Ključ po kome se prepoznaje isti kanal u drugom kvalitetu: zemlja i naziv bez oznake zemlje,
     * kvaliteta, kodeka i oznaka RAW i VIP. „RS: Sport Uno HD” i „SR| Sport Uno VIP RAW” daju isti ključ.
     * Oznaka zemlje se skida i kad stoji bez znaka iza sebe („RS Sport Uno”), ako je to zemlja kanala.
     */
    fun variantKey(name: String, category: String? = null): String {
        val country = country(name, category)
        val marked = countryPrefix.find(name)?.takeIf { countryCode(it.groupValues[1]) != null }
        val rest = when {
            marked != null -> name.substring(marked.range.last + 1)
            country != null -> leadingCode.find(name)?.takeIf { countryCode(it.groupValues[1]) == country }
                ?.let { name.substring(it.range.last + 1) } ?: name
            else -> name
        }
        val base = upper(rest).replace(tags, " ").replace(variantTags, " ").replace(separators, " ").trim()
        return country.orEmpty() + "|" + base.ifEmpty { upper(name).trim() }
    }

    /** Kanal sa vremenskim pomakom: u nazivu stoji „+1” kao zasebna oznaka („RTL +1”, ali ne „Canal+ 1”). */
    fun isPlusOne(name: String): Boolean = plusOne.containsMatchIn(upper(name))

    /** ID-jevi kanala koje sklanjaju [rules] i [filter]. */
    fun hidden(rows: List<Row>, rules: ChannelRuleSettings, filter: ChannelFilter = ChannelFilter()): Set<Long> {
        if (!rules.any && !filter.active) return emptySet()
        val hidden = HashSet<Long>()
        var shown = rows
        if (filter.active) {
            // Ranije sačuvana oznaka koja nije zemlja više ne filtrira ništa.
            val wanted = countryCode(filter.country)
            shown = shown.filter { row ->
                val stays = (filter.minQuality == 0 || quality(row.name) >= filter.minQuality) &&
                    (wanted == null || country(row.name, row.category) == wanted)
                stays.also { if (!it) hidden += row.id }
            }
        }
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
