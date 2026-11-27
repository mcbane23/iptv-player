package com.bane23.iptvplayer.ui.common.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.NowNext
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import com.bane23.iptvplayer.ui.common.guide.formatTime
import com.bane23.iptvplayer.ui.common.rememberClockText

/** Šta ekran pauze prikazuje preko zatamnjene slike. */
data class PauseInfo(
    /** Sitan red iznad naslova: „Pauzirano” i koliko je ostalo, ili naziv kanala. */
    val kicker: String,
    val title: String,
    /** Kratke oznake ispod naslova (ocena, godina, žanr, vreme emisije). */
    val meta: List<String> = emptyList(),
    val description: String? = null,
    /** Red na dnu, npr. šta sledi na kanalu. */
    val footer: String? = null,
)

/** Da li je ekran pauze uključen u Podešavanjima. */
@Composable
fun rememberPauseInfoEnabled(settingsViewModel: SettingsViewModel = hiltViewModel()): Boolean {
    val enabled by settingsViewModel.pauseInfo.collectAsStateWithLifecycle()
    return enabled
}

/** Ekran pauze za film, epizodu, arhivu ili snimak: naslov, opis i koliko je ostalo do kraja. */
@Composable
fun vodPauseInfo(
    title: String,
    positionMs: Long,
    durationMs: Long,
    meta: List<String>,
    description: String?,
): PauseInfo {
    val leftMinutes = PauseTime.minutesLeft(positionMs, durationMs)
    val kicker = when {
        leftMinutes == null -> stringResource(R.string.pause_paused)
        leftMinutes >= MINUTES_IN_HOUR -> stringResource(
            R.string.pause_left_hours,
            leftMinutes / MINUTES_IN_HOUR,
            leftMinutes % MINUTES_IN_HOUR,
        )
        else -> stringResource(R.string.pause_left_minutes, leftMinutes)
    }
    return PauseInfo(kicker = kicker, title = title, meta = meta, description = description)
}

/** Ekran pauze za kanal uživo: emisija koja je na programu sa opisom i šta sledi. */
@Composable
fun livePauseInfo(channelName: String, nowNext: NowNext): PauseInfo {
    val now = nowNext.now
    val next = nowNext.next
    return PauseInfo(
        kicker = stringResource(R.string.pause_on_channel, channelName),
        title = now?.title ?: channelName,
        meta = listOfNotNull(now?.let { "${formatTime(it.startMs)} – ${formatTime(it.stopMs)}" }),
        description = now?.description,
        footer = next?.let { stringResource(R.string.pause_next, formatTime(it.startMs), it.title) },
    )
}

/** Računanje vremena za ekran pauze, odvojeno da može da se testira. */
object PauseTime {
    /** Minuti do kraja, zaokruženi naviše; null kad trajanje nije poznato ili je sadržaj pri kraju. */
    fun minutesLeft(positionMs: Long, durationMs: Long): Int? {
        if (durationMs <= 0) return null
        val leftMs = durationMs - positionMs
        if (leftMs <= 0) return null
        return ((leftMs + MINUTE_MS - 1) / MINUTE_MS).toInt()
    }

    private const val MINUTE_MS = 60_000L
}

private const val MINUTES_IN_HOUR = 60

/**
 * Ekran pauze za kanal uživo: sat u uglu, a dole levo naslov, oznake i opis (film i epizoda imaju
 * [PauseScreensaver]). Crta se preko zatamnjene slike umesto
 * kontrola; isti je na telefonu i TV-u, [compact] ga smanjuje za telefon.
 */
@Composable
fun BoxScope.PauseInfoScreen(info: PauseInfo, compact: Boolean) {
    val clock by rememberClockText()
    val edge = if (compact) 24.dp else 48.dp
    val top = if (compact) 20.dp else 27.dp
    BasicText(
        text = clock,
        style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = if (compact) 18.sp else 24.sp),
        maxLines = 1,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = edge, top = top),
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth(if (compact) 0.92f else 0.62f)
            .padding(start = edge, bottom = if (compact) 28.dp else 54.dp),
    ) {
        PauseInfoTexts(info, compact)
    }
}

/** Tekst ekrana pauze jedan ispod drugog (zove se u koloni); [smallTitle] je za užu pločicu sa posterom: red iznad naslova, naslov, oznake, opis i red na dnu. */
@Composable
internal fun PauseInfoTexts(info: PauseInfo, compact: Boolean, smallTitle: Boolean = false) {
    BasicText(
        text = info.kicker,
        style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = if (compact) 13.sp else 15.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    BasicText(
        text = info.title,
        style = TextStyle(
            color = Color.White,
            fontSize = when {
                compact && smallTitle -> 20.sp
                compact -> 24.sp
                smallTitle -> 27.sp
                else -> 34.sp
            },
            lineHeight = when {
                compact && smallTitle -> 25.sp
                compact -> 29.sp
                smallTitle -> 33.sp
                else -> 40.sp
            },
            fontWeight = FontWeight.SemiBold,
        ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    if (info.meta.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            info.meta.take(MAX_META).forEach { label ->
                BasicText(
                    text = label,
                    style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = if (compact) 12.sp else 14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
    info.description?.takeIf { it.isNotBlank() }?.let { text ->
        BasicText(
            text = text.trim(),
            style = TextStyle(
                color = Color.White.copy(alpha = 0.85f),
                fontSize = if (compact) 14.sp else 17.sp,
                lineHeight = if (compact) 19.sp else 24.sp,
            ),
            maxLines = if (compact) 3 else 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
    info.footer?.let { text ->
        BasicText(
            text = text,
            style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = if (compact) 13.sp else 15.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val MAX_META = 3
