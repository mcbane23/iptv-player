package com.bane23.iptvplayer.data.download

import com.bane23.iptvplayer.data.db.DownloadEntity
import com.bane23.iptvplayer.data.storage.PublicFolder
import com.bane23.iptvplayer.data.xtream.Episode
import com.bane23.iptvplayer.data.xtream.Season
import com.bane23.iptvplayer.data.xtream.SeriesDetails

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

    /** Nastavak fajla dolazi od provajdera, pa se iz njega uzimaju samo slova i cifre. */
    fun safeExtension(extension: String): String =
        extension.filter { it.isLetterOrDigit() }.take(8).lowercase().ifEmpty { "mp4" }

    /** Naziv fajla bez nastavka, čitljiv u upravniku fajlova: naslov i, za epizodu, njena oznaka. */
    fun readableName(title: String, subtitle: String?, kind: String, itemId: Long): String =
        PublicFolder.safeName(listOfNotNull(title, subtitle).joinToString(" "), fallback = "${kind}_$itemId")

    /** Oznaka epizode u spisku, npr. „S1 E3”. */
    fun episodeLabel(season: Int, number: Int): String = "S$season E$number"

    private val EPISODE_LABEL = Regex("""^S(\d+) E(\d+)$""")

    /** Sezona i broj epizode iz oznake koju pravi [episodeLabel], ili null kad oznaka nije takva. */
    fun parseEpisodeLabel(label: String?): Pair<Int, Int>? {
        val match = EPISODE_LABEL.matchEntire(label ?: return null) ?: return null
        val season = match.groupValues[1].toIntOrNull() ?: return null
        val number = match.groupValues[2].toIntOrNull() ?: return null
        return season to number
    }

    /**
     * Serija sastavljena samo od preuzetih epizoda, za gledanje bez interneta: kad spisak epizoda ne
     * može da se pročita sa servera, „Sledeća” i kartica na kraju epizode idu kroz ono što je na
     * uređaju. [rows] su do kraja preuzete epizode jedne serije; one bez čitljive oznake se preskaču.
     */
    fun offlineSeries(rows: List<DownloadEntity>): SeriesDetails? {
        val episodes = rows.mapNotNull { row ->
            val (season, number) = parseEpisodeLabel(row.subtitle) ?: return@mapNotNull null
            Episode(
                id = row.itemId,
                season = season,
                number = number,
                title = row.subtitle.orEmpty(),
                extension = row.extension,
                plot = null,
                image = null,
                durationSeconds = null,
            )
        }.distinctBy { it.id }.sortedWith(compareBy({ it.season }, { it.number }))
        if (episodes.isEmpty()) return null
        val first = rows.first()
        return SeriesDetails(
            name = first.title,
            poster = first.poster,
            plot = null,
            genre = null,
            cast = null,
            releaseDate = null,
            rating = null,
            seasons = episodes.groupBy { it.season }.map { (season, list) -> Season(season, list) },
        )
    }
}
