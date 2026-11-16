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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.bane23.iptvplayer.ui.common.theme.label
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import com.bane23.iptvplayer.ui.common.UpdateViewModel
import com.bane23.iptvplayer.ui.common.player.SubtitlePreview
import com.bane23.iptvplayer.ui.common.playerSettingChoices
import com.bane23.iptvplayer.work.Autostart

/**
 * Podešavanja aplikacije na TV-u: levo spisak podešavanja, desno vrednosti onog koje je izabrano.
 * OK na podešavanju otvara njegove vrednosti, OK na vrednosti je bira i vraća fokus levo.
 */
@Composable
fun TvSettingsScreen(
    onCategories: (() -> Unit)? = null,
    onParental: (() -> Unit)? = null,
    onTrakt: (() -> Unit)? = null,
    onRecordings: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val resumeLastChannel by viewModel.resumeLastChannel.collectAsStateWithLifecycle()
    val channelPreview by viewModel.tvChannelPreview.collectAsStateWithLifecycle()
    val autostart by viewModel.autostart.collectAsStateWithLifecycle()
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
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
    // Podešavanje na kome je fokus, zbog objašnjenja ispod spiska.
    var focusedChoice by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp),
        )
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
                            focusedChoice = null
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
                            focusedChoice = null
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
                            focusedChoice = null
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
                            focusedChoice = null
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
                        focusedChoice = null
                        openChoice = null
                    },
                    modifier = Modifier.focusRequester(firstFocus),
                )
                TvTextRow(
                    text = stringResource(R.string.settings_tv_preview),
                    checked = channelPreview,
                    onClick = { viewModel.setTvChannelPreview(!channelPreview) },
                    onFocused = {
                        focusedChoice = null
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
                    text = stringResource(R.string.update_check),
                    trailing = updateViewModel.installedVersion,
                    onClick = updateViewModel::check,
                    onFocused = {
                        focusedChoice = null
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
                Text(
                    text = when (val focused = focusedChoice) {
                        null -> stringResource(R.string.settings_resume_last_summary)
                        ACCENT_ROW -> stringResource(R.string.settings_accent_color_tv_summary)
                        LANGUAGE_ROW -> stringResource(R.string.settings_language_tv_summary)
                        AUTOSTART_ROW -> stringResource(
                            when {
                                !autostart || autostartAllowed -> R.string.settings_autostart_summary
                                permissionScreenMissing -> R.string.settings_autostart_manual
                                else -> R.string.settings_autostart_permission
                            },
                        )
                        else -> choices[focused].summary
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                )
                Text(
                    text = stringResource(R.string.settings_player_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
            }

            val open = openChoice
            // Probni titl se vidi dok je fokus na podešavanju izgleda titlova ili se bira njegova vrednost.
            val showPreview = (open ?: focusedChoice)?.let { choices.getOrNull(it)?.subtitleLook } == true
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
}

/** Oznaka reda „Pokreni pri paljenju” na mestu rednog broja podešavanja plejera. */
private const val AUTOSTART_ROW = -1
private const val ACCENT_ROW = -2
private const val LANGUAGE_ROW = -3
