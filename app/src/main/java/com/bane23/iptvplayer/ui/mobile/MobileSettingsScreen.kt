package com.bane23.iptvplayer.ui.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bane23.iptvplayer.ui.common.AppLanguage
import com.bane23.iptvplayer.ui.common.AppLocale
import com.bane23.iptvplayer.ui.common.theme.AccentColor
import com.bane23.iptvplayer.ui.common.theme.UiScale
import com.bane23.iptvplayer.ui.common.theme.label
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.update.UpdateChannel
import com.bane23.iptvplayer.ui.common.label
import com.bane23.iptvplayer.ui.common.languageSummary
import com.bane23.iptvplayer.ui.common.SettingsGroup
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import com.bane23.iptvplayer.ui.common.UpdateViewModel
import com.bane23.iptvplayer.ui.common.HiddenChannelsViewModel
import com.bane23.iptvplayer.ui.common.crashReportValue
import com.bane23.iptvplayer.ui.common.handOverCrashReport
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.SubtitlePreview
import com.bane23.iptvplayer.ui.common.playerSettingChoices
import com.bane23.iptvplayer.ui.common.ScreenPosition
import com.bane23.iptvplayer.ui.common.screenPositionChoice
import com.bane23.iptvplayer.ui.common.themeChoice
import com.bane23.iptvplayer.ui.common.theme.Glass
import androidx.compose.foundation.BorderStroke

/** Podešavanja aplikacije na telefonu. */
@Composable
fun MobileSettingsScreen(
    onBack: (() -> Unit)? = null,
    onPlaylists: (() -> Unit)? = null,
    onCategories: (() -> Unit)? = null,
    onParental: (() -> Unit)? = null,
    onTrakt: (() -> Unit)? = null,
    onOpenSubtitles: (() -> Unit)? = null,
    onRecordings: (() -> Unit)? = null,
    onDownloads: (() -> Unit)? = null,
    onTransfer: (() -> Unit)? = null,
    onBackup: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
    hiddenViewModel: HiddenChannelsViewModel = hiltViewModel(),
) {
    val resumeLastChannel by viewModel.resumeLastChannel.collectAsStateWithLifecycle()
    val autoRefreshPlaylists by viewModel.autoRefreshPlaylists.collectAsStateWithLifecycle()
    val vodLocalCache by viewModel.vodLocalCache.collectAsStateWithLifecycle()
    val drawUnderCutout by viewModel.drawUnderCutout.collectAsStateWithLifecycle()
    val appFullScreen by viewModel.appFullScreen.collectAsStateWithLifecycle()
    val playerSettings by viewModel.playerSettings.collectAsStateWithLifecycle()
    val brightnessSlider by viewModel.playerBrightnessSwipe.collectAsStateWithLifecycle()
    val volumeSlider by viewModel.playerVolumeSwipe.collectAsStateWithLifecycle()
    val backgroundPlay by viewModel.backgroundPlay.collectAsStateWithLifecycle()
    val pauseInfo by viewModel.pauseInfo.collectAsStateWithLifecycle()
    val autoNextEpisode by viewModel.autoNextEpisode.collectAsStateWithLifecycle()
    val channelRules by viewModel.channelRules.collectAsStateWithLifecycle()
    val downloadOnMobile by viewModel.downloadOnMobile.collectAsStateWithLifecycle()
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val blackTheme by viewModel.blackTheme.collectAsStateWithLifecycle()
    val classicLook by viewModel.classicLook.collectAsStateWithLifecycle()
    val uiScale = UiScale.fromName(viewModel.uiScale.collectAsStateWithLifecycle().value, isTv = false)
    val screenPosition by viewModel.screenPosition.collectAsStateWithLifecycle()
    val choices = playerSettingChoices(playerSettings, viewModel) + themeChoice(appTheme, viewModel::setAppTheme) +
        screenPositionChoice(ScreenPosition.fromName(screenPosition), viewModel::setScreenPosition)
    // Podešavanje čiji se spisak vrednosti trenutno bira, ili null.
    var openChoice by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val language = AppLocale.get(context)
    var languageOpen by remember { mutableStateOf(false) }
    var channelOpen by remember { mutableStateOf(false) }
    var hiddenOpen by remember { mutableStateOf(false) }
    val hiddenChannels by hiddenViewModel.hidden.collectAsStateWithLifecycle()
    val updateChannel by updateViewModel.channel.collectAsStateWithLifecycle()

    // Otvorena grupa; null je glavni ekran sa spiskom grupa.
    var group by rememberSaveable { mutableStateOf<SettingsGroup?>(null) }
    val current = group
    BackHandler(enabled = current != null) { group = null }
    // Podešavanje sa mestima (npr. jezici titlova) čiji je podekran otvoren, i mesto čija se vrednost bira.
    var openSlots by rememberSaveable { mutableStateOf<Int?>(null) }
    var openSlot by remember { mutableStateOf<Int?>(null) }
    BackHandler(enabled = openSlots != null) { openSlots = null }
    val groups = SettingsGroup.entries.filter { it != SettingsGroup.ACCOUNTS || onTrakt != null || onTransfer != null || onBackup != null }
    // Podešavanja plejera koja pripadaju otvorenoj grupi.
    val choiceRows: @Composable () -> Unit = {
        choices.forEachIndexed { index, choice ->
            if (choice.group != current) return@forEachIndexed
            if (choice.toggle) {
                SettingsSwitchRow(choice.title, choice.summary, choice.checked) { choice.flip() }
            } else if (choice.slots.isNotEmpty()) {
                SettingsSlotsRow(choice.title, choice.summary, choice.value) { openSlots = index }
            } else {
                SettingsValueRow(choice.title, choice.summary, choice.value, choice.options) { openChoice = index }
            }
        }
    }

    // Providno: pozadinu sa sjajem crta početni ekran.
    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) { padding ->
        // Svaki ekran (glavni i svaka grupa) ima svoj položaj pomeranja.
        val slotsChoice = openSlots?.let { choices.getOrNull(it) }
        key(current, openSlots) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (slotsChoice != null) {
                    SectionTopBar(title = slotsChoice.title, onBack = { openSlots = null })
                    SettingsCard {
                        slotsChoice.slots.forEachIndexed { index, slot ->
                            SettingsValueRow(
                                title = "${index + 1}. ${slot.value}",
                                summary = slot.title,
                                value = stringResource(R.string.settings_language_change),
                                onClick = { openSlot = index },
                            )
                        }
                    }
                    Text(
                        text = slotsChoice.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
                    )
                } else if (current == null) {
                    SectionTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
                    // Jezik je prva stavka: ko ne razume trenutni jezik, nalazi ga odmah na vrhu.
                    SettingsCard {
                        SettingsValueRow(
                            title = stringResource(R.string.settings_language),
                            summary = languageSummary(),
                            value = language.label(),
                            options = AppLanguage.entries.map { it.label() },
                            onClick = { languageOpen = true },
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsCard {
                        groups.forEach {
                            SettingsLinkRow(
                                icon = it.icon,
                                title = stringResource(it.title),
                                summary = stringResource(it.summary),
                                onClick = { group = it },
                                accent = true,
                            )
                        }
                    }
                } else {
                    SectionTopBar(title = stringResource(current.title), onBack = { group = null })
                    SettingsCard {
                        when (current) {
                            SettingsGroup.CONTENT -> {
                                if (onPlaylists != null) {
                                    SettingsLinkRow(
                                        AppIcons.Playlists,
                                        stringResource(R.string.playlists_title),
                                        stringResource(R.string.settings_playlists_summary),
                                        onPlaylists,
                                    )
                                }
                                if (onCategories != null) {
                                    SettingsLinkRow(
                                        AppIcons.Categories,
                                        stringResource(R.string.categories_manage_title),
                                        stringResource(R.string.settings_categories_summary),
                                        onCategories,
                                    )
                                }
                                SettingsLinkRow(
                                    AppIcons.LiveTv,
                                    stringResource(R.string.hidden_channels_title),
                                    stringResource(R.string.hidden_channels_summary),
                                    onClick = { hiddenOpen = true },
                                )
                                if (onParental != null) {
                                    SettingsLinkRow(
                                        AppIcons.Lock,
                                        stringResource(R.string.parental_title),
                                        stringResource(R.string.settings_parental_summary),
                                        onParental,
                                    )
                                }
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_auto_refresh),
                                    summary = stringResource(R.string.settings_auto_refresh_summary),
                                    checked = autoRefreshPlaylists,
                                    onChange = viewModel::setAutoRefreshPlaylists,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_vod_cache),
                                    summary = stringResource(R.string.settings_vod_cache_summary),
                                    checked = vodLocalCache,
                                    onChange = viewModel::setVodLocalCache,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_rule_hide_sd),
                                    summary = stringResource(R.string.settings_rule_hide_sd_summary),
                                    checked = channelRules.hideSd,
                                    onChange = viewModel::setRuleHideSd,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_rule_plus_one),
                                    summary = stringResource(R.string.settings_rule_plus_one_summary),
                                    checked = channelRules.hidePlusOne,
                                    onChange = viewModel::setRuleHidePlusOne,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_rule_same_name),
                                    summary = stringResource(R.string.settings_rule_same_name_summary),
                                    checked = channelRules.mergeSameName,
                                    onChange = viewModel::setRuleMergeSameName,
                                )
                            }

                            SettingsGroup.WATCHING -> {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_resume_last),
                                    summary = stringResource(R.string.settings_resume_last_summary),
                                    checked = resumeLastChannel,
                                    onChange = viewModel::setResumeLastChannel,
                                )
                                choiceRows()
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_pause_info),
                                    summary = stringResource(R.string.settings_pause_info_summary),
                                    checked = pauseInfo,
                                    onChange = viewModel::setPauseInfo,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_auto_next),
                                    summary = stringResource(R.string.settings_auto_next_summary),
                                    checked = autoNextEpisode,
                                    onChange = viewModel::setAutoNextEpisode,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_background_play),
                                    summary = stringResource(R.string.settings_background_play_summary),
                                    checked = backgroundPlay,
                                    onChange = viewModel::setBackgroundPlay,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_brightness_swipe),
                                    summary = stringResource(R.string.settings_brightness_swipe_summary),
                                    checked = brightnessSlider,
                                    onChange = viewModel::setPlayerBrightnessSwipe,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_volume_swipe),
                                    summary = stringResource(R.string.settings_volume_swipe_summary),
                                    checked = volumeSlider,
                                    onChange = viewModel::setPlayerVolumeSwipe,
                                )
                            }

                            SettingsGroup.SUBTITLES -> {
                                choiceRows()
                                if (onOpenSubtitles != null) {
                                    SettingsLinkRow(
                                        AppIcons.Subtitles,
                                        stringResource(R.string.opensubtitles_title),
                                        stringResource(R.string.settings_opensubtitles_summary),
                                        onOpenSubtitles,
                                    )
                                }
                            }

                            SettingsGroup.LOOK -> {
                                // Tema se bira sa spiska, kao i ostala podešavanja sa vrednostima.
                                choiceRows()
                                AccentColorRow(selected = accentColor, onSelect = viewModel::setAccentColor)
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_black_theme),
                                    summary = stringResource(R.string.settings_black_theme_summary),
                                    checked = blackTheme,
                                    onChange = viewModel::setBlackTheme,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_classic_look),
                                    summary = stringResource(R.string.settings_classic_look_summary),
                                    checked = classicLook,
                                    onChange = viewModel::setClassicLook,
                                )
                                // Svaki dodir prelazi na sledeću veličinu; promena se vidi odmah.
                                SettingsValueRow(
                                    title = stringResource(R.string.settings_ui_scale),
                                    summary = stringResource(R.string.settings_ui_scale_summary),
                                    value = uiScale.label,
                                    options = UiScale.choices(isTv = false).map { it.label },
                                    onClick = { viewModel.setUiScale(UiScale.next(uiScale, isTv = false)) },
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_under_cutout),
                                    summary = stringResource(R.string.settings_under_cutout_summary),
                                    checked = drawUnderCutout,
                                    onChange = viewModel::setDrawUnderCutout,
                                )
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_full_screen),
                                    summary = stringResource(R.string.settings_full_screen_summary),
                                    checked = appFullScreen,
                                    onChange = viewModel::setAppFullScreen,
                                )
                            }

                            SettingsGroup.RECORDINGS -> {
                                if (onRecordings != null) {
                                    SettingsLinkRow(
                                        AppIcons.Record,
                                        stringResource(R.string.recordings_title),
                                        stringResource(R.string.settings_recordings_summary),
                                        onRecordings,
                                    )
                                }
                                if (onDownloads != null) {
                                    SettingsLinkRow(
                                        AppIcons.Download,
                                        stringResource(R.string.downloads_title),
                                        stringResource(R.string.settings_downloads_summary),
                                        onDownloads,
                                    )
                                }
                                SettingsSwitchRow(
                                    title = stringResource(R.string.settings_download_mobile),
                                    summary = stringResource(R.string.settings_download_mobile_summary),
                                    checked = downloadOnMobile,
                                    onChange = viewModel::setDownloadOnMobile,
                                )
                            }

                            SettingsGroup.ACCOUNTS -> {
                                if (onTrakt != null) {
                                    SettingsLinkRow(
                                        AppIcons.Check,
                                        stringResource(R.string.trakt_title),
                                        stringResource(R.string.settings_trakt_summary),
                                        onTrakt,
                                    )
                                }
                                if (onTransfer != null) {
                                    SettingsLinkRow(
                                        AppIcons.Cast,
                                        stringResource(R.string.transfer_all_title),
                                        stringResource(R.string.settings_transfer_summary),
                                        onTransfer,
                                    )
                                }
                                if (onBackup != null) {
                                    SettingsLinkRow(
                                        AppIcons.Download,
                                        stringResource(R.string.backup_title),
                                        stringResource(R.string.settings_backup_summary),
                                        onBackup,
                                    )
                                }
                            }

                            SettingsGroup.PICTURE -> choiceRows()

                            SettingsGroup.ABOUT -> {
                                SettingsValueRow(
                                    title = stringResource(R.string.update_check),
                                    summary = stringResource(R.string.update_check_summary),
                                    value = updateViewModel.installedVersion,
                                    onClick = updateViewModel::check,
                                )
                                SettingsValueRow(
                                    title = stringResource(R.string.update_channel),
                                    summary = stringResource(R.string.update_channel_summary),
                                    value = updateChannel.label(),
                                    options = UpdateChannel.entries.map { it.label() },
                                    onClick = { channelOpen = true },
                                )
                                SettingsValueRow(
                                    title = stringResource(R.string.crash_report_title),
                                    summary = stringResource(R.string.crash_report_summary),
                                    value = crashReportValue(),
                                    onClick = { handOverCrashReport(context) },
                                )
                            }
                        }
                    }
                    if (current == SettingsGroup.SUBTITLES) {
                        // Probni titl: odmah pokazuje izabranu veličinu, položaj i pozadinu.
                        SubtitlePreview(
                            settings = playerSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    if (choices.any { it.group == current }) {
                        Text(
                            text = stringResource(R.string.settings_player_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (hiddenOpen) {
        // Dodir vraća kanal u spiskove i zatvara prozor.
        TrackDialog(
            title = stringResource(R.string.hidden_channels_title),
            options = hiddenChannels.map { it.name to false },
            emptyText = stringResource(R.string.hidden_channels_empty),
            onSelect = { index -> hiddenChannels.getOrNull(index)?.let(hiddenViewModel::show) },
            onDismiss = { hiddenOpen = false },
        )
    }

    if (channelOpen) {
        val channels = UpdateChannel.entries
        TrackDialog(
            title = stringResource(R.string.update_channel),
            options = channels.map { it.label() to (it == updateChannel) },
            emptyText = null,
            onSelect = {
                channelOpen = false
                if (channels[it] != updateChannel) updateViewModel.setChannel(channels[it])
            },
            onDismiss = { channelOpen = false },
        )
    }

    if (languageOpen) {
        val languages = AppLanguage.entries
        TrackDialog(
            title = stringResource(R.string.settings_language),
            options = languages.map { it.label() to (it == language) },
            emptyText = null,
            onSelect = {
                languageOpen = false
                AppLocale.change(context, languages[it])
            },
            onDismiss = { languageOpen = false },
        )
    }

    openSlot?.let { index ->
        val slot = openSlots?.let { choices.getOrNull(it) }?.slots?.getOrNull(index)
        if (slot != null) {
            TrackDialog(
                title = slot.title,
                options = slot.options.mapIndexed { option, label -> label to (option == slot.selected) },
                emptyText = null,
                onSelect = slot.onSelect,
                onDismiss = { openSlot = null },
            )
        }
    }

    openChoice?.let { index ->
        val choice = choices[index]
        TrackDialog(
            title = choice.title,
            options = choice.options.mapIndexed { option, label -> label to (option == choice.selected) },
            emptyText = null,
            onSelect = choice.onSelect,
            onDismiss = { openChoice = null },
        )
    }
}

/** Izbor akcentne boje: naziv i red krugova u ponuđenim bojama; izabrana ima beli prsten. */
@Composable
private fun AccentColorRow(selected: AccentColor?, onSelect: (AccentColor) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.settings_accent_color),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected != null) {
                Text(
                    text = selected.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_accent_color_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            modifier = Modifier
                .padding(top = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentColor.entries.forEach { accent ->
                val description = accent.label()
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .then(
                            if (accent == selected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else {
                                Modifier
                            },
                        )
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(accent.color)
                        .selectable(selected = accent == selected, role = Role.RadioButton) { onSelect(accent) }
                        .semantics { contentDescription = description },
                )
            }
        }
    }
}

/** Zaobljena kartica sa redovima podešavanja. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = Glass.CardShape,
        color = Glass.Fill,
        border = BorderStroke(1.dp, Glass.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(content = content)
    }
}

/**
 * Podešavanje sa vrednošću: naziv i opis levo, trenutna vrednost desno. Dodir ga menja ili otvara izbor.
 *
 * Vrednost zauzima širinu najduže od ponuđenih vrednosti ([options]), pa se opis uvek isto prelama
 * i red ne menja visinu kad se izabere duža ili kraća vrednost.
 */
@Composable
private fun SettingsValueRow(
    title: String,
    summary: String,
    value: String,
    options: List<String> = emptyList(),
    onClick: () -> Unit,
) {
    val valueStyle = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val valueWidth = remember(options, valueStyle, density) {
        val widest = options.maxOfOrNull { measurer.measure(it, valueStyle, maxLines = 1).size.width } ?: 0
        with(density) { widest.toDp() }.coerceAtMost(VALUE_MAX_WIDTH)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = valueStyle,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier
                .padding(start = 12.dp)
                .widthIn(min = valueWidth, max = VALUE_MAX_WIDTH),
        )
    }
}

/** Najveća širina vrednosti u redu podešavanja; duža vrednost se skraćuje, da ne istisne naziv i opis. */
private val VALUE_MAX_WIDTH = 160.dp

/** Podešavanje sa više vrednosti po redu (npr. jezici): naziv, opis i ispod njih izabrane vrednosti. Dodir otvara podekran. */
@Composable
private fun SettingsSlotsRow(title: String, summary: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Prekidač: naziv, opis ispod njega i prekidač desno. */
@Composable
private fun SettingsSwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
    }
}

/** Red koji vodi na drugi ekran (npr. Liste): ikonica, naziv sa opisom i strelica desno. */
@Composable
private fun SettingsLinkRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    // Ikonica u boji aplikacije: za grupe na glavnom ekranu.
    accent: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
