package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.vod.VodSearchViewModel

/** Pretraga filmova, odnosno serija, kroz sve kategorije, na telefonu. */
@Composable
fun MobileVodSearchScreen(
    series: Boolean,
    onOpen: (VodItem) -> Unit,
    onBack: () -> Unit,
    viewModel: VodSearchViewModel = hiltViewModel(),
) {
    LaunchedEffect(series) { viewModel.open(series) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val watched by viewModel.watched.collectAsStateWithLifecycle()
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
                    placeholder = {
                        Text(stringResource(if (series) R.string.search_series else R.string.search_movies))
                    },
                    leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                        .focusRequester(fieldFocus),
                )
            }
            when {
                state.failed -> Failure(onRetry = viewModel::retry)
                state.loading -> Message(stringResource(R.string.vod_loading))
                state.query.isNotBlank() && state.results.isEmpty() -> Message(stringResource(R.string.search_empty_vod))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.results, key = { it.id }) { item ->
                        PosterTile(
                            name = item.name,
                            poster = item.poster,
                            onClick = { onOpen(item) },
                            favorite = item.id in favoriteIds,
                            watched = watched.of(item),
                            onToggleFavorite = { viewModel.toggleFavorite(item) },
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
}
