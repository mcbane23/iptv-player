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

/** Koji spisak zapisa je otvoren preko slike. */
enum class TvTrackKind { AUDIO, SUBTITLES }

/**
 * Spisak zvučnih zapisa ili titlova uz desnu ivicu ekrana. Fokus kreće od izabranog zapisa,
 * a izbor zatvara spisak.
 */
@Composable
fun TvTrackPanel(
    kind: TvTrackKind,
    playback: PlaybackState,
    onSelectAudio: (TrackOption) -> Unit,
    onSelectText: (TrackOption?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedFocus = remember { FocusRequester() }
    // Za titlove je prva stavka „Isključeno”, pa se ostale pomeraju za jedan.
    val rows: List<Pair<String, Boolean>> = when (kind) {
        TvTrackKind.AUDIO -> playback.audioTracks.map { it.label to it.selected }
        TvTrackKind.SUBTITLES -> listOf(stringResource(R.string.player_subtitles_off) to !playback.textEnabled) +
            playback.textTracks.map { it.label to it.selected }
    }
    val focusIndex = rows.indexOfFirst { it.second }.coerceAtLeast(0)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.32f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(start = 16.dp, end = 48.dp, top = 27.dp, bottom = 27.dp),
    ) {
        item {
            Text(
                text = stringResource(if (kind == TvTrackKind.AUDIO) R.string.player_audio else R.string.player_subtitles),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
        }
        if (rows.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.player_no_tracks),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
        items(rows.size) { index ->
            val (label, selected) = rows[index]
            TvTextRow(
                text = label,
                selected = selected,
                trailing = if (selected) "✓" else null,
                onClick = {
                    when (kind) {
                        TvTrackKind.AUDIO -> onSelectAudio(playback.audioTracks[index])
                        TvTrackKind.SUBTITLES -> onSelectText(if (index == 0) null else playback.textTracks[index - 1])
                    }
                    onClose()
                },
                modifier = if (index == focusIndex) Modifier.focusRequester(selectedFocus) else Modifier,
            )
        }
    }
    // Prazan spisak nema šta da primi fokus; zatvara se tasterom Nazad.
    LaunchedEffect(kind) { selectedFocus.tryRequestFocus() }
}
