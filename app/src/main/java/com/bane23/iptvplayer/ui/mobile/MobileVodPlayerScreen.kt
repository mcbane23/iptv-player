package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.ui.common.player.KeepScreenOn
import com.bane23.iptvplayer.ui.common.player.VideoSurface
import com.bane23.iptvplayer.ui.common.player.formatDuration
import com.bane23.iptvplayer.ui.common.player.label
import com.bane23.iptvplayer.ui.common.player.statusMessage
import com.bane23.iptvplayer.ui.common.vod.VodPlayerUiState
import com.bane23.iptvplayer.ui.common.vod.VodPlayerViewModel
import com.bane23.iptvplayer.ui.common.vod.episodeLabel
import kotlinx.coroutines.delay

private enum class VodPanel { NONE, AUDIO, SUBTITLES }

/**
 * Plejer za film ili epizodu na telefonu. [start] pokreće puštanje (poziva se jednom);
 * dodir prikazuje i sklanja kontrole.
 */
@Composable
fun MobileVodPlayerScreen(
    start: (VodPlayerViewModel) -> Unit,
    onClose: () -> Unit,
    viewModel: VodPlayerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(VodPanel.NONE) }
    var gestureHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { start(viewModel) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    KeepScreenOn()
    ImmersiveMode()

    // Kontrole se same sklanjaju posle nekoliko sekundi gledanja; svaka promena pozicije odlaže to.
    LaunchedEffect(controlsVisible, panel, playback.status, ui.positionMs / SEEK_STEP_MS) {
        if (controlsVisible && panel == VodPanel.NONE && playback.status == PlaybackStatus.PLAYING) {
            delay(VOD_CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(gestureHint) {
        if (gestureHint != null) {
            delay(VOD_GESTURE_HINT_MS)
            gestureHint = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { controlsVisible = !controlsVisible }) }
            .volumeBrightnessDrag { gestureHint = it },
    ) {
        VideoSurface(player = viewModel.player, aspectMode = ui.aspectMode, modifier = Modifier.fillMaxSize())

        statusMessage(ui.sourceMissing, playback)?.let { message ->
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
            )
        }

        gestureHint?.let { hint -> GestureHint(hint, Modifier.align(Alignment.TopCenter)) }

        if (controlsVisible) {
            VodControls(
                ui = ui,
                playback = playback,
                onClose = onClose,
                onPlayPause = viewModel::togglePlayPause,
                onRetry = viewModel::retry,
                onSeekBy = viewModel::seekBy,
                onSeekTo = viewModel::seekTo,
                onAudio = { panel = VodPanel.AUDIO },
                onSubtitles = { panel = VodPanel.SUBTITLES },
                onAspect = viewModel::cycleAspectMode,
                onNext = viewModel::next,
            )
        }
    }

    when (panel) {
        VodPanel.AUDIO -> TrackDialog(
            title = stringResource(R.string.player_audio),
            options = playback.audioTracks.map { it.label to it.selected },
            emptyText = stringResource(R.string.player_no_tracks),
            onSelect = { index -> viewModel.selectAudio(playback.audioTracks[index]) },
            onDismiss = { panel = VodPanel.NONE },
        )

        VodPanel.SUBTITLES -> TrackDialog(
            title = stringResource(R.string.player_subtitles),
            options = listOf(stringResource(R.string.player_subtitles_off) to !playback.textEnabled) +
                playback.textTracks.map { it.label to it.selected },
            emptyText = null,
            onSelect = { index ->
                viewModel.selectText(if (index == 0) null else playback.textTracks[index - 1])
            },
            onDismiss = { panel = VodPanel.NONE },
        )

        VodPanel.NONE -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VodControls(
    ui: VodPlayerUiState,
    playback: PlaybackState,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onAspect: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .safeDrawingPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_back)) }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = ui.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val season = ui.season
                val episode = ui.episode
                if (season != null && episode != null) {
                    Text(
                        text = episodeLabel(season, episode),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = { onSeekBy(-SEEK_STEP_MS) }) { Text(stringResource(R.string.vod_seek_back)) }
            if (playback.status == PlaybackStatus.FAILED || ui.sourceMissing) {
                FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.player_retry)) }
            } else {
                FilledTonalButton(onClick = onPlayPause) {
                    Text(
                        stringResource(
                            if (playback.status == PlaybackStatus.PAUSED) R.string.player_play else R.string.player_pause,
                        ),
                    )
                }
            }
            FilledTonalButton(onClick = { onSeekBy(SEEK_STEP_MS) }) { Text(stringResource(R.string.vod_seek_forward)) }
        }

        Column {
            // Dok korisnik vuče klizač prikazuje se pozicija pod prstom, a premotava se kad ga pusti.
            var dragged by remember { mutableStateOf<Float?>(null) }
            val duration = ui.durationMs.coerceAtLeast(1)
            val shown = dragged?.let { (it * duration).toLong() } ?: ui.positionMs
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = formatDuration(shown), color = Color.White, style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = dragged ?: (ui.positionMs.toFloat() / duration).coerceIn(0f, 1f),
                    onValueChange = { dragged = it },
                    onValueChangeFinished = {
                        dragged?.let { onSeekTo((it * duration).toLong()) }
                        dragged = null
                    },
                    enabled = ui.durationMs > 0,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                Text(
                    text = formatDuration(ui.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            ) {
                TextButton(onClick = onAudio) { Text(stringResource(R.string.player_audio)) }
                TextButton(onClick = onSubtitles) { Text(stringResource(R.string.player_subtitles)) }
                TextButton(onClick = onAspect) { Text(stringResource(R.string.player_aspect, ui.aspectMode.label())) }
                if (ui.hasNext) {
                    TextButton(onClick = onNext) { Text(stringResource(R.string.vod_next_episode)) }
                }
            }
        }
    }
}

private const val SEEK_STEP_MS = 10_000L
private const val VOD_GESTURE_HINT_MS = 1_200L
private const val VOD_CONTROLS_TIMEOUT_MS = 4_000L
