package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.bane23.iptvplayer.ui.common.lock.vodItem
import com.bane23.iptvplayer.ui.common.lock.ItemLock
import com.bane23.iptvplayer.ui.common.lock.rememberLockMenu
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.bane23.iptvplayer.data.repo.ContinueItem
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import com.bane23.iptvplayer.ui.common.vod.FAVORITES_ID
import com.bane23.iptvplayer.ui.common.vod.OpenedFromMenu
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
import com.bane23.iptvplayer.data.repo.VodFilter
import com.bane23.iptvplayer.data.repo.VodSort
import com.bane23.iptvplayer.ui.common.vodFilterChoices
import com.bane23.iptvplayer.ui.common.vod.label
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.vod.Poster
import com.bane23.iptvplayer.ui.common.vod.VodLoading
import com.bane23.iptvplayer.ui.common.vod.vodLoadingHint
import com.bane23.iptvplayer.ui.common.vod.VodBrowseViewModel
import com.bane23.iptvplayer.ui.common.vod.toVodItem
import com.bane23.iptvplayer.ui.common.theme.Glass
import com.bane23.iptvplayer.ui.common.theme.glass
import com.bane23.iptvplayer.ui.common.theme.glassSelected

/**
 * Filmovi ili serije na telefonu: čipovi (Favoriti, „Nastavi gledanje”, kategorija) kao u Uživo,
 * naslov izabrane grupe i mreža postera. Lupa u zaglavlju traži kroz sve kategorije.
 */
@Composable
fun MobileVodScreen(
    series: Boolean,
    onOpen: (VodItem) -> Unit,
    onSearch: () -> Unit,
    onBack: (() -> Unit)? = null,
    onCategories: () -> Unit = {},
    onUnlock: () -> Unit = {},
    viewModel: VodBrowseViewModel = hiltViewModel(),
) {
    LaunchedEffect(series) { viewModel.open(series) }
    OpenedFromMenu(viewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val continueItems by viewModel.continueItems.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val favoriteIds = remember(favorites) { favorites.mapTo(HashSet()) { it.id } }
    val watched by viewModel.watched.collectAsStateWithLifecycle()
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val lockMenu = rememberLockMenu(onUnlock = {
        pickerOpen = false
        onUnlock()
    })
    // Uspravno: „Nastavi gledanje” je čip, kao zakačen red na TV-u; dok je izabran, mreža prikazuje te naslove.
    var showingContinue by rememberSaveable { mutableStateOf(false) }
    // Kategorija koja je bila izabrana pre favorita, da drugi dodir na zvezdicu vrati na nju.
    var beforeFavorites by rememberSaveable { mutableStateOf<String?>(null) }
    val favoritesLabel = stringResource(R.string.category_favorites)
    val showingFavorites = state.selected?.id == FAVORITES_ID
    val title = stringResource(if (series) R.string.vod_series else R.string.vod_movies)
    val searchLabel = stringResource(if (series) R.string.search_series else R.string.search_movies)

    // Mreža postera deli isto stanje u oba položaja telefona, pa okretanje ne pomera spisak.
    // Druga grupa (kategorija, favoriti, „Nastavi gledanje”) kreće od vrha, kao kanali u Uživo.
    val gridState = rememberLazyGridState()
    val shownGroup = if (showingContinue && continueItems.isNotEmpty()) CONTINUE_GROUP else state.selected?.id
    var gridShows by rememberSaveable { mutableStateOf(shownGroup) }
    LaunchedEffect(shownGroup) {
        if (gridShows != shownGroup) {
            gridShows = shownGroup
            gridState.scrollToItem(0)
        }
    }
    val landscape = onBack == null && LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Položen telefon ima kategorije stalno levo; otvoren spisak se zatvara da ne iskoči posle povratka.
    LaunchedEffect(landscape) { if (landscape) pickerOpen = false }
    if (landscape) {
        // Položen telefon: kategorije levo, posteri desno.
        MobileVodLandscape(
            series = series,
            state = state,
            continueItems = continueItems,
            favorites = favorites,
            watched = watched,
            continueChosen = showingContinue,
            onContinueChosen = { showingContinue = it },
            gridState = gridState,
            onSelectCategory = viewModel::selectCategory,
            onHideCategory = viewModel::hideCategory,
            onMoveCategoryToTop = viewModel::moveCategoryToTop,
            onCategories = onCategories,
            lockMenu = lockMenu,
            onToggleFavorite = viewModel::toggleFavorite,
            onToggleWatched = viewModel::toggleWatched,
            onDismissContinue = viewModel::dismissContinue,
            onOpen = onOpen,
            onSearch = onSearch,
            onSort = viewModel::setSort,
            onFilter = viewModel::setFilter,
            onRetry = { if (state.categoriesFailed) viewModel.loadCategories() else viewModel.retryItems() },
        )
        return
    }

    // Providno: pozadinu sa sjajem crta početni ekran.
    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val topBar: @Composable () -> Unit = {
                SectionTopBar(title = title, onBack = onBack) {
                    VodSortButton(sort = state.sort, onSort = viewModel::setSort)
                    FilterButton(
                        title = stringResource(if (series) R.string.filter_title_series else R.string.filter_title_movies),
                        count = state.filter.count,
                        choices = vodFilterChoices(state.filter, viewModel::setFilter),
                        onReset = { viewModel.setFilter(VodFilter()) },
                    )
                    ToolbarIcon(AppIcons.Search, searchLabel, onSearch)
                }
            }
            // Dok se posteri listaju, naslov i čipovi se sklanjaju; tada su deo vrha koji se skuplja.
            val browsing = !state.loadingCategories && !state.categoriesFailed && state.categories.isNotEmpty()
            if (!browsing) topBar()
            val header = rememberCollapsingHeaderState { gridState.canScrollForward }
            LaunchedEffect(shownGroup) { header.expand() }

            when {
                state.loadingCategories -> VodLoading(
                    series = series,
                    accent = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    hint = vodLoadingHint(state.storedOnDevice),
                    modifier = Modifier.fillMaxSize(),
                )
                // Prazan ekran se pomera, da bi povlačenje nadole (osvežavanje liste) radilo i na njemu.
                state.categoriesFailed -> Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Failure(onRetry = viewModel::loadCategories)
                }
                state.categories.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    EmptyState(icon = if (series) AppIcons.Series else AppIcons.Movies, text = stringResource(R.string.vod_empty))
                }

                else -> Column(modifier = Modifier.weight(1f).nestedScroll(header.connection)) {
                    // Prečice kao u Uživo, odmah ispod naslova i uvek na mestu: Favoriti (zvezdica),
                    // „Nastavi gledanje” i čip kategorije, koji nosi naziv izabrane i otvara spisak svih.
                    val inContinue = showingContinue && continueItems.isNotEmpty()
                    // Broj naslova stoji na dugmetu kategorije, pa poseban naslov nad posterima ne treba.
                    val shownCount = when {
                        inContinue -> continueItems.size
                        showingFavorites -> favorites.size
                        state.loadingItems || state.itemsFailed -> null
                        else -> state.items.size
                    }
                    val shownLabel = when {
                        inContinue -> stringResource(R.string.vod_continue_watching)
                        showingFavorites -> favoritesLabel
                        else -> state.selected?.name
                    }
                    CollapsingHeader(
                        state = header,
                        title = title,
                        detail = listOfNotNull(shownLabel, shownCount?.toString()).joinToString(" · ").ifEmpty { null },
                    ) {
                    Column {
                    topBar()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GroupChip(
                            label = favoritesLabel,
                            selected = showingFavorites && !inContinue,
                            onClick = {
                                showingContinue = false
                                // Zvezdica je prekidač: prvi dodir prikazuje favorite, drugi vraća prethodnu kategoriju.
                                if (!showingFavorites) {
                                    beforeFavorites = state.selected?.id
                                    viewModel.selectCategory(VodCategory(FAVORITES_ID, favoritesLabel))
                                } else {
                                    val back = state.categories.firstOrNull { it.id == beforeFavorites }
                                        ?: state.categories.firstOrNull()
                                    back?.let(viewModel::selectCategory)
                                }
                            },
                            iconOnly = AppIcons.Star,
                        )
                        if (continueItems.isNotEmpty()) {
                            GroupChip(
                                label = stringResource(R.string.vod_continue_watching),
                                selected = inContinue,
                                onClick = { showingContinue = !showingContinue },
                                iconOnly = AppIcons.Play,
                            )
                        }
                        // Jedno široko dugme: naziv onoga što mreža prikazuje, broj naslova i strelica za spisak kategorija.
                        CategoryButton(
                            label = when {
                                inContinue -> stringResource(R.string.vod_continue_watching)
                                showingFavorites -> favoritesLabel
                                else -> state.selected?.name ?: stringResource(R.string.category_chip_more)
                            },
                            count = shownCount,
                            selected = !inContinue && !showingFavorites && state.selected != null,
                            onClick = { pickerOpen = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        state = gridState,
                        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when {
                            inContinue -> items(continueItems, key = { "c" + it.id }) { item ->
                                ContinueTile(item = item, onOpen = onOpen, onRemove = viewModel::dismissContinue)
                            }

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
                                    onToggleWatched = if (series) null else ({ viewModel.toggleWatched(item) }),
                                    lock = lockMenu.vodItem(series, item.id, item.name),
                                    compact = true,
                                )
                            }

                            state.loadingItems -> item(span = { GridItemSpan(maxLineSpan) }) {
                                VodLoading(
                                    series = series,
                                    accent = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
                                )
                            }

                            state.itemsFailed -> item(span = { GridItemSpan(maxLineSpan) }) {
                                Failure(onRetry = viewModel::retryItems)
                            }

                            state.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                                EmptyState(icon = if (series) AppIcons.Series else AppIcons.Movies, text = stringResource(if (state.filter.active) R.string.filter_empty else R.string.vod_empty))
                            }

                            else -> items(state.items, key = { it.id }) { item ->
                                PosterTile(
                                    name = item.name,
                                    poster = item.poster,
                                    onClick = { onOpen(item) },
                                    favorite = item.id in favoriteIds,
                                    watched = watched.of(item),
                                    onToggleFavorite = { viewModel.toggleFavorite(item) },
                                    onToggleWatched = if (series) null else ({ viewModel.toggleWatched(item) }),
                                    lock = lockMenu.vodItem(series, item.id, item.name),
                                    compact = true,
                                )
                            }
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
                showingContinue = false
                val category = state.categories.firstOrNull { it.id == key } ?: VodCategory(FAVORITES_ID, favoritesLabel)
                viewModel.selectCategory(category)
            },
            onDismiss = { pickerOpen = false },
            actions = CategoryActions(
                onHide = { key -> state.categories.firstOrNull { it.id == key }?.let(viewModel::hideCategory) },
                onMoveToTop = { key -> state.categories.firstOrNull { it.id == key }?.let(viewModel::moveCategoryToTop) },
                onManage = {
                    pickerOpen = false
                    onCategories()
                },
                lockMenu = lockMenu,
                lockKey = { key -> lockMenu.state.vodKey(series, key) },
            ),
        )
    }
}

/** Oznaka grupe „Nastavi gledanje” za praćenje šta mreža postera prikazuje; nije id kategorije. */
private const val CONTINUE_GROUP = "continue-watching"

/** Dugme za redosled postera: otvara meni sa načinima slaganja, izabrani je obeležen. */
@Composable
internal fun VodSortButton(sort: VodSort, onSort: (VodSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolbarIcon(AppIcons.Sort, stringResource(R.string.vod_sort), { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (option in VodSort.entries) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(option.label),
                            color = if (option == sort) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        )
                    },
                    onClick = {
                        open = false
                        onSort(option)
                    },
                )
            }
        }
    }
}

/**
 * Poster u „Nastavi gledanje”. Dugi pritisak otvara meni kojim se film, odnosno serija, sklanja
 * iz reda; vraća se kad se ponovo gleda.
 */
@Composable
fun ContinueTile(
    item: ContinueItem,
    onOpen: (VodItem) -> Unit,
    onRemove: (ContinueItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        PosterTile(
            name = item.name,
            poster = item.poster,
            progress = item.progress,
            onClick = { onOpen(item.toVodItem()) },
            onLongClick = { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.vod_continue_remove)) },
                onClick = {
                    menuOpen = false
                    onRemove(item)
                },
            )
        }
    }
}

/**
 * Poster sa nazivom ispod (najviše dva reda, pa „…”). Kad je [favorite] zadat, u uglu postera je
 * zvezdica kojom se film ili serija brzo dodaje u omiljene.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    onLongClick: (() -> Unit)? = null,
    // Označavanje odgledanog (samo filmovi), stavka menija na dugi pritisak.
    onToggleWatched: (() -> Unit)? = null,
    // Dugi pritisak tada otvara meni roditeljske zaštite za ovaj film, odnosno seriju.
    lock: ItemLock? = null,
    /**
     * Zbijen prikaz: naziv u jednom redu, a zvezdica samo na posterima koji su već u omiljenima.
     * Dodavanje u omiljene je tada prva stavka menija na dugi pritisak.
     */
    compact: Boolean = false,
) {
    var lockMenuOpen by remember { mutableStateOf(false) }
    val favoriteInMenu = compact && favorite != null
    val hasMenu = lock != null || favoriteInMenu || onToggleWatched != null
    val longClick = onLongClick ?: if (hasMenu) ({ lockMenuOpen = true }) else null
    Column(modifier = modifier.combinedClickable(onClick = onClick, onLongClick = longClick)) {
        if (hasMenu) {
            DropdownMenu(expanded = lockMenuOpen, onDismissRequest = { lockMenuOpen = false }) {
                if (favoriteInMenu) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (favorite == true) R.string.favorite_remove else R.string.favorite_add)) },
                        onClick = {
                            lockMenuOpen = false
                            onToggleFavorite()
                        },
                    )
                }
                if (onToggleWatched != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (watched) R.string.vod_mark_unwatched else R.string.vod_mark_watched)) },
                        onClick = {
                            lockMenuOpen = false
                            onToggleWatched()
                        },
                    )
                }
                lock?.actions?.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(stringResource(action.label)) },
                        onClick = {
                            lockMenuOpen = false
                            lock.run(action)
                        },
                    )
                }
            }
        }
        Box {
            Poster(url = poster, name = name, progress = progress, watched = watched, modifier = Modifier.fillMaxWidth())
            if (favorite != null && (favorite || !compact)) {
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
            color = if (compact) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Dugme kategorije nad posterima na uspravnom telefonu: naziv, broj naslova i strelica. Zauzima
 * ostatak reda pored čipova, pa naziv nije odsečen ivicom ekrana. Izgleda kao čip.
 */
@Composable
private fun CategoryButton(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = Glass.PillShape
    val accent = MaterialTheme.colorScheme.primary
    val content = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .then(if (selected) Modifier.glassSelected(accent, shape) else Modifier.glass(shape))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(
                text = count.toString(),
                color = content.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Icon(AppIcons.ArrowDown, contentDescription = null, tint = content, modifier = Modifier.padding(start = 4.dp).size(18.dp))
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
