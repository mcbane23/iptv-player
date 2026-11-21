package com.bane23.iptvplayer.ui.common

import coil3.compose.AsyncImagePainter
import coil3.network.HttpException

/**
 * Slike (logoi, posteri) koje nedavno nisu mogle da se učitaju. Bez ovoga se adresa koja ne radi
 * traži iznova svaki put kad red uđe u ekran, pa pri skrolovanju zauzima veze i usporava slike koje
 * rade. Živi samo u memoriji; posle isteka se adresa proba ponovo.
 */
object FailedImages {
    private const val MAX_ENTRIES = 2_000

    /** Server je odgovorio greškom (npr. 404): slike tamo skoro sigurno nema. */
    private const val REFUSED_MS = 30 * 60_000L

    /** Veza nije uspela: možda je samo mreža na tren pala, pa se uskoro proba ponovo. */
    private const val UNREACHABLE_MS = 2 * 60_000L

    /** Adresa i vreme do kog se ne traži ponovo; najstariji unos ispada kad se spisak napuni. */
    private val until = object : LinkedHashMap<String, Long>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > MAX_ENTRIES
    }

    @Synchronized
    fun contains(url: String?, now: Long = System.currentTimeMillis()): Boolean {
        val expires = until[url ?: return false] ?: return false
        if (expires > now) return true
        until.remove(url)
        return false
    }

    @Synchronized
    fun add(url: String?, refused: Boolean, now: Long = System.currentTimeMillis()) {
        if (url.isNullOrBlank()) return
        until[url] = now + if (refused) REFUSED_MS else UNREACHABLE_MS
    }

    /** Beleži neuspelo učitavanje slike [url] iz stanja koje javlja Coil. */
    fun add(url: String?, error: AsyncImagePainter.State.Error) = add(url, refused = error.result.throwable is HttpException)
}
