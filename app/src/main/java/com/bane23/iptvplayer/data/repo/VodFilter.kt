package com.bane23.iptvplayer.data.repo

/** Godine izlaska po kojima se filtriraju filmovi i serije. */
enum class VodYears(val from: Int, val to: Int) {
    ALL(0, Int.MAX_VALUE),
    FROM_2020(2020, Int.MAX_VALUE),
    FROM_2010(2010, 2019),
    FROM_2000(2000, 2009),
    OLDER(1, 1999),
}

/**
 * Filter postera u kategoriji. Lista u spisku šalje samo godinu i ocenu, pa se filtrira po njima i
 * po oznaci 4K u nazivu. Stavka bez podatka (godine, ocene) ne prolazi filter koji taj podatak traži.
 */
data class VodFilter(
    val years: VodYears = VodYears.ALL,
    /** Najmanja ocena (6, 7 ili 8); 0 znači sve. */
    val minRating: Int = 0,
    val only4k: Boolean = false,
) {
    /** Koliko je uslova uključeno; 0 znači da filtera nema. */
    val count: Int get() = (if (years != VodYears.ALL) 1 else 0) + (if (minRating > 0) 1 else 0) + (if (only4k) 1 else 0)
    val active: Boolean get() = count > 0

    /** Zapis za podešavanja. */
    fun encode(): String = "${years.name}|$minRating|${if (only4k) 1 else 0}"

    companion object {
        val RATINGS = listOf(0, 6, 7, 8)

        fun decode(text: String?): VodFilter {
            val parts = text?.split('|') ?: return VodFilter()
            return VodFilter(
                years = VodYears.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: VodYears.ALL,
                minRating = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in RATINGS } ?: 0,
                only4k = parts.getOrNull(2) == "1",
            )
        }
    }
}

private val ultraInName = Regex("(?<![\\p{L}\\p{N}])(4K|UHD|2160P)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)

/** Stavke koje prolaze [filter], istim redom. */
fun filterVodItems(items: List<VodItem>, filter: VodFilter): List<VodItem> {
    if (!filter.active) return items
    return items.filter { item ->
        (filter.years == VodYears.ALL || item.sortYear() in filter.years.from..filter.years.to) &&
            (filter.minRating == 0 || (item.rating?.toDoubleOrNull() ?: 0.0) >= filter.minRating) &&
            (!filter.only4k || ultraInName.containsMatchIn(item.name))
    }
}
