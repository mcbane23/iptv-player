package com.bane23.iptvplayer.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.bane23.iptvplayer.domain.RemoteAction
import com.bane23.iptvplayer.domain.RemoteButton
import com.bane23.iptvplayer.ui.common.player.RemoteButtonsViewModel
import com.bane23.iptvplayer.ui.common.theme.glowBackground

/**
 * „Dugmad daljinskog” na TV-u: levo dugmad sa radnjom koju trenutno imaju, desno objašnjenje i,
 * posle OK, spisak radnji za izabrano dugme. OK na radnji je bira i vraća fokus na dugme.
 */
@Composable
fun TvRemoteButtonsScreen(viewModel: RemoteButtonsViewModel = hiltViewModel()) {
    val mapping by viewModel.mapping.collectAsStateWithLifecycle()
    val buttons = RemoteButton.entries
    val actions = RemoteAction.entries
    val rowFocus = remember { List(buttons.size) { FocusRequester() } }
    val optionFocus = remember { FocusRequester() }
    // Dugme čije se radnje trenutno biraju, ili null.
    var open by remember { mutableStateOf<RemoteButton?>(null) }
    // Dugme na kome je fokus, zbog objašnjenja desno.
    var focused by remember { mutableStateOf<RemoteButton?>(null) }

    // Nazad prvo zatvara spisak radnji i vraća fokus na dugme.
    BackHandler(enabled = open != null) {
        open?.let { rowFocus[it.ordinal].tryRequestFocus() }
        open = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        TvHeader(title = stringResource(R.string.remote_buttons_title), modifier = Modifier.padding(bottom = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                buttons.forEach { button ->
                    TvTextRow(
                        text = stringResource(button.label()),
                        trailing = stringResource((mapping[button] ?: button.defaultAction).label()),
                        selected = open == button,
                        onClick = { open = button },
                        onFocused = {
                            focused = button
                            if (open != button) open = null
                        },
                        modifier = Modifier.focusRequester(rowFocus[button.ordinal]),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Objašnjenje stoji van spiska koji se pomera, pa se vidi na kom god dugmetu da je fokus.
                (open ?: focused)?.let { button ->
                    Text(
                        text = stringResource(button.summary()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                open?.let { button ->
                    val current = mapping[button] ?: button.defaultAction
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(bottom = 27.dp),
                    ) {
                        itemsIndexed(actions) { index, action ->
                            TvTextRow(
                                text = stringResource(action.label()),
                                trailing = if (action == current) "✓" else null,
                                selected = action == current,
                                onClick = {
                                    viewModel.set(button, action)
                                    // Fokus se vraća na dugme pre nego što spisak radnji nestane.
                                    rowFocus[button.ordinal].tryRequestFocus()
                                    open = null
                                },
                                modifier = if (index == 0) Modifier.focusRequester(optionFocus) else Modifier,
                            )
                        }
                    }
                    // Fokus prelazi na radnje čim se otvore.
                    LaunchedEffect(button) { optionFocus.tryRequestFocus() }
                }
            }
        }
    }
    LaunchedEffect(Unit) { rowFocus.first().tryRequestFocus() }
}

private fun RemoteButton.label(): Int = when (this) {
    RemoteButton.RED -> R.string.remote_button_red
    RemoteButton.GREEN -> R.string.remote_button_green
    RemoteButton.YELLOW -> R.string.remote_button_yellow
    RemoteButton.BLUE -> R.string.remote_button_blue
    RemoteButton.CHANNEL_UP -> R.string.remote_button_channel_up
    RemoteButton.CHANNEL_DOWN -> R.string.remote_button_channel_down
    RemoteButton.MENU -> R.string.remote_button_menu
    RemoteButton.LONG_OK -> R.string.remote_button_long_ok
}

private fun RemoteButton.summary(): Int = when (this) {
    RemoteButton.LONG_OK -> R.string.remote_button_long_ok_summary
    RemoteButton.MENU -> R.string.remote_button_menu_summary
    else -> R.string.remote_button_summary
}

private fun RemoteAction.label(): Int = when (this) {
    RemoteAction.NONE -> R.string.remote_action_none
    RemoteAction.LAST_CHANNEL -> R.string.player_last_channel
    RemoteAction.NEXT_CHANNEL -> R.string.remote_action_next_channel
    RemoteAction.PREVIOUS_CHANNEL -> R.string.remote_action_previous_channel
    RemoteAction.CHANNEL_LIST -> R.string.remote_action_channel_list
    RemoteAction.SCHEDULE -> R.string.remote_action_schedule
    RemoteAction.FAVORITE -> R.string.remote_action_favorite
    RemoteAction.AUDIO -> R.string.player_audio
    RemoteAction.SUBTITLES -> R.string.player_subtitles
    RemoteAction.QUICK_SETTINGS -> R.string.player_quick_settings
    RemoteAction.INFO -> R.string.player_stream_info
    RemoteAction.CONTROLS -> R.string.remote_action_controls
}
