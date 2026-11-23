package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.db.RecordingEntity
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.recording.RecordingsViewModel
import com.bane23.iptvplayer.ui.common.recording.details
import com.bane23.iptvplayer.ui.common.recording.label
import com.bane23.iptvplayer.ui.common.recording.playable

/** Snimci na telefonu: dodir pušta snimak, ikonice zaustavljaju snimanje ili brišu snimak. */
@Composable
fun MobileRecordingsScreen(
    onPlay: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: RecordingsViewModel = hiltViewModel(),
) {
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<RecordingEntity?>(null) }
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    var pickLocation by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshLocations() }

    AppScaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SectionTopBar(title = stringResource(R.string.recordings_title), onBack = onBack)
            storage?.let {
                Text(
                    text = it.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            // Izbor mesta ima smisla samo kad je priključena SD kartica ili USB memorija.
            if (locations.size > 1) {
                val selected = locations.indexOfFirst { it.selected }.coerceAtLeast(0)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pickLocation = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.recording_location),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = locations[selected].label(selected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            val list = recordings
            when {
                list == null -> Unit

                list.isEmpty() -> Text(
                    text = stringResource(R.string.recordings_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { recording ->
                        val recordingNow = recording.status == RecordingEntity.STATUS_RECORDING
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = recording.playable) { onPlay(recording.id) }
                                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = recording.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = recording.details(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (recordingNow) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (recordingNow) {
                                TextButton(onClick = { viewModel.stop(recording.id) }) {
                                    Text(stringResource(R.string.recording_stop))
                                }
                            }
                            ToolbarIcon(AppIcons.Delete, stringResource(R.string.action_delete)) { pendingDelete = recording }
                        }
                    }
                }
            }
        }
    }

    if (pickLocation) {
        TrackDialog(
            title = stringResource(R.string.recording_location),
            options = locations.mapIndexed { index, location -> location.label(index) to location.selected },
            emptyText = null,
            onSelect = { index -> locations.getOrNull(index)?.let { viewModel.setLocation(it.path) } },
            onDismiss = { pickLocation = false },
        )
    }

    pendingDelete?.let { recording ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.recording_delete_title)) },
            text = { Text(stringResource(R.string.recording_delete_message, recording.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(recording.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
