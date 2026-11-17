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
import androidx.compose.ui.platform.LocalContext
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
import com.bane23.iptvplayer.ui.common.player.PlayerSeekBar
import com.bane23.iptvplayer.ui.common.player.PlayerResolution
import com.bane23.iptvplayer.ui.common.player.PlayerTransportBar
import com.bane23.iptvplayer.ui.common.player.formatDuration
import com.bane23.iptvplayer.ui.common.player.PlayerClock
import com.bane23.iptvplayer.ui.common.player.PlayerTitle
import com.bane23.iptvplayer.ui.common.player.PlayerStreamInfo
import com.bane23.iptvplayer.ui.common.player.VideoSurface
import com.bane23.iptvplayer.ui.common.player.quickSettingChoices
import com.bane23.iptvplayer.ui.common.player.seekStepFor
import com.bane23.iptvplayer.ui.common.player.statusMessage
import com.bane23.iptvplayer.ui.common.subtitles.appliedSubtitleToast
import com.bane23.iptvplayer.ui.common.vod.VodPlayerViewModel
import com.bane23.iptvplayer.ui.common.vod.episodeLabel
import kotlinx.coroutines.delay

/** Šta dobija fokus kad se kontrole pojave. */
private enum class VodFocus { PLAY, SEEK }

/**
 * Plejer za film ili epizodu na TV-u. Dok su kontrole sklonjene: OK (ili gore/dole) ih prikazuje,
 * levo/desno premotava (držanje premotava sve brže; plejer skače tek kad se strelica pusti, a dotle
 * se na traci vidi vreme na koje se stiže). Kad su kontrole prikazane, traka u fokusu premotava tek
 * posle potvrde sa OK, da se strelicama lako pređe preko nje do dugmadi. Kontrole su iste kao na telefonu: naslov gore,
 * a dole traka sa vremenom i dugmad Titlovi, Audio i Podešavanja (tu je i odnos slike). Nazad prvo skloni kontrole.
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
    // Traka premotava tek kad se potvrdi sa OK; do tada levo/desno samo pomera fokus preko nje.
    var seekArmed by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf<TvTrackKind?>(null) }
    // Informacije o strimu; ostaju preko slike i kad se kontrole sklone, dok se ne ugase istim dugmetom.
    var infoVisible by remember { mutableStateOf(false) }
    // Spisak titlova sa interneta; otvara se iz spiska titlova.
    var onlineOpen by remember { mutableStateOf(false) }
    // Panel „Brza podešavanja” je otvoren.
    var settingsOpen by remember { mutableStateOf(false) }
    val online by viewModel.online.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Svaki pritisak tastera povećava brojač, čime se kontrole zadržavaju još nekoliko sekundi.
    var activity by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val failed = playback.status == PlaybackStatus.FAILED || ui.sourceMissing

    LaunchedEffect(Unit) { start(viewModel) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    KeepScreenOn(playback.status)
    BackHandler(enabled = tracks != null || onlineOpen || settingsOpen || controls) {
        when {
            settingsOpen -> settingsOpen = false
            onlineOpen -> {
                onlineOpen = false
                viewModel.closeOnlineSubtitles()
            }
            tracks != null -> tracks = null
            seekArmed -> {
                viewModel.commitScrub()
                seekArmed = false
            }
            else -> controls = false
        }
    }
    // Titl je preuzet i uključen: spisak se zatvara, a kratka poruka javlja koliko preuzimanja je ostalo.
    LaunchedEffect(online.appliedId) {
        if (online.appliedId != null) {
            appliedSubtitleToast(context, online.remaining)
            onlineOpen = false
            viewModel.closeOnlineSubtitles()
        }
    }

    LaunchedEffect(controls, tracks, onlineOpen, settingsOpen, activity, playback.status, ui.scrubMs) {
        // Dok traje premotavanje kontrole ostaju, da se vidi pregled vremena.
        if (controls && tracks == null && !onlineOpen && !settingsOpen && ui.scrubMs == null && playback.status == PlaybackStatus.PLAYING) {
            delay(CONTROLS_TIMEOUT_MS)
            controls = false
        }
    }
    // Fokus prati ono što je na ekranu, inače tasteri ne bi stizali nikuda.
    LaunchedEffect(controls, tracks, onlineOpen, settingsOpen, focusOn) {
        when {
            tracks != null || onlineOpen || settingsOpen -> Unit
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
        // Premotavanje započeto strelicom dok su kontrole sklonjene nastavlja se bez potvrde.
        seekArmed = focus == VodFocus.SEEK
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

                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND, AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (isDown) {
                            val direction = if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_MEDIA_REWIND) -1 else 1
                            viewModel.scrubBy(direction * seekStepFor(event.nativeKeyEvent.repeatCount + 6, viewModel.seekStepMs))
                            // Kontrole se prikazuju da se na traci vidi dokle se stiglo.
                            if (!controls) show(VodFocus.SEEK)
                        } else if (isUp) {
                            viewModel.commitScrub()
                        }
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
                            viewModel.scrubBy(direction * seekStepFor(event.nativeKeyEvent.repeatCount, viewModel.seekStepMs))
                            show(VodFocus.SEEK)
                        } else if (isUp) {
                            viewModel.commitScrub()
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
        if (infoVisible) {
            PlayerStreamInfo(
                player = viewModel.player,
                technical = playback.video,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 96.dp, end = 48.dp),
            )
        }

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
                val season = ui.season
                val episode = ui.episode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 27.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TitleBackIcon()
                    PlayerTitle(
                        title = if (season != null && episode != null) {
                            "${episodeLabel(season, episode)}  ·  ${ui.title}"
                        } else {
                            ui.title
                        },
                        subtitle = null,
                        large = true,
                        modifier = Modifier.weight(1f),
                    )
                    PlayerClock(large = true, modifier = Modifier.padding(start = 16.dp))
                }
            }

            Box(modifier = Modifier.align(Alignment.BottomStart)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 27.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val paused = playback.status == PlaybackStatus.PAUSED
                    val scrub = ui.scrubMs
                    if (scrub != null) {
                        // Pregled dok se premotava: vreme na koje se stiže i pomak od početnog mesta.
                        val delta = scrub - ui.scrubFromMs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = formatDuration(scrub),
                                color = Color.White,
                                style = MaterialTheme.typography.displaySmall,
                            )
                            Text(
                                text = (if (delta < 0) "−" else "+") + formatDuration(kotlin.math.abs(delta)),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                            )
                        }
                    }
                    PlayerTransportBar(
                        startLabel = formatDuration(scrub ?: ui.positionMs),
                        endLabel = formatDuration(ui.durationMs),
                        leading = {
                            PlayerIconButton(
                                icon = AppIcons.FastRewind,
                                description = stringResource(R.string.vod_seek_back, (viewModel.seekStepMs / 1_000).toInt()),
                                onClick = { viewModel.seekBy(-viewModel.seekStepMs) },
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
                            PlayerResolution(viewModel.player, modifier = Modifier.padding(horizontal = 8.dp))
                            PlayerIconButton(
                                icon = AppIcons.FastForward,
                                description = stringResource(R.string.vod_seek_forward, (viewModel.seekStepMs / 1_000).toInt()),
                                onClick = { viewModel.seekBy(viewModel.seekStepMs) },
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
                            positionMs = scrub ?: ui.positionMs,
                            durationMs = ui.durationMs,
                            onSeekTo = viewModel::seekTo,
                            onSeekBy = viewModel::scrubBy,
                            modifier = barModifier.focusRequester(seekFocus),
                            stepMs = viewModel.seekStepMs,
                            onSeekEnd = viewModel::commitScrub,
                            armed = seekArmed,
                            onArmedChange = { seekArmed = it },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlayerChip(
                            icon = AppIcons.Subtitles,
                            label = stringResource(R.string.player_subtitles),
                            active = playback.textEnabled,
                            onClick = { tracks = TvTrackKind.SUBTITLES },
                        )
                        PlayerChip(
                            icon = AppIcons.Audio,
                            label = stringResource(R.string.player_audio),
                            onClick = { tracks = TvTrackKind.AUDIO },
                        )
                        PlayerChip(
                            icon = AppIcons.Settings,
                            label = stringResource(R.string.settings_title),
                            // Brzina koja nije obična se vidi i na samom dugmetu.
                            active = ui.speed != 1f,
                            onClick = { settingsOpen = true },
                        )
                        PlayerChip(
                            icon = AppIcons.Info,
                            label = stringResource(R.string.player_stream_info),
                            active = infoVisible,
                            onClick = { infoVisible = !infoVisible },
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
                onSearchOnline = if (ui.onlineSubtitles) {
                    {
                        tracks = null
                        onlineOpen = true
                        viewModel.searchOnlineSubtitles()
                    }
                } else {
                    null
                },
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
                    speed = ui.speed,
                    onSpeed = viewModel::setSpeed,
                ),
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        if (onlineOpen) {
            TvOnlineSubtitlesPanel(
                state = online,
                onPick = viewModel::downloadSubtitle,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

private const val CONTROLS_TIMEOUT_MS = 5_000L
private const val FOCUS_ATTEMPTS = 10
private const val FOCUS_STEP_MS = 50L
