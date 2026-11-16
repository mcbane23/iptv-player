package com.bane23.iptvplayer.ui.mobile

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
import com.bane23.iptvplayer.ui.common.label
import com.bane23.iptvplayer.ui.common.theme.AccentColor
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import com.bane23.iptvplayer.ui.common.UpdateViewModel
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.SubtitlePreview
import com.bane23.iptvplayer.ui.common.playerSettingChoices

/** Podešavanja aplikacije na telefonu. */
@Composable
fun MobileSettingsScreen(
    onBack: (() -> Unit)? = null,
    onPlaylists: (() -> Unit)? = null,
    onCategories: (() -> Unit)? = null,
    onParental: (() -> Unit)? = null,
    onTrakt: (() -> Unit)? = null,
    onRecordings: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val resumeLastChannel by viewModel.resumeLastChannel.collectAsStateWithLifecycle()
    val drawUnderCutout by viewModel.drawUnderCutout.collectAsStateWithLifecycle()
    val playerSettings by viewModel.playerSettings.collectAsStateWithLifecycle()
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
    val choices = playerSettingChoices(playerSettings, viewModel)
    // Podešavanje čiji se spisak vrednosti trenutno bira, ili null.
    var openChoice by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val language = AppLocale.get(context)
    var languageOpen by remember { mutableStateOf(false) }

    // Providno: pozadinu sa sjajem crta početni ekran.
    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
            if (onPlaylists != null || onCategories != null || onParental != null || onTrakt != null || onRecordings != null) {
                SettingsGroup(stringResource(R.string.settings_content_header)) {
                    if (onPlaylists != null) {
                        SettingsLinkRow(AppIcons.Playlists, stringResource(R.string.playlists_title), onPlaylists)
                    }
                    if (onCategories != null) {
                        SettingsLinkRow(AppIcons.Categories, stringResource(R.string.categories_manage_title), onCategories)
                    }
                    if (onParental != null) {
                        SettingsLinkRow(AppIcons.Lock, stringResource(R.string.parental_title), onParental)
                    }
                    if (onTrakt != null) {
                        SettingsLinkRow(AppIcons.Check, stringResource(R.string.trakt_title), onTrakt)
                    }
                    if (onRecordings != null) {
                        SettingsLinkRow(AppIcons.Record, stringResource(R.string.recordings_title), onRecordings)
                    }
                }
            }
            SettingsGroup(stringResource(R.string.settings_general_header)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_resume_last),
                    summary = stringResource(R.string.settings_resume_last_summary),
                    checked = resumeLastChannel,
                    onChange = viewModel::setResumeLastChannel,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_under_cutout),
                    summary = stringResource(R.string.settings_under_cutout_summary),
                    checked = drawUnderCutout,
                    onChange = viewModel::setDrawUnderCutout,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { languageOpen = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_language),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = language.label(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                AccentColorRow(selected = accentColor, onSelect = viewModel::setAccentColor)
            }
            SettingsGroup(stringResource(R.string.settings_player_header)) {
                choices.forEachIndexed { index, choice ->
                    if (choice.toggle) {
                        SettingsSwitchRow(choice.title, choice.summary, choice.checked) { choice.flip() }
                        return@forEachIndexed
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openChoice = index }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = choice.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = choice.value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .widthIn(max = 160.dp),
                            )
                        }
                        Text(
                            text = choice.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                // Probni titl: odmah pokazuje izabranu veličinu, položaj i pozadinu.
                SubtitlePreview(
                    settings = playerSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            Text(
                text = stringResource(R.string.settings_player_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
            )
            SettingsGroup(stringResource(R.string.settings_about_header)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = updateViewModel::check)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.update_check),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.update_current_version, updateViewModel.installedVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
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
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected != null) {
                Text(
                    text = selected.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
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

/** Grupa podešavanja: naslov iznad i zaobljena kartica sa redovima. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 8.dp),
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(content = content)
    }
}

/** Red koji vodi na drugi ekran (npr. Liste): ikonica, naziv i strelica desno. */
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
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
    }
}

/** Red koji vodi na drugi ekran. */
@Composable
private fun SettingsLinkRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
