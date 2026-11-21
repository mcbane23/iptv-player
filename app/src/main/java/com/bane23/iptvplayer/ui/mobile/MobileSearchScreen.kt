package com.bane23.iptvplayer.ui.mobile

import com.bane23.iptvplayer.ui.common.KeepNowPlayingFresh
import androidx.compose.foundation.layout.Column
import com.bane23.iptvplayer.ui.common.FavoriteListsViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.SearchViewModel
import com.bane23.iptvplayer.ui.common.recording.RecordingNotices
import com.bane23.iptvplayer.ui.common.icons.AppIcons

/** Pretraga u Uživo na telefonu: po nazivu kanala i po nazivu emisije u vodiču. */
@Composable
fun MobileSearchScreen(
    onPlay: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val results = viewModel.results.collectAsLazyPagingItems()
    KeepNowPlayingFresh(results)
    val programs by viewModel.programs.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    RecordingNotices(viewModel.notice)
    val now = System.currentTimeMillis()
    val fieldFocus = remember { FocusRequester() }
    val favoriteLists: FavoriteListsViewModel = hiltViewModel()
    MobileFavoritePickDialog(favoriteLists)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarIcon(AppIcons.Back, stringResource(R.string.action_back), onBack)
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.search_channels)) },
                    leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                        .focusRequester(fieldFocus),
                )
            }
            if (state.query.isNotBlank() && results.itemCount == 0 && programs.isEmpty() &&
                results.loadState.refresh is LoadState.NotLoading
            ) {
                EmptyState(icon = AppIcons.Search, text = stringResource(R.string.search_empty))
            }
            // Isti red kanala kao u Uživo (pločica), da pretraga ne izgleda kao drugi deo aplikacije.
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // Naslovi grupa samo kad ima i emisija; bez njih je spisak kanala kao i do sada.
                if (programs.isNotEmpty() && results.itemCount > 0) {
                    item(key = "h-channels") {
                        MobileSearchGroupTitle(stringResource(R.string.search_group_channels), results.itemCount)
                    }
                }
                items(count = results.itemCount, key = results.itemKey { it.id }) { index ->
                    MobileChannelCard(
                        channel = results[index],
                        onClick = { onPlay(it.id) },
                        onToggleFavorite = favoriteLists::favoriteAction,
                    )
                }
                if (programs.isNotEmpty()) {
                    item(key = "h-programs") {
                        MobileSearchGroupTitle(stringResource(R.string.search_group_programs), programs.size)
                    }
                    items(count = programs.size, key = { "p${programs[it].channelId}-${programs[it].startMs}" }) { index ->
                        val hit = programs[index]
                        MobileProgramHitRow(
                            hit = hit,
                            now = now,
                            reminded = hit.key in reminders,
                            onPlay = { onPlay(hit.channelId) },
                            onToggleReminder = { viewModel.toggleReminder(hit) },
                            sidePadding = 0.dp,
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
}
