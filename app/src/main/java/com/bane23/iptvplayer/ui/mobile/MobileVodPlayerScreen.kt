package com.bane23.iptvplayer.ui.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.KeepScreenOn
import com.bane23.iptvplayer.ui.common.player.PlayerChip
import com.bane23.iptvplayer.ui.common.player.PlayerIconButton
import com.bane23.iptvplayer.ui.common.player.PlayerSeekBar
import com.bane23.iptvplayer.ui.common.player.PlayerStreamInfo
import com.bane23.iptvplayer.ui.common.player.PlayerTransportBar
import com.bane23.iptvplayer.ui.common.player.formatDuration
import com.bane23.iptvplayer.ui.common.player.VideoSurface
import com.bane23.iptvplayer.ui.common.player.quickSettingChoices
import com.bane23.iptvplayer.ui.common.player.statusMessage
import com.bane23.iptvplayer.ui.common.vod.VodPlayerViewModel
import com.bane23.iptvplayer.ui.common.vod.episodeLabel
import kotlinx.coroutines.delay

private enum class VodPanel { NONE, AUDIO, SUBTITLES, ONLINE_SUBTITLES, SETTINGS }

/**
 * Plejer za film ili epizodu na telefonu, sa istim kontrolama kao na TV-u. Dodir prikazuje i
 * sklanja kontrole, dupli dodir levo ili desno premotava 10 s, prevlačenje gore-dole menja
 * osvetljenje (levo) i jačinu zvuka (desno). [start] pokreće puštanje (poziva se jednom).
 */
@Composable
fun MobileVodPlayerScreen(
    start: (VodPlayerViewModel) -> Unit,
    onClose: () -> Unit,
    onCastStarted: () -> Unit = {},
    viewModel: VodPlayerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(VodPanel.NONE) }
    val levels = rememberPlayerLevels()
    var locked by remember { mutableStateOf(false) }
    var infoVisible by remember { mutableStateOf(false) }
    var unlockVisible by remember { mutableStateOf(false) }
    // Svaki dodir na kontrolama odlaže njihovo sklanjanje.
    var activity by remember { mutableIntStateOf(0) }
    // Pozicija pod prstom dok se prevlači traka.
    var preview by remember { mutableStateOf<Long?>(null) }
    val failed = playback.status == PlaybackStatus.FAILED || ui.sourceMissing

    LaunchedEffect(Unit) { start(viewModel) }
    KeepScreenOn(playback.status)
    ImmersiveMode()
    val inPip = rememberIsInPictureInPicture()
    PlayerLifecycle(
        player = viewModel.player,
        title = ui.title,
        inPip = inPip,
        onTogglePlayPause = viewModel::togglePlayPause,
        onForeground = viewModel::onForeground,
        onBackground = viewModel::onBackground,
    )
    val cast = rememberCastLauncher(media = { viewModel.castMedia() }, onStarted = onCastStarted)
    PlayerPictureInPicture(playback.status, viewModel::togglePlayPause)
    // Zaključan ekran ne izlazi ni na Nazad, samo pokaže dugme za otključavanje.
    BackHandler(enabled = locked) { unlockVisible = true }
    BackHandler(enabled = panel == VodPanel.SETTINGS) { panel = VodPanel.NONE }

    LaunchedEffect(controlsVisible, panel, playback.status, activity, preview) {
        if (controlsVisible && panel == VodPanel.NONE && preview == null && playback.status == PlaybackStatus.PLAYING) {
            delay(VOD_CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(unlockVisible) {
        if (unlockVisible) {
            delay(VOD_CONTROLS_TIMEOUT_MS)
            unlockVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = {
                        when {
                            locked -> unlockVisible = !unlockVisible
                            panel == VodPanel.SETTINGS -> panel = VodPanel.NONE
                            else -> controlsVisible = !controlsVisible
                        }
                    },
                    // Dupli tap: leva i desna trećina slike premotavaju, sredina pauzira ili pušta.
                    onDoubleTap = { offset ->
                        if (!locked && panel != VodPanel.SETTINGS) {
                            when {
                                offset.x < size.width / 3 -> viewModel.seekBy(-viewModel.seekStepMs)
                                offset.x > size.width * 2 / 3 -> viewModel.seekBy(viewModel.seekStepMs)
                                else -> viewModel.togglePlayPause()
                            }
                            activity++
                        }
                    },
                )
            },
    ) {
        VideoSurface(player = viewModel.player, aspectMode = ui.aspectMode, modifier = Modifier.fillMaxSize())

        // U slici u slici ostaje samo video, kao kod kanala.
        if (inPip) return@Box

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

        // Ostaje preko slike i kad se kontrole sklone, dok se ne ugasi istim dugmetom.
        if (infoVisible) {
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
            return@Box
        }

        if (panel == VodPanel.SETTINGS) {
            // Trake ostaju na ekranu, da se odmah vidi šta prekidač radi.
            PlayerSideSliders(levels) { }
            MobileQuickSettingsPanel(
                quickSettingChoices(
                    tv = false,
                    aspectMode = ui.aspectMode,
                    onAspectMode = viewModel::setAspectMode,
                    decoder = ui.decoder,
                    onDecoder = viewModel::setDecoder,
                    speed = ui.speed,
                    onSpeed = viewModel::setSpeed,
                    subtitleOffsetMs = ui.subtitleOffsetMs,
                    onShiftSubtitle = viewModel::shiftSubtitle,
                ),
            )
        } else if (controlsVisible) {
            PlayerSideSliders(levels) { activity++ }
            val season = ui.season
            val episode = ui.episode
            MobilePlayerControls(
                title = if (season != null && episode != null) {
                    "${episodeLabel(season, episode)}  ·  ${ui.title}"
                } else {
                    ui.title
                },
                subtitle = null,
                onClose = onClose,
                onLock = {
                    locked = true
                    controlsVisible = false
                },
                onPip = rememberEnterPictureInPicture(),
                onCast = cast,
                bottom = {
                    // Premotavanje na krajevima, pauza posle premotavanja unazad, traka u sredini.
                    PlayerTransportBar(
                        startLabel = formatDuration(preview ?: ui.positionMs),
                        endLabel = formatDuration(ui.durationMs),
                        leading = {
                            PlayerIconButton(
                                icon = AppIcons.FastRewind,
                                description = stringResource(R.string.vod_seek_back, (viewModel.seekStepMs / 1_000).toInt()),
                                onClick = {
                                    viewModel.seekBy(-viewModel.seekStepMs)
                                    activity++
                                },
                            )
                            PlayPauseButton(
                                paused = playback.status == PlaybackStatus.PAUSED,
                                failed = failed,
                                onPlayPause = {
                                    viewModel.togglePlayPause()
                                    activity++
                                },
                                onRetry = viewModel::retry,
                            )
                        },
                        trailing = {
                            PlayerIconButton(
                                icon = AppIcons.FastForward,
                                description = stringResource(R.string.vod_seek_forward, (viewModel.seekStepMs / 1_000).toInt()),
                                onClick = {
                                    viewModel.seekBy(viewModel.seekStepMs)
                                    activity++
                                },
                            )
                        },
                    ) { barModifier ->
                        PlayerSeekBar(
                            positionMs = ui.positionMs,
                            durationMs = ui.durationMs,
                            onSeekTo = viewModel::seekTo,
                            onSeekBy = viewModel::seekBy,
                            onPreview = { preview = it },
                            modifier = barModifier,
                        )
                    }
                },
                chips = {
                    PlayerChip(
                        icon = AppIcons.Subtitles,
                        label = stringResource(R.string.player_subtitles),
                        active = playback.textEnabled,
                        onClick = { panel = VodPanel.SUBTITLES },
                    )
                    PlayerChip(
                        icon = AppIcons.Audio,
                        label = stringResource(R.string.player_audio),
                        onClick = { panel = VodPanel.AUDIO },
                    )
                    PlayerChip(
                        icon = AppIcons.Settings,
                        label = stringResource(R.string.settings_title),
                        // Brzina koja nije obična se vidi i na samom dugmetu.
                        active = ui.speed != 1f,
                        onClick = { panel = VodPanel.SETTINGS },
                    )
                    PlayerChip(
                        icon = AppIcons.Info,
                        label = stringResource(R.string.player_stream_info),
                        active = infoVisible,
                        onClick = {
                            infoVisible = !infoVisible
                            activity++
                        },
                    )
                    // Na TV-u je sledeća epizoda ikonica na traci; ovde bi suzila traku za premotavanje.
                    if (ui.hasNext) {
                        PlayerChip(
                            icon = AppIcons.Next,
                            label = stringResource(R.string.vod_next_episode),
                            onClick = viewModel::next,
                        )
                    }
                },
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
            extra = if (ui.onlineSubtitles) {
                stringResource(R.string.subtitles_search_online) to {
                    viewModel.searchOnlineSubtitles()
                    panel = VodPanel.ONLINE_SUBTITLES
                }
            } else {
                null
            },
        )

        VodPanel.ONLINE_SUBTITLES -> {
            val online by viewModel.online.collectAsStateWithLifecycle()
            OnlineSubtitlesDialog(
                state = online,
                onPick = viewModel::downloadSubtitle,
                onDismiss = {
                    viewModel.closeOnlineSubtitles()
                    panel = VodPanel.NONE
                },
            )
        }

        VodPanel.NONE, VodPanel.SETTINGS -> Unit
    }
}

private const val VOD_CONTROLS_TIMEOUT_MS = 4_000L
