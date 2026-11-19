package com.bane23.iptvplayer.ui.mobile

import android.app.PendingIntent
import com.bane23.iptvplayer.ui.common.FavoriteListsViewModel
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.bane23.iptvplayer.ui.common.player.PauseDimLayer
import com.bane23.iptvplayer.ui.common.player.PauseInfoScreen
import com.bane23.iptvplayer.ui.common.player.livePauseInfo
import com.bane23.iptvplayer.ui.common.player.rememberPauseInfoEnabled
import com.bane23.iptvplayer.ui.common.player.pauseDimInput
import com.bane23.iptvplayer.ui.common.player.rememberPauseDim
import com.bane23.iptvplayer.ui.common.player.PlayerChip
import com.bane23.iptvplayer.ui.common.player.PlayerIconButton
import com.bane23.iptvplayer.ui.common.player.PlayerProgress
import com.bane23.iptvplayer.ui.common.player.PlayerStreamInfo
import com.bane23.iptvplayer.ui.common.player.PlayerTransportBar
import com.bane23.iptvplayer.ui.common.rememberNow
import com.bane23.iptvplayer.ui.common.player.PlayerUiState
import com.bane23.iptvplayer.ui.common.player.SourceNotices
import com.bane23.iptvplayer.ui.common.player.label
import com.bane23.iptvplayer.ui.common.player.PlayerViewModel
import com.bane23.iptvplayer.ui.common.player.VideoSurface
import com.bane23.iptvplayer.ui.common.player.quickSettingChoices
import com.bane23.iptvplayer.ui.common.player.statusMessage
import kotlinx.coroutines.delay

private enum class Panel { NONE, CHANNELS, AUDIO, RECORD, SETTINGS, SCHEDULE, SOURCES }

/** Plejer na telefonu: dodir prikazuje kontrole, prevlačenje menja osvetljenje (levo) i jačinu zvuka (desno). */
@Composable
fun MobilePlayerScreen(
    channelId: Long,
    categoryId: Long?,
    onClose: () -> Unit,
    onCastStarted: () -> Unit = {},
    onArchive: (channelId: Long) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    RecordingNotices(viewModel.recordingNotice)
    SourceNotices(viewModel.sourceNotice)
    val favoriteLists: FavoriteListsViewModel = hiltViewModel()
    MobileFavoritePickDialog(favoriteLists)
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
    val levels = rememberPlayerLevels()
    // Dodir na bočnim trakama odlaže sklanjanje kontrola.
    var sliderTouches by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var infoVisible by remember { mutableStateOf(false) }
    var unlockVisible by remember { mutableStateOf(false) }

    LaunchedEffect(channelId) { viewModel.start(channelId, categoryId) }
    PlayerLifecycle(
        player = viewModel.player,
        title = ui.channel?.name.orEmpty(),
        inPip = inPip,
        onTogglePlayPause = viewModel::togglePlayPause,
        onForeground = viewModel::onForeground,
        onBackground = viewModel::onBackground,
    )
    KeepScreenOn(playback.status)
    // Zaboravljena pauza: slika se zatamni, a posle dužeg vremena plejer se zatvara.
    // Izbor zvuka i snimanja su zasebni prozori, pa se dodiri u njima ovde ne vide.
    val pauseDim = rememberPauseDim(
        status = playback.status,
        enabled = panel != Panel.AUDIO && panel != Panel.RECORD && panel != Panel.SOURCES,
        info = rememberPauseInfoEnabled() && panel == Panel.NONE && !inPip,
        onExit = onClose,
    )
    ImmersiveMode()
    PlayerPictureInPicture(playback.status, viewModel::togglePlayPause)

    // Kontrole se same sklanjaju posle nekoliko sekundi gledanja.
    LaunchedEffect(controlsVisible, panel, playback.status, sliderTouches) {
        if (controlsVisible && panel == Panel.NONE && playback.status == PlaybackStatus.PLAYING) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
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
            .pauseDimInput(pauseDim)
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = {
                        when {
                            locked -> unlockVisible = !unlockVisible
                            panel != Panel.NONE -> panel = Panel.NONE
                            else -> controlsVisible = !controlsVisible
                        }
                    },
                    // Dupli tap na sliku pauzira ili pušta kanal.
                    onDoubleTap = { if (!locked && panel == Panel.NONE) viewModel.togglePlayPause() },
                )
            },
    ) {
        VideoSurface(
            player = viewModel.player,
            aspectMode = ui.aspectMode,
            modifier = Modifier.fillMaxSize(),
        )

        PauseDimLayer(
            state = pauseDim,
            info = { ui.channel?.let { PauseInfoScreen(livePauseInfo(it.name, nowNext), compact = true) } },
        ) {
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

                // Ostaje preko slike i kad se kontrole sklone, dok se ne ugasi istim dugmetom.
                if (infoVisible && panel != Panel.CHANNELS) {
                    PlayerStreamInfo(
                        player = viewModel.player,
                        technical = playback.video,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .safeDrawingPadding()
                            .padding(start = streamInfoStartPadding(), top = 76.dp, end = 16.dp),
                    )
                }

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
                        onClose = onClose,
                        onLock = {
                            locked = true
                            controlsVisible = false
                        },
                        onPrevious = viewModel::previous,
                        onNext = viewModel::next,
                        onLastChannel = viewModel::playLastChannel,
                        onPlayPause = viewModel::togglePlayPause,
                        onRetry = viewModel::retry,
                        onChannels = { panel = Panel.CHANNELS },
                        onSchedule = { panel = Panel.SCHEDULE },
                        onSources = if (ui.sources.size > 1) ({ panel = Panel.SOURCES }) else null,
                        onAudio = { panel = Panel.AUDIO },
                        onSettings = { panel = Panel.SETTINGS },
                        infoVisible = infoVisible,
                        onInfo = { infoVisible = !infoVisible },
                        isFavorite = isFavorite,
                        nowNext = nowNext,
                        onFavorite = { ui.channel?.let { favoriteLists.favoriteAction(it.id) } },
                        isRecording = isRecording,
                        onRecord = record,
                        onPip = rememberEnterPictureInPicture(),
                        onCast = cast,
                    )
                    PlayerSideSliders(levels) { sliderTouches++ }
                }

                if (panel == Panel.SETTINGS) {
                    // Trake ostaju na ekranu, da se odmah vidi šta prekidač radi.
                    PlayerSideSliders(levels) { }
                    MobileQuickSettingsPanel(
                        quickSettingChoices(
                            tv = false,
                            aspectMode = ui.aspectMode,
                            onAspectMode = viewModel::setAspectMode,
                            decoder = ui.decoder,
                            onDecoder = viewModel::setDecoder,
                        ),
                    )
                }

                val scheduleChannel = ui.channel
                if (panel == Panel.SCHEDULE && scheduleChannel != null) {
                    MobileChannelSchedulePanel(
                        channel = scheduleChannel,
                        onArchive = { onArchive(scheduleChannel.id) },
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
    }

    when (panel) {
        Panel.AUDIO -> TrackDialog(
            title = stringResource(R.string.player_audio),
            options = playback.audioTracks.map { it.label to it.selected },
            emptyText = stringResource(R.string.player_no_tracks),
            onSelect = { index -> viewModel.selectAudio(playback.audioTracks[index]) },
            onDismiss = { panel = Panel.NONE },
        )

        Panel.SOURCES -> TrackDialog(
            title = stringResource(R.string.player_sources),
            options = ui.sources.map { it.label(ui) to (it.channelId == ui.sourceId) },
            emptyText = null,
            onSelect = { index -> ui.sources.getOrNull(index)?.let { viewModel.selectSource(it.channelId) } },
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
    onClose: () -> Unit,
    onLock: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLastChannel: () -> Unit,
    onPlayPause: () -> Unit,
    onRetry: () -> Unit,
    onChannels: () -> Unit,
    onSchedule: () -> Unit,
    onSources: (() -> Unit)?,
    onAudio: () -> Unit,
    onSettings: () -> Unit,
    infoVisible: Boolean,
    onInfo: () -> Unit,
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
            // Prethodni i sledeći kanal na krajevima, traka emisije u sredini.
            PlayerTransportBar(
                startLabel = program?.let { formatTime(it.startMs) },
                endLabel = program?.let { formatTime(it.stopMs) },
                leading = {
                    PlayerIconButton(AppIcons.Previous, stringResource(R.string.player_previous), onPrevious)
                    PlayPauseButton(
                        paused = playback.status == PlaybackStatus.PAUSED,
                        failed = failed,
                        onPlayPause = onPlayPause,
                        onRetry = onRetry,
                    )
                },
                trailing = {
                    PlayerIconButton(AppIcons.Next, stringResource(R.string.player_next), onNext)
                    if (ui.lastChannel != null) {
                        PlayerIconButton(AppIcons.Swap, stringResource(R.string.player_last_channel), onLastChannel)
                    }
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
            if (channel != null) {
                PlayerChip(icon = AppIcons.Guide, label = stringResource(R.string.player_schedule), onClick = onSchedule)
            }
            // Isti kanal postoji i u spojenoj listi: može da se izabere odakle se gleda.
            if (onSources != null) {
                PlayerChip(icon = AppIcons.Swap, label = stringResource(R.string.player_sources), onClick = onSources)
            }
            PlayerChip(icon = AppIcons.Audio, label = stringResource(R.string.player_audio), onClick = onAudio)
            PlayerChip(icon = AppIcons.Settings, label = stringResource(R.string.settings_title), onClick = onSettings)
            PlayerChip(
                icon = AppIcons.Info,
                label = stringResource(R.string.player_stream_info),
                active = infoVisible,
                onClick = onInfo,
            )
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

/**
 * Podešava sliku u slici dok je plejer otvoren: izlazak na početni ekran telefona sam prebacuje
 * plejer u mali prozor dok se gleda (Android 12+), a u malom prozoru stoji dugme za pauzu i puštanje.
 */
@Composable
internal fun PlayerPictureInPicture(status: PlaybackStatus, onTogglePlayPause: () -> Unit) {
    val activity = LocalActivity.current
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val toggle by rememberUpdatedState(onTogglePlayPause)
    val autoEnter = status == PlaybackStatus.PLAYING
    // Dok se strim učitava ili ponovo povezuje, dugme i dalje nudi pauzu, kao i kontrole plejera.
    val showPause = status == PlaybackStatus.PLAYING || status == PlaybackStatus.LOADING ||
        status == PlaybackStatus.RETRYING

    // Pri prelasku sa jednog plejera na drugi oba su kratko na ekranu; dugme pripada onom koji je poslednji otvoren.
    val owner = remember { Any() }
    val inPip = rememberIsInPictureInPicture()

    DisposableEffect(activity) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (pipOwner === owner) toggle()
            }
        }
        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter(ACTION_PIP_PLAY_PAUSE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            activity.unregisterReceiver(receiver)
            // Plejer koji se zatvara ne sme da obriše dugme plejera koji se upravo otvorio.
            if (pipOwner === owner) {
                pipOwner = null
                pipActions = emptyList()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activity.setPictureInPictureParams(pipParams(autoEnter = false))
                }
            }
        }
    }
    // Ponavlja se i pri ulasku u mali prozor, da dugme sigurno stigne do sistema.
    DisposableEffect(activity, autoEnter, showPause, inPip) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pipOwner = owner
            pipActions = listOf(playPauseAction(activity, showPause))
            activity.setPictureInPictureParams(pipParams(autoEnter = autoEnter))
        }
        onDispose {}
    }
}

/** Plejer čije je dugme trenutno u malom prozoru, i samo dugme. Menja se samo sa glavne niti. */
private var pipOwner: Any? = null
private var pipActions: List<RemoteAction> = emptyList()

@androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
private fun playPauseAction(context: Context, showPause: Boolean): RemoteAction {
    val label = context.getString(if (showPause) R.string.player_pause else R.string.player_play)
    val intent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(ACTION_PIP_PLAY_PAUSE).setPackage(context.packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val icon = Icon.createWithResource(context, if (showPause) R.drawable.ic_pip_pause else R.drawable.ic_pip_play)
    return RemoteAction(icon, label, label, intent)
}

/** Svako podešavanje nosi i trenutno dugme, i kad se u mali prozor ulazi dugmetom iz plejera. */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
private fun pipParams(autoEnter: Boolean): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .setActions(pipActions)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(autoEnter)
    return builder.build()
}

private const val ACTION_PIP_PLAY_PAUSE = "com.bane23.iptvplayer.action.PIP_PLAY_PAUSE"
private const val CONTROLS_TIMEOUT_MS = 4_000L
