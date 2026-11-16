package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
        Text(
            text = stringResource(if (series) R.string.vod_series else R.string.vod_movies),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        when {
            state.loadingCategories -> Note(stringResource(R.string.vod_loading))

            state.categoriesFailed -> {
                Note(stringResource(R.string.vod_load_failed))
                // Početni fokus mora biti na nekom elementu, inače daljinski ne radi ništa.
                Button(onClick = viewModel::loadCategories, modifier = Modifier.focusRequester(firstFocus)) {
                    Text(stringResource(R.string.player_retry))
                }
                LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
            }

            state.categories.isEmpty() -> Note(stringResource(R.string.vod_empty))

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

                        showFavorites && favorites.isEmpty() -> Note(stringResource(R.string.vod_favorites_empty))

                        showFavorites -> PosterGrid {
                            items(favorites, key = { it.id }) { item ->
                                PosterCard(
                                    name = item.name,
                                    poster = item.poster,
                                    onClick = { onOpen(item) },
                                    onFocused = { lastItemId = item.id },
                                    modifier = if (item.id == lastItemId) Modifier.focusRequester(posterFocus) else Modifier,
                                )
                            }
                        }

                        state.selected == null -> Note(stringResource(R.string.vod_pick_category))
                        state.loadingItems -> Note(stringResource(R.string.vod_loading))
                        state.itemsFailed -> Note(stringResource(R.string.vod_load_failed))
                        state.items.isEmpty() -> Note(stringResource(R.string.vod_empty))

                        else -> PosterGrid {
                            items(state.items, key = { it.id }) { item ->
                                PosterCard(
                                    name = item.name,
                                    poster = item.poster,
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
        contentPadding = PaddingValues(4.dp),
        content = content,
    )
}

private const val FOCUS_RESTORE_ATTEMPTS = 10
private const val FOCUS_RESTORE_STEP_MS = 60L

/** Poster sa nazivom ispod; fokus se vidi kao okvir u akcentnoj boji. */
@Composable
fun PosterCard(
    name: String,
    poster: String?,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .onFocusChanged {
                if (it.isFocused && !focused) onFocused()
                focused = it.isFocused
            }
            .border(3.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(5.dp),
    ) {
        Poster(url = poster, progress = progress, modifier = Modifier.fillMaxWidth())
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
