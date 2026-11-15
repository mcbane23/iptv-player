package com.bane23.iptvplayer.ui.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.ui.common.player.KeepScreenOn
import com.bane23.iptvplayer.ui.common.player.VideoSurface
import com.bane23.iptvplayer.ui.common.player.formatDuration
import com.bane23.iptvplayer.ui.common.player.statusMessage
import com.bane23.iptvplayer.ui.common.vod.VodPlayerUiState
import com.bane23.iptvplayer.ui.common.vod.VodPlayerViewModel
import com.bane23.iptvplayer.ui.common.vod.episodeLabel
import kotlinx.coroutines.delay

/**
 * Plejer za film ili epizodu na TV-u, upravlja se samo daljinskim: levo/desno premotava,
 * OK pauzira i nastavlja, gore otvara opcije (zvuk, titlovi, slika, sledeća epizoda),
 * dole prikazuje traku sa pozicijom. [start] pokreće puštanje (poziva se jednom).
 */
@Composable
fun TvVodPlayerScreen(
    start: (VodPlayerViewModel) -> Unit,
    viewModel: VodPlayerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    var optionsOpen by remember { mutableStateOf(false) }
    // Svaki pritisak tastera povećava brojač, čime se traka ponovo prikazuje na nekoliko sekundi.
    var activity by remember { mutableIntStateOf(0) }
    var barVisible by remember { mutableStateOf(true) }
    val rootFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { start(viewModel) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    KeepScreenOn()
    BackHandler(enabled = optionsOpen) { optionsOpen = false }

    LaunchedEffect(activity, playback.status) {
        barVisible = true
        if (playback.status == PlaybackStatus.PLAYING) {
            delay(BAR_TIMEOUT_MS)
            barVisible = false
        }
    }
    // Kad se opcije zatvore, fokus se vraća na plejer, inače tasteri ne bi stizali do njega.
    LaunchedEffect(optionsOpen) {
        if (!optionsOpen) runCatching { rootFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { event ->
                if (optionsOpen) return@onKeyEvent false
                val isDown = event.type == KeyEventType.KeyDown
                val isUp = event.type == KeyEventType.KeyUp
                val handled = when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (isDown) viewModel.seekBy(-SEEK_STEP_MS)
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (isDown) viewModel.seekBy(SEEK_STEP_MS)
                        true
                    }

                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        if (isDown) viewModel.seekBy(-FAST_SEEK_STEP_MS)
                        true
                    }

                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (isDown) viewModel.seekBy(FAST_SEEK_STEP_MS)
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                    -> {
                        if (isUp) viewModel.togglePlayPause()
                        true
                    }

                    // Panel se otvara kad se taster pusti, da isti pritisak ne bi odmah izabrao stavku u njemu.
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_MENU -> {
                        if (isUp) optionsOpen = true
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true

                    AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (isDown) viewModel.next()
                        true
                    }

                    else -> false
                }
                if (handled && isDown) activity++
                handled
            },
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

        if (barVisible && !optionsOpen) {
            ProgressBar(
                ui = ui,
                paused = playback.status == PlaybackStatus.PAUSED,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        if (optionsOpen) {
            TvPlayerOptionsPanel(
                playback = playback,
                aspectMode = ui.aspectMode,
                onCycleAspect = viewModel::cycleAspectMode,
                onSelectAudio = viewModel::selectAudio,
                onSelectText = viewModel::selectText,
                modifier = Modifier.align(Alignment.CenterEnd),
                firstRowText = if (ui.hasNext) stringResource(R.string.vod_next_episode) else null,
                onFirstRow = {
                    optionsOpen = false
                    viewModel.next()
                },
            )
        }
    }
}

/** Naslov, pozicija i trajanje, sa trakom koja pokazuje dokle se stiglo. */
@Composable
private fun ProgressBar(ui: VodPlayerUiState, paused: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 48.dp, vertical = 20.dp),
    ) {
        val season = ui.season
        val episode = ui.episode
        val heading = listOfNotNull(
            ui.title,
            if (season != null && episode != null) episodeLabel(season, episode) else null,
            if (paused) stringResource(R.string.player_pause) else null,
        ).joinToString(" · ")
        Text(
            text = heading,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = formatDuration(ui.positionMs), color = Color.White, style = MaterialTheme.typography.bodyMedium)
            val fraction = if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.3f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Text(text = formatDuration(ui.durationMs), color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = stringResource(R.string.vod_tv_hint),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private const val SEEK_STEP_MS = 10_000L
private const val FAST_SEEK_STEP_MS = 30_000L
private const val BAR_TIMEOUT_MS = 4_000L
