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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.bane23.iptvplayer.ui.common.theme.glowBackground
import com.bane23.iptvplayer.data.repo.ContinueItem
import com.bane23.iptvplayer.data.repo.VodCategory
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.vod.FAVORITES_ID
import com.bane23.iptvplayer.ui.common.vod.Poster
import com.bane23.iptvplayer.ui.common.vod.SKELETON_POSTERS
import com.bane23.iptvplayer.ui.common.vod.SkeletonPoster
import com.bane23.iptvplayer.ui.common.skeleton
import com.bane23.iptvplayer.ui.common.vod.VodBrowseViewModel
import com.bane23.iptvplayer.ui.common.vod.label
import com.bane23.iptvplayer.data.repo.VodSort
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
    onCategories: () -> Unit = {},
    viewModel: VodBrowseViewModel = hiltViewModel(),
    // Filmovi i serije imaju odvojen opis (isti broj može biti i film i serija).
    previewViewModel: TvVodPreviewViewModel = hiltViewModel(key = "vod-preview-$series"),
) {
    LaunchedEffect(series) { viewModel.open(series) }
    val previewEnabled by previewViewModel.enabled.collectAsStateWithLifecycle()
    val preview by previewViewModel.preview.collectAsStateWithLifecycle()
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
    // „Nastavi gledanje” je prikazano dok korisnik ne izabere neku kategoriju, osim ako je prošli
    // put izašao sa kategorije; tada se otvara ona.
    var continueChosen by rememberSaveable(state.loadingCategories) { mutableStateOf(state.startOnContinue) }
    val showContinue = continueChosen && continueItems.isNotEmpty()
    val showFavorites = !showContinue && state.selected?.id == FAVORITES_ID
    var categoryFilter by rememberSaveable { mutableStateOf("") }
    val categoryListState = rememberLazyListState()
    // Meni kategorije (dugi OK): sakrij, pomeri na vrh, uredi.
    var menuCategory by remember { mutableStateOf<VodCategory?>(null) }
    var sortOpen by remember { mutableStateOf(false) }
    // Kategorija koja čeka sakrivanje i red na koji fokus prelazi pre nego što njen red nestane.
    var pendingHide by remember { mutableStateOf<VodCategory?>(null) }
    var focusTargetId by remember { mutableStateOf<String?>(null) }
    val moveFocus = remember { FocusRequester() }
    var movedToTop by remember { mutableIntStateOf(0) }
    var shownCategoryIds by remember { mutableStateOf(emptyList<String>()) }
    // Meni postera u „Nastavi gledanje” (dugi OK): skloni iz reda.
    var menuContinue by remember { mutableStateOf<ContinueItem?>(null) }
    // Stavka koja čeka sklanjanje i poster na koji fokus prelazi pre nego što njen poster nestane.
    var pendingDismiss by remember { mutableStateOf<ContinueItem?>(null) }
    var dismissFocusId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(pendingDismiss) {
        val item = pendingDismiss ?: return@LaunchedEffect
        var moved = false
        var attempts = 0
        while (!moved && attempts < FOCUS_RESTORE_ATTEMPTS) {
            delay(FOCUS_RESTORE_STEP_MS)
            moved = moveFocus.tryRequestFocus()
            attempts++
        }
        viewModel.dismissContinue(item)
        dismissFocusId = null
        focusTargetId = null
        pendingDismiss = null
    }
    menuContinue?.let { item ->
        TvActionDialog(
            title = item.name,
            options = listOf(stringResource(R.string.vod_continue_remove)),
            onSelect = {
                val index = continueItems.indexOfFirst { it.id == item.id }
                val neighbor = continueItems.getOrNull(index + 1) ?: continueItems.getOrNull(index - 1)
                // Poslednja stavka: red nestaje ceo, pa fokus ide na Favorite, koji uvek postoje.
                if (neighbor != null) dismissFocusId = neighbor.id else focusTargetId = FAVORITES_ID
                pendingDismiss = item
            },
            onClose = { menuContinue = null },
        )
    }

    LaunchedEffect(pendingHide) {
        val category = pendingHide ?: return@LaunchedEffect
        var moved = false
        var attempts = 0
        while (!moved && attempts < FOCUS_RESTORE_ATTEMPTS) {
            delay(FOCUS_RESTORE_STEP_MS)
            moved = moveFocus.tryRequestFocus()
            attempts++
        }
        viewModel.hideCategory(category)
        focusTargetId = null
        pendingHide = null
    }
    LaunchedEffect(movedToTop) {
        if (movedToTop > 0) {
            // Kratka pauza da se spisak složi iznova; kategorija (i fokus) je sada na vrhu.
            delay(MOVE_SETTLE_MS)
            runCatching { categoryListState.scrollToItem(0) }
        }
    }
    if (sortOpen) {
        TvActionDialog(
            title = stringResource(R.string.vod_sort),
            options = VodSort.entries.map { stringResource(it.label) },
            onSelect = { option ->
                lastItemId = null
                viewModel.setSort(VodSort.entries[option])
            },
            onClose = { sortOpen = false },
        )
    }
    menuCategory?.let { category ->
        TvActionDialog(
            title = category.name,
            options = listOf(
                stringResource(R.string.category_hide),
                stringResource(R.string.category_move_top),
                stringResource(R.string.categories_edit),
            ),
            onSelect = { option ->
                when (option) {
                    0 -> {
                        val index = shownCategoryIds.indexOf(category.id)
                        // Bez susedne kategorije fokus ide na Favorite, koji uvek postoje.
                        focusTargetId = shownCategoryIds.getOrNull(index + 1) ?: shownCategoryIds.getOrNull(index - 1) ?: FAVORITES_ID
                        pendingHide = category
                    }

                    1 -> {
                        viewModel.moveCategoryToTop(category)
                        movedToTop++
                    }

                    else -> onCategories()
                }
            },
            onClose = { menuCategory = null },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary)
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
                // Levo: pretraga, Favoriti i „Nastavi gledanje” stoje zakačeni na vrhu, a ispod njih
                // polje za filtriranje i kategorije koje se skroluju.
                Column(
                    modifier = Modifier
                        .weight(0.28f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TvListRow(onClick = onSearch) { color ->
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
                    TvListRow(onClick = { sortOpen = true }) { color ->
                        Icon(AppIcons.Sort, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                        Text(
                            text = stringResource(R.string.vod_sort_current, stringResource(state.sort.label)),
                            color = color,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    PinnedRow(
                        icon = AppIcons.Star,
                        title = favoritesLabel,
                        subtitle = stringResource(R.string.vod_total, favorites.size),
                        selected = showFavorites,
                        onClick = {
                            lastItemId = null
                            continueChosen = false
                            viewModel.selectCategory(VodCategory(FAVORITES_ID, favoritesLabel))
                        },
                        modifier = (if (showFavorites) Modifier.focusRequester(firstFocus) else Modifier)
                            .then(if (focusTargetId == FAVORITES_ID) Modifier.focusRequester(moveFocus) else Modifier),
                    )
                    if (continueItems.isNotEmpty()) {
                        PinnedRow(
                            icon = AppIcons.Play,
                            title = stringResource(R.string.vod_continue_watching),
                            subtitle = stringResource(R.string.vod_total, continueItems.size),
                            selected = showContinue,
                            onClick = {
                                lastItemId = null
                                continueChosen = true
                                viewModel.rememberContinue()
                            },
                            modifier = if (showContinue) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                    TvFilterField(
                        value = categoryFilter,
                        onChange = { categoryFilter = it },
                        placeholder = stringResource(R.string.search_categories),
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    val shownCategories = remember(state.categories, categoryFilter) {
                        val text = categoryFilter.trim()
                        if (text.isEmpty()) state.categories else state.categories.filter { it.name.contains(text, ignoreCase = true) }
                    }
                    LaunchedEffect(shownCategories) { shownCategoryIds = shownCategories.map { it.id } }
                    LazyColumn(
                        state = categoryListState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(shownCategories, key = { it.id }) { category ->
                            val selected = !showContinue && category == state.selected
                            TvTextRow(
                                text = category.name,
                                selected = selected,
                                onClick = {
                                    lastItemId = null
                                    continueChosen = false
                                    viewModel.selectCategory(category)
                                },
                                onLongClick = { menuCategory = category },
                                modifier = (if (selected) Modifier.focusRequester(firstFocus) else Modifier)
                                    .then(if (focusTargetId == category.id) Modifier.focusRequester(moveFocus) else Modifier),
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(0.72f)
                        .fillMaxHeight(),
                ) {
                    if (previewEnabled) TvVodPreviewHeader(preview = preview)
                    when {
                        showContinue -> PosterGrid {
                            items(continueItems, key = { it.id }) { item ->
                                PosterCard(
                                    name = item.name,
                                    poster = item.poster,
                                    progress = item.progress,
                                    onLongClick = { menuContinue = item },
                                    onClick = { onOpen(item.toVodItem()) },
                                    onFocused = {
                                        lastItemId = item.id
                                        previewViewModel.focus(item.toVodItem(), series)
                                    },
                                    modifier = when (item.id) {
                                        dismissFocusId -> Modifier.focusRequester(moveFocus)
                                        lastItemId -> Modifier.focusRequester(posterFocus)
                                        else -> Modifier
                                    },
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
                                    onFocused = {
                                        lastItemId = item.id
                                        previewViewModel.focus(item, series)
                                    },
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
                                    onFocused = {
                                        lastItemId = item.id
                                        previewViewModel.focus(item, series)
                                    },
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
                    // Izabrana kategorija može biti daleko niz spisak; njen red mora da postoji da bi primio fokus.
                    if (!showContinue && !showFavorites) {
                        val row = state.categories.indexOfFirst { it.id == state.selected?.id }
                        if (row > 3) runCatching { categoryListState.scrollToItem(row - 3) }
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

/** Zakačen red levo (Favoriti, „Nastavi gledanje”): ikonica, naziv i broj stavki ispod. */
@Composable
private fun PinnedRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvListRow(onClick = onClick, selected = selected, modifier = modifier) { color ->
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = title,
                color = color,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = color.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
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
private const val MOVE_SETTLE_MS = 150L

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
    onLongClick: (() -> Unit)? = onToggleFavorite,
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
            // Dug pritisak na OK dodaje u omiljene, odnosno uklanja; u „Nastavi gledanje” otvara meni.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
