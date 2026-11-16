package com.bane23.iptvplayer.ui.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.KeepScreenOn
import com.bane23.iptvplayer.ui.common.player.PlayerChip
import com.bane23.iptvplayer.ui.common.player.PlayerIconButton
import com.bane23.iptvplayer.ui.common.player.PlayerScrim
import com.bane23.iptvplayer.ui.common.player.PlayerSeekBar
import com.bane23.iptvplayer.ui.common.player.PlayerResolution
import com.bane23.iptvplayer.ui.common.player.PlayerTransportBar
import com.bane23.iptvplayer.ui.common.player.formatDuration
import com.bane23.iptvplayer.ui.common.player.PlayerTitle
import com.bane23.iptvplayer.ui.common.player.VideoSurface
import com.bane23.iptvplayer.ui.common.player.label
import com.bane23.iptvplayer.ui.common.player.seekStepFor
import com.bane23.iptvplayer.ui.common.player.statusMessage
import com.bane23.iptvplayer.ui.common.vod.VodPlayerViewModel
import com.bane23.iptvplayer.ui.common.vod.episodeLabel
import kotlinx.coroutines.delay

/** Šta dobija fokus kad se kontrole pojave. */
private enum class VodFocus { PLAY, SEEK }

/**
 * Plejer za film ili epizodu na TV-u. Dok su kontrole sklonjene: OK (ili gore/dole) ih prikazuje,
 * levo/desno premotava (držanje premotava sve brže). Kontrole su iste kao na telefonu: naslov gore,
 * a dole traka sa vremenom i dugmad Titlovi, Audio, Format i Od početka. Nazad prvo skloni kontrole.
 * [start] pokreće puštanje (poziva se jednom).
 */
@Composable
fun TvVodPlayerScreen(
    start: (VodPlayerViewModel) -> Unit,
    viewModel: VodPlayerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    var controls by remember { mutableStateOf(true) }
    var focusOn by remember { mutableStateOf(VodFocus.PLAY) }
    var tracks by remember { mutableStateOf<TvTrackKind?>(null) }
    // Svaki pritisak tastera povećava brojač, čime se kontrole zadržavaju još nekoliko sekundi.
    var activity by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val failed = playback.status == PlaybackStatus.FAILED || ui.sourceMissing

    LaunchedEffect(Unit) { start(viewModel) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    KeepScreenOn()
    BackHandler(enabled = tracks != null || controls) {
        if (tracks != null) tracks = null else controls = false
    }

    LaunchedEffect(controls, tracks, activity, playback.status) {
        if (controls && tracks == null && playback.status == PlaybackStatus.PLAYING) {
            delay(CONTROLS_TIMEOUT_MS)
            controls = false
        }
    }
    // Fokus prati ono što je na ekranu, inače tasteri ne bi stizali nikuda.
    LaunchedEffect(controls, tracks, focusOn) {
        when {
            tracks != null -> Unit
            controls -> repeat(FOCUS_ATTEMPTS) {
                val target = if (focusOn == VodFocus.SEEK) seekFocus else playFocus
                if (target.tryRequestFocus()) return@LaunchedEffect
                delay(FOCUS_STEP_MS)
            }
            else -> rootFocus.tryRequestFocus()
        }
    }

    fun show(focus: VodFocus) {
        focusOn = focus
        controls = true
        activity++
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) activity++
                false
            }
            .onKeyEvent { event ->
                val isDown = event.type == KeyEventType.KeyDown
                val isUp = event.type == KeyEventType.KeyUp
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                    -> {
                        if (isDown) viewModel.togglePlayPause()
                        return@onKeyEvent true
                    }

                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        if (isDown) viewModel.seekBy(-seekStepFor(event.nativeKeyEvent.repeatCount + 6))
                        return@onKeyEvent true
                    }

                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (isDown) viewModel.seekBy(seekStepFor(event.nativeKeyEvent.repeatCount + 6))
                        return@onKeyEvent true
                    }

                    AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (isDown) viewModel.next()
                        return@onKeyEvent true
                    }
                }
                // Ostali tasteri ovde stižu samo dok su kontrole sklonjene (fokus je na samom plejeru).
                if (controls) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (isDown) {
                            val direction = if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                            viewModel.seekBy(direction * seekStepFor(event.nativeKeyEvent.repeatCount))
                            show(VodFocus.SEEK)
                        }
                        true
                    }

                    // Kontrole se pojavljuju kad se taster pusti, da isti pritisak ne bi odmah pritisnuo dugme.
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    AndroidKeyEvent.KEYCODE_DPAD_UP,
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                    AndroidKeyEvent.KEYCODE_MENU,
                    -> {
                        if (isUp) show(VodFocus.PLAY)
                        true
                    }

                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        VideoSurface(player = viewModel.player, aspectMode = ui.aspectMode, modifier = Modifier.fillMaxSize())

        statusMessage(ui.sourceMissing, playback)?.let { message ->
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 96.dp),
            )
        }

        if (controls) {
            Box(modifier = Modifier.align(Alignment.TopStart)) {
                PlayerScrim(top = true, modifier = Modifier.matchParentSize())
                val season = ui.season
                val episode = ui.episode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 48.dp, top = 27.dp, bottom = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TitleBackIcon()
                    PlayerTitle(
                        title = ui.title,
                        subtitle = if (season != null && episode != null) episodeLabel(season, episode) else null,
                        large = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Box(modifier = Modifier.align(Alignment.BottomStart)) {
                PlayerScrim(top = false, modifier = Modifier.matchParentSize())
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 48.dp, top = 48.dp, bottom = 27.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val paused = playback.status == PlaybackStatus.PAUSED
                    PlayerTransportBar(
                        startLabel = formatDuration(ui.positionMs),
                        endLabel = formatDuration(ui.durationMs),
                        leading = {
                            PlayerIconButton(
                                icon = AppIcons.Rewind,
                                description = stringResource(R.string.vod_seek_back),
                                onClick = { viewModel.seekBy(-SEEK_STEP_MS) },
                            )
                            PlayerIconButton(
                                icon = if (paused || failed) AppIcons.Play else AppIcons.Pause,
                                description = stringResource(
                                    when {
                                        failed -> R.string.player_retry
                                        paused -> R.string.player_play
                                        else -> R.string.player_pause
                                    },
                                ),
                                onClick = { if (failed) viewModel.retry() else viewModel.togglePlayPause() },
                                primary = true,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .focusRequester(playFocus),
                            )
                        },
                        trailing = {
                            PlayerResolution(viewModel.player, modifier = Modifier.padding(end = 8.dp))
                            PlayerIconButton(
                                icon = AppIcons.Forward,
                                description = stringResource(R.string.vod_seek_forward),
                                onClick = { viewModel.seekBy(SEEK_STEP_MS) },
                            )
                            if (ui.hasNext) {
                                PlayerIconButton(
                                    icon = AppIcons.Next,
                                    description = stringResource(R.string.vod_next_episode),
                                    onClick = viewModel::next,
                                )
                            }
                        },
                    ) { barModifier ->
                        PlayerSeekBar(
                            positionMs = ui.positionMs,
                            durationMs = ui.durationMs,
                            onSeekTo = viewModel::seekTo,
                            onSeekBy = viewModel::seekBy,
                            modifier = barModifier.focusRequester(seekFocus),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlayerChip(
                            label = stringResource(R.string.player_subtitles),
                            icon = AppIcons.Subtitles,
                            active = playback.textEnabled,
                            onClick = { tracks = TvTrackKind.SUBTITLES },
                        )
                        PlayerChip(
                            label = stringResource(R.string.player_audio),
                            icon = AppIcons.Audio,
                            onClick = { tracks = TvTrackKind.AUDIO },
                        )
                        PlayerChip(
                            label = ui.aspectMode.label(),
                            icon = AppIcons.AspectRatio,
                            onClick = viewModel::cycleAspectMode,
                        )
                        PlayerChip(
                            label = stringResource(R.string.vod_start_over),
                            icon = AppIcons.Previous,
                            onClick = { viewModel.seekTo(0) },
                        )
                    }
                }
            }
        }

        tracks?.let { kind ->
            TvTrackPanel(
                kind = kind,
                playback = playback,
                onSelectAudio = viewModel::selectAudio,
                onSelectText = viewModel::selectText,
                onClose = { tracks = null },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

private const val SEEK_STEP_MS = 10_000L
private const val CONTROLS_TIMEOUT_MS = 5_000L
private const val FOCUS_ATTEMPTS = 10
private const val FOCUS_STEP_MS = 50L
