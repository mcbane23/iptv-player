package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.theme.glowBackground
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.ui.common.vod.VodSearchViewModel

/** Pretraga filmova, odnosno serija, kroz sve kategorije, na TV-u: polje gore, posteri ispod. */
@Composable
fun TvVodSearchScreen(
    series: Boolean,
    onOpen: (VodItem) -> Unit,
    viewModel: VodSearchViewModel = hiltViewModel(),
) {
    LaunchedEffect(series) { viewModel.open(series) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val watched by viewModel.watched.collectAsStateWithLifecycle()
    val fieldFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Text(
            text = stringResource(if (series) R.string.vod_series else R.string.vod_movies),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = {
                androidx.compose.material3.Text(
                    stringResource(if (series) R.string.search_series else R.string.search_movies),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .focusRequester(fieldFocus),
        )
        when {
            state.failed -> Column(modifier = Modifier.padding(top = 24.dp)) {
                Text(
                    text = stringResource(R.string.vod_load_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                TvButton(onClick = viewModel::retry, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.player_retry))
                }
            }

            state.loading -> SearchNote(stringResource(R.string.vod_loading))
            state.query.isNotBlank() && state.results.isEmpty() -> SearchNote(stringResource(R.string.search_empty_vod))

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
            ) {
                items(state.results, key = { it.id }) { item ->
                    PosterCard(
                        name = item.name,
                        poster = item.poster,
                        onClick = { onOpen(item) },
                        onFocused = {},
                        favorite = item.id in favoriteIds,
                        watched = watched.of(item),
                        onToggleFavorite = { viewModel.toggleFavorite(item) },
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
}

@Composable
private fun SearchNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 24.dp),
    )
}
