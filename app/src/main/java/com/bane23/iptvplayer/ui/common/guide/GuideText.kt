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

/** Red „Sada: …” za plejer, ili null ako nema podataka. */
@Composable
fun NowNext.nowLine(): String? = now?.let { stringResource(R.string.epg_now, it.timeRange(), it.title) }

/** Red „Sledeće: …” za plejer, ili null ako nema podataka. */
@Composable
fun NowNext.nextLine(): String? = next?.let { stringResource(R.string.epg_next, formatTime(it.startMs), it.title) }

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
