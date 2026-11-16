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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.positionChange
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
import androidx.media3.common.Player
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.recording.RecordingNotices
import com.bane23.iptvplayer.ui.common.recording.recordingChoices
import com.bane23.iptvplayer.ui.common.recording.rememberRecordingToggle
import com.bane23.iptvplayer.domain.NowNext
import com.bane23.iptvplayer.ui.common.guide.nextLine
import com.bane23.iptvplayer.ui.common.guide.formatTime
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.KeepScreenOn
import com.bane23.iptvplayer.ui.common.player.PlayerChip
import com.bane23.iptvplayer.ui.common.player.PlayerIconButton
import com.bane23.iptvplayer.ui.common.player.PlayerProgress
import com.bane23.iptvplayer.ui.common.player.PlayerResolution
import com.bane23.iptvplayer.ui.common.player.PlayerTransportBar
import com.bane23.iptvplayer.ui.common.rememberNow
import com.bane23.iptvplayer.ui.common.player.PlayerUiState
import com.bane23.iptvplayer.ui.common.player.PlayerViewModel
import com.bane23.iptvplayer.ui.common.player.VideoSurface
import com.bane23.iptvplayer.ui.common.player.label
import com.bane23.iptvplayer.ui.common.player.statusMessage
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlin.math.abs

private enum class Panel { NONE, CHANNELS, AUDIO, SUBTITLES, RECORD }

/** Plejer na telefonu: dodir prikazuje kontrole, prevlačenje menja osvetljenje (levo) i jačinu zvuka (desno). */
@Composable
fun MobilePlayerScreen(
    channelId: Long,
    categoryId: Long?,
    onClose: () -> Unit,
    onCastStarted: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    RecordingNotices(viewModel.recordingNotice)
    val nowNext by viewModel.nowNext.collectAsStateWithLifecycle()
    val inPip = rememberIsInPictureInPicture()
    val cast = rememberCastLauncher(media = { viewModel.castMedia() }, onStarted = onCastStarted)
    var controlsVisible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    // Dugme „Snimaj” zaustavlja snimanje koje traje, a inače prvo pita koliko dugo da snima.
    val record = rememberRecordingToggle(isRecording) {
        if (isRecording) viewModel.toggleRecording() else panel = Panel.RECORD
    }
    val recordChoices = recordingChoices(nowNext.now?.stopMs)
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf(false) }
    var unlockVisible by remember { mutableStateOf(false) }

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
    // Zaključan ekran ne izlazi ni na Nazad, samo pokaže dugme za otključavanje.
    BackHandler(enabled = locked) { unlockVisible = true }
    LaunchedEffect(unlockVisible) {
        if (unlockVisible) {
            delay(CONTROLS_TIMEOUT_MS)
            unlockVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(locked) {
                detectTapGestures(onTap = {
                    when {
                        locked -> unlockVisible = !unlockVisible
                        panel != Panel.NONE -> panel = Panel.NONE
                        else -> controlsVisible = !controlsVisible
                    }
                })
            }
            .then(if (locked) Modifier else Modifier.volumeBrightnessDrag { gestureHint = it }),
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

            gestureHint?.let { hint -> GestureHint(hint, Modifier.align(Alignment.TopCenter)) }
            FirstLaunchGestureHint()

            if (locked) {
                LockedOverlay(visible = unlockVisible) {
                    locked = false
                    unlockVisible = false
                    controlsVisible = true
                }
            } else if (controlsVisible && panel == Panel.NONE) {
                LiveControls(
                    ui = ui,
                    playback = playback,
                    player = viewModel.player,
                    onClose = onClose,
                    onLock = {
                        locked = true
                        controlsVisible = false
                    },
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                    onPlayPause = viewModel::togglePlayPause,
                    onRetry = viewModel::retry,
                    onChannels = { panel = Panel.CHANNELS },
                    onAudio = { panel = Panel.AUDIO },
                    onSubtitles = { panel = Panel.SUBTITLES },
                    onAspect = viewModel::cycleAspectMode,
                    isFavorite = isFavorite,
                    nowNext = nowNext,
                    onFavorite = viewModel::toggleFavorite,
                    isRecording = isRecording,
                    onRecord = record,
                    onPip = rememberEnterPictureInPicture(),
                    onCast = cast,
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

        Panel.RECORD -> TrackDialog(
            title = stringResource(R.string.recording_duration_title),
            options = recordChoices.map { it.label to false },
            emptyText = null,
            onSelect = { index -> viewModel.toggleRecording(recordChoices[index].stopAtMs()) },
            onDismiss = { panel = Panel.NONE },
        )

        else -> Unit
    }
}

/** Iste kontrole kao u plejeru filmova: naziv kanala gore, emisija sa trakom i dugmad dole. */
@Composable
private fun LiveControls(
    ui: PlayerUiState,
    playback: PlaybackState,
    player: Player,
    onClose: () -> Unit,
    onLock: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit,
    onChannels: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onAspect: () -> Unit,
    isFavorite: Boolean,
    nowNext: NowNext,
    onFavorite: () -> Unit,
    isRecording: Boolean,
    onRecord: () -> Unit,
    onPip: (() -> Unit)?,
    onCast: (() -> Unit)?,
) {
    val channel = ui.channel
    val now by rememberNow()
    val failed = playback.status == PlaybackStatus.FAILED || ui.sourceMissing
    MobilePlayerControls(
        title = listOfNotNull(channel?.number?.toString(), channel?.name).joinToString("  "),
        subtitle = nowNext.now?.title,
        onClose = onClose,
        onLock = onLock,
        onPip = onPip,
        onCast = onCast,
        bottom = {
            val program = nowNext.now
            val paused = playback.status == PlaybackStatus.PAUSED
            PlayerTransportBar(
                startLabel = program?.let { formatTime(it.startMs) },
                endLabel = program?.let { formatTime(it.stopMs) },
                leading = {
                    PlayerIconButton(AppIcons.Previous, stringResource(R.string.player_previous), onPrevious)
                    PlayerIconButton(
                        icon = if (paused || failed) AppIcons.Play else AppIcons.Pause,
                        description = stringResource(
                            when {
                                failed -> R.string.player_retry
                                paused -> R.string.player_play
                                else -> R.string.player_pause
                            },
                        ),
                        onClick = if (failed) onRetry else onPlayPause,
                        primary = true,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                },
                trailing = {
                    PlayerResolution(player, modifier = Modifier.padding(horizontal = 8.dp))
                    PlayerIconButton(AppIcons.Next, stringResource(R.string.player_next), onNext)
                    PlayerIconButton(AppIcons.AspectRatio, ui.aspectMode.label(), onAspect)
                },
            ) { barModifier ->
                val length = program?.let { (it.stopMs - it.startMs).coerceAtLeast(1) }
                PlayerProgress(
                    fraction = if (program != null && length != null) (now - program.startMs).toFloat() / length else 0f,
                    modifier = barModifier,
                )
            }
            nowNext.nextLine()?.let { line ->
                Text(
                    text = line,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        },
        chips = {
            PlayerChip(icon = AppIcons.ChannelList, label = stringResource(R.string.player_channels), onClick = onChannels)
            PlayerChip(
                icon = AppIcons.Subtitles,
                label = stringResource(R.string.player_subtitles),
                active = playback.textEnabled,
                onClick = onSubtitles,
            )
            PlayerChip(icon = AppIcons.Audio, label = stringResource(R.string.player_audio), onClick = onAudio)
            PlayerChip(icon = AppIcons.AspectRatio, label = ui.aspectMode.label(), onClick = onAspect)
            if (channel != null) {
                PlayerChip(
                    icon = if (isFavorite) AppIcons.Star else AppIcons.StarOutline,
                    label = stringResource(R.string.category_favorites),
                    active = isFavorite,
                    onClick = onFavorite,
                )
                PlayerChip(
                    icon = AppIcons.Record,
                    label = stringResource(if (isRecording) R.string.recording_chip_active else R.string.recording_chip),
                    active = isRecording,
                    onClick = onRecord,
                )
            }
        },
    )
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
internal fun TrackDialog(
    title: String,
    options: List<Pair<String, Boolean>>,
    emptyText: String?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    /** Dodatna stavka na dnu spiska (npr. „Potraži online”); ne zatvara dijalog sama. */
    extra: Pair<String, () -> Unit>? = null,
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
                if (extra != null) {
                    TextButton(onClick = extra.second, modifier = Modifier.fillMaxWidth()) {
                        Text(text = extra.first, modifier = Modifier.fillMaxWidth())
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

/**
 * Prevlačenje gore-dole: leva polovina ekrana menja osvetljenje, desna jačinu zvuka. Računa se samo
 * potez koji je krenuo uspravno; vodoravan potez (npr. sistemski gest Nazad) ne menja ništa, kao ni
 * potez koji počne uz samu levu ili desnu ivicu ekrana.
 */
@Composable
internal fun Modifier.volumeBrightnessDrag(onHint: (String) -> Unit): Modifier {
    val volumeText = stringResource(R.string.gesture_volume)
    val brightnessText = stringResource(R.string.gesture_brightness)
    val gestures = rememberPlayerGestures()
    return pointerInput(gestures) {
        val edge = GESTURE_EDGE.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.x < edge || down.position.x > size.width - edge) return@awaitEachGesture
            val leftSide = down.position.x < size.width / 2
            var moved = Offset.Zero
            var vertical = false
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val step = change.positionChange()
                if (!vertical) {
                    // Smer se odlučuje jednom, čim prst pređe prag; posle toga se više ne menja.
                    moved += step
                    if (moved.getDistance() < viewConfiguration.touchSlop) continue
                    if (abs(moved.y) < abs(moved.x) * VERTICAL_RATIO) break
                    vertical = true
                }
                change.consume()
                val delta = -step.y / size.height
                onHint(
                    if (leftSide) {
                        "$brightnessText ${gestures.changeBrightness(delta)}%"
                    } else {
                        "$volumeText ${gestures.changeVolume(delta)}%"
                    },
                )
            }
        }
    }
}

/** Pojas uz levu i desnu ivicu u kome se potezi ne računaju, jer odatle kreće sistemski gest Nazad. */
private val GESTURE_EDGE = 32.dp

/** Koliko puta pomeraj gore-dole mora biti veći od pomeraja levo-desno da bi se potez računao kao uspravan. */
private const val VERTICAL_RATIO = 1.5f

/** Kratka poruka sa novom vrednošću zvuka ili osvetljenja. */
@Composable
internal fun GestureHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .safeDrawingPadding()
            .padding(top = 72.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
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
internal fun ImmersiveMode() {
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
internal fun rememberIsInPictureInPicture(): Boolean {
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
internal fun rememberEnterPictureInPicture(): (() -> Unit)? {
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
internal fun AutoPictureInPicture(enabled: Boolean) {
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
