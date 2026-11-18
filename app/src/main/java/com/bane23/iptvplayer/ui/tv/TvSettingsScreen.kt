package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.ui.common.AppLanguage
import com.bane23.iptvplayer.ui.common.AppLocale
import com.bane23.iptvplayer.ui.common.label
import com.bane23.iptvplayer.ui.common.theme.AccentColor
import com.bane23.iptvplayer.ui.common.theme.UiScale
import com.bane23.iptvplayer.ui.common.theme.label
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.update.UpdateChannel
import com.bane23.iptvplayer.ui.common.theme.glowBackground
import com.bane23.iptvplayer.ui.common.SettingChoice
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import com.bane23.iptvplayer.ui.common.UpdateViewModel
import com.bane23.iptvplayer.ui.common.player.SubtitlePreview
import com.bane23.iptvplayer.ui.common.playerSettingChoices
import com.bane23.iptvplayer.work.Autostart

/**
 * Podešavanja aplikacije na TV-u: levo spisak podešavanja, desno objašnjenje onog na kome je fokus
 * i vrednosti onog koje je izabrano.
 * OK na podešavanju otvara njegove vrednosti, OK na vrednosti je bira i vraća fokus levo.
 */
@Composable
fun TvSettingsScreen(
    onCategories: (() -> Unit)? = null,
    onParental: (() -> Unit)? = null,
    onTrakt: (() -> Unit)? = null,
    onOpenSubtitles: (() -> Unit)? = null,
    onRecordings: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val resumeLastChannel by viewModel.resumeLastChannel.collectAsStateWithLifecycle()
    val autoRefreshPlaylists by viewModel.autoRefreshPlaylists.collectAsStateWithLifecycle()
    val matchFrameRate by viewModel.matchFrameRate.collectAsStateWithLifecycle()
    val channelPreview by viewModel.tvChannelPreview.collectAsStateWithLifecycle()
    val vodPreview by viewModel.tvVodPreview.collectAsStateWithLifecycle()
    val autostart by viewModel.autostart.collectAsStateWithLifecycle()
    val reopenAfterUpdate by viewModel.reopenAfterUpdate.collectAsStateWithLifecycle()
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
    val uiScale = UiScale.fromName(viewModel.uiScale.collectAsStateWithLifecycle().value, isTv = true)
    val updateChannel by updateViewModel.channel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Dozvola se menja u sistemskim podešavanjima, pa se proverava pri svakom povratku na ekran.
    var autostartAllowed by remember { mutableStateOf(Autostart.isAllowed(context)) }
    // Uređaj nema sistemski ekran za dozvolu (neki TV-i), pa korisnik dobija uputstvo.
    var permissionScreenMissing by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { autostartAllowed = Autostart.isAllowed(context) }
    val playerSettings by viewModel.playerSettings.collectAsStateWithLifecycle()
    val choices = playerSettingChoices(playerSettings, viewModel)
    val firstFocus = remember { FocusRequester() }
    val rowFocus = remember { List(choices.size) { FocusRequester() } }
    val optionFocus = remember { FocusRequester() }
    // Podešavanje čije se vrednosti trenutno biraju, ili null.
    var openChoice by remember { mutableStateOf<Int?>(null) }
    // Podešavanje na kome je fokus, zbog objašnjenja desno od spiska; null dok fokus nije ni na jednom.
    var focusedChoice by remember { mutableStateOf<Int?>(null) }
    // Probni titl se vidi dok je fokus na podešavanju izgleda titlova ili se bira njegova vrednost.
    // Izvedeno stanje: ekran se ponovo iscrtava samo kad se ovo promeni, a ne na svaki pomeraj fokusa.
    val currentChoices by rememberUpdatedState(choices)
    val showPreview by remember {
        derivedStateOf { (openChoice ?: focusedChoice)?.let { currentChoices.getOrNull(it)?.subtitleLook } == true }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        TvHeader(title = stringResource(R.string.settings_title), modifier = Modifier.padding(bottom = 16.dp))
        // Jedan stepen sitnija slova nego u spiskovima kanala i kategorija, da stane više teksta.
        SettingsType {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(
                    // Podešavanja ima više nego što staje na ekran; spisak se pomera za fokusom.
                    modifier = Modifier
                        .weight(0.55f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (onCategories != null) {
                        TvTextRow(
                            text = stringResource(R.string.categories_manage_title),
                            trailing = "›",
                            onClick = onCategories,
                            onFocused = {
                                focusedChoice = CATEGORIES_ROW
                                openChoice = null
                            },
                        )
                    }
                    if (onParental != null) {
                        TvTextRow(
                            text = stringResource(R.string.parental_title),
                            trailing = "›",
                            onClick = onParental,
                            onFocused = {
                                focusedChoice = PARENTAL_ROW
                                openChoice = null
                            },
                        )
                    }
                    if (onTrakt != null) {
                        TvTextRow(
                            text = stringResource(R.string.trakt_title),
                            trailing = "›",
                            onClick = onTrakt,
                            onFocused = {
                                focusedChoice = TRAKT_ROW
                                openChoice = null
                            },
                        )
                    }
                    if (onOpenSubtitles != null) {
                        TvTextRow(
                            text = stringResource(R.string.opensubtitles_title),
                            trailing = "›",
                            onClick = onOpenSubtitles,
                            onFocused = {
                                focusedChoice = OPENSUBTITLES_ROW
                                openChoice = null
                            },
                        )
                    }
                    if (onRecordings != null) {
                        TvTextRow(
                            text = stringResource(R.string.recordings_title),
                            trailing = "›",
                            onClick = onRecordings,
                            onFocused = {
                                focusedChoice = RECORDINGS_ROW
                                openChoice = null
                            },
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_general_header),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp),
                    )
                    TvTextRow(
                        text = stringResource(R.string.settings_resume_last),
                        checked = resumeLastChannel,
                        onClick = { viewModel.setResumeLastChannel(!resumeLastChannel) },
                        onFocused = {
                            focusedChoice = RESUME_ROW
                            openChoice = null
                        },
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    TvTextRow(
                        text = stringResource(R.string.settings_auto_refresh),
                        checked = autoRefreshPlaylists,
                        onClick = { viewModel.setAutoRefreshPlaylists(!autoRefreshPlaylists) },
                        onFocused = {
                            focusedChoice = AUTO_REFRESH_ROW
                            openChoice = null
                        },
                    )
                    TvTextRow(
                        text = stringResource(R.string.settings_match_frame_rate),
                        checked = matchFrameRate,
                        onClick = { viewModel.setMatchFrameRate(!matchFrameRate) },
                        onFocused = {
                            focusedChoice = MATCH_FRAME_RATE_ROW
                            openChoice = null
                        },
                    )
                    TvTextRow(
                        text = stringResource(R.string.settings_tv_preview),
                        checked = channelPreview,
                        onClick = { viewModel.setTvChannelPreview(!channelPreview) },
                        onFocused = {
                            focusedChoice = TV_PREVIEW_ROW
                            openChoice = null
                        },
                    )
                    TvTextRow(
                        text = stringResource(R.string.settings_tv_vod_preview),
                        checked = vodPreview,
                        onClick = { viewModel.setTvVodPreview(!vodPreview) },
                        onFocused = {
                            focusedChoice = VOD_PREVIEW_ROW
                            openChoice = null
                        },
                    )
                    TvTextRow(
                        text = stringResource(R.string.settings_autostart),
                        checked = autostart,
                        onClick = {
                            val enable = !autostart
                            viewModel.setAutostart(enable)
                            if (enable && !autostartAllowed) {
                                permissionScreenMissing = !Autostart.openPermissionScreen(context)
                            }
                        },
                        onFocused = {
                            focusedChoice = AUTOSTART_ROW
                            openChoice = null
                        },
                    )
                    TvTextRow(
                        text = stringResource(R.string.settings_language),
                        trailing = AppLocale.get(context).label(),
                        onClick = {
                            // Svaki pritisak prelazi na sledeći jezik; ekran se odmah iscrtava na njemu.
                            val next = (AppLocale.get(context).ordinal + 1) % AppLanguage.entries.size
                            AppLocale.change(context, AppLanguage.entries[next])
                        },
                        onFocused = {
                            focusedChoice = LANGUAGE_ROW
                            openChoice = null
                        },
                    )
                    TvTextRow(
                        text = stringResource(R.string.settings_accent_color),
                        trailing = accentColor?.label(),
                        onClick = {
                            // Svaki pritisak prelazi na sledeću boju; promena se vidi odmah.
                            val current = accentColor ?: AccentColor.DEFAULT
                            viewModel.setAccentColor(AccentColor.entries[(current.ordinal + 1) % AccentColor.entries.size])
                        },
                        onFocused = {
                            focusedChoice = ACCENT_ROW
                            openChoice = null
                        },
                    )
                    TvTextRow(
                        text = stringResource(R.string.settings_ui_scale),
                        trailing = uiScale.label,
                        // Svaki pritisak prelazi na sledeću veličinu; promena se vidi odmah.
                        onClick = { viewModel.setUiScale(UiScale.next(uiScale, isTv = true)) },
                        onFocused = {
                            focusedChoice = UI_SCALE_ROW
                            openChoice = null
                        },
                    )
                    TvTextRow(
                        text = stringResource(R.string.update_check),
                        trailing = updateViewModel.installedVersion,
                        onClick = updateViewModel::check,
                        onFocused = {
                            focusedChoice = UPDATE_CHECK_ROW
                            openChoice = null
                        },
                    )
                    TvTextRow(
                        text = stringResource(R.string.update_channel),
                        trailing = updateChannel.label(),
                        onClick = {
                            // Svaki pritisak prelazi na drugi kanal i odmah proverava šta on nudi.
                            val channels = UpdateChannel.entries
                            updateViewModel.setChannel(channels[(updateChannel.ordinal + 1) % channels.size])
                        },
                        onFocused = {
                            focusedChoice = UPDATE_CHANNEL_ROW
                            openChoice = null
                        },
                    )
                    // Uključeno je tek kad postoji i dozvola; bez nje sistem ne da aplikaciji da se sama otvori.
                    val reopens = reopenAfterUpdate && autostartAllowed
                    TvTextRow(
                        text = stringResource(R.string.settings_reopen_after_update),
                        checked = reopens,
                        onClick = {
                            viewModel.setReopenAfterUpdate(!reopens)
                            if (!reopens && !autostartAllowed) {
                                permissionScreenMissing = !Autostart.openPermissionScreen(context)
                            }
                        },
                        onFocused = {
                            focusedChoice = REOPEN_ROW
                            openChoice = null
                        },
                    )
                    Text(
                        text = stringResource(R.string.settings_player_header),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
                    )
                    choices.forEachIndexed { index, choice ->
                        TvTextRow(
                            text = choice.title,
                            trailing = choice.value.takeIf { !choice.toggle },
                            checked = choice.checked.takeIf { choice.toggle },
                            selected = openChoice == index,
                            // Prekidač se menja odmah; ostala podešavanja otvaraju spisak vrednosti.
                            onClick = { if (choice.toggle) choice.flip() else openChoice = index },
                            onFocused = {
                                focusedChoice = index
                                if (openChoice != index) openChoice = null
                            },
                            modifier = Modifier.focusRequester(rowFocus[index]),
                        )
                    }
                }

                val open = openChoice
                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Objašnjenje stoji van spiska koji se pomera, pa se vidi na kom god redu da je fokus.
                    FocusedSummary(
                        focusedChoice = { focusedChoice },
                        choices = choices,
                        reopenSummary = when {
                            autostartAllowed -> R.string.settings_reopen_after_update_summary
                            permissionScreenMissing -> R.string.settings_autostart_manual
                            else -> R.string.settings_reopen_after_update_permission
                        },
                        autostartSummary = when {
                            !autostart || autostartAllowed -> R.string.settings_autostart_summary
                            permissionScreenMissing -> R.string.settings_autostart_manual
                            else -> R.string.settings_autostart_permission
                        },
                    )
                    if (open != null) {
                        val choice = choices[open]
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(bottom = 27.dp),
                        ) {
                            itemsIndexed(choice.options) { index, option ->
                                TvTextRow(
                                    text = option,
                                    trailing = if (index == choice.selected) "✓" else null,
                                    selected = index == choice.selected,
                                    onClick = {
                                        choice.onSelect(index)
                                        // Fokus se vraća na podešavanje pre nego što spisak vrednosti nestane.
                                        rowFocus[open].tryRequestFocus()
                                        openChoice = null
                                    },
                                    modifier = if (index == 0) Modifier.focusRequester(optionFocus) else Modifier,
                                )
                            }
                        }
                        // Fokus prelazi na vrednosti čim se otvore; Nazad ili levo ga vraća na podešavanja.
                        LaunchedEffect(open) { optionFocus.tryRequestFocus() }
                    }
                    if (open == null) Spacer(modifier = Modifier.weight(1f))
                    if (showPreview) {
                        SubtitlePreview(
                            settings = playerSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 27.dp),
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
}

/**
 * Objašnjenje podešavanja na kome je fokus. Fokus čita
 * samo ovaj deo, pa pomeranje D-padom ne iscrtava ponovo ceo ekran dok se spisak pomera.
 */
@Composable
private fun FocusedSummary(
    focusedChoice: () -> Int?,
    choices: List<SettingChoice>,
    autostartSummary: Int,
    reopenSummary: Int,
) {
    val focused = focusedChoice() ?: return
    val summary = when (focused) {
        CATEGORIES_ROW -> stringResource(R.string.settings_categories_summary)
        PARENTAL_ROW -> stringResource(R.string.settings_parental_summary)
        TRAKT_ROW -> stringResource(R.string.settings_trakt_summary)
        OPENSUBTITLES_ROW -> stringResource(R.string.settings_opensubtitles_summary)
        RECORDINGS_ROW -> stringResource(R.string.settings_recordings_summary)
        TV_PREVIEW_ROW -> stringResource(R.string.settings_tv_preview_summary)
        VOD_PREVIEW_ROW -> stringResource(R.string.settings_tv_vod_preview_summary)
        UPDATE_CHECK_ROW -> stringResource(R.string.update_check_summary)
        RESUME_ROW -> stringResource(R.string.settings_resume_last_summary)
        ACCENT_ROW -> stringResource(R.string.settings_accent_color_tv_summary)
        UI_SCALE_ROW -> stringResource(R.string.settings_ui_scale_tv_summary)
        LANGUAGE_ROW -> stringResource(R.string.settings_language_tv_summary)
        AUTO_REFRESH_ROW -> stringResource(R.string.settings_auto_refresh_summary)
        MATCH_FRAME_RATE_ROW -> stringResource(R.string.settings_match_frame_rate_summary)
        UPDATE_CHANNEL_ROW -> stringResource(R.string.update_channel_summary)
        AUTOSTART_ROW -> stringResource(autostartSummary)
        REOPEN_ROW -> stringResource(reopenSummary)
        else -> choices.getOrNull(focused)?.summary.orEmpty()
    }
    // U visini teksta prvog reda spiska.
    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Napomena važi samo za podešavanja plejera.
        if (focused >= 0) {
            Text(
                text = stringResource(R.string.settings_player_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Oznaka reda „Pokreni pri paljenju” na mestu rednog broja podešavanja plejera. */
private const val AUTOSTART_ROW = -1
private const val ACCENT_ROW = -2
private const val LANGUAGE_ROW = -3
private const val UPDATE_CHANNEL_ROW = -4
private const val AUTO_REFRESH_ROW = -5
private const val MATCH_FRAME_RATE_ROW = -6
private const val UI_SCALE_ROW = -7
private const val RESUME_ROW = -8
private const val REOPEN_ROW = -9
private const val CATEGORIES_ROW = -10
private const val PARENTAL_ROW = -11
private const val TRAKT_ROW = -12
private const val OPENSUBTITLES_ROW = -13
private const val RECORDINGS_ROW = -14
private const val TV_PREVIEW_ROW = -15
private const val VOD_PREVIEW_ROW = -16
private const val UPDATE_CHECK_ROW = -17

/** Tipografija Podešavanja: tekst redova, vrednosti i naslovi grupa su za jedan stepen sitniji. */
@Composable
private fun SettingsType(content: @Composable () -> Unit) {
    val type = MaterialTheme.typography
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = type.copy(bodyLarge = type.bodyMedium, bodyMedium = type.bodySmall, titleMedium = type.titleSmall),
        content = content,
    )
}
