package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.player.TrackOption
import com.bane23.iptvplayer.ui.common.player.AspectMode
import com.bane23.iptvplayer.ui.common.player.label

/**
 * Panel sa opcijama plejera na TV-u: odnos stranica, zvuk i titlovi. Koriste ga i plejer za
 * kanale i plejer za filmove; svaki može da doda svoju prvu stavku ([firstRowText]).
 */
@Composable
fun TvPlayerOptionsPanel(
    playback: PlaybackState,
    aspectMode: AspectMode,
    onCycleAspect: () -> Unit,
    onSelectAudio: (TrackOption) -> Unit,
    onSelectText: (TrackOption?) -> Unit,
    modifier: Modifier = Modifier,
    firstRowText: String? = null,
    firstRowTrailing: String? = null,
    onFirstRow: () -> Unit = {},
) {
    val firstFocus = remember { FocusRequester() }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.35f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(start = 16.dp, end = 48.dp, top = 27.dp, bottom = 27.dp),
    ) {
        if (firstRowText != null) {
            item {
                TvTextRow(
                    text = firstRowText,
                    trailing = firstRowTrailing,
                    onClick = onFirstRow,
                    modifier = Modifier.focusRequester(firstFocus),
                )
            }
        }
        item {
            TvTextRow(
                text = stringResource(R.string.player_aspect, aspectMode.label()),
                onClick = onCycleAspect,
                modifier = if (firstRowText == null) Modifier.focusRequester(firstFocus) else Modifier,
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
                onClick = { onSelectAudio(track) },
            )
        }

        item { PanelHeader(stringResource(R.string.player_subtitles)) }
        item {
            TvTextRow(
                text = stringResource(R.string.player_subtitles_off),
                selected = !playback.textEnabled,
                trailing = if (!playback.textEnabled) "✓" else null,
                onClick = { onSelectText(null) },
            )
        }
        items(playback.textTracks.size) { index ->
            val track = playback.textTracks[index]
            TvTextRow(
                text = track.label,
                selected = track.selected,
                trailing = if (track.selected) "✓" else null,
                onClick = { onSelectText(track) },
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
