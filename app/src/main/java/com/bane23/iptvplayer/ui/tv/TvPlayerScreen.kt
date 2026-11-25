package com.bane23.iptvplayer.ui.tv

import android.view.KeyEvent as AndroidKeyEvent
import com.bane23.iptvplayer.ui.common.FavoriteListsViewModel
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
import com.bane23.iptvplayer.ui.tv.text.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.recording.RecordingNotices
import com.bane23.iptvplayer.ui.common.recording.recordingChoices
import com.bane23.iptvplayer.ui.common.recording.rememberRecordingToggle
import com.bane23.iptvplayer.domain.NowNext
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.ui.common.ChannelLogo
import com.bane23.iptvplayer.ui.common.guide.formatTime
import com.bane23.iptvplayer.ui.common.guide.infoLines
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
import com.bane23.iptvplayer.ui.common.player.PlayerMenu
import com.bane23.iptvplayer.ui.common.player.PlayerMenuAnchors
import com.bane23.iptvplayer.ui.common.player.PlayerMenuBubble
import com.bane23.iptvplayer.ui.common.player.playerMenuAnchor
import com.bane23.iptvplayer.ui.common.player.playerMenuBar
import com.bane23.iptvplayer.ui.common.player.rememberPlayerMenuAnchors
import com.bane23.iptvplayer.ui.common.player.PlayerProgress
import com.bane23.iptvplayer.ui.common.player.PlayerResolution
import com.bane23.iptvplayer.ui.common.player.PlayerTransportBar
import com.bane23.iptvplayer.domain.REMOTE_LONG_PRESS_MS
import com.bane23.iptvplayer.domain.RemoteAction
import com.bane23.iptvplayer.domain.RemoteButton
import com.bane23.iptvplayer.ui.common.player.PlayerClock
import com.bane23.iptvplayer.ui.common.player.RemoteButtonsViewModel
import com.bane23.iptvplayer.ui.common.player.PlayerTitle
import com.bane23.iptvplayer.ui.common.player.PlayerUiState
import com.bane23.iptvplayer.ui.common.player.SourceNotices
import com.bane23.iptvplayer.ui.common.player.PlayerViewModel
import com.bane23.iptvplayer.ui.common.player.PlayerStreamInfo
import com.bane23.iptvplayer.ui.common.player.VideoSurface
import com.bane23.iptvplayer.ui.common.player.label
import com.bane23.iptvplayer.ui.common.player.quickSettingChoices
import com.bane23.iptvplayer.ui.common.player.statusMessage
import com.bane23.iptvplayer.ui.common.rememberNow
import kotlinx.coroutines.delay

private fun Int.isOkKey(): Boolean = this == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
    this == AndroidKeyEvent.KEYCODE_ENTER ||
    this == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER

/** Kontrole preko slike: sklonjene, samo prikazane posle promene kanala, ili u fokusu. */
private enum class Overlay { HIDDEN, INFO, ACTIVE }

/**
 * Plejer kanala na TV-u, upravlja se samo daljinskim. Dok su kontrole sklonjene: gore/dole menja
 * kanal, levo otvara listu kanala, OK (ili desno) prikazuje kontrole, cifre biraju kanal po broju.
 * Obojena dugmad, kanal gore i dole, meni i dugo OK rade ono što je izabrano u Podešavanjima.
 * Kontrole izgledaju kao u plejeru filmova: naziv kanala gore, a dole emisija sa trakom i dugmad
 * Kanali, Audio, Podešavanja i Omiljeni. Odnos slike se menja na kraju trake i u Podešavanjima. Nazad prvo zatvara spisak ili kontrole.
 */
@Composable
fun TvPlayerScreen(
    channelId: Long,
    categoryId: Long?,
    onClose: () -> Unit,
    onArchive: (channelId: Long) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val nowNext by viewModel.nowNext.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    RecordingNotices(viewModel.recordingNotice)
    SourceNotices(viewModel.sourceNotice)
    val favoriteLists: FavoriteListsViewModel = hiltViewModel()
    TvFavoritePickDialog(favoriteLists)
    var overlay by remember { mutableStateOf(Overlay.INFO) }
    var channelList by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf<TvTrackKind?>(null) }
    // Informacije o strimu; ostaju preko slike i kad se kontrole sklone, dok se ne ugase istim dugmetom.
    var infoVisible by remember { mutableStateOf(false) }
    // Izbor trajanja snimanja je otvoren.
    var recordPanel by remember { mutableStateOf(false) }
    // Panel „Brza podešavanja” je otvoren.
    var settingsOpen by remember { mutableStateOf(false) }
    // Panel „Program” (raspored kanala) je otvoren.
    var scheduleOpen by remember { mutableStateOf(false) }
    // Panel „Izvori” (isti kanal iz spojenih lista) je otvoren.
    var sourcesOpen by remember { mutableStateOf(false) }
    // Meniji su oblačići iznad svojih dugmića; kad se meni zatvori, fokus se vraća na to dugme.
    val anchors = rememberPlayerMenuAnchors()
    val chipFocus = remember { PlayerMenu.entries.associateWith { FocusRequester() } }
    val returnFocus = remember { arrayOfNulls<FocusRequester>(1) }
    // Meni se otvara iznad svog dugmeta, pa dugmići moraju da budu na ekranu.
    fun openMenu(menu: PlayerMenu) {
        returnFocus[0] = chipFocus[menu]
        overlay = Overlay.ACTIVE
        when (menu) {
            PlayerMenu.SUBTITLES -> tracks = TvTrackKind.SUBTITLES
            PlayerMenu.AUDIO -> tracks = TvTrackKind.AUDIO
            PlayerMenu.SETTINGS -> settingsOpen = true
            PlayerMenu.SOURCES -> sourcesOpen = true
            PlayerMenu.RECORD -> recordPanel = true
        }
    }
    val openedMenu = when {
        tracks == TvTrackKind.SUBTITLES -> PlayerMenu.SUBTITLES
        tracks == TvTrackKind.AUDIO -> PlayerMenu.AUDIO
        settingsOpen -> PlayerMenu.SETTINGS
        sourcesOpen -> PlayerMenu.SOURCES
        recordPanel -> PlayerMenu.RECORD
        else -> null
    }
    // Dugme „Snimaj” zaustavlja snimanje koje traje, a inače prvo pita koliko dugo da snima.
    val record = rememberRecordingToggle(isRecording) {
        if (isRecording) viewModel.toggleRecording() else openMenu(PlayerMenu.RECORD)
    }
    // Šta koje dugme daljinskog radi (Podešavanja → „Dugmad daljinskog”).
    val remoteButtons: RemoteButtonsViewModel = hiltViewModel()
    val remote by remoteButtons.mapping.collectAsStateWithLifecycle()
    // Dugo OK je već izvršilo svoju radnju; ostatak tog pritiska se preskače.
    var longOkFired by remember { mutableStateOf(false) }
    var activity by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val firstChipFocus = remember { FocusRequester() }
    val panelOpen = channelList || tracks != null || recordPanel || settingsOpen || scheduleOpen || sourcesOpen

    LaunchedEffect(channelId) { viewModel.start(channelId, categoryId) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    KeepScreenOn(playback.status)
    // Pauza: posle kratkog čekanja ekran pauze, a posle dužeg vremena plejer se zatvara.
    val pauseInfoOn = rememberPauseInfoEnabled()
    val pauseDim = rememberPauseDim(playback.status, info = !panelOpen, describe = pauseInfoOn, onExit = onClose)
    MatchDisplayFrameRate(playback.video.frameRate)
    val backCloses = panelOpen || overlay != Overlay.HIDDEN

    // Nazad zatvara ono što je na vrhu: prvo otvoren panel, pa kontrole.
    fun closeTop() {
        when {
            tracks != null -> tracks = null
            recordPanel -> recordPanel = false
            settingsOpen -> settingsOpen = false
            scheduleOpen -> scheduleOpen = false
            sourcesOpen -> sourcesOpen = false
            channelList -> channelList = false
            else -> overlay = Overlay.HIDDEN
        }
    }
    BackHandler(enabled = backCloses) { closeTop() }

    fun perform(action: RemoteAction) {
        when (action) {
            RemoteAction.NONE -> Unit
            RemoteAction.LAST_CHANNEL -> viewModel.playLastChannel()
            RemoteAction.NEXT_CHANNEL -> viewModel.next()
            RemoteAction.PREVIOUS_CHANNEL -> viewModel.previous()
            RemoteAction.CHANNEL_LIST -> channelList = true
            RemoteAction.SCHEDULE -> scheduleOpen = true
            RemoteAction.FAVORITE -> {
                ui.channel?.let { favoriteLists.favoriteAction(it.id) }
                // Zvezdica je među kontrolama, pa se one kratko pokažu da se vidi šta se promenilo.
                if (overlay == Overlay.HIDDEN) overlay = Overlay.INFO
            }
            RemoteAction.AUDIO -> openMenu(PlayerMenu.AUDIO)
            RemoteAction.SUBTITLES -> openMenu(PlayerMenu.SUBTITLES)
            RemoteAction.QUICK_SETTINGS -> openMenu(PlayerMenu.SETTINGS)
            RemoteAction.INFO -> infoVisible = !infoVisible
            RemoteAction.CONTROLS -> overlay = Overlay.ACTIVE
        }
    }

    // Radnja koja se ponavlja kreće na pritisak; ostale kad se dugme pusti, da isti pritisak
    // ne bi odmah nešto izabrao u spisku koji se otvorio.
    fun press(button: RemoteButton, isDown: Boolean, isUp: Boolean) {
        val action = remote[button] ?: button.defaultAction
        if (if (action.repeats) isDown else isUp) perform(action)
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
                if ((returnFocus[0] ?: firstChipFocus).tryRequestFocus()) {
                    returnFocus[0] = null
                    return@LaunchedEffect
                }
                // Dugme na koje fokus treba da se vrati više nije na ekranu.
                if (it == FOCUS_ATTEMPTS / 2) returnFocus[0] = null
                delay(FOCUS_STEP_MS)
            }
            else -> rootFocus.tryRequestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pauseDimInput(pauseDim)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) activity++
                // Posle dugog OK ostatak pritiska ne sme da stigne do spiska koji se upravo otvorio.
                if (longOkFired && it.nativeKeyEvent.keyCode.isOkKey()) {
                    val fresh = it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0
                    if (it.type == KeyEventType.KeyUp || fresh) longOkFired = false
                    // Nov pritisak (puštanje starog je otišlo u prozor za izbor) se više ne preskače.
                    if (!fresh) return@onPreviewKeyEvent true
                }
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
                // Nazad se obrađuje ovde, inače bi ga Compose potrošio na pomeranje fokusa sa dugmeta
                // na plejer, pa bi kontrole ostale na ekranu bez fokusa.
                if (code == AndroidKeyEvent.KEYCODE_BACK && backCloses) {
                    if (isUp) closeTop()
                    return@onKeyEvent true
                }
                val button = RemoteButton.ofKey(code)
                // Obojena dugmad nemaju drugu ulogu, pa rade i dok su kontrole na ekranu.
                if (button != null && button.isColor && !panelOpen) {
                    press(button, isDown, isUp)
                    return@onKeyEvent true
                }
                // Ostali tasteri ovde stižu samo dok je fokus na samom plejeru.
                if (overlay == Overlay.ACTIVE || panelOpen) return@onKeyEvent false
                if (button != null) {
                    press(button, isDown, isUp)
                    return@onKeyEvent true
                }
                when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                        if (isDown) viewModel.previous()
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (isDown) viewModel.next()
                        true
                    }

                    // Taster „prethodni kanal” na daljinskim koji ga imaju.
                    AndroidKeyEvent.KEYCODE_LAST_CHANNEL -> {
                        if (isDown) viewModel.playLastChannel()
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
                    -> {
                        val native = event.nativeKeyEvent
                        val longAction = remote[RemoteButton.LONG_OK] ?: RemoteButton.LONG_OK.defaultAction
                        val held = native.repeatCount > 0 && native.eventTime - native.downTime >= REMOTE_LONG_PRESS_MS
                        if (isDown && held && longAction != RemoteAction.NONE) {
                            // Dugo OK: radnja kreće dok se dugme još drži; kratko OK i dalje otvara kontrole.
                            longOkFired = true
                            perform(longAction)
                        } else if (isUp) {
                            overlay = Overlay.ACTIVE
                        }
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
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

        PauseDimLayer(
            state = pauseDim,
            info = { ui.channel?.let { PauseInfoScreen(livePauseInfo(it.name, nowNext), compact = false) } },
        ) {
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
                    onSchedule = { scheduleOpen = true },
                    onSources = { openMenu(PlayerMenu.SOURCES) },
                    onAudio = { openMenu(PlayerMenu.AUDIO) },
                    onSettings = { openMenu(PlayerMenu.SETTINGS) },
                    anchors = anchors,
                    chipFocus = chipFocus,
                    openedMenu = openedMenu,
                    infoVisible = infoVisible,
                    onInfo = { infoVisible = !infoVisible },
                    onFavorite = { ui.channel?.let { favoriteLists.favoriteAction(it.id) } },
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
                val menu = if (kind == TvTrackKind.SUBTITLES) PlayerMenu.SUBTITLES else PlayerMenu.AUDIO
                PlayerMenuBubble(anchor = anchors[menu], width = TvTrackMenuWidth) {
                    TvTrackPanel(
                        kind = kind,
                        playback = playback,
                        onSelectAudio = viewModel::selectAudio,
                        onSelectText = viewModel::selectText,
                        onClose = { tracks = null },
                    )
                }
            }

            if (settingsOpen) {
                PlayerMenuBubble(anchor = anchors[PlayerMenu.SETTINGS], width = TvSettingsMenuWidth) {
                    TvQuickSettingsPanel(
                        choices = quickSettingChoices(
                            tv = true,
                            aspectMode = ui.aspectMode,
                            onAspectMode = viewModel::setAspectMode,
                            decoder = ui.decoder,
                            onDecoder = viewModel::setDecoder,
                        ),
                    )
                }
            }

            val scheduleChannel = ui.channel
            if (scheduleOpen && scheduleChannel != null) {
                TvChannelSchedulePanel(
                    channel = scheduleChannel,
                    onArchive = { onArchive(scheduleChannel.id) },
                    onClose = { scheduleOpen = false },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            if (sourcesOpen) {
                val sources = ui.sources
                PlayerMenuBubble(anchor = anchors[PlayerMenu.SOURCES], width = TvSettingsMenuWidth) {
                    TvChoicePanel(
                        title = stringResource(R.string.player_sources),
                        options = sources.map { it.label(ui) },
                        onSelect = { index -> sources.getOrNull(index)?.let { viewModel.selectSource(it.channelId) } },
                        onClose = { sourcesOpen = false },
                        bubble = true,
                    )
                }
            }

            if (recordPanel) {
                val choices = recordingChoices(nowNext.now?.stopMs)
                PlayerMenuBubble(anchor = anchors[PlayerMenu.RECORD], width = TvTrackMenuWidth) {
                    TvChoicePanel(
                        title = stringResource(R.string.recording_duration_title),
                        options = choices.map { it.label },
                        onSelect = { index -> viewModel.toggleRecording(choices[index].stopAtMs()) },
                        onClose = { recordPanel = false },
                        bubble = true,
                    )
                }
            }
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
    onSchedule: () -> Unit,
    onSources: () -> Unit,
    onAudio: () -> Unit,
    onSettings: () -> Unit,
    infoVisible: Boolean,
    onInfo: () -> Unit,
    onFavorite: () -> Unit,
    isRecording: Boolean,
    onRecord: () -> Unit,
    anchors: PlayerMenuAnchors,
    chipFocus: Map<PlayerMenu, FocusRequester>,
    openedMenu: PlayerMenu?,
) {
    // Dugme koje otvara meni: pamti mu se mesto (za oblačić) i fokus se vraća na njega.
    fun Modifier.menuChip(menu: PlayerMenu): Modifier =
        this.focusRequester(chipFocus.getValue(menu)).playerMenuAnchor(anchors, menu)
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
                    modifier = Modifier.weight(1f),
                )
                // Dok se kuca broj kanala, na tom mestu stoji broj.
                if (ui.typedNumber.isEmpty()) {
                    PlayerClock(modifier = Modifier.padding(start = 16.dp))
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
                    // Meniji se otvaraju iznad trake, pa se njeno mesto pamti.
                    modifier = Modifier.playerMenuBar(anchors),
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
                        if (ui.lastChannel != null) {
                            PlayerIconButton(
                                icon = AppIcons.Swap,
                                description = stringResource(R.string.player_last_channel),
                                onClick = viewModel::playLastChannel,
                            )
                        }
                        PlayerIconButton(
                            icon = AppIcons.AspectRatio,
                            description = ui.aspectMode.label(),
                            onClick = viewModel::cycleAspectMode,
                        )
                    },
                ) { barModifier ->
                    val length = program?.let { (it.stopMs - it.startMs).coerceAtLeast(1) }
                    PlayerProgress(
                        fraction = if (program != null && length != null) (now - program.startMs).toFloat() / length else 0f,
                        modifier = barModifier,
                    )
                }
                nowNext.infoLines(now)?.let { line ->
                    Text(
                        text = line,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
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
                    PlayerChip(icon = AppIcons.Guide, label = stringResource(R.string.player_schedule), onClick = onSchedule)
                    // Isti kanal postoji i u spojenoj listi: može da se izabere odakle se gleda.
                    if (ui.sources.size > 1) {
                        PlayerChip(
                            icon = AppIcons.Swap,
                            label = stringResource(R.string.player_sources),
                            open = openedMenu == PlayerMenu.SOURCES,
                            onClick = onSources,
                            modifier = Modifier.menuChip(PlayerMenu.SOURCES),
                        )
                    }
                    PlayerChip(
                        icon = AppIcons.Audio,
                        label = stringResource(R.string.player_audio),
                        open = openedMenu == PlayerMenu.AUDIO,
                        onClick = onAudio,
                        modifier = Modifier.menuChip(PlayerMenu.AUDIO),
                    )
                    PlayerChip(
                        icon = AppIcons.Settings,
                        label = stringResource(R.string.settings_title),
                        open = openedMenu == PlayerMenu.SETTINGS,
                        onClick = onSettings,
                        modifier = Modifier.menuChip(PlayerMenu.SETTINGS),
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
                        open = openedMenu == PlayerMenu.RECORD,
                        onClick = onRecord,
                        modifier = Modifier.menuChip(PlayerMenu.RECORD),
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .tvKeepFocus()
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
