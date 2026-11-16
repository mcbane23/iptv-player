package com.bane23.iptvplayer.ui.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.cast.CastMedia
import com.bane23.iptvplayer.ui.common.cast.CastPickerViewModel
import com.bane23.iptvplayer.ui.common.cast.CastRemoteViewModel
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.formatDuration
import kotlinx.coroutines.launch

/**
 * Dugme za cast u plejeru. Vraća akciju koja otvara izbor uređaja, ili null kad Google Cast nije
 * dostupan na telefonu (tada se dugme ne prikazuje). [media] daje ono što se trenutno gleda, a
 * [onStarted] se poziva kad puštanje na TV-u krene.
 */
@Composable
fun rememberCastLauncher(media: suspend () -> CastMedia?, onStarted: () -> Unit): (() -> Unit)? {
    val picker: CastPickerViewModel = hiltViewModel()
    val state by picker.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    if (open) {
        MobileCastDialog(
            media = media,
            onStarted = {
                open = false
                onStarted()
            },
            onDismiss = { open = false },
            viewModel = picker,
        )
    }
    return if (state.available) ({ open = true }) else null
}

/** Izbor uređaja: spisak Chromecast uređaja u mreži. Ako je veza već uspostavljena, odmah pušta. */
@Composable
private fun MobileCastDialog(
    media: suspend () -> CastMedia?,
    onStarted: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: CastPickerViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var requested by remember { mutableStateOf(false) }
    var noSource by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.startDiscovery()
        onDispose { viewModel.stopDiscovery() }
    }
    LaunchedEffect(Unit) {
        if (viewModel.state.value.deviceName != null) {
            val current = media()
            if (current == null) {
                noSource = true
            } else {
                viewModel.play(current)
                onStarted()
            }
        }
    }
    LaunchedEffect(state.deviceName, requested) {
        if (requested && state.deviceName != null) onStarted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cast_pick_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    noSource -> Text(stringResource(R.string.cast_no_source), color = MaterialTheme.colorScheme.error)

                    state.connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(stringResource(R.string.cast_connecting), modifier = Modifier.padding(start = 12.dp))
                    }

                    else -> {
                        if (state.connectFailed) {
                            Text(stringResource(R.string.cast_connect_failed), color = MaterialTheme.colorScheme.error)
                        }
                        if (state.routes.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text(stringResource(R.string.cast_searching), modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                        state.routes.forEach { route ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            val current = media()
                                            if (current == null) {
                                                noSource = true
                                            } else {
                                                requested = true
                                                viewModel.connect(route.id, current)
                                            }
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(imageVector = AppIcons.Cast, contentDescription = null)
                                Text(
                                    text = route.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.cast_search_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Daljinski dok se sadržaj pušta na TV-u. Nazad zaustavlja puštanje i prekida vezu. */
@Composable
fun MobileCastRemoteScreen(onClose: () -> Unit, viewModel: CastRemoteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val media = state.media
    val stop = {
        viewModel.stop()
        onClose()
    }
    // Veza je prekinuta (i sa TV-a ili gašenjem uređaja): daljinski više nema čime da upravlja.
    LaunchedEffect(state.deviceName) {
        if (state.deviceName == null) onClose()
    }
    BackHandler(onBack = stop)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SectionTopBar(title = stringResource(R.string.cast_remote_title), onBack = stop)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = AppIcons.Cast,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = media?.title.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        state.failed -> stringResource(R.string.cast_failed)
                        state.buffering -> stringResource(R.string.cast_loading)
                        else -> stringResource(R.string.cast_playing_on, state.deviceName.orEmpty())
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                val live = media?.live != false
                if (!live && state.durationMs > 0) {
                    var drag by remember { mutableStateOf<Float?>(null) }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = drag ?: state.positionMs.toFloat().coerceIn(0f, state.durationMs.toFloat()),
                            onValueChange = { drag = it },
                            onValueChangeFinished = {
                                drag?.let { viewModel.seekTo(it.toLong()) }
                                drag = null
                            },
                            valueRange = 0f..state.durationMs.toFloat(),
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatDuration(drag?.toLong() ?: state.positionMs), style = MaterialTheme.typography.bodySmall)
                            Text(formatDuration(state.durationMs), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    val stepSeconds = (viewModel.seekStepMs / 1_000).toInt()
                    if (live) {
                        if (media?.channelId != null) {
                            RemoteButton(AppIcons.Previous, stringResource(R.string.player_previous), viewModel::previous)
                        }
                    } else {
                        RemoteButton(AppIcons.FastRewind, stringResource(R.string.vod_seek_back, stepSeconds)) {
                            viewModel.seekBy(-viewModel.seekStepMs)
                        }
                    }
                    FilledIconButton(
                        onClick = { if (state.failed) viewModel.retry() else viewModel.togglePlayPause() },
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            imageVector = if (state.playing) AppIcons.Pause else AppIcons.Play,
                            contentDescription = stringResource(
                                when {
                                    state.failed -> R.string.player_retry
                                    state.playing -> R.string.player_pause
                                    else -> R.string.player_play
                                },
                            ),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    if (live) {
                        if (media?.channelId != null) {
                            RemoteButton(AppIcons.Next, stringResource(R.string.player_next), viewModel::next)
                        }
                    } else {
                        RemoteButton(AppIcons.FastForward, stringResource(R.string.vod_seek_forward, stepSeconds)) {
                            viewModel.seekBy(viewModel.seekStepMs)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    RemoteButton(AppIcons.VolumeDown, stringResource(R.string.cast_volume_down), viewModel::volumeDown)
                    RemoteButton(AppIcons.VolumeUp, stringResource(R.string.cast_volume_up), viewModel::volumeUp)
                }

                OutlinedButton(onClick = stop) { Text(stringResource(R.string.cast_stop)) }
            }
        }
    }
}

@Composable
private fun RemoteButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(imageVector = icon, contentDescription = description)
    }
}
