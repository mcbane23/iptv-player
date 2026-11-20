package com.bane23.iptvplayer.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.domain.Program
import com.bane23.iptvplayer.ui.common.guide.GuideViewModel
import com.bane23.iptvplayer.ui.common.guide.formatTime
import com.bane23.iptvplayer.ui.common.guide.rememberChannelSchedule
import com.bane23.iptvplayer.ui.common.guide.scheduleActions
import com.bane23.iptvplayer.ui.common.guide.scheduleMark
import com.bane23.iptvplayer.ui.common.recording.RecordingNotices
import com.bane23.iptvplayer.ui.common.rememberNow
import kotlinx.coroutines.delay

/**
 * Panel „Program” u plejeru na TV-u: raspored kanala koji se gleda, bez izlaska iz slike. OK na
 * emisiji nudi podsetnik i snimanje; prvi red otvara arhivu kad je kanal ima. Nazad zatvara panel.
 */
@Composable
fun TvChannelSchedulePanel(
    channel: Channel,
    onArchive: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    guide: GuideViewModel = hiltViewModel(),
) {
    val programs by rememberChannelSchedule(channel, guide)
    val reminders by guide.reminders.collectAsStateWithLifecycle()
    val recordings by guide.recordings.collectAsStateWithLifecycle()
    val now by rememberNow()
    RecordingNotices(guide.recordingNotice)
    var picked by remember(channel.id) { mutableStateOf<Program?>(null) }
    val firstFocus = remember { FocusRequester() }
    // Fokus ulazi u panel kad se otvori i vraća se u spisak kad se zatvori izbor radnje.
    LaunchedEffect(programs != null, picked == null) {
        if (picked != null) return@LaunchedEffect
        repeat(FOCUS_ATTEMPTS) {
            if (firstFocus.tryRequestFocus()) return@LaunchedEffect
            delay(FOCUS_STEP_MS)
        }
    }

    picked?.let { program ->
        val actions = scheduleActions(channel, program, now, reminders, recordings, guide)
        BackHandler { picked = null }
        TvChoicePanel(
            title = program.title,
            options = actions.map { it.label },
            onSelect = { index -> actions[index].run() },
            onClose = { picked = null },
            modifier = modifier,
        )
        return
    }

    val list = programs
    val hasArchive = channel.archiveDays != null
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .tvKeepFocus()
            .fillMaxHeight()
            .fillMaxWidth(0.4f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(start = 16.dp, end = 48.dp, top = 27.dp, bottom = 27.dp),
    ) {
        item {
            Text(
                text = channel.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
        }
        if (hasArchive) {
            item {
                TvTextRow(
                    text = "↺  " + stringResource(R.string.archive_title),
                    onClick = {
                        onClose()
                        onArchive()
                    },
                    modifier = Modifier.focusRequester(firstFocus),
                )
            }
        }
        if (list != null && list.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.guide_no_data),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        items(list.orEmpty(), key = { it.startMs }) { program ->
            val current = program.startMs <= now
            val first = !hasArchive && program === list?.firstOrNull()
            TvListRow(
                onClick = { picked = program },
                selected = current,
                modifier = if (first) Modifier.focusRequester(firstFocus) else Modifier,
            ) { color ->
                Text(
                    text = formatTime(program.startMs),
                    color = color.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.width(64.dp),
                )
                Text(
                    text = program.title,
                    color = color,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                scheduleMark(channel, program, now, reminders, recordings)?.let { mark ->
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        Text(text = mark, color = color, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private const val FOCUS_ATTEMPTS = 10
private const val FOCUS_STEP_MS = 50L
