package com.bane23.iptvplayer.data.repo

/** Redosled filmova, odnosno serija, u kategoriji. */
enum class VodSort {
    /** Onako kako ih šalje provajder. */
    DEFAULT,

    /** Najskorije dodati prvi. */
    ADDED,
    NAME,

    /** Najbolje ocenjeni prvi. */
    RATING,

    /** Najnoviji po godini prvi. */
    YEAR,
    ;

    companion object {
        fun of(name: String?): VodSort = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

private val yearInName = Regex("\\((19\\d{2}|20\\d{2})\\)")

/** Godina iz podataka panela, a kad je nema, iz naziva („Naziv (2021)”); 0 kad se ne zna. */
internal fun VodItem.sortYear(): Int =
    if (year > 0) year else yearInName.findAll(name).lastOrNull()?.groupValues?.get(1)?.toIntOrNull() ?: 0

/**
 * Slaže spisak po [sort]. Stavke bez podatka (ocene, godine, datuma) idu na kraj, a među jednakima
 * ostaje redosled provajdera.
 */
fun sortVodItems(items: List<VodItem>, sort: VodSort): List<VodItem> = when (sort) {
    VodSort.DEFAULT -> items
    VodSort.ADDED -> items.sortedByDescending { it.added }
    VodSort.NAME -> {
        val collator = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("sr-Latn"))
        items.sortedWith { a, b -> collator.compare(a.name.trim(), b.name.trim()) }
    }
    VodSort.RATING -> items.sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
    VodSort.YEAR -> items.map { it to it.sortYear() }.sortedByDescending { it.second }.map { it.first }
}
