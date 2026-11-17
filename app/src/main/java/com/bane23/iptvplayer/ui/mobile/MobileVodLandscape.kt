package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.ContinueItem
import com.bane23.iptvplayer.data.repo.VodCategory
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.data.repo.VodSort
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.vod.FAVORITES_ID
import com.bane23.iptvplayer.ui.common.vod.SKELETON_POSTERS
import com.bane23.iptvplayer.ui.common.vod.SkeletonPoster
import com.bane23.iptvplayer.ui.common.vod.VodBrowseUiState
import com.bane23.iptvplayer.ui.common.vod.WatchedMarks
import com.bane23.iptvplayer.ui.common.vod.toVodItem

/**
 * Filmovi ili serije na telefonu u landscape-u, u dve kolone: levo Favoriti i „Nastavi gledanje”
 * zakačeni na vrhu, ispod njih polje za filtriranje i kategorije koje se skroluju; desno posteri
 * izabrane grupe. Sadržaj ide do ivica ekrana.
 */
@Composable
fun MobileVodLandscape(
    series: Boolean,
    state: VodBrowseUiState,
    continueItems: List<ContinueItem>,
    favorites: List<VodItem>,
    watched: WatchedMarks,
    onSelectCategory: (VodCategory) -> Unit,
    onHideCategory: (VodCategory) -> Unit,
    onMoveCategoryToTop: (VodCategory) -> Unit,
    onCategories: () -> Unit,
    onToggleFavorite: (VodItem) -> Unit,
    onDismissContinue: (ContinueItem) -> Unit,
    onOpen: (VodItem) -> Unit,
    onSearch: () -> Unit,
    onSort: (VodSort) -> Unit,
    onRetry: () -> Unit,
) {
    val favoritesLabel = stringResource(R.string.category_favorites)
    // „Nastavi gledanje” je prikazano dok korisnik ne izabere nešto drugo, kao na TV-u.
    var continueChosen by rememberSaveable { mutableStateOf(false) }
    val showContinue = continueChosen && continueItems.isNotEmpty()
    val showFavorites = !showContinue && state.selected?.id == FAVORITES_ID
    var filter by rememberSaveable { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val categoryActions = CategoryActions(
        onHide = { key -> state.categories.firstOrNull { it.id == key }?.let(onHideCategory) },
        onMoveToTop = { key -> state.categories.firstOrNull { it.id == key }?.let(onMoveCategoryToTop) },
        onManage = onCategories,
    )
    val favoriteIds = remember(favorites) { favorites.mapTo(HashSet()) { it.id } }
    val title = stringResource(if (series) R.string.vod_series else R.string.vod_movies)
    val selectedName = when {
        showContinue -> stringResource(R.string.vod_continue_watching)
        showFavorites -> favoritesLabel
        else -> state.selected?.name.orEmpty()
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight()
                .padding(start = 8.dp, top = 8.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PaneRow(
                title = favoritesLabel,
                subtitle = stringResource(R.string.vod_total, favorites.size),
                icon = AppIcons.Star,
                selected = showFavorites,
                onClick = {
                    continueChosen = false
                    onSelectCategory(VodCategory(FAVORITES_ID, favoritesLabel))
                },
            )
            if (continueItems.isNotEmpty()) {
                PaneRow(
                    title = stringResource(R.string.vod_continue_watching),
                    subtitle = stringResource(R.string.vod_total, continueItems.size),
                    icon = AppIcons.Play,
                    selected = showContinue,
                    onClick = { continueChosen = true },
                )
            }
            FilterField(value = filter, onChange = { filter = it })
            val shown = remember(state.categories, filter) {
                val text = filter.trim()
                if (text.isEmpty()) state.categories else state.categories.filter { it.name.contains(text, ignoreCase = true) }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(shown, key = { it.id }) { category ->
                    Box {
                        PaneRow(
                            title = category.name,
                            selected = !showContinue && category == state.selected,
                            onClick = {
                                continueChosen = false
                                onSelectCategory(category)
                            },
                            onLongClick = { menuFor = category.id },
                        )
                        CategoryActionMenu(
                            expanded = menuFor == category.id,
                            actions = categoryActions,
                            key = category.id,
                            onDismiss = { menuFor = null },
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(0.7f)
                .fillMaxHeight(),
        ) {
            // Zaglavlje: sekcija, izabrana grupa i pretraga kroz sve kategorije.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                )
                Text(
                    text = selectedName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                VodSortButton(sort = state.sort, onSort = onSort)
                ToolbarIcon(
                    AppIcons.Search,
                    stringResource(if (series) R.string.search_series else R.string.search_movies),
                    onSearch,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                when {
                    showContinue -> PosterGrid {
                        items(continueItems, key = { it.id }) { item ->
                            ContinueTile(item = item, onOpen = onOpen, onRemove = onDismissContinue)
                        }
                    }

                    showFavorites && favorites.isEmpty() ->
                        EmptyState(icon = AppIcons.StarOutline, text = stringResource(R.string.vod_favorites_empty))

                    showFavorites -> PosterGrid {
                        items(favorites, key = { it.id }) { item ->
                            PosterTile(
                                name = item.name,
                                poster = item.poster,
                                onClick = { onOpen(item) },
                                favorite = true,
                                watched = watched.of(item),
                                onToggleFavorite = { onToggleFavorite(item) },
                            )
                        }
                    }

                    state.loadingCategories || state.loadingItems -> PosterGrid {
                        items(SKELETON_POSTERS) { SkeletonPoster() }
                    }

                    state.categoriesFailed || state.itemsFailed -> Failure(onRetry = onRetry)

                    state.items.isEmpty() -> EmptyState(
                        icon = if (series) AppIcons.Series else AppIcons.Movies,
                        text = stringResource(R.string.vod_empty),
                    )

                    else -> PosterGrid {
                        items(state.items, key = { it.id }) { item ->
                            PosterTile(
                                name = item.name,
                                poster = item.poster,
                                onClick = { onOpen(item) },
                                favorite = item.id in favoriteIds,
                                watched = watched.of(item),
                                onToggleFavorite = { onToggleFavorite(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterGrid(content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        contentPadding = PaddingValues(start = 8.dp, end = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Red u levoj koloni: ikonica, naziv i, po želji, broj stavki ispod. Izabrani red je u akcentnoj boji. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaneRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimary else colors.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primary else colors.surfaceVariant.copy(alpha = 0.6f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) content else colors.primary,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Polje za filtriranje kategorija po nazivu, nisko da stane iznad spiska. */
@Composable
private fun FilterField(value: String, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_categories),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
