package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.SettingsViewModel
import com.bane23.iptvplayer.ui.common.playerSettingChoices

/**
 * Podešavanja aplikacije na TV-u: levo spisak podešavanja, desno vrednosti onog koje je izabrano.
 * OK na podešavanju otvara njegove vrednosti, OK na vrednosti je bira i vraća fokus levo.
 */
@Composable
fun TvSettingsScreen(
    onCategories: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val resumeLastChannel by viewModel.resumeLastChannel.collectAsStateWithLifecycle()
    val channelPreview by viewModel.tvChannelPreview.collectAsStateWithLifecycle()
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
                modifier = Modifier.weight(0.55f),
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
                Text(
                    text = stringResource(R.string.settings_general_header),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp),
                )
                TvTextRow(
                    text = stringResource(R.string.settings_resume_last),
                    trailing = stringResource(if (resumeLastChannel) R.string.setting_on else R.string.setting_off),
                    onClick = { viewModel.setResumeLastChannel(!resumeLastChannel) },
                    onFocused = {
                        focusedChoice = null
                        openChoice = null
                    },
                    modifier = Modifier.focusRequester(firstFocus),
                )
                TvTextRow(
                    text = stringResource(R.string.settings_tv_preview),
                    trailing = stringResource(if (channelPreview) R.string.setting_on else R.string.setting_off),
                    onClick = { viewModel.setTvChannelPreview(!channelPreview) },
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
                        trailing = choice.value,
                        selected = openChoice == index,
                        onClick = { openChoice = index },
                        onFocused = {
                            focusedChoice = index
                            if (openChoice != index) openChoice = null
                        },
                        modifier = Modifier.focusRequester(rowFocus[index]),
                    )
                }
                Text(
                    text = focusedChoice?.let { choices[it].summary }
                        ?: stringResource(R.string.settings_resume_last_summary),
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
            if (open != null) {
                val choice = choices[open]
                LazyColumn(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight(),
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
        }
    }
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
}
