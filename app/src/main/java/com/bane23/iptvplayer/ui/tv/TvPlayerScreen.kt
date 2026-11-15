package com.bane23.iptvplayer.ui.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.ui.common.ChannelLogo
import com.bane23.iptvplayer.ui.common.player.KeepScreenOn
import com.bane23.iptvplayer.ui.common.player.PlayerUiState
import com.bane23.iptvplayer.ui.common.player.PlayerViewModel
import com.bane23.iptvplayer.ui.common.player.VideoSurface
import com.bane23.iptvplayer.ui.common.player.label
import com.bane23.iptvplayer.ui.common.player.statusMessage
import kotlinx.coroutines.delay

private enum class TvPanel { NONE, CHANNELS, OPTIONS }

/**
 * Plejer na TV-u, upravlja se samo daljinskim:
 * gore/dole menja kanal, OK ili levo otvara listu kanala, desno otvara opcije (zvuk, titlovi, slika),
 * cifre biraju kanal po broju, a Nazad zatvara otvoreni panel ili plejer.
 */
@Composable
fun TvPlayerScreen(
    channelId: Long,
    categoryId: Long?,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    var panel by remember { mutableStateOf(TvPanel.NONE) }
    var infoVisible by remember { mutableStateOf(true) }
    val rootFocus = remember { FocusRequester() }

    LaunchedEffect(channelId) { viewModel.start(channelId, categoryId) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    KeepScreenOn()
    BackHandler(enabled = panel != TvPanel.NONE) { panel = TvPanel.NONE }

    // Naziv kanala se kratko prikaže posle svake promene kanala.
    LaunchedEffect(ui.channel?.id) {
        infoVisible = true
        delay(INFO_TIMEOUT_MS)
        infoVisible = false
    }
    // Kad se panel zatvori, fokus se vraća na plejer, inače tasteri ne bi stizali do njega.
    LaunchedEffect(panel) {
        if (panel == TvPanel.NONE) runCatching { rootFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { event ->
                if (panel != TvPanel.NONE) return@onKeyEvent false
                val code = event.nativeKeyEvent.keyCode
                val isDown = event.type == KeyEventType.KeyDown
                val isUp = event.type == KeyEventType.KeyUp
                when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (isDown) viewModel.previous()
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (isDown) viewModel.next()
                        true
                    }

                    // Panel se otvara kad se taster pusti, da isti pritisak ne bi odmah izabrao stavku u njemu.
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    -> {
                        if (isUp) panel = TvPanel.CHANNELS
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MENU -> {
                        if (isUp) panel = TvPanel.OPTIONS
                        true
                    }

                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                    -> {
                        if (isDown) viewModel.togglePlayPause()
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
            },
    ) {
        VideoSurface(
            player = viewModel.player,
            aspectMode = ui.aspectMode,
            modifier = Modifier.fillMaxSize(),
        )

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

        if (infoVisible && panel == TvPanel.NONE) {
            ChannelInfoBar(ui = ui, modifier = Modifier.align(Alignment.BottomStart))
        }

        when (panel) {
            TvPanel.CHANNELS -> ChannelListPanel(
                viewModel = viewModel,
                ui = ui,
                onPick = { panel = TvPanel.NONE },
                modifier = Modifier.align(Alignment.CenterStart),
            )

            TvPanel.OPTIONS -> OptionsPanel(
                viewModel = viewModel,
                ui = ui,
                playback = playback,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TvPanel.NONE -> Unit
        }
    }
}

@Composable
private fun ChannelInfoBar(ui: PlayerUiState, modifier: Modifier = Modifier) {
    val channel = ui.channel ?: return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 48.dp, vertical = 20.dp),
    ) {
        Text(
            text = channel.name,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.player_tv_hint),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall,
        )
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
                ChannelLogo(url = channel?.logo, size = 32.dp)
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

/** Opcije: zvuk, titlovi i odnos stranica. */
@Composable
private fun OptionsPanel(
    viewModel: PlayerViewModel,
    ui: PlayerUiState,
    playback: PlaybackState,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.35f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(start = 16.dp, end = 48.dp, top = 27.dp, bottom = 27.dp),
    ) {
        item {
            TvTextRow(
                text = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
                trailing = if (isFavorite) "★" else "☆",
                onClick = viewModel::toggleFavorite,
                modifier = Modifier.focusRequester(firstFocus),
            )
        }
        item {
            TvTextRow(
                text = stringResource(R.string.player_aspect, ui.aspectMode.label()),
                onClick = viewModel::cycleAspectMode,
            )
        }

        item { PanelHeader(stringResource(R.string.player_audio)) }
        if (playback.audioTracks.isEmpty()) {
            item { PanelNote(stringResource(R.string.player_no_tracks)) }
        }
        items(playback.audioTracks.size) { index ->
            val track = playback.audioTracks[index]
            TvTextRow(
                text = track.label,
                selected = track.selected,
                trailing = if (track.selected) "✓" else null,
                onClick = { viewModel.selectAudio(track) },
            )
        }

        item { PanelHeader(stringResource(R.string.player_subtitles)) }
        item {
            TvTextRow(
                text = stringResource(R.string.player_subtitles_off),
                selected = !playback.textEnabled,
                trailing = if (!playback.textEnabled) "✓" else null,
                onClick = { viewModel.selectText(null) },
            )
        }
        items(playback.textTracks.size) { index ->
            val track = playback.textTracks[index]
            TvTextRow(
                text = track.label,
                selected = track.selected,
                trailing = if (track.selected) "✓" else null,
                onClick = { viewModel.selectText(track) },
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}

@Composable
private fun PanelHeader(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun PanelNote(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
    )
}

private const val INFO_TIMEOUT_MS = 4_000L
