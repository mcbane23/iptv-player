package com.bane23.iptvplayer.ui.common.trakt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.trakt.PlaybackLogEntry
import com.bane23.iptvplayer.data.trakt.PlaybackLogEvent
import java.text.DateFormat
import java.util.Date

/** Dnevnik razmene pozicija sa Trakt-om: naslov, objašnjenje i poslednjih [limit] događaja, od najnovijeg. */
@Composable
fun TraktPlaybackLogList(entries: List<PlaybackLogEntry>, limit: Int, style: TextStyle, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.trakt_log_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(
            text = stringResource(R.string.trakt_log_summary),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entries.isEmpty()) {
            Text(text = stringResource(R.string.trakt_log_empty), style = style, color = MaterialTheme.colorScheme.onBackground)
        }
        for (entry in entries.take(limit)) {
            Text(text = "${logTime(entry.atMs)}  ${entry.text()}", style = style, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

private fun logTime(millis: Long): String = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(millis))

@Composable
private fun PlaybackLogEntry.text(): String {
    val skip = when (event) {
        PlaybackLogEvent.SKIP_NO_ITEM -> R.string.trakt_log_reason_no_item
        PlaybackLogEvent.SKIP_NO_EPISODE -> R.string.trakt_log_reason_no_episode
        PlaybackLogEvent.SKIP_NEWER_HERE -> R.string.trakt_log_reason_newer_here
        PlaybackLogEvent.SKIP_SAME -> R.string.trakt_log_reason_same
        PlaybackLogEvent.SKIP_FINISHED -> R.string.trakt_log_reason_finished
        PlaybackLogEvent.SKIP_DISMISSED -> R.string.trakt_log_reason_dismissed
        PlaybackLogEvent.SKIP_NO_DURATION -> R.string.trakt_log_reason_no_duration
        PlaybackLogEvent.SKIP_PANEL -> R.string.trakt_log_reason_panel
        else -> null
    }
    val text = when (event) {
        PlaybackLogEvent.SENT -> stringResource(R.string.trakt_log_sent, item, percent, count)
        PlaybackLogEvent.SEND_NO_TMDB -> stringResource(R.string.trakt_log_send_no_tmdb, item)
        PlaybackLogEvent.SEND_PANEL_WAIT -> stringResource(R.string.trakt_log_send_panel, item)
        PlaybackLogEvent.RECEIVED_LIST -> stringResource(R.string.trakt_log_received_list, count, percent)
        PlaybackLogEvent.APPLIED -> stringResource(R.string.trakt_log_applied, item, percent, count)
        PlaybackLogEvent.FAILED -> stringResource(R.string.trakt_log_failed)
        PlaybackLogEvent.PLAYER_START -> stringResource(
            when (percent) {
                1 -> R.string.trakt_log_player_start_synced
                0 -> R.string.trakt_log_player_start_late
                else -> R.string.trakt_log_player_start_no_trakt
            },
            item,
            com.bane23.iptvplayer.ui.common.player.formatDuration(count * 1000L),
        )
        else -> stringResource(R.string.trakt_log_skipped, item, percent, stringResource(skip ?: R.string.trakt_log_reason_panel))
    }
    return if (detail.isEmpty()) text else "$text\n$detail"
}
