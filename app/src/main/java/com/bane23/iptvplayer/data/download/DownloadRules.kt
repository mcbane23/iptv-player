package com.bane23.iptvplayer.data.download

import com.bane23.iptvplayer.data.db.DownloadEntity

/** Mreža preko koje uređaj trenutno izlazi na internet. Kablovska mreža se računa kao Wi-Fi. */
enum class NetworkKind { NONE, MOBILE, WIFI }

/** Zašto preuzimanja trenutno stoje iako ih ima u redu. */
enum class DownloadHold { PLAYING, NO_WIFI, OFFLINE }

/** Šta se o preuzimanju prikazuje u spisku i na dugmetu „Preuzmi”. */
enum class DownloadState { DOWNLOADING, WAITING, HELD_PLAYING, HELD_WIFI, HELD_OFFLINE, PAUSED, DONE, FAILED }

/** Šta raditi sa odgovorom servera kad na uređaju već postoji deo fajla. */
sealed interface ResumePlan {
    /** Server nastavlja odakle se stalo: podaci se dodaju na kraj. [totalBytes] je 0 kad se ne zna. */
    data class Append(val totalBytes: Long) : ResumePlan

    /** Server šalje ceo fajl ispočetka: ono što postoji se briše. */
    data class Restart(val totalBytes: Long) : ResumePlan

    /** Fajl je već ceo. */
    data object Complete : ResumePlan

    /** Deo na uređaju ne odgovara fajlu na serveru: briše se i traži ispočetka. */
    data object StartOver : ResumePlan
}

object DownloadRules {

    /** Preuzimanje staje dok se nešto gleda, dok nema interneta i, bez dozvole, dok nema Wi-Fi mreže. */
    fun hold(playing: Boolean, network: NetworkKind, allowMobile: Boolean): DownloadHold? = when {
        playing -> DownloadHold.PLAYING
        network == NetworkKind.NONE -> DownloadHold.OFFLINE
        network == NetworkKind.MOBILE && !allowMobile -> DownloadHold.NO_WIFI
        else -> null
    }

    fun state(status: String, active: Boolean, hold: DownloadHold?): DownloadState = when (status) {
        DownloadEntity.STATUS_DONE -> DownloadState.DONE
        DownloadEntity.STATUS_PAUSED -> DownloadState.PAUSED
        DownloadEntity.STATUS_FAILED -> DownloadState.FAILED
        else -> when {
            hold == DownloadHold.PLAYING -> DownloadState.HELD_PLAYING
            hold == DownloadHold.NO_WIFI -> DownloadState.HELD_WIFI
            hold == DownloadHold.OFFLINE -> DownloadState.HELD_OFFLINE
            active -> DownloadState.DOWNLOADING
            else -> DownloadState.WAITING
        }
    }

    /**
     * Tumači odgovor na zahtev „pošalji od bajta [existing]”. [contentLength] je -1 kad ga server ne
     * šalje. Vraća null kad odgovor nije upotrebljiv (greška servera).
     */
    fun resumePlan(existing: Long, code: Int, contentLength: Long, contentRange: String?): ResumePlan? = when (code) {
        206 -> {
            val range = parseContentRange(contentRange)
            when {
                // Server je poslao neki drugi deo fajla: nadovezivanje bi pokvarilo fajl.
                range?.first != null && range.first != existing -> ResumePlan.StartOver
                range?.second != null -> ResumePlan.Append(range.second ?: 0)
                contentLength >= 0 -> ResumePlan.Append(existing + contentLength)
                else -> ResumePlan.Append(0)
            }
        }
        200 -> ResumePlan.Restart(contentLength.coerceAtLeast(0))
        416 -> {
            val total = parseContentRange(contentRange)?.second
            if (existing > 0 && total == existing) ResumePlan.Complete else if (existing > 0) ResumePlan.StartOver else null
        }
        else -> null
    }

    /** Iz „bytes 100-999/1000” vraća (100, 1000); iz „bytes [zvezdica]/1000” vraća (null, 1000). */
    internal fun parseContentRange(value: String?): Pair<Long?, Long?>? {
        val text = value?.trim()?.removePrefix("bytes")?.trim() ?: return null
        val slash = text.indexOf('/')
        if (slash < 0) return null
        val start = text.substring(0, slash).substringBefore('-').trim().toLongOrNull()
        val total = text.substring(slash + 1).trim().toLongOrNull()
        return start to total
    }

    /** Procenat preuzetog, ili null dok se veličina fajla ne zna. */
    fun percent(bytes: Long, total: Long): Int? =
        if (total <= 0) null else (bytes * 100 / total).toInt().coerceIn(0, 100)

    /** Naziv fajla na uređaju. Nastavak dolazi od provajdera, pa se iz njega uzimaju samo slova i cifre. */
    fun fileName(kind: String, playlistId: Long, itemId: Long, extension: String): String {
        val safe = extension.filter { it.isLetterOrDigit() }.take(8).lowercase().ifEmpty { "mp4" }
        return "${kind}_${playlistId}_$itemId.$safe"
    }

    /** Oznaka epizode u spisku, npr. „S1 E3”. */
    fun episodeLabel(season: Int, number: Int): String = "S$season E$number"
}
