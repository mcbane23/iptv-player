package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.ui.common.guide.GuideCell
import com.bane23.iptvplayer.ui.common.guide.GuideSelection
import com.bane23.iptvplayer.ui.common.guide.GuideRecordings
import com.bane23.iptvplayer.ui.common.guide.GuideViewModel
import com.bane23.iptvplayer.ui.common.recording.RecordingNotices
import com.bane23.iptvplayer.ui.common.recording.rememberRecordingToggle
import com.bane23.iptvplayer.ui.common.guide.formatDayTime
import com.bane23.iptvplayer.ui.common.guide.formatTime
import com.bane23.iptvplayer.ui.common.guide.message
import com.bane23.iptvplayer.ui.common.guide.timeRange

/** Na telefonu se odjednom vidi sat i po programa. */
private const val WINDOW_MS = 90 * 60_000L

/** TV vodič na telefonu: za svaki kanal red emisija na zajedničkoj vremenskoj osi. */
@Composable
fun MobileGuideScreen(
    playlistId: Long,
    categoryId: Long?,
    onPlay: (Long) -> Unit,
    onArchive: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    LaunchedEffect(playlistId, categoryId) { viewModel.open(playlistId, categoryId) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    RecordingNotices(viewModel.recordingNotice)
    val channels = viewModel.channelList.collectAsLazyPagingItems()
    val now = System.currentTimeMillis()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SectionTopBar(title = stringResource(R.string.guide_title), onBack = onBack) {
                if (!status.loading) {
                    ToolbarIcon(AppIcons.Refresh, stringResource(R.string.action_refresh), viewModel::refresh)
                }
            }
            Text(
                text = status.message(),
                style = MaterialTheme.typography.bodySmall,
                color = if (status.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { viewModel.shift(-1) }) { Text(stringResource(R.string.guide_earlier)) }
                Text(
                    text = "${formatDayTime(ui.windowStart)} – ${formatTime(ui.windowStart + WINDOW_MS)}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                TextButton(onClick = viewModel::jumpToNow) { Text(stringResource(R.string.guide_now)) }
                TextButton(onClick = { viewModel.shift(1) }) { Text(stringResource(R.string.guide_later)) }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                    val channel = channels[index]
                    if (channel != null) {
                        GuideRow(
                            channel = channel,
                            windowStart = ui.windowStart,
                            reloadKey = status.refreshedAt,
                            now = now,
                            selection = ui.selection,
                            recordings = recordings,
                            reminders = reminders,
                            viewModel = viewModel,
                        )
                    } else {
                        Box(modifier = Modifier.height(ROW_HEIGHT + 28.dp))
                    }
                }
            }

            ui.selection?.let { selection ->
                val program = selection.program
                val marked = program != null && recordings.marks(selection.channel, program, now)
                val record = rememberRecordingToggle(isRecording = marked) {
                    if (program != null) viewModel.toggleRecording(selection.channel, program)
                }
                val hasReminder = program != null && GuideRecordings.key(selection.channel, program.startMs) in reminders
                // Podsetnik traži istu dozvolu za obaveštenja kao snimanje.
                val remind = rememberRecordingToggle(isRecording = hasReminder) {
                    if (program != null) viewModel.toggleReminder(selection.channel, program)
                }
                SelectionPanel(
                    selection = selection,
                    remindLabel = when {
                        program == null || program.startMs <= now -> null
                        hasReminder -> stringResource(R.string.reminder_remove)
                        else -> stringResource(R.string.reminder_add)
                    },
                    onRemind = remind,
                    // Snimanje se nudi za emisiju koja još nije završena.
                    recordLabel = when {
                        program == null || program.stopMs <= now -> null
                        marked && program.startMs > now -> stringResource(R.string.recording_schedule_cancel)
                        marked -> stringResource(R.string.recording_stop)
                        program.startMs > now -> stringResource(R.string.recording_schedule)
                        else -> stringResource(R.string.recording_chip)
                    },
                    onRecord = record,
                    onPlay = { onPlay(selection.channel.id) },
                    onArchive = { onArchive(selection.channel.id) },
                    onClose = viewModel::clearSelection,
                )
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    windowStart: Long,
    reloadKey: Long?,
    now: Long,
    selection: GuideSelection?,
    recordings: GuideRecordings,
    reminders: Set<String>,
    viewModel: GuideViewModel,
) {
    val cells by produceState(emptyList<GuideCell>(), channel.id, windowStart, reloadKey) {
        value = viewModel.row(channel.id, windowStart, WINDOW_MS)
    }
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            text = channel.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (cells.isEmpty()) {
                EmptyCell(Modifier.weight(1f), onClick = { viewModel.select(channel, null) })
            }
            for (cell in cells) {
                val program = cell.program
                if (program == null) {
                    EmptyCell(Modifier.weight(cell.weight), onClick = { viewModel.select(channel, null) })
                } else {
                    val live = program.startMs <= now && program.stopMs > now
                    val selected = selection?.channel?.id == channel.id && selection.program == program
                    val background = when {
                        selected -> MaterialTheme.colorScheme.primary
                        live -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val textColor = when {
                        selected -> MaterialTheme.colorScheme.onPrimary
                        live -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .weight(cell.weight)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(background)
                            .clickable { viewModel.select(channel, program) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = when {
                                recordings.marks(channel, program, now) -> "● ${program.title}"
                                GuideRecordings.key(channel, program.startMs) in reminders -> "🔔 ${program.title}"
                                else -> program.title
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCell(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
    )
}

/** Detalji izabrane emisije, dugme za gledanje kanala i, ako kanal ima arhivu, ulaz u nju. */
@Composable
private fun SelectionPanel(
    selection: GuideSelection,
    remindLabel: String?,
    onRemind: () -> Unit,
    recordLabel: String?,
    onRecord: () -> Unit,
    onPlay: () -> Unit,
    onArchive: () -> Unit,
    onClose: () -> Unit,
) {
    val program = selection.program
    Surface(tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = selection.channel.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = program?.title ?: stringResource(R.string.guide_no_data),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (program != null) {
                Text(
                    text = program.timeRange(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                program.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState(), reverseScrolling = true)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
                if (remindLabel != null) {
                    TextButton(onClick = onRemind) { Text(remindLabel, maxLines = 1) }
                }
                if (recordLabel != null) {
                    TextButton(onClick = onRecord) { Text(recordLabel, maxLines = 1) }
                }
                if (selection.hasArchive) {
                    TextButton(onClick = onArchive) { Text(stringResource(R.string.archive_title)) }
                }
                Button(onClick = onPlay) { Text(stringResource(R.string.guide_watch)) }
            }
        }
    }
}

private val ROW_HEIGHT = 52.dp
