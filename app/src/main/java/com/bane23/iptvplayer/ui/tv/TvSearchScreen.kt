package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import com.bane23.iptvplayer.ui.common.FavoriteListsViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.theme.glowBackground
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.ChannelLogo
import com.bane23.iptvplayer.ui.common.SearchViewModel
import com.bane23.iptvplayer.ui.common.recording.RecordingNotices

/**
 * Pretraga u Uživo na TV-u, po nazivu kanala i po nazivu emisije u vodiču: polje za unos gore,
 * rezultati ispod (prvo kanali, pa emisije).
 */
@Composable
fun TvSearchScreen(
    onPlay: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val results = viewModel.results.collectAsLazyPagingItems()
    val programs by viewModel.programs.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    RecordingNotices(viewModel.notice)
    val now = System.currentTimeMillis()
    val fieldFocus = remember { FocusRequester() }
    // Posle Nazad iz plejera fokus se vraća na otvoreni rezultat, ne u polje za kucanje.
    val back = rememberTvReturnFocus()
    val play: (Long) -> Unit = { id ->
        back.opened()
        onPlay(id)
    }
    val favoriteLists: FavoriteListsViewModel = hiltViewModel()
    TvFavoritePickDialog(favoriteLists)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        // Isto zaglavlje kao na ostalim TV ekranima: naslov levo, sat desno.
        TvHeader(
            title = stringResource(R.string.search_title),
            modifier = Modifier.padding(bottom = 16.dp),
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = { androidx.compose.material3.Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .focusRequester(fieldFocus)
                    .tvFieldExit(),
        )
        if (state.query.isNotBlank() && results.itemCount == 0 && programs.isEmpty() &&
            results.loadState.refresh is LoadState.NotLoading
        ) {
            TvEmptyState(icon = AppIcons.Search, text = stringResource(R.string.search_empty))
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Naslovi grupa samo kad ima i emisija; bez njih je spisak kanala kao i do sada.
            if (programs.isNotEmpty() && results.itemCount > 0) {
                item(key = "h-channels") {
                    TvSearchGroupTitle(stringResource(R.string.search_group_channels), results.itemCount)
                }
            }
            items(count = results.itemCount, key = results.itemKey { it.id }) { index ->
                val channel = results[index]
                val focusKey = "c${channel?.id}"
                TvListRow(
                    onClick = { channel?.let { play(it.id) } },
                    onLongClick = { channel?.let { favoriteLists.favoriteAction(it.id) } },
                    onFocused = { if (channel != null) back.focused(focusKey) },
                    modifier = back.target(focusKey),
                ) { color ->
                    ChannelLogo(url = channel?.logo, name = channel?.name, size = 36.dp)
                    Text(
                        text = channel?.name.orEmpty(),
                        color = color,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                    )
                    if (channel?.favorite == true) {
                        Text(text = "★", color = color, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
            if (programs.isNotEmpty()) {
                item(key = "h-programs") {
                    TvSearchGroupTitle(stringResource(R.string.search_group_programs), programs.size)
                }
                items(count = programs.size, key = { "p${programs[it].channelId}-${programs[it].startMs}" }) { index ->
                    val hit = programs[index]
                    val focusKey = "p${hit.channelId}-${hit.startMs}"
                    TvProgramHitRow(
                        hit = hit,
                        now = now,
                        reminded = hit.key in reminders,
                        onPlay = { play(hit.channelId) },
                        onToggleReminder = { viewModel.toggleReminder(hit) },
                        onFocused = { back.focused(focusKey) },
                        modifier = back.target(focusKey),
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!back.restoreIfBack()) fieldFocus.tryRequestFocus()
    }
}
