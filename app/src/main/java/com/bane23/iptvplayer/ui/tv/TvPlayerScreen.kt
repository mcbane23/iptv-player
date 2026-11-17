package com.bane23.iptvplayer.ui.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.recording.RecordingNotices
import com.bane23.iptvplayer.ui.common.recording.recordingChoices
import com.bane23.iptvplayer.ui.common.recording.rememberRecordingToggle
import com.bane23.iptvplayer.domain.NowNext
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.ui.common.ChannelLogo
import com.bane23.iptvplayer.ui.common.guide.formatTime
import com.bane23.iptvplayer.ui.common.guide.nextLine
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.KeepScreenOn
import com.bane23.iptvplayer.ui.common.player.PlayerChip
import com.bane23.iptvplayer.ui.common.player.PlayerIconButton
import com.bane23.iptvplayer.ui.common.player.PlayerProgress
import com.bane23.iptvplayer.ui.common.player.PlayerResolution
import com.bane23.iptvplayer.ui.common.player.PlayerTransportBar
import com.bane23.iptvplayer.ui.common.player.PlayerClock
import com.bane23.iptvplayer.ui.common.player.PlayerTitle
import com.bane23.iptvplayer.ui.common.player.PlayerUiState
import com.bane23.iptvplayer.ui.common.player.PlayerViewModel
import com.bane23.iptvplayer.ui.common.player.PlayerStreamInfo
import com.bane23.iptvplayer.ui.common.player.VideoSurface
import com.bane23.iptvplayer.ui.common.player.label
import com.bane23.iptvplayer.ui.common.player.quickSettingChoices
import com.bane23.iptvplayer.ui.common.player.statusMessage
import com.bane23.iptvplayer.ui.common.rememberNow
import kotlinx.coroutines.delay

/** Kontrole preko slike: sklonjene, samo prikazane posle promene kanala, ili u fokusu. */
private enum class Overlay { HIDDEN, INFO, ACTIVE }

/**
 * Plejer kanala na TV-u, upravlja se samo daljinskim. Dok su kontrole sklonjene: gore/dole menja
 * kanal, levo otvara listu kanala, OK (ili desno) prikazuje kontrole, cifre biraju kanal po broju.
 * Kontrole izgledaju kao u plejeru filmova: naziv kanala gore, a dole emisija sa trakom i dugmad
 * Kanali, Titlovi, Audio, Format i Omiljeni. Nazad prvo zatvara spisak ili kontrole.
 */
@Composable
fun TvPlayerScreen(
    channelId: Long,
    categoryId: Long?,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val nowNext by viewModel.nowNext.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    RecordingNotices(viewModel.recordingNotice)
    var overlay by remember { mutableStateOf(Overlay.INFO) }
    var channelList by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf<TvTrackKind?>(null) }
    // Informacije o strimu; ostaju preko slike i kad se kontrole sklone, dok se ne ugase istim dugmetom.
    var infoVisible by remember { mutableStateOf(false) }
    // Izbor trajanja snimanja je otvoren.
    var recordPanel by remember { mutableStateOf(false) }
    // Panel „Brza podešavanja” je otvoren.
    var settingsOpen by remember { mutableStateOf(false) }
    // Dugme „Snimaj” zaustavlja snimanje koje traje, a inače prvo pita koliko dugo da snima.
    val record = rememberRecordingToggle(isRecording) {
        if (isRecording) viewModel.toggleRecording() else recordPanel = true
    }
    var activity by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val firstChipFocus = remember { FocusRequester() }
    val panelOpen = channelList || tracks != null || recordPanel || settingsOpen

    LaunchedEffect(channelId) { viewModel.start(channelId, categoryId) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    KeepScreenOn(playback.status)
    BackHandler(enabled = panelOpen || overlay != Overlay.HIDDEN) {
        when {
            tracks != null -> tracks = null
            recordPanel -> recordPanel = false
            settingsOpen -> settingsOpen = false
            channelList -> channelList = false
            else -> overlay = Overlay.HIDDEN
        }
    }

    // Posle promene kanala kratko se prikaže šta se gleda.
    LaunchedEffect(ui.channel?.id) {
        if (overlay == Overlay.HIDDEN) overlay = Overlay.INFO
    }
    LaunchedEffect(overlay, panelOpen, activity, playback.status) {
        if (overlay != Overlay.HIDDEN && !panelOpen && playback.status == PlaybackStatus.PLAYING) {
            delay(if (overlay == Overlay.INFO) INFO_TIMEOUT_MS else CONTROLS_TIMEOUT_MS)
            overlay = Overlay.HIDDEN
        }
    }
    // Fokus prati ono što je na ekranu, inače tasteri ne bi stizali nikuda.
    LaunchedEffect(overlay, panelOpen) {
        when {
            panelOpen -> Unit
            overlay == Overlay.ACTIVE -> repeat(FOCUS_ATTEMPTS) {
                if (firstChipFocus.tryRequestFocus()) return@LaunchedEffect
                delay(FOCUS_STEP_MS)
            }
            else -> rootFocus.tryRequestFocus()
        }
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
                val code = event.nativeKeyEvent.keyCode
                val isDown = event.type == KeyEventType.KeyDown
                val isUp = event.type == KeyEventType.KeyUp
                if (code == AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                    code == AndroidKeyEvent.KEYCODE_MEDIA_PLAY ||
                    code == AndroidKeyEvent.KEYCODE_MEDIA_PAUSE
                ) {
                    if (isDown) viewModel.togglePlayPause()
                    return@onKeyEvent true
                }
                // Ostali tasteri ovde stižu samo dok je fokus na samom plejeru.
                if (overlay == Overlay.ACTIVE || panelOpen) return@onKeyEvent false
                when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (isDown) viewModel.previous()
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (isDown) viewModel.next()
                        true
                    }

                    // Spisak i kontrole se otvaraju kad se taster pusti, da isti pritisak ne bi odmah nešto izabrao.
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (isUp) channelList = true
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidKeyEvent.KEYCODE_MENU,
                    -> {
                        if (isUp) overlay = Overlay.ACTIVE
                        true
                    }

                    in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> {
                        if (isDown) viewModel.typeDigit(code - AndroidKeyEvent.KEYCODE_0)
                        true
                    }

                    in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 -> {
                        if (isDown) viewModel.typeDigit(code - AndroidKeyEvent.KEYCODE_NUMPAD_0)
                        true
                    }

                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        VideoSurface(
            player = viewModel.player,
            aspectMode = ui.aspectMode,
            modifier = Modifier.fillMaxSize(),
        )
        if (infoVisible && !channelList) {
            PlayerStreamInfo(
                player = viewModel.player,
                technical = playback.video,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 96.dp, end = 48.dp),
            )
        }

        statusMessage(ui, playback)?.let { message ->
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

        if (ui.typedNumber.isNotEmpty()) {
            Text(
                text = ui.typedNumber,
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 48.dp, vertical = 27.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        if (overlay != Overlay.HIDDEN && !channelList) {
            LiveControls(
                ui = ui,
                nowNext = nowNext,
                playback = playback,
                viewModel = viewModel,
                isFavorite = isFavorite,
                firstChipFocus = firstChipFocus,
                onChannels = { channelList = true },
                onSubtitles = { tracks = TvTrackKind.SUBTITLES },
                onAudio = { tracks = TvTrackKind.AUDIO },
                onAspect = viewModel::cycleAspectMode,
                onSettings = { settingsOpen = true },
                infoVisible = infoVisible,
                onInfo = { infoVisible = !infoVisible },
                onFavorite = viewModel::toggleFavorite,
                isRecording = isRecording,
                onRecord = record,
            )
        }

        if (channelList) {
            ChannelListPanel(
                viewModel = viewModel,
                ui = ui,
                onPick = {
                    channelList = false
                    overlay = Overlay.INFO
                },
                modifier = Modifier.align(Alignment.CenterStart),
            )
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

        if (settingsOpen) {
            TvQuickSettingsPanel(
                choices = quickSettingChoices(
                    tv = true,
                    aspectMode = ui.aspectMode,
                    onAspectMode = viewModel::setAspectMode,
                    decoder = ui.decoder,
                    onDecoder = viewModel::setDecoder,
                ),
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        if (recordPanel) {
            val choices = recordingChoices(nowNext.now?.stopMs)
            TvChoicePanel(
                title = stringResource(R.string.recording_duration_title),
                options = choices.map { it.label },
                onSelect = { index -> viewModel.toggleRecording(choices[index].stopAtMs()) },
                onClose = { recordPanel = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/** Naziv kanala gore; dole emisija sa trakom koliko je odmakla i dugmad. */
@Composable
private fun LiveControls(
    ui: PlayerUiState,
    nowNext: NowNext,
    playback: PlaybackState,
    viewModel: PlayerViewModel,
    isFavorite: Boolean,
    firstChipFocus: FocusRequester,
    onChannels: () -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onAspect: () -> Unit,
    onSettings: () -> Unit,
    infoVisible: Boolean,
    onInfo: () -> Unit,
    onFavorite: () -> Unit,
    isRecording: Boolean,
    onRecord: () -> Unit,
) {
    val channel = ui.channel ?: return
    val now by rememberNow()
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.align(Alignment.TopStart)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 27.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TitleBackIcon()
                PlayerTitle(
                    title = listOfNotNull(channel.number?.toString(), channel.name).joinToString("  "),
                    subtitle = nowNext.now?.title,
                    large = true,
                    modifier = Modifier.weight(1f),
                )
                // Dok se kuca broj kanala, na tom mestu stoji broj.
                if (ui.typedNumber.isEmpty()) {
                    PlayerClock(large = true, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomStart)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 27.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val program = nowNext.now
                val paused = playback.status == PlaybackStatus.PAUSED
                val failed = playback.status == PlaybackStatus.FAILED || ui.sourceMissing
                PlayerTransportBar(
                    startLabel = program?.let { formatTime(it.startMs) },
                    endLabel = program?.let { formatTime(it.stopMs) },
                    leading = {
                        PlayerIconButton(
                            icon = AppIcons.Previous,
                            description = stringResource(R.string.player_previous),
                            onClick = viewModel::previous,
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
                                .focusRequester(firstChipFocus),
                        )
                    },
                    trailing = {
                        PlayerResolution(viewModel.player, modifier = Modifier.padding(horizontal = 8.dp))
                        PlayerIconButton(
                            icon = AppIcons.Next,
                            description = stringResource(R.string.player_next),
                            onClick = viewModel::next,
                        )
                        PlayerIconButton(
                            icon = AppIcons.AspectRatio,
                            description = ui.aspectMode.label(),
                            onClick = onAspect,
                        )
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
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlayerChip(
                        icon = AppIcons.ChannelList,
                        label = stringResource(R.string.player_channels),
                        onClick = onChannels,
                    )
                    PlayerChip(
                        icon = AppIcons.Subtitles,
                        label = stringResource(R.string.player_subtitles),
                        active = playback.textEnabled,
                        onClick = onSubtitles,
                    )
                    PlayerChip(icon = AppIcons.Audio, label = stringResource(R.string.player_audio), onClick = onAudio)
                    PlayerChip(icon = AppIcons.AspectRatio, label = ui.aspectMode.label(), onClick = onAspect)
                    PlayerChip(
                        icon = AppIcons.Settings,
                        label = stringResource(R.string.settings_title),
                        onClick = onSettings,
                    )
                    PlayerChip(
                        icon = AppIcons.Info,
                        label = stringResource(R.string.player_stream_info),
                        active = infoVisible,
                        onClick = onInfo,
                    )
                    PlayerChip(
                        icon = if (isFavorite) AppIcons.Star else AppIcons.StarOutline,
                        label = stringResource(R.string.category_favorites),
                        active = isFavorite,
                        onClick = onFavorite,
                    )
                    // Isto dugme počinje i zaustavlja snimanje (menja se samo natpis), pa fokus ostaje na njemu.
                    PlayerChip(
                        icon = AppIcons.Record,
                        label = stringResource(if (isRecording) R.string.recording_chip_active else R.string.recording_chip),
                        active = isRecording,
                        onClick = onRecord,
                    )
                }
            }
        }
    }
}

/** Lista kanala preko slike; fokus kreće od kanala koji se trenutno gleda. */
@Composable
private fun ChannelListPanel(
    viewModel: PlayerViewModel,
    ui: PlayerUiState,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val channels = viewModel.channels.collectAsLazyPagingItems()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ui.position)
    val currentFocus = remember { FocusRequester() }
    var focusPlaced by remember { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.4f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(start = 48.dp, end = 16.dp, top = 27.dp, bottom = 27.dp),
    ) {
        items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
            val channel = channels[index]
            val isCurrent = channel != null && channel.id == ui.channel?.id
            TvListRow(
                selected = isCurrent,
                onClick = {
                    channel?.let(viewModel::playChannel)
                    onPick()
                },
                modifier = if (isCurrent) Modifier.focusRequester(currentFocus) else Modifier,
            ) { color ->
                ChannelLogo(url = channel?.logo, name = channel?.name, size = 32.dp)
                Text(
                    text = channel?.name.orEmpty(),
                    color = color,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
            }
            if (isCurrent && !focusPlaced) {
                LaunchedEffect(Unit) {
                    runCatching { currentFocus.requestFocus() }
                    focusPlaced = true
                }
            }
        }
    }
}

private const val INFO_TIMEOUT_MS = 4_000L
private const val CONTROLS_TIMEOUT_MS = 6_000L
private const val FOCUS_ATTEMPTS = 10
private const val FOCUS_STEP_MS = 50L
