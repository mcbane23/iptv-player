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
import com.bane23.iptvplayer.ui.common.player.NextEpisodeCard
import com.bane23.iptvplayer.ui.common.player.KeepScreenOn
import com.bane23.iptvplayer.ui.common.player.PauseDimLayer
import com.bane23.iptvplayer.ui.common.player.PauseInfoScreen
import com.bane23.iptvplayer.ui.common.player.vodPauseInfo
import com.bane23.iptvplayer.ui.common.player.rememberPauseInfoEnabled
import com.bane23.iptvplayer.ui.common.player.pauseDimInput
import com.bane23.iptvplayer.ui.common.player.rememberPauseDim
import com.bane23.iptvplayer.ui.common.player.PlayerChip
import com.bane23.iptvplayer.ui.common.player.PlayerIconButton
import com.bane23.iptvplayer.ui.common.player.PlayerMenu
import com.bane23.iptvplayer.ui.common.player.PlayerMenuBubble
import com.bane23.iptvplayer.ui.common.player.playerMenuAnchor
import com.bane23.iptvplayer.ui.common.player.playerMenuBar
import com.bane23.iptvplayer.ui.common.player.rememberPlayerMenuAnchors
import com.bane23.iptvplayer.ui.common.player.PlayerSeekBar
import com.bane23.iptvplayer.ui.common.player.PlayerStreamInfo
import com.bane23.iptvplayer.ui.common.player.PlayerTransportBar
import com.bane23.iptvplayer.ui.common.player.formatDuration
import com.bane23.iptvplayer.ui.common.player.VideoSurface
import com.bane23.iptvplayer.ui.common.player.quickSettingChoices
import com.bane23.iptvplayer.ui.common.player.statusMessage
import com.bane23.iptvplayer.ui.common.player.subtitleStepChoices
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
    // Zaboravljena pauza: slika se zatamni, a posle dužeg vremena plejer se zatvara.
    // Titlovi, zvuk i podešavanja su oblačići iznad svojih dugmića: dodir pored njih ili Nazad ih
    // zatvara, a kontrole ostaju na ekranu. Titlovi sa interneta su drugi korak oblačića „Titlovi”:
    // Nazad iz njih vraća na spisak titlova.
    val sidePanel = panel != VodPanel.NONE
    fun closePanel(back: Boolean = false) {
        val online = panel == VodPanel.ONLINE_SUBTITLES
        if (online) viewModel.closeOnlineSubtitles()
        panel = if (online && back) VodPanel.SUBTITLES else VodPanel.NONE
    }
    val anchors = rememberPlayerMenuAnchors()
    val pauseInfoOn = rememberPauseInfoEnabled()
    val pauseDim = rememberPauseDim(
        status = playback.status,
        enabled = panel == VodPanel.NONE || sidePanel,
        info = pauseInfoOn && panel == VodPanel.NONE,
        onExit = onClose,
    )
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
    BackHandler(enabled = sidePanel) { closePanel(back = true) }

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
            .pauseDimInput(pauseDim)
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = {
                        viewModel.onUserActivity()
                        when {
                            locked -> unlockVisible = !unlockVisible
                            // Stanje se čita ovde, a ne iz ranije izračunate vrednosti, da dodir vidi otvoren meni.
                            panel != VodPanel.NONE -> closePanel()
                            else -> controlsVisible = !controlsVisible
                        }
                    },
                    // Dupli tap: leva i desna trećina slike premotavaju, sredina pauzira ili pušta.
                    onDoubleTap = { offset ->
                        if (!locked && panel == VodPanel.NONE) {
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
        VideoSurface(
            player = viewModel.player,
            aspectMode = ui.aspectMode,
            modifier = Modifier.fillMaxSize(),
            subtitleText = viewModel.subtitleText,
        )

        PauseDimLayer(
            state = pauseDim,
            info = {
                if (!inPip) {
                    PauseInfoScreen(
                        vodPauseInfo(ui.title, ui.positionMs, ui.durationMs, ui.meta, ui.description),
                        compact = true,
                    )
                }
            },
        ) {
            // U slici u slici ostaje samo video, kao kod kanala.
            if (inPip) return@PauseDimLayer

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
                return@PauseDimLayer
            }

            // Kartica se vidi dok su kontrole sklonjene; sa kontrolama na ekranu postoji dugme „Sledeća epizoda”.
            val nextCard = ui.nextCard
            if (nextCard != null && !controlsVisible && panel == VodPanel.NONE) {
                NextEpisodeCard(
                    card = nextCard,
                    tv = false,
                    onAccept = viewModel::acceptNext,
                    onCancel = viewModel::cancelNext,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .safeDrawingPadding()
                        .padding(16.dp),
                )
            }

            if (controlsVisible || sidePanel) {
                // Dok je meni otvoren trake ostaju na ekranu, da se odmah vidi šta prekidač radi.
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
                            // Meniji se otvaraju iznad trake, pa se njeno mesto pamti.
                            modifier = Modifier.playerMenuBar(anchors),
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
                            open = panel == VodPanel.SUBTITLES || panel == VodPanel.ONLINE_SUBTITLES,
                            onClick = { panel = VodPanel.SUBTITLES },
                            modifier = Modifier.playerMenuAnchor(anchors, PlayerMenu.SUBTITLES),
                        )
                        PlayerChip(
                            icon = AppIcons.Audio,
                            label = stringResource(R.string.player_audio),
                            open = panel == VodPanel.AUDIO,
                            onClick = { panel = VodPanel.AUDIO },
                            modifier = Modifier.playerMenuAnchor(anchors, PlayerMenu.AUDIO),
                        )
                        PlayerChip(
                            icon = AppIcons.Settings,
                            label = stringResource(R.string.settings_title),
                            // Brzina koja nije obična se vidi i na samom dugmetu.
                            active = ui.speed != 1f,
                            open = panel == VodPanel.SETTINGS,
                            onClick = { panel = VodPanel.SETTINGS },
                            modifier = Modifier.playerMenuAnchor(anchors, PlayerMenu.SETTINGS),
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

            when (panel) {
                VodPanel.SUBTITLES -> PlayerMenuBubble(anchors[PlayerMenu.SUBTITLES], MobileTrackMenuWidth) {
                    MobileChoiceMenu(
                        options = listOf(stringResource(R.string.player_subtitles_off) to !playback.textEnabled) +
                            playback.textTracks.map { it.label to it.selected },
                        onSelect = { index ->
                            viewModel.selectText(if (index == 0) null else playback.textTracks[index - 1])
                            panel = VodPanel.NONE
                        },
                        extra = if (ui.onlineSubtitles) {
                            stringResource(R.string.subtitles_search_online) to {
                                viewModel.searchOnlineSubtitles()
                                panel = VodPanel.ONLINE_SUBTITLES
                            }
                        } else {
                            null
                        },
                        steps = subtitleStepChoices(ui.subtitleOffsetMs, viewModel::shiftSubtitle),
                    )
                }

                VodPanel.AUDIO -> PlayerMenuBubble(anchors[PlayerMenu.AUDIO], MobileTrackMenuWidth) {
                    MobileChoiceMenu(
                        options = playback.audioTracks.map { it.label to it.selected },
                        onSelect = { index ->
                            viewModel.selectAudio(playback.audioTracks[index])
                            panel = VodPanel.NONE
                        },
                        emptyText = stringResource(R.string.player_no_tracks),
                    )
                }

                VodPanel.SETTINGS -> PlayerMenuBubble(anchors[PlayerMenu.SETTINGS], MobileSettingsMenuWidth) {
                    MobileQuickSettingsMenu(
                        quickSettingChoices(
                            tv = false,
                            aspectMode = ui.aspectMode,
                            onAspectMode = viewModel::setAspectMode,
                            decoder = ui.decoder,
                            onDecoder = viewModel::setDecoder,
                            speed = ui.speed,
                            onSpeed = viewModel::setSpeed,
                        ),
                    )
                }

                VodPanel.ONLINE_SUBTITLES -> PlayerMenuBubble(anchors[PlayerMenu.SUBTITLES], MobileOnlineMenuWidth) {
                    val online by viewModel.online.collectAsStateWithLifecycle()
                    MobileOnlineSubtitlesMenu(
                        state = online,
                        onPick = viewModel::downloadSubtitle,
                        onBack = { closePanel(back = true) },
                        onDone = { closePanel() },
                    )
                }

                VodPanel.NONE -> Unit
            }
        }
    }
}

private const val VOD_CONTROLS_TIMEOUT_MS = 4_000L
