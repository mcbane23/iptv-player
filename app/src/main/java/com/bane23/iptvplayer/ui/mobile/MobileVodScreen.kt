package com.bane23.iptvplayer.ui.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.ui.common.vod.Poster
import com.bane23.iptvplayer.ui.common.vod.VodBrowseViewModel

/**
 * Filmovi ili serije na telefonu: prvo kategorije, pa posteri izabrane kategorije.
 * Bez [onBack] (kartica donje trake) strelica nazad se vidi samo unutar kategorije.
 */
@Composable
fun MobileVodScreen(
    series: Boolean,
    onOpen: (VodItem) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: VodBrowseViewModel = hiltViewModel(),
) {
    LaunchedEffect(series) { viewModel.open(series) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected = state.selected
    BackHandler(enabled = selected != null) { viewModel.clearCategory() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val back: (() -> Unit)? = if (selected != null) viewModel::clearCategory else onBack
            SectionTopBar(
                title = selected?.name ?: stringResource(if (series) R.string.vod_series else R.string.vod_movies),
                onBack = back,
            )

            when {
                selected == null && state.loadingCategories -> Message(stringResource(R.string.vod_loading))
                selected == null && state.categoriesFailed -> Failure(onRetry = viewModel::loadCategories)
                selected == null && state.categories.isEmpty() -> Message(stringResource(R.string.vod_empty))

                selected == null -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(state.categories, key = { it.id }) { category ->
                        Column(modifier = Modifier.clickable { viewModel.selectCategory(category) }) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }

                state.loadingItems -> Message(stringResource(R.string.vod_loading))
                state.itemsFailed -> Failure(onRetry = viewModel::retryItems)
                state.items.isEmpty() -> Message(stringResource(R.string.vod_empty))

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        Column(modifier = Modifier.clickable { onOpen(item) }) {
                            Poster(url = item.poster, modifier = Modifier.fillMaxWidth())
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

@Composable
private fun Failure(onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.vod_load_failed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.player_retry)) }
    }
}
