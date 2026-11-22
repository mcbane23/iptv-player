package com.bane23.iptvplayer.ui.common.guide

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.EpgStatus
import com.bane23.iptvplayer.domain.NowNext
import com.bane23.iptvplayer.domain.Program
import java.text.DateFormat
import java.util.Date

/** Vreme u obliku koji je podešen na uređaju (npr. 20:15). */
fun formatTime(timeMs: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timeMs))

/** Dan i vreme, za početak prozora vodiča (npr. ned 20:00). */
fun formatDayTime(timeMs: Long): String {
    val date = Date(timeMs)
    val day = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(date)
    return "$day ${formatTime(timeMs)}"
}

fun Program.timeRange(): String = "${formatTime(startMs)} – ${formatTime(stopMs)}"

/** Naziv tekuće emisije i do kada traje (npr. „Dnevnik · do 21:00”), za red kanala u listi. */
@Composable
fun nowUntil(title: String, stopMs: Long?): String =
    if (stopMs == null) title else stringResource(R.string.epg_until, title, formatTime(stopMs))

/** Vreme emisije i koliko je još ostalo (npr. „20:30 – 21:00 · još 18 min”). Bez ostatka kad emisija ne traje. */
@Composable
fun Program.timeRangeLeft(nowMs: Long): String {
    val left = ((stopMs - nowMs + 59_999) / 60_000).toInt()
    return if (nowMs < startMs || left <= 0) timeRange() else stringResource(R.string.epg_time_left, timeRange(), left)
}

/** Red „Sada: …” za plejer, ili null ako nema podataka. */
@Composable
fun NowNext.nowLine(): String? = now?.let { stringResource(R.string.epg_now, it.timeRange(), it.title) }

/** Red „Sledeće: …” za plejer, ili null ako nema podataka. */
@Composable
fun NowNext.nextLine(): String? = next?.let { stringResource(R.string.epg_next, formatTime(it.startMs), it.title) }

/**
 * Koliko je minuta emisije prošlo i koliko još traje, u trenutku [now]. Preostalo se zaokružuje
 * naviše, da pred kraj ne piše „još 0 min”. Null kad emisija u tom trenutku ne traje.
 */
fun programMinutes(startMs: Long, stopMs: Long, now: Long): Pair<Int, Int>? {
    if (now < startMs || now >= stopMs) return null
    val elapsed = ((now - startMs) / 60_000L).toInt()
    val left = ((stopMs - now + 59_999L) / 60_000L).toInt()
    return elapsed to left
}

/**
 * Tekst ispod trake emisije u plejeru: „Prošlo 25 min, još 35 min”, a u drugom redu „Sledeće: …”.
 * Null kad nema nijednog od ta dva podatka.
 */
@Composable
fun NowNext.infoLines(nowMs: Long): String? {
    val progress = now?.let { programMinutes(it.startMs, it.stopMs, nowMs) }
        ?.let { (elapsed, left) -> stringResource(R.string.epg_progress, elapsed, left) }
    return listOfNotNull(progress, nextLine()).takeIf { it.isNotEmpty() }?.joinToString("\n")
}

/** Poruka o stanju vodiča koja se prikazuje ispod naslova ekrana. */
@Composable
fun EpgStatus.message(): String = when {
    loading -> stringResource(R.string.guide_loading)
    noSource -> stringResource(R.string.guide_no_source)
    error != null -> stringResource(R.string.guide_failed, error)
    refreshedAt != null -> stringResource(
        R.string.guide_refreshed_at,
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(refreshedAt)),
    )
    else -> stringResource(R.string.guide_not_loaded)
}
