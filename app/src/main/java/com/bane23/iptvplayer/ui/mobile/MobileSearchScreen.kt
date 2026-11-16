package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.layout.Column
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
import com.bane23.iptvplayer.ui.common.icons.AppIcons

/** Pretraga kanala po nazivu na telefonu. */
@Composable
fun MobileSearchScreen(
    onPlay: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val results = viewModel.results.collectAsLazyPagingItems()
    val fieldFocus = remember { FocusRequester() }

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
            if (state.query.isNotBlank() && results.itemCount == 0 && results.loadState.refresh is LoadState.NotLoading) {
                EmptyState(icon = AppIcons.Search, text = stringResource(R.string.search_empty))
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(count = results.itemCount, key = results.itemKey { it.id }) { index ->
                    MobileChannelRow(
                        channel = results[index],
                        onClick = onPlay,
                        onToggleFavorite = viewModel::toggleFavorite,
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
}
