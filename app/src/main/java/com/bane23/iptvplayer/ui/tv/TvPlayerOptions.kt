package com.bane23.iptvplayer.ui.tv

import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.bane23.iptvplayer.ui.tv.text.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.player.TrackOption
import com.bane23.iptvplayer.data.subtitles.SubtitleResult
import com.bane23.iptvplayer.ui.common.SettingChoice
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.PlayerMenuDivider
import com.bane23.iptvplayer.ui.common.player.PlayerMenuMuted
import com.bane23.iptvplayer.ui.common.player.PlayerMenuNote
import com.bane23.iptvplayer.ui.common.player.PlayerMenuText
import com.bane23.iptvplayer.ui.common.player.StepChoice
import com.bane23.iptvplayer.ui.common.subtitles.details
import com.bane23.iptvplayer.ui.common.subtitles.message
import com.bane23.iptvplayer.ui.common.vod.OnlineSubtitlesState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/** Širina oblačića sa spiskom zapisa i sa podešavanjima (vrednosti su duže, pa je širi). */
internal val TvTrackMenuWidth = 250.dp
internal val TvSettingsMenuWidth = 300.dp
internal val TvOnlineMenuWidth = 380.dp

private val MenuRowShape = RoundedCornerShape(7.dp)

/**
 * Red u meniju plejera (oblačić): iste veličine slova kao dugmad ispod trake. U fokusu ima tamnu
 * podlogu i okvir u boji teme, kao dugmad plejera; izabrana stavka je u boji teme. [content]
 * dobija boju teksta.
 */
@Composable
private fun TvMenuRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable RowScope.(color: Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .then(
                if (focused) {
                    Modifier
                        .background(Color.Black.copy(alpha = 0.85f), MenuRowShape)
                        .border(2.dp, primary, MenuRowShape)
                } else {
                    Modifier
                },
            )
            .clip(MenuRowShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        content = { content(if (selected) primary else Color.White) },
    )
}

/** Red menija sa jednim tekstom; po želji kvačica ili strelica ispred i vrednost ili strelica iza. */
@Composable
private fun TvMenuTextRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    /** Mesto za kvačicu postoji u svakom redu spiska, da nazivi stoje u istoj liniji. */
    checkSlot: Boolean = false,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    value: String? = null,
) {
    TvMenuRow(onClick = onClick, modifier = modifier, selected = selected) { color ->
        if (checkSlot || leading != null) {
            Box(modifier = Modifier.size(14.dp)) {
                val icon = leading ?: if (selected) AppIcons.Check else null
                if (icon != null) Icon(icon, contentDescription = null, tint = if (leading != null) PlayerMenuMuted else color)
            }
        }
        Text(
            text = text,
            color = color,
            style = PlayerMenuText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (checkSlot || leading != null) 8.dp else 0.dp),
        )
        if (value != null) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.primary,
                style = PlayerMenuText,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (trailing != null) {
            Icon(trailing, contentDescription = null, tint = PlayerMenuMuted, modifier = Modifier.size(16.dp))
        }
    }
}

/** Mali prekidač u redu menija: uključen je u boji teme. */
@Composable
private fun TvMenuSwitch(checked: Boolean) {
    Box(
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .padding(start = 12.dp)
            .size(width = 28.dp, height = 16.dp)
            .background(if (checked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f), CircleShape)
            .padding(2.dp),
    ) {
        Box(modifier = Modifier.size(12.dp).background(if (checked) MaterialTheme.colorScheme.onPrimary else Color.White, CircleShape))
    }
}

/** Koji spisak zapisa je otvoren preko slike. */
enum class TvTrackKind { AUDIO, SUBTITLES }

/**
 * Spisak zvučnih zapisa ili titlova; sadržaj oblačića iznad trake (PlayerMenuBubble). Fokus kreće
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

    LazyColumn(modifier = modifier.tvKeepFocus()) {
        if (rows.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.player_no_tracks),
                    color = PlayerMenuMuted,
                    style = PlayerMenuText,
                    modifier = Modifier
                        .focusRequester(selectedFocus)
                        .focusable()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                )
            }
        }
        items(rows.size) { index ->
            val (label, selected) = rows[index]
            TvMenuTextRow(
                text = label,
                selected = selected,
                checkSlot = true,
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
                TvMenuTextRow(
                    text = stringResource(R.string.subtitles_search_online),
                    checkSlot = true,
                    trailing = AppIcons.ChevronRight,
                    onClick = onSearchOnline,
                )
            }
        }
        if (kind == TvTrackKind.SUBTITLES && steps.isNotEmpty()) {
            item { MenuDivider() }
            items(steps.size) { index -> TvStepRow(steps[index]) }
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
    val primary = MaterialTheme.colorScheme.primary
    TvMenuRow(
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
            style = PlayerMenuText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = AppIcons.ChevronLeft,
            contentDescription = null,
            tint = primary.copy(alpha = if (choice.canDecrease) 1f else 0.3f),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = choice.value,
            color = primary,
            style = PlayerMenuText,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 64.dp),
        )
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = primary.copy(alpha = if (choice.canIncrease) 1f else 0.3f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Linija koja u meniju deli spisak od redova sa vrednostima. */
@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .heightIn(min = 1.dp)
            .background(PlayerMenuDivider),
    )
}

/** Kratak spisak izbora preko slike (npr. trajanje snimanja). OK bira i zatvara, Nazad samo zatvara. */
@Composable
fun TvChoicePanel(
    title: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Spisak je sadržaj oblačića iznad trake (PlayerMenuBubble), a ne panel uz ivicu ekrana. */
    bubble: Boolean = false,
) {
    val firstFocus = remember { FocusRequester() }
    // Meni se često otvara dugim pritiskom na OK. Dok se taster još drži, ponovljeni pritisci stižu
    // u meni i puštanje tastera bi izabralo prvu stavku. Zato OK važi tek od novog pritiska.
    var okArmed by remember { mutableStateOf(false) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(if (bubble) 0.dp else 6.dp),
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
                    Modifier
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
            if (bubble) {
                Text(
                    text = title,
                    color = PlayerMenuMuted,
                    style = PlayerMenuText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            } else {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
                )
            }
        }
        items(options.size) { index ->
            val focusModifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
            val pick = {
                onSelect(index)
                onClose()
            }
            if (bubble) {
                TvMenuTextRow(text = options[index], onClick = pick, modifier = focusModifier)
            } else {
                TvTextRow(text = options[index], onClick = pick, modifier = focusModifier)
            }
        }
    }
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
}

/**
 * Stavke menija „Podešavanja”; sadržaj oblačića iznad trake (PlayerMenuBubble). Stavka sa više
 * vrednosti se menja strelicama levo/desno (OK je ne menja), a prekidač tasterom OK. Meni ostaje
 * otvoren; Nazad ga zatvara (to rešava ekran plejera).
 */
@Composable
fun TvQuickSettingsPanel(
    choices: List<SettingChoice>,
    modifier: Modifier = Modifier,
    /** Radnje ispod podešavanja (npr. „Poništi filter”): natpis i šta OK radi. */
    actions: List<Pair<String, () -> Unit>> = emptyList(),
    /** Red na koji staje fokus kad se panel prikaže. */
    focusIndex: Int = 0,
    /** OK na podešavanju sa dugačkim spiskom ([SettingChoice.list]); dobija redni broj podešavanja. */
    onOpenList: (Int) -> Unit = {},
) {
    val firstFocus = remember { FocusRequester() }
    LazyColumn(modifier = modifier.tvKeepFocus()) {
        items(choices.size) { index ->
            val choice = choices[index]
            val focusModifier = if (index == focusIndex) Modifier.focusRequester(firstFocus) else Modifier
            if (choice.list) {
                TvMenuTextRow(
                    text = choice.title,
                    onClick = { onOpenList(index) },
                    modifier = focusModifier,
                    trailing = AppIcons.ChevronRight,
                    value = choice.value,
                )
            } else if (choice.toggle) {
                TvMenuRow(
                    onClick = { choice.flip() },
                    modifier = focusModifier.semantics {
                        role = Role.Switch
                        toggleableState = if (choice.checked) ToggleableState.On else ToggleableState.Off
                    },
                ) { color ->
                    Text(
                        text = choice.title,
                        color = color,
                        style = PlayerMenuText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TvMenuSwitch(choice.checked)
                }
            } else {
                TvStepRow(
                    choice = StepChoice(
                        title = choice.title,
                        value = choice.value,
                        canDecrease = choice.selected > 0,
                        canIncrease = choice.selected < choice.options.lastIndex,
                        onStep = choice::step,
                    ),
                    modifier = focusModifier,
                )
            }
        }
        items(actions.size) { index ->
            TvMenuTextRow(text = actions[index].first, onClick = actions[index].second)
        }
        // Napomene uz stavke koje ne važe odmah (bafer).
        val notes = choices.map { it.summary }.filter { it.isNotEmpty() }
        items(notes.size) { index ->
            Text(
                text = notes[index],
                color = PlayerMenuMuted,
                style = PlayerMenuNote,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
}

/**
 * Titlovi nađeni na internetu: drugi korak u oblačiću „Titlovi” (PlayerMenuBubble). Prva stavka
 * vraća na spisak titlova ([onBack]); izbor rezultata preuzima titl i uključuje ga.
 */
@Composable
fun TvOnlineSubtitlesPanel(
    state: OnlineSubtitlesState,
    onPick: (SubtitleResult) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val results = state.results.orEmpty()
    val status = when {
        state.searching -> stringResource(R.string.subtitles_searching)
        state.failure != null -> stringResource(state.failure.message())
        state.results != null && results.isEmpty() -> stringResource(R.string.subtitles_none_found)
        else -> null
    }

    LazyColumn(modifier = modifier.tvKeepFocus()) {
        item {
            TvMenuTextRow(
                text = stringResource(R.string.player_subtitles),
                leading = AppIcons.ChevronLeft,
                onClick = onBack,
                modifier = Modifier.focusRequester(backFocus),
            )
        }
        item { MenuDivider() }
        if (status != null) {
            item {
                Text(
                    text = status,
                    color = if (state.failure != null) MaterialTheme.colorScheme.error else PlayerMenuMuted,
                    style = PlayerMenuText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                )
            }
        }
        items(results.size) { index ->
            val result = results[index]
            TvMenuRow(
                onClick = { onPick(result) },
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
            ) { color ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.name,
                        color = color,
                        style = PlayerMenuText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (state.downloadingId == result.fileId) {
                            stringResource(R.string.subtitles_downloading)
                        } else {
                            result.details()
                        },
                        color = PlayerMenuMuted,
                        style = PlayerMenuNote,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    // Dok traje pretraga ili nema rezultata fokus drži stavka za povratak.
    LaunchedEffect(results.isEmpty()) {
        if (results.isEmpty()) backFocus.tryRequestFocus() else firstFocus.tryRequestFocus()
    }
}
