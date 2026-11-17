package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
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
import com.bane23.iptvplayer.data.subtitles.SubtitleResult
import com.bane23.iptvplayer.ui.common.SettingChoice
import com.bane23.iptvplayer.ui.common.subtitles.details
import com.bane23.iptvplayer.ui.common.subtitles.message
import com.bane23.iptvplayer.ui.common.vod.OnlineSubtitlesState
import androidx.compose.ui.text.style.TextOverflow

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
    /** Kad je zadato, spisak titlova na dnu ima stavku „Potraži online”. */
    onSearchOnline: (() -> Unit)? = null,
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
        if (kind == TvTrackKind.SUBTITLES && onSearchOnline != null) {
            item {
                TvTextRow(
                    text = stringResource(R.string.subtitles_search_online),
                    trailing = "›",
                    onClick = onSearchOnline,
                )
            }
        }
    }
    // Prazan spisak nema šta da primi fokus; zatvara se tasterom Nazad.
    LaunchedEffect(kind) { selectedFocus.tryRequestFocus() }
}

/** Kratak spisak izbora preko slike (npr. trajanje snimanja). OK bira i zatvara, Nazad samo zatvara. */
@Composable
fun TvChoicePanel(
    title: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
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
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
        }
        items(options.size) { index ->
            TvTextRow(
                text = options[index],
                onClick = {
                    onSelect(index)
                    onClose()
                },
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
}

/**
 * Panel „Brza podešavanja” uz desnu ivicu ekrana. OK na stavci prelazi na sledeću vrednost, a panel
 * ostaje otvoren; Nazad ga zatvara (to rešava ekran plejera).
 */
@Composable
fun TvQuickSettingsPanel(choices: List<SettingChoice>, modifier: Modifier = Modifier) {
    val firstFocus = remember { FocusRequester() }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.4f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(start = 16.dp, end = 48.dp, top = 27.dp, bottom = 27.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.player_quick_settings),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
        }
        items(choices.size) { index ->
            val choice = choices[index]
            TvTextRow(
                text = choice.title,
                trailing = if (choice.toggle) null else choice.value,
                checked = if (choice.toggle) choice.checked else null,
                onClick = { if (choice.toggle) choice.flip() else choice.cycle() },
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
        // Napomene uz stavke koje ne važe odmah (bafer).
        val notes = choices.map { it.summary }.filter { it.isNotEmpty() }
        items(notes.size) { index ->
            Text(
                text = notes[index],
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp),
            )
        }
    }
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
}

/**
 * Titlovi nađeni na internetu, uz desnu ivicu ekrana. OK preuzima titl i uključuje ga. Dok traje
 * pretraga (ili kad nema rezultata) fokus drži sama poruka, da daljinski ne ostane bez fokusa.
 */
@Composable
fun TvOnlineSubtitlesPanel(
    state: OnlineSubtitlesState,
    onPick: (SubtitleResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    val statusFocus = remember { FocusRequester() }
    val results = state.results.orEmpty()
    val status = when {
        state.searching -> stringResource(R.string.subtitles_searching)
        state.failure != null -> stringResource(state.failure.message())
        state.results != null && results.isEmpty() -> stringResource(R.string.subtitles_none_found)
        else -> null
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.45f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(start = 16.dp, end = 48.dp, top = 27.dp, bottom = 27.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.subtitles_online_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
        }
        if (status != null) {
            item {
                Text(
                    text = status,
                    color = if (state.failure != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .focusRequester(statusFocus)
                        .focusable()
                        .padding(start = 16.dp, bottom = 8.dp),
                )
            }
        }
        items(results.size) { index ->
            val result = results[index]
            TvListRow(
                onClick = { onPick(result) },
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
            ) { color ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.name,
                        color = color,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (state.downloadingId == result.fileId) {
                            stringResource(R.string.subtitles_downloading)
                        } else {
                            result.details()
                        },
                        color = color.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    LaunchedEffect(results.isEmpty()) {
        if (results.isEmpty()) statusFocus.tryRequestFocus() else firstFocus.tryRequestFocus()
    }
}
