package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.VodCategory
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.vod.FAVORITES_ID
import com.bane23.iptvplayer.ui.common.vod.Poster
import com.bane23.iptvplayer.ui.common.vod.SKELETON_POSTERS
import com.bane23.iptvplayer.ui.common.vod.SkeletonPoster
import com.bane23.iptvplayer.ui.common.skeleton
import com.bane23.iptvplayer.ui.common.vod.VodBrowseViewModel
import com.bane23.iptvplayer.ui.common.vod.toVodItem
import kotlinx.coroutines.delay

/**
 * Filmovi ili serije na TV-u: levo pretraga, „Nastavi gledanje” i kategorije (bira se sa OK),
 * desno posteri izabrane grupe.
 */
@Composable
fun TvVodScreen(
    series: Boolean,
    onOpen: (VodItem) -> Unit,
    onSearch: () -> Unit,
    viewModel: VodBrowseViewModel = hiltViewModel(),
) {
    LaunchedEffect(series) { viewModel.open(series) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val continueItems by viewModel.continueItems.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val favoriteIds = remember(favorites) { favorites.mapTo(HashSet()) { it.id } }
    val watched by viewModel.watched.collectAsStateWithLifecycle()
    val favoritesLabel = stringResource(R.string.category_favorites)
    val firstFocus = remember { FocusRequester() }
    val posterFocus = remember { FocusRequester() }
    // Poster na kome je fokus bio poslednji put, da se posle povratka iz detalja nastavi odatle.
    var lastItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    // „Nastavi gledanje” je prikazano dok korisnik ne izabere neku kategoriju.
    var continueChosen by rememberSaveable { mutableStateOf(true) }
    val showContinue = continueChosen && continueItems.isNotEmpty()
    val showFavorites = !showContinue && state.selected?.id == FAVORITES_ID

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        TvHeader(
            title = stringResource(if (series) R.string.vod_series else R.string.vod_movies),
            modifier = Modifier.padding(bottom = 16.dp),
        )
        when {
            state.loadingCategories -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(
                    modifier = Modifier
                        .weight(0.28f)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    repeat(SKELETON_CATEGORIES) {
                        Box(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .fillMaxWidth(0.7f)
                                .height(16.dp)
                                .skeleton(4.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(0.72f)) { SkeletonPosterGrid() }
            }

            state.categoriesFailed -> {
                TvEmptyState(icon = AppIcons.Warning, text = stringResource(R.string.vod_load_failed), isError = true)
                // Početni fokus mora biti na nekom elementu, inače daljinski ne radi ništa.
                Button(onClick = viewModel::loadCategories, modifier = Modifier.focusRequester(firstFocus)) {
                    Text(stringResource(R.string.player_retry))
                }
                LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
            }

            state.categories.isEmpty() -> TvEmptyState(icon = if (series) AppIcons.Series else AppIcons.Movies, text = stringResource(R.string.vod_empty))

            else -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LazyColumn(
                    modifier = Modifier
                        .weight(0.28f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(key = "search") {
                        TvListRow(onClick = onSearch, modifier = Modifier.padding(bottom = 8.dp)) { color ->
                            Icon(AppIcons.Search, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                            Text(
                                text = stringResource(if (series) R.string.search_series else R.string.search_movies),
                                color = color,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                    if (continueItems.isNotEmpty()) {
                        item(key = "continue") {
                            TvTextRow(
                                text = stringResource(R.string.vod_continue_watching),
                                selected = showContinue,
                                onClick = {
                                    lastItemId = null
                                    continueChosen = true
                                },
                                modifier = if (showContinue) Modifier.focusRequester(firstFocus) else Modifier,
                            )
                        }
                    }
                    item(key = "favorites") {
                        TvListRow(
                            selected = showFavorites,
                            onClick = {
                                lastItemId = null
                                continueChosen = false
                                viewModel.selectCategory(VodCategory(FAVORITES_ID, favoritesLabel))
                            },
                            modifier = if (showFavorites) Modifier.focusRequester(firstFocus) else Modifier,
                        ) { color ->
                            Icon(AppIcons.Star, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                            Text(
                                text = favoritesLabel,
                                color = color,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                    items(state.categories, key = { it.id }) { category ->
                        val selected = !showContinue && category == state.selected
                        TvTextRow(
                            text = category.name,
                            selected = selected,
                            onClick = {
                                lastItemId = null
                                continueChosen = false
                                viewModel.selectCategory(category)
                            },
                            modifier = if (selected) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(0.72f)
                        .fillMaxHeight(),
                ) {
                    when {
                        showContinue -> PosterGrid {
                            items(continueItems, key = { it.id }) { item ->
                                PosterCard(
                                    name = item.name,
                                    poster = item.poster,
                                    progress = item.progress,
                                    onClick = { onOpen(item.toVodItem()) },
                                    onFocused = { lastItemId = item.id },
                                    modifier = if (item.id == lastItemId) Modifier.focusRequester(posterFocus) else Modifier,
                                )
                            }
                        }

                        showFavorites && favorites.isEmpty() -> TvEmptyState(icon = AppIcons.StarOutline, text = stringResource(R.string.vod_favorites_empty))

                        showFavorites -> PosterGrid {
                            items(favorites, key = { it.id }) { item ->
                                PosterCard(
                                    name = item.name,
                                    poster = item.poster,
                                    // Odavde se ne uklanja dugim pritiskom: poster bi nestao zajedno sa fokusom.
                                    favorite = true,
                                    watched = watched.of(item),
                                    onClick = { onOpen(item) },
                                    onFocused = { lastItemId = item.id },
                                    modifier = if (item.id == lastItemId) Modifier.focusRequester(posterFocus) else Modifier,
                                )
                            }
                        }

                        state.selected == null -> Note(stringResource(R.string.vod_pick_category))
                        state.loadingItems -> SkeletonPosterGrid()
                        state.itemsFailed -> TvEmptyState(icon = AppIcons.Warning, text = stringResource(R.string.vod_load_failed), isError = true)
                        state.items.isEmpty() -> TvEmptyState(icon = if (series) AppIcons.Series else AppIcons.Movies, text = stringResource(R.string.vod_empty))

                        else -> PosterGrid {
                            items(state.items, key = { it.id }) { item ->
                                PosterCard(
                                    name = item.name,
                                    poster = item.poster,
                                    favorite = item.id in favoriteIds,
                                    watched = watched.of(item),
                                    onToggleFavorite = { viewModel.toggleFavorite(item) },
                                    onClick = { onOpen(item) },
                                    onFocused = { lastItemId = item.id },
                                    modifier = if (item.id == lastItemId) Modifier.focusRequester(posterFocus) else Modifier,
                                )
                            }
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    if (lastItemId != null) {
                        repeat(FOCUS_RESTORE_ATTEMPTS) {
                            if (posterFocus.tryRequestFocus()) return@LaunchedEffect
                            delay(FOCUS_RESTORE_STEP_MS)
                        }
                    }
                    repeat(FOCUS_RESTORE_ATTEMPTS) {
                        if (firstFocus.tryRequestFocus()) return@LaunchedEffect
                        delay(FOCUS_RESTORE_STEP_MS)
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterGrid(content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        // Mesto za uvećan poster u fokusu, da ga ivica mreže ne odseče.
        contentPadding = PaddingValues(12.dp),
        content = content,
    )
}

/** Obrisi postera dok se kategorija učitava; nisu fokusirani, pa fokus ostaje u kategorijama. */
@Composable
private fun SkeletonPosterGrid() {
    PosterGrid {
        items(SKELETON_POSTERS) { SkeletonPoster(modifier = Modifier.padding(5.dp)) }
    }
}

private const val FOCUSED_POSTER_SCALE = 1.08f
private const val SKELETON_CATEGORIES = 8
private const val FOCUS_RESTORE_ATTEMPTS = 10
private const val FOCUS_RESTORE_STEP_MS = 60L

/**
 * Poster sa nazivom ispod; fokus se vidi kao okvir u akcentnoj boji, omiljeni imaju zvezdicu
 * u desnom, a odgledani kvačicu u levom gornjem uglu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    name: String,
    poster: String?,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    favorite: Boolean = false,
    watched: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            // Fokusiran poster je malo veći i crta se preko suseda, da se sa kauča jasno vidi gde je
            // fokus. Bez animacije, jer bi na slabijim uređajima usporila kretanje kroz mrežu.
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                val scale = if (focused) FOCUSED_POSTER_SCALE else 1f
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                if (it.isFocused && !focused) onFocused()
                focused = it.isFocused
            }
            .background(if (focused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, shape)
            .border(3.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            // Dug pritisak na OK dodaje u omiljene, odnosno uklanja.
            .combinedClickable(onClick = onClick, onLongClick = onToggleFavorite)
            .padding(5.dp),
    ) {
        Box {
            Poster(url = poster, name = name, progress = progress, watched = watched, modifier = Modifier.fillMaxWidth())
            if (favorite) {
                Icon(
                    imageVector = AppIcons.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD166),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(26.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(4.dp),
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}
