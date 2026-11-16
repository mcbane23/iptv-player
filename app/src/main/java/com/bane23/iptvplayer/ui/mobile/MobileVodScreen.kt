package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.vod.Poster
import com.bane23.iptvplayer.ui.common.vod.VodBrowseViewModel
import com.bane23.iptvplayer.ui.common.vod.toVodItem

/**
 * Filmovi ili serije na telefonu: red „Nastavi gledanje”, kategorije kao čipovi i mreža postera
 * izabrane kategorije. Lupa u zaglavlju traži kroz sve kategorije.
 */
@Composable
fun MobileVodScreen(
    series: Boolean,
    onOpen: (VodItem) -> Unit,
    onSearch: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: VodBrowseViewModel = hiltViewModel(),
) {
    LaunchedEffect(series) { viewModel.open(series) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val continueItems by viewModel.continueItems.collectAsStateWithLifecycle()
    val title = stringResource(if (series) R.string.vod_series else R.string.vod_movies)
    val searchLabel = stringResource(if (series) R.string.search_series else R.string.search_movies)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SectionTopBar(title = title, onBack = onBack) {
                ToolbarIcon(AppIcons.Search, searchLabel, onSearch)
            }

            when {
                state.loadingCategories -> Message(stringResource(R.string.vod_loading))
                state.categoriesFailed -> Failure(onRetry = viewModel::loadCategories)
                state.categories.isEmpty() -> Message(stringResource(R.string.vod_empty))

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (continueItems.isNotEmpty()) {
                        item(key = "continue", span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text(
                                    text = stringResource(R.string.vod_continue_watching),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(continueItems, key = { it.id }) { item ->
                                        PosterTile(
                                            name = item.name,
                                            poster = item.poster,
                                            progress = item.progress,
                                            onClick = { onOpen(item.toVodItem()) },
                                            modifier = Modifier.width(104.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "categories", span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.categories, key = { it.id }) { category ->
                                FilterChip(
                                    selected = category == state.selected,
                                    onClick = { viewModel.selectCategory(category) },
                                    label = {
                                        Text(
                                            text = category.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 180.dp),
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                )
                            }
                        }
                    }
                    when {
                        state.loadingItems -> item(span = { GridItemSpan(maxLineSpan) }) {
                            Message(stringResource(R.string.vod_loading))
                        }

                        state.itemsFailed -> item(span = { GridItemSpan(maxLineSpan) }) {
                            Failure(onRetry = viewModel::retryItems)
                        }

                        state.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                            Message(stringResource(R.string.vod_empty))
                        }

                        else -> items(state.items, key = { it.id }) { item ->
                            PosterTile(name = item.name, poster = item.poster, onClick = { onOpen(item) })
                        }
                    }
                }
            }
        }
    }
}

/** Poster sa nazivom ispod (najviše dva reda, pa „…”). */
@Composable
fun PosterTile(
    name: String,
    poster: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Poster(url = poster, progress = progress, modifier = Modifier.fillMaxWidth())
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

@Composable
internal fun Failure(onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.vod_load_failed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.player_retry)) }
    }
}
