package com.bane23.iptvplayer.ui.tv

import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.player.TrackOption
import com.bane23.iptvplayer.data.subtitles.SubtitleResult
import com.bane23.iptvplayer.ui.common.SettingChoice
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.StepChoice
import com.bane23.iptvplayer.ui.common.subtitles.details
import com.bane23.iptvplayer.ui.common.subtitles.message
import com.bane23.iptvplayer.ui.common.vod.OnlineSubtitlesState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/** Širina oblačića sa spiskom zapisa i sa podešavanjima (vrednosti su duže, pa je širi). */
internal val TvTrackMenuWidth = 290.dp
internal val TvSettingsMenuWidth = 340.dp

/** Koji spisak zapisa je otvoren preko slike. */
enum class TvTrackKind { AUDIO, SUBTITLES }

/**
 * Spisak zvučnih zapisa ili titlova; sadržaj oblačića iznad dugmeta (PlayerMenuBubble). Fokus kreće
 * od izabranog zapisa, a izbor zatvara spisak.
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
    /** Redovi ispod spiska titlova čija se vrednost menja strelicama levo i desno (veličina, pomeranje). */
    steps: List<StepChoice> = emptyList(),
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .tvKeepFocus()
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        if (rows.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.player_no_tracks),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .focusRequester(selectedFocus)
                        .focusable()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
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
        if (kind == TvTrackKind.SUBTITLES) {
            items(steps.size) { index ->
                TvStepRow(steps[index], modifier = if (index == 0) Modifier.padding(top = 10.dp) else Modifier)
            }
        }
    }
    // U praznom spisku fokus drži sama poruka; zatvara se tasterom Nazad.
    LaunchedEffect(kind) { selectedFocus.tryRequestFocus() }
}

/**
 * Red sa jednom vrednošću: dok je u fokusu, strelica levo je korak naniže, a desno korak naviše.
 * OK ne radi ništa, da se vrednost ne promeni slučajno. Strelica je bleda kad dalje ne može.
 */
@Composable
private fun TvStepRow(choice: StepChoice, modifier: Modifier = Modifier) {
    TvListRow(
        onClick = {},
        modifier = modifier.onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            val delta = when (native.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> -1
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> 1
                else -> return@onPreviewKeyEvent false
            }
            if (native.action == AndroidKeyEvent.ACTION_DOWN) choice.onStep(delta)
            true
        },
    ) { color ->
        Text(
            text = choice.title,
            color = color,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = AppIcons.ChevronLeft,
            contentDescription = null,
            tint = color.copy(alpha = if (choice.canDecrease) 1f else 0.3f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = choice.value,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 84.dp),
        )
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = color.copy(alpha = if (choice.canIncrease) 1f else 0.3f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Kratak spisak izbora preko slike (npr. trajanje snimanja). OK bira i zatvara, Nazad samo zatvara. */
@Composable
fun TvChoicePanel(
    title: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Spisak je sadržaj oblačića iznad dugmeta (PlayerMenuBubble), a ne panel uz ivicu ekrana. */
    bubble: Boolean = false,
) {
    val firstFocus = remember { FocusRequester() }
    // Meni se često otvara dugim pritiskom na OK. Dok se taster još drži, ponovljeni pritisci stižu
    // u meni i puštanje tastera bi izabralo prvu stavku. Zato OK važi tek od novog pritiska.
    var okArmed by remember { mutableStateOf(false) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val ok = native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                    native.keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                    native.keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                if (!ok || okArmed) return@onPreviewKeyEvent false
                if (native.action == AndroidKeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                    okArmed = true
                    false
                } else {
                    true
                }
            }
            .tvKeepFocus()
            .then(
                if (bubble) {
                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                } else {
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.32f)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                        .padding(start = 16.dp, end = 48.dp, top = 27.dp, bottom = 27.dp)
                },
            ),
    ) {
        item {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = if (bubble) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = if (bubble) 6.dp else 0.dp, bottom = if (bubble) 4.dp else 12.dp),
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
 * Stavke menija „Podešavanja”; sadržaj oblačića iznad dugmeta (PlayerMenuBubble). OK na stavci
 * prelazi na sledeću vrednost, a meni ostaje otvoren; Nazad ga zatvara (to rešava ekran plejera).
 */
@Composable
fun TvQuickSettingsPanel(choices: List<SettingChoice>, modifier: Modifier = Modifier) {
    val firstFocus = remember { FocusRequester() }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .tvKeepFocus()
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .tvKeepFocus()
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
