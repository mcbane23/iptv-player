package com.bane23.iptvplayer.domain

/**
 * Štednja mobilnih podataka: među verzijama istog kanala (isti kanal u drugom kvalitetu) bira onu
 * koja troši manje. Provajderi šalju svaki kanal u jednom, stalnom kvalitetu, pa aplikacija ne može
 * da smanji kvalitet strima; može samo da pusti drugu verziju. Rezolucija se zna tek kad se verzija
 * jednom pusti ili izmeri, jer oznake u nazivima (4K, HD) često nisu tačne.
 */
object ChannelVariants {
    /** Verzija kanala. [height] i [bitrate] su 0 dok nisu izmereni. */
    data class Option(val id: Long, val height: Int = 0, val bitrate: Int = 0)

    /** Rezolucija koja se traži na mobilnim podacima. */
    const val TARGET_HEIGHT = 720

    /** Slika malo niža od 720 (npr. 1280 × 696) se i dalje računa kao 720p. */
    const val MIN_TARGET_HEIGHT = 650

    /**
     * Verzija koju treba pustiti umesto izabrane ([chosenId]). Redom:
     * izabrana, ako je poznato da je 720p ili niža; poznata verzija od 720p (pri više njih ona sa
     * manjim protokom); najniža poznata verzija iznad 720p, ako je niža od izabrane; inače izabrana.
     * Kad se rezolucija izabrane ne zna, a nijedna druga nije 720p, ostaje izabrana.
     */
    fun pick(options: List<Option>, chosenId: Long): Long {
        val chosen = options.firstOrNull { it.id == chosenId } ?: return chosenId
        if (chosen.height in 1..TARGET_HEIGHT) return chosenId
        val target = options
            .filter { it.height in MIN_TARGET_HEIGHT..TARGET_HEIGHT }
            // Nepoznat protok ide posle poznatog.
            .minWithOrNull(compareBy<Option>({ -it.height }, { if (it.bitrate > 0) it.bitrate else Int.MAX_VALUE }))
        if (target != null) return target.id
        if (chosen.height <= 0) return chosenId
        return options.filter { it.height > TARGET_HEIGHT && it.height < chosen.height }.minByOrNull { it.height }?.id ?: chosenId
    }

    /** Oznaka rezolucije za prikaz („720p”), ili null kad nije izmerena. */
    fun heightLabel(height: Int): String? = if (height > 0) "${height}p" else null
}
