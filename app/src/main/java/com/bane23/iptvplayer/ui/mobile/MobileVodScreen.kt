package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import com.bane23.iptvplayer.ui.common.vod.FAVORITES_ID
import com.bane23.iptvplayer.data.repo.VodCategory
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.vod.Poster
import com.bane23.iptvplayer.ui.common.vod.SKELETON_POSTERS
import com.bane23.iptvplayer.ui.common.vod.SkeletonPoster
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
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val favoriteIds = remember(favorites) { favorites.mapTo(HashSet()) { it.id } }
    val watched by viewModel.watched.collectAsStateWithLifecycle()
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val favoritesLabel = stringResource(R.string.category_favorites)
    val showingFavorites = state.selected?.id == FAVORITES_ID
    val title = stringResource(if (series) R.string.vod_series else R.string.vod_movies)
    val searchLabel = stringResource(if (series) R.string.search_series else R.string.search_movies)

    // Providno: pozadinu sa sjajem crta početni ekran.
    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SectionTopBar(title = title, onBack = onBack) {
                ToolbarIcon(AppIcons.Search, searchLabel, onSearch)
            }

            when {
                state.loadingCategories -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false,
                ) {
                    items(SKELETON_POSTERS) { SkeletonPoster() }
                }
                state.categoriesFailed -> Failure(onRetry = viewModel::loadCategories)
                state.categories.isEmpty() -> EmptyState(icon = if (series) AppIcons.Series else AppIcons.Movies, text = stringResource(R.string.vod_empty))

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
                        // Prečice kao u Uživo: Favoriti jednim dodirom, a čip kategorije nosi naziv
                        // izabrane kategorije i otvara spisak svih.
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GroupChip(
                                label = favoritesLabel,
                                selected = showingFavorites,
                                onClick = {
                                    if (!showingFavorites) viewModel.selectCategory(VodCategory(FAVORITES_ID, favoritesLabel))
                                },
                                leading = AppIcons.Star,
                            )
                            GroupChip(
                                label = state.selected?.takeIf { !showingFavorites }?.name
                                    ?: stringResource(R.string.category_chip_more),
                                selected = !showingFavorites && state.selected != null,
                                onClick = { pickerOpen = true },
                                trailing = AppIcons.ArrowDown,
                            )
                        }
                    }
                    when {
                        showingFavorites && favorites.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(icon = AppIcons.StarOutline, text = stringResource(R.string.vod_favorites_empty))
                        }

                        showingFavorites -> items(favorites, key = { it.id }) { item ->
                            PosterTile(
                                name = item.name,
                                poster = item.poster,
                                onClick = { onOpen(item) },
                                favorite = true,
                                watched = watched.of(item),
                                onToggleFavorite = { viewModel.toggleFavorite(item) },
                            )
                        }

                        state.loadingItems -> items(SKELETON_POSTERS) { SkeletonPoster() }

                        state.itemsFailed -> item(span = { GridItemSpan(maxLineSpan) }) {
                            Failure(onRetry = viewModel::retryItems)
                        }

                        state.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(icon = if (series) AppIcons.Series else AppIcons.Movies, text = stringResource(R.string.vod_empty))
                        }

                        else -> items(state.items, key = { it.id }) { item ->
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
    }

    if (pickerOpen) {
        CategoryPickerDialog(
            title = title,
            // Favoriti imaju svoj čip, pa su u spisku samo prave kategorije.
            groups = state.categories.map { PickerGroup(it.id, it.name) },
            selectedKey = state.selected?.id,
            onPick = { key ->
                pickerOpen = false
                val category = state.categories.firstOrNull { it.id == key } ?: VodCategory(FAVORITES_ID, favoritesLabel)
                viewModel.selectCategory(category)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

/**
 * Poster sa nazivom ispod (najviše dva reda, pa „…”). Kad je [favorite] zadat, u uglu postera je
 * zvezdica kojom se film ili serija brzo dodaje u omiljene.
 */
@Composable
fun PosterTile(
    name: String,
    poster: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    favorite: Boolean? = null,
    watched: Boolean = false,
    onToggleFavorite: () -> Unit = {},
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box {
            Poster(url = poster, name = name, progress = progress, watched = watched, modifier = Modifier.fillMaxWidth())
            if (favorite != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(40.dp)
                        .clickable(
                            onClick = onToggleFavorite,
                            onClickLabel = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (favorite) AppIcons.Star else AppIcons.StarOutline,
                        contentDescription = null,
                        tint = if (favorite) Color(0xFFFFD166) else Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .padding(4.dp),
                    )
                }
            }
        }
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
    EmptyState(
        icon = AppIcons.Warning,
        text = stringResource(R.string.vod_load_failed),
        isError = true,
        actionLabel = stringResource(R.string.player_retry),
        actionIcon = AppIcons.Refresh,
        onAction = onRetry,
    )
}
