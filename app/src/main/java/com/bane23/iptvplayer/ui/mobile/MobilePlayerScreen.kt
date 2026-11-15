package com.bane23.iptvplayer.ui.mobile

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.ui.common.player.KeepScreenOn
import com.bane23.iptvplayer.ui.common.player.PlayerUiState
import com.bane23.iptvplayer.ui.common.player.PlayerViewModel
import com.bane23.iptvplayer.ui.common.player.VideoSurface
import com.bane23.iptvplayer.ui.common.player.label
import com.bane23.iptvplayer.ui.common.player.statusMessage
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class Panel { NONE, CHANNELS, AUDIO, SUBTITLES }

/** Plejer na telefonu: dodir prikazuje kontrole, prevlačenje menja osvetljenje (levo) i jačinu zvuka (desno). */
@Composable
fun MobilePlayerScreen(
    channelId: Long,
    categoryId: Long?,
    onClose: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val inPip = rememberIsInPictureInPicture()
    var controlsVisible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var gestureHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(channelId) { viewModel.start(channelId, categoryId) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    KeepScreenOn()
    ImmersiveMode()
    AutoPictureInPicture(enabled = playback.status == PlaybackStatus.PLAYING)

    // Kontrole se same sklanjaju posle nekoliko sekundi gledanja.
    LaunchedEffect(controlsVisible, panel, playback.status) {
        if (controlsVisible && panel == Panel.NONE && playback.status == PlaybackStatus.PLAYING) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(gestureHint) {
        if (gestureHint != null) {
            delay(GESTURE_HINT_MS)
            gestureHint = null
        }
    }
    BackHandler(enabled = panel != Panel.NONE) { panel = Panel.NONE }

    val volumeText = stringResource(R.string.gesture_volume)
    val brightnessText = stringResource(R.string.gesture_brightness)
    val gestures = rememberPlayerGestures()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (panel != Panel.NONE) panel = Panel.NONE else controlsVisible = !controlsVisible
                })
            }
            .pointerInput(Unit) {
                var leftSide = false
                detectDragGestures(
                    onDragStart = { offset -> leftSide = offset.x < size.width / 2 },
                ) { change, drag ->
                    change.consume()
                    val delta = -drag.y / size.height
                    gestureHint = if (leftSide) {
                        "$brightnessText ${gestures.changeBrightness(delta)}%"
                    } else {
                        "$volumeText ${gestures.changeVolume(delta)}%"
                    }
                }
            },
    ) {
        VideoSurface(
            player = viewModel.player,
            aspectMode = ui.aspectMode,
            modifier = Modifier.fillMaxSize(),
        )

        if (!inPip) {
            statusMessage(ui, playback)?.let { message ->
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

            gestureHint?.let { hint ->
                Text(
                    text = hint,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .safeDrawingPadding()
                        .padding(top = 72.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (controlsVisible && panel == Panel.NONE) {
                PlayerControls(
                    ui = ui,
                    playback = playback,
                    onClose = onClose,
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                    onPlayPause = viewModel::togglePlayPause,
                    onRetry = viewModel::retry,
                    onChannels = { panel = Panel.CHANNELS },
                    onAudio = { panel = Panel.AUDIO },
                    onSubtitles = { panel = Panel.SUBTITLES },
                    onAspect = viewModel::cycleAspectMode,
                    onPip = rememberEnterPictureInPicture(),
                )
            }

            if (panel == Panel.CHANNELS) {
                ChannelListPanel(
                    viewModel = viewModel,
                    ui = ui,
                    onPick = { panel = Panel.NONE },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
    }

    when (panel) {
        Panel.AUDIO -> TrackDialog(
            title = stringResource(R.string.player_audio),
            options = playback.audioTracks.map { it.label to it.selected },
            emptyText = stringResource(R.string.player_no_tracks),
            onSelect = { index -> viewModel.selectAudio(playback.audioTracks[index]) },
            onDismiss = { panel = Panel.NONE },
        )

        Panel.SUBTITLES -> TrackDialog(
            title = stringResource(R.string.player_subtitles),
            options = listOf(stringResource(R.string.player_subtitles_off) to !playback.textEnabled) +
                playback.textTracks.map { it.label to it.selected },
            emptyText = null,
            onSelect = { index ->
                viewModel.selectText(if (index == 0) null else playback.textTracks[index - 1])
            },
            onDismiss = { panel = Panel.NONE },
        )

        else -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerControls(
    ui: PlayerUiState,
    playback: PlaybackState,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit,
    onChannels: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onAspect: () -> Unit,
    onPip: (() -> Unit)?,
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
            Text(
                text = ui.channel?.name.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onPrevious) { Text(stringResource(R.string.player_previous)) }
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
            FilledTonalButton(onClick = onNext) { Text(stringResource(R.string.player_next)) }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            TextButton(onClick = onChannels) { Text(stringResource(R.string.player_channels)) }
            TextButton(onClick = onAudio) { Text(stringResource(R.string.player_audio)) }
            TextButton(onClick = onSubtitles) { Text(stringResource(R.string.player_subtitles)) }
            TextButton(onClick = onAspect) {
                Text(stringResource(R.string.player_aspect, ui.aspectMode.label()))
            }
            if (onPip != null) {
                TextButton(onClick = onPip) { Text(stringResource(R.string.player_pip)) }
            }
        }
    }
}

/** Lista kanala preko slike; kreće od kanala koji se trenutno gleda. */
@Composable
private fun ChannelListPanel(
    viewModel: PlayerViewModel,
    ui: PlayerUiState,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val channels = viewModel.channels.collectAsLazyPagingItems()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ui.position)
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxHeight()
            .widthIn(max = 360.dp)
            .fillMaxWidth(0.85f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .safeDrawingPadding(),
    ) {
        items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
            val channel = channels[index]
            MobileChannelRow(
                channel = channel,
                highlighted = channel != null && channel.id == ui.channel?.id,
                onClick = {
                    channel?.let(viewModel::playChannel)
                    onPick()
                },
            )
        }
    }
}

@Composable
private fun TrackDialog(
    title: String,
    options: List<Pair<String, Boolean>>,
    emptyText: String?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (options.isEmpty() && emptyText != null) Text(emptyText)
                options.forEachIndexed { index, (label, selected) ->
                    TextButton(
                        onClick = {
                            onSelect(index)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (selected) "✓  $label" else label,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// ---------------------------------------------------------------------------------------------

/** Menja jačinu zvuka i osvetljenje ekrana; vraća novu vrednost u procentima. */
private class PlayerGestures(private val activity: Activity?, private val audio: AudioManager) {
    private val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private var volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
    private var brightness = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f

    fun changeVolume(delta: Float): Int {
        volume = (volume + delta).coerceIn(0f, 1f)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVolume).roundToInt(), 0)
        return (volume * 100).roundToInt()
    }

    fun changeBrightness(delta: Float): Int {
        brightness = (brightness + delta).coerceIn(0.01f, 1f)
        activity?.window?.let { window ->
            window.attributes = window.attributes.apply { screenBrightness = brightness }
        }
        return (brightness * 100).roundToInt()
    }

    /** Vraća osvetljenje na sistemsko kad se plejer zatvori. */
    fun restoreBrightness() {
        activity?.window?.let { window ->
            window.attributes = window.attributes.apply { screenBrightness = -1f }
        }
    }
}

@Composable
private fun rememberPlayerGestures(): PlayerGestures {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val gestures = remember(activity) {
        PlayerGestures(activity, context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
    }
    DisposableEffect(gestures) {
        onDispose { gestures.restoreBrightness() }
    }
    return gestures
}

/** Sakriva sistemske trake dok je plejer na ekranu. */
@Composable
private fun ImmersiveMode() {
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(activity, view) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun rememberIsInPictureInPicture(): Boolean {
    val activity = LocalActivity.current as? ComponentActivity
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity?.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

/** Funkcija koja prebacuje plejer u sliku u slici, ili null ako uređaj to ne podržava. */
@Composable
private fun rememberEnterPictureInPicture(): (() -> Unit)? {
    val activity = LocalActivity.current
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                activity.enterPictureInPictureMode(pipParams(autoEnter = false))
            } catch (_: IllegalStateException) {
                // uređaj ne podržava sliku u slici
            }
        }
    }
}

/** Dok se kanal gleda, izlazak na početni ekran telefona sam prebacuje plejer u sliku u slici (Android 12+). */
@Composable
private fun AutoPictureInPicture(enabled: Boolean) {
    val activity = LocalActivity.current
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    DisposableEffect(activity, enabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.setPictureInPictureParams(pipParams(autoEnter = enabled))
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                activity.setPictureInPictureParams(pipParams(autoEnter = false))
            }
        }
    }
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
private fun pipParams(autoEnter: Boolean): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(autoEnter)
    return builder.build()
}

private const val CONTROLS_TIMEOUT_MS = 4_000L
private const val GESTURE_HINT_MS = 1_200L
