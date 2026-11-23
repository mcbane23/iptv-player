package com.bane23.iptvplayer.ui.mobile

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.ui.common.guide.GuideViewModel
import com.bane23.iptvplayer.ui.common.guide.formatTime
import com.bane23.iptvplayer.ui.common.guide.rememberChannelSchedule
import com.bane23.iptvplayer.ui.common.guide.scheduleActions
import com.bane23.iptvplayer.ui.common.guide.scheduleMark
import com.bane23.iptvplayer.ui.common.recording.RecordingNotices
import com.bane23.iptvplayer.ui.common.rememberNow
import com.bane23.iptvplayer.ui.common.theme.Glass
import androidx.compose.foundation.border

/**
 * Panel „Program” u plejeru na telefonu: raspored kanala koji se gleda, preko slike. Dodir na
 * emisiju otvara ispod nje radnje (podsetnik, snimanje); prvi red otvara arhivu kad je kanal ima.
 * U položenom telefonu stoji uz desnu ivicu, u uspravnom pri dnu. Zatvara se dodirom van panela
 * ili tasterom Nazad (to rešava ekran plejera).
 */
@Composable
internal fun BoxScope.MobileChannelSchedulePanel(
    channel: Channel,
    onArchive: () -> Unit,
    guide: GuideViewModel = hiltViewModel(),
) {
    val programs by rememberChannelSchedule(channel, guide)
    val reminders by guide.reminders.collectAsStateWithLifecycle()
    val recordings by guide.recordings.collectAsStateWithLifecycle()
    val now by rememberNow()
    RecordingNotices(guide.recordingNotice)
    var openStart by remember(channel.id) { mutableStateOf<Long?>(null) }
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val list = programs
    LazyColumn(
        modifier = Modifier
            .align(if (portrait) Alignment.BottomCenter else Alignment.CenterEnd)
            .safeDrawingPadding()
            .padding(12.dp)
            .then(
                if (portrait) {
                    Modifier.fillMaxWidth().heightIn(max = 360.dp)
                } else {
                    Modifier.widthIn(max = 340.dp).fillMaxHeight()
                },
            )
            .clip(Glass.CardShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(1.dp, Glass.Border, Glass.CardShape)
            // Dodir na sam panel ne sme da ga zatvori.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(vertical = 8.dp),
    ) {
        item {
            Text(
                text = channel.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (channel.archiveDays != null) {
            item {
                Text(
                    text = "↺  " + stringResource(R.string.archive_title),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onArchive)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        if (list != null && list.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.guide_no_data),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        items(list.orEmpty(), key = { it.startMs }) { program ->
            val current = program.startMs <= now
            val open = openStart == program.startMs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openStart = if (open) null else program.startMs }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatTime(program.startMs),
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier.width(56.dp),
                    )
                    Text(
                        text = program.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = if (open) 3 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    scheduleMark(channel, program, now, reminders, recordings)?.let { mark ->
                        Text(
                            text = mark,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (open) {
                    Row(modifier = Modifier.padding(start = 44.dp)) {
                        scheduleActions(channel, program, now, reminders, recordings, guide).forEach { action ->
                            TextButton(onClick = action.run) { Text(action.label, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}
