package com.bane23.iptvplayer.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.bane23.iptvplayer.ui.common.SettingsGroup
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
import com.bane23.iptvplayer.ui.common.languageSummary
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
import com.bane23.iptvplayer.ui.common.themeChoice
import com.bane23.iptvplayer.work.Autostart

/**
 * Podešavanja aplikacije na TV-u u tri kolone: levo jezik i grupe, u sredini stavke grupe na kojoj
 * je fokus, desno objašnjenje reda na kome je fokus i vrednosti podešavanja koje je izabrano.
 * Desno ili OK na grupi ulazi u njene stavke, levo ili Nazad vraća na grupe.
 * OK na podešavanju otvara njegove vrednosti, OK na vrednosti je bira i vraća fokus na podešavanje.
 */
@Composable
fun TvSettingsScreen(
    onCategories: (() -> Unit)? = null,
    onParental: (() -> Unit)? = null,
    onTrakt: (() -> Unit)? = null,
    onOpenSubtitles: (() -> Unit)? = null,
    onRecordings: (() -> Unit)? = null,
    onRemoteButtons: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val resumeLastChannel by viewModel.resumeLastChannel.collectAsStateWithLifecycle()
    val autoRefreshPlaylists by viewModel.autoRefreshPlaylists.collectAsStateWithLifecycle()
    val matchFrameRate by viewModel.matchFrameRate.collectAsStateWithLifecycle()
    val pauseInfo by viewModel.pauseInfo.collectAsStateWithLifecycle()
    val watchNext by viewModel.watchNext.collectAsStateWithLifecycle()
    val blackTheme by viewModel.blackTheme.collectAsStateWithLifecycle()
    val channelPreview by viewModel.tvChannelPreview.collectAsStateWithLifecycle()
    val vodPreview by viewModel.tvVodPreview.collectAsStateWithLifecycle()
    val autostart by viewModel.autostart.collectAsStateWithLifecycle()
    val reopenAfterUpdate by viewModel.reopenAfterUpdate.collectAsStateWithLifecycle()
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val uiScale = UiScale.fromName(viewModel.uiScale.collectAsStateWithLifecycle().value, isTv = true)
    val updateChannel by updateViewModel.channel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Dozvola se menja u sistemskim podešavanjima, pa se proverava pri svakom povratku na ekran.
    var autostartAllowed by remember { mutableStateOf(Autostart.isAllowed(context)) }
    // Uređaj nema sistemski ekran za dozvolu (neki TV-i), pa korisnik dobija uputstvo.
    var permissionScreenMissing by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { autostartAllowed = Autostart.isAllowed(context) }
    val playerSettings by viewModel.playerSettings.collectAsStateWithLifecycle()
    val choices = playerSettingChoices(playerSettings, viewModel) + themeChoice(appTheme, viewModel::setAppTheme)
    val groups = SettingsGroup.entries.filter { it != SettingsGroup.RECORDINGS || onRecordings != null }
    val groupFocus = remember { SettingsGroup.entries.map { FocusRequester() } }
    val languageFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    val rowFocus = remember { List(choices.size) { FocusRequester() } }
    val optionFocus = remember { FocusRequester() }
    // Grupa čije se stavke vide u srednjoj koloni; null dok je fokus na jeziku.
    var currentGroup by rememberSaveable { mutableStateOf<SettingsGroup?>(SettingsGroup.CONTENT) }
    // Fokus je u srednjoj koloni, među stavkama grupe.
    var inItems by remember { mutableStateOf(false) }
    // Podešavanje čije se vrednosti trenutno biraju, ili null.
    var openChoice by remember { mutableStateOf<Int?>(null) }
    // Za podešavanje sa mestima (npr. jezici titlova): mesto čije se vrednosti biraju, ili null dok se vide mesta.
    var openSlot by remember(openChoice) { mutableStateOf<Int?>(null) }
    // Mesto na koje se fokus vraća posle izbora vrednosti.
    var lastSlot by remember(openChoice) { androidx.compose.runtime.mutableIntStateOf(0) }
    // Red na kome je fokus, zbog objašnjenja desno; null dok fokus nije ni na jednom.
    var focusedChoice by remember { mutableStateOf<Int?>(null) }
    // Probni titl se vidi dok je fokus na podešavanju izgleda titlova ili se bira njegova vrednost.
    // Izvedeno stanje: ekran se ponovo iscrtava samo kad se ovo promeni, a ne na svaki pomeraj fokusa.
    val currentChoices by rememberUpdatedState(choices)
    val showPreview by remember {
        derivedStateOf { (openChoice ?: focusedChoice)?.let { currentChoices.getOrNull(it)?.subtitleLook } == true }
    }

    // Podešavanja plejera koja pripadaju grupi, kao redovi; redni broj u [choices] im je oznaka.
    fun choiceItems(group: SettingsGroup): List<TvItem> = choices.mapIndexedNotNull { index, choice ->
        if (choice.group != group) return@mapIndexedNotNull null
        TvItem(
            id = index,
            text = choice.title,
            trailing = choice.value.takeIf { !choice.toggle },
            checked = choice.checked.takeIf { choice.toggle },
            // Prekidač se menja odmah; ostala podešavanja otvaraju spisak vrednosti.
            onClick = { if (choice.toggle) choice.flip() else openChoice = index },
        )
    }
    // Uključeno je tek kad postoji i dozvola; bez nje sistem ne da aplikaciji da se sama otvori.
    val reopens = reopenAfterUpdate && autostartAllowed
    val items: List<TvItem> = when (currentGroup) {
        null -> emptyList()

        SettingsGroup.CONTENT -> listOfNotNull(
            onCategories?.let { TvItem(CATEGORIES_ROW, stringResource(R.string.categories_manage_title), "›", onClick = it) },
            onParental?.let { TvItem(PARENTAL_ROW, stringResource(R.string.parental_title), "›", onClick = it) },
            TvItem(AUTO_REFRESH_ROW, stringResource(R.string.settings_auto_refresh), checked = autoRefreshPlaylists) {
                viewModel.setAutoRefreshPlaylists(!autoRefreshPlaylists)
            },
        )

        SettingsGroup.WATCHING -> listOf(
            TvItem(RESUME_ROW, stringResource(R.string.settings_resume_last), checked = resumeLastChannel) {
                viewModel.setResumeLastChannel(!resumeLastChannel)
            },
        ) + choiceItems(SettingsGroup.WATCHING) + listOfNotNull(
            TvItem(PAUSE_INFO_ROW, stringResource(R.string.settings_pause_info), checked = pauseInfo) {
                viewModel.setPauseInfo(!pauseInfo)
            },
            TvItem(TV_PREVIEW_ROW, stringResource(R.string.settings_tv_preview), checked = channelPreview) {
                viewModel.setTvChannelPreview(!channelPreview)
            },
            onRemoteButtons?.let { TvItem(REMOTE_BUTTONS_ROW, stringResource(R.string.remote_buttons_title), "›", onClick = it) },
        )

        SettingsGroup.SUBTITLES -> choiceItems(SettingsGroup.SUBTITLES) + listOfNotNull(
            onOpenSubtitles?.let { TvItem(OPENSUBTITLES_ROW, stringResource(R.string.opensubtitles_title), "›", onClick = it) },
        )

        // Tema se bira sa spiska, kao i ostala podešavanja sa vrednostima.
        SettingsGroup.LOOK -> choiceItems(SettingsGroup.LOOK) + listOf(
            TvItem(ACCENT_ROW, stringResource(R.string.settings_accent_color), accentColor?.label()) {
                // Svaki pritisak prelazi na sledeću boju; promena se vidi odmah.
                val current = accentColor ?: AccentColor.DEFAULT
                viewModel.setAccentColor(AccentColor.entries[(current.ordinal + 1) % AccentColor.entries.size])
            },
            TvItem(BLACK_THEME_ROW, stringResource(R.string.settings_black_theme), checked = blackTheme) {
                viewModel.setBlackTheme(!blackTheme)
            },
            // Svaki pritisak prelazi na sledeću veličinu; promena se vidi odmah.
            TvItem(UI_SCALE_ROW, stringResource(R.string.settings_ui_scale), uiScale.label) {
                viewModel.setUiScale(UiScale.next(uiScale, isTv = true))
            },
            TvItem(VOD_PREVIEW_ROW, stringResource(R.string.settings_tv_vod_preview), checked = vodPreview) {
                viewModel.setTvVodPreview(!vodPreview)
            },
        )

        SettingsGroup.RECORDINGS -> listOfNotNull(
            onRecordings?.let { TvItem(RECORDINGS_ROW, stringResource(R.string.recordings_title), "›", onClick = it) },
        )

        SettingsGroup.ACCOUNTS -> listOfNotNull(
            onTrakt?.let { TvItem(TRAKT_ROW, stringResource(R.string.trakt_title), "›", onClick = it) },
            TvItem(WATCH_NEXT_ROW, stringResource(R.string.settings_watch_next), checked = watchNext) {
                viewModel.setWatchNext(!watchNext)
            },
            TvItem(AUTOSTART_ROW, stringResource(R.string.settings_autostart), checked = autostart) {
                val enable = !autostart
                viewModel.setAutostart(enable)
                if (enable && !autostartAllowed) {
                    permissionScreenMissing = !Autostart.openPermissionScreen(context)
                }
            },
        )

        SettingsGroup.PICTURE -> choiceItems(SettingsGroup.PICTURE) + listOf(
            TvItem(MATCH_FRAME_RATE_ROW, stringResource(R.string.settings_match_frame_rate), checked = matchFrameRate) {
                viewModel.setMatchFrameRate(!matchFrameRate)
            },
        )

        SettingsGroup.ABOUT -> listOf(
            TvItem(UPDATE_CHECK_ROW, stringResource(R.string.update_check), updateViewModel.installedVersion) {
                updateViewModel.check()
            },
            TvItem(UPDATE_CHANNEL_ROW, stringResource(R.string.update_channel), updateChannel.label()) {
                // Svaki pritisak prelazi na drugi kanal i odmah proverava šta on nudi.
                val channels = UpdateChannel.entries
                updateViewModel.setChannel(channels[(updateChannel.ordinal + 1) % channels.size])
            },
            TvItem(REOPEN_ROW, stringResource(R.string.settings_reopen_after_update), checked = reopens) {
                viewModel.setReopenAfterUpdate(!reopens)
                if (!reopens && !autostartAllowed) {
                    permissionScreenMissing = !Autostart.openPermissionScreen(context)
                }
            },
        )
    }

    // Nazad iz spiska vrednosti vraća na podešavanje, a iz stavki grupe na spisak grupa.
    BackHandler(enabled = inItems || openChoice != null) {
        val open = openChoice
        if (open != null && openSlot != null) {
            openSlot = null
        } else if (open != null) {
            rowFocus[open].tryRequestFocus()
            openChoice = null
        } else {
            currentGroup?.let { groupFocus[it.ordinal].tryRequestFocus() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        TvHeader(title = stringResource(R.string.settings_title), modifier = Modifier.padding(bottom = 16.dp))
        // Jedan stepen sitnija slova nego u spiskovima kanala i kategorija, da stane više teksta.
        SettingsType {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Levo: jezik i grupe. Sve staju na ekran; pomeranje je tu samo za najveću veličinu prikaza.
                Column(
                    modifier = Modifier
                        .weight(0.30f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // Jezik je prva stavka: ko ne razume trenutni jezik, nalazi ga odmah na vrhu.
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
                            currentGroup = null
                            openChoice = null
                        },
                        modifier = Modifier.focusRequester(languageFocus),
                    )
                    groups.forEach { group ->
                        TvTextRow(
                            text = stringResource(group.title),
                            icon = group.icon,
                            trailing = "›",
                            selected = currentGroup == group,
                            // OK ili desno ulazi u stavke grupe, uvek na prvu.
                            onClick = { firstItemFocus.tryRequestFocus() },
                            onFocused = {
                                focusedChoice = GROUP_ROW - group.ordinal
                                currentGroup = group
                                openChoice = null
                            },
                            modifier = Modifier
                                .focusRequester(groupFocus[group.ordinal])
                                .onPreviewKeyEvent {
                                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight) {
                                        firstItemFocus.tryRequestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                },
                        )
                    }
                }

                // Sredina: stavke grupe na kojoj je fokus. Levo se uvek vraća na tu grupu.
                Column(
                    modifier = Modifier
                        .weight(0.38f)
                        .onFocusChanged { inItems = it.hasFocus }
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) {
                                currentGroup?.let { group -> groupFocus[group.ordinal].tryRequestFocus() }
                                true
                            } else {
                                false
                            }
                        }
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items.forEachIndexed { index, item ->
                        key(item.id) {
                            TvTextRow(
                                text = item.text,
                                trailing = item.trailing,
                                checked = item.checked,
                                selected = openChoice == item.id,
                                onClick = item.onClick,
                                onFocused = {
                                    focusedChoice = item.id
                                    if (openChoice != item.id) openChoice = null
                                },
                                modifier = Modifier
                                    .then(if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                                    .then(if (item.id >= 0) Modifier.focusRequester(rowFocus[item.id]) else Modifier),
                            )
                        }
                    }
                }

                val open = openChoice
                Column(
                    modifier = Modifier
                        .weight(0.32f)
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
                        val parent = choices[open]
                        val slot = openSlot?.let { parent.slots.getOrNull(it) }
                        val choice = slot ?: parent
                        val change = stringResource(R.string.settings_language_change)
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(bottom = 27.dp),
                        ) {
                            if (slot == null && parent.slots.isNotEmpty()) {
                                // Mesta po redu važnosti; OK na mestu otvara spisak vrednosti za to mesto.
                                itemsIndexed(parent.slots) { index, item ->
                                    TvTextRow(
                                        text = "${index + 1}. ${item.value}",
                                        trailing = change,
                                        onClick = {
                                            lastSlot = index
                                            openSlot = index
                                        },
                                        modifier = if (index == lastSlot) Modifier.focusRequester(optionFocus) else Modifier,
                                    )
                                }
                            } else {
                                itemsIndexed(choice.options) { index, option ->
                                    TvTextRow(
                                        text = option,
                                        trailing = if (index == choice.selected) "✓" else null,
                                        selected = index == choice.selected,
                                        onClick = {
                                            choice.onSelect(index)
                                            if (slot != null) {
                                                // Posle izbora se opet vide mesta.
                                                openSlot = null
                                            } else {
                                                // Fokus se vraća na podešavanje pre nego što spisak vrednosti nestane.
                                                rowFocus[open].tryRequestFocus()
                                                openChoice = null
                                            }
                                        },
                                        modifier = if (index == 0) Modifier.focusRequester(optionFocus) else Modifier,
                                    )
                                }
                            }
                        }
                        // Fokus prelazi na vrednosti čim se otvore; Nazad ili levo ga vraća na podešavanja.
                        LaunchedEffect(open, openSlot) { optionFocus.tryRequestFocus() }
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
    // Fokus kreće sa reda na kome je bio (posle povratka sa podekrana ili promene jezika).
    LaunchedEffect(Unit) {
        val group = currentGroup
        if (group == null) languageFocus.tryRequestFocus() else groupFocus[group.ordinal].tryRequestFocus()
    }
}

/** Jedan red u srednjoj koloni: podešavanje ili veza ka drugom ekranu. [id] bira objašnjenje desno. */
private class TvItem(
    val id: Int,
    val text: String,
    val trailing: String? = null,
    val checked: Boolean? = null,
    val onClick: () -> Unit,
)

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
        in GROUP_ROW - SettingsGroup.entries.size + 1..GROUP_ROW ->
            stringResource(SettingsGroup.entries[GROUP_ROW - focused].summary)
        CATEGORIES_ROW -> stringResource(R.string.settings_categories_summary)
        PARENTAL_ROW -> stringResource(R.string.settings_parental_summary)
        TRAKT_ROW -> stringResource(R.string.settings_trakt_summary)
        OPENSUBTITLES_ROW -> stringResource(R.string.settings_opensubtitles_summary)
        RECORDINGS_ROW -> stringResource(R.string.settings_recordings_summary)
        REMOTE_BUTTONS_ROW -> stringResource(R.string.settings_remote_buttons_summary)
        TV_PREVIEW_ROW -> stringResource(R.string.settings_tv_preview_summary)
        VOD_PREVIEW_ROW -> stringResource(R.string.settings_tv_vod_preview_summary)
        UPDATE_CHECK_ROW -> stringResource(R.string.update_check_summary)
        RESUME_ROW -> stringResource(R.string.settings_resume_last_summary)
        ACCENT_ROW -> stringResource(R.string.settings_accent_color_tv_summary)
        BLACK_THEME_ROW -> stringResource(R.string.settings_black_theme_summary)
        PAUSE_INFO_ROW -> stringResource(R.string.settings_pause_info_summary)
        UI_SCALE_ROW -> stringResource(R.string.settings_ui_scale_tv_summary)
        LANGUAGE_ROW -> languageSummary() + " " + stringResource(R.string.settings_language_tv_summary)
        AUTO_REFRESH_ROW -> stringResource(R.string.settings_auto_refresh_summary)
        MATCH_FRAME_RATE_ROW -> stringResource(R.string.settings_match_frame_rate_summary)
        WATCH_NEXT_ROW -> stringResource(R.string.settings_watch_next_summary)
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
private const val WATCH_NEXT_ROW = -18
private const val BLACK_THEME_ROW = -19
private const val PAUSE_INFO_ROW = -20
private const val REMOTE_BUTTONS_ROW = -21

/** Oznaka reda prve grupe; grupa sa rednim brojem n ima oznaku GROUP_ROW - n. */
private const val GROUP_ROW = -100

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
