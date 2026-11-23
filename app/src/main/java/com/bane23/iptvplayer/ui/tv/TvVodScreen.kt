package com.bane23.iptvplayer.ui.tv

import com.bane23.iptvplayer.ui.common.lock.vodItem
import com.bane23.iptvplayer.ui.common.lock.LockAction
import com.bane23.iptvplayer.ui.common.lock.ItemLock
import com.bane23.iptvplayer.ui.common.lock.rememberLockMenu
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.ContinueItem
import com.bane23.iptvplayer.data.repo.VodCategory
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.vod.FAVORITES_ID
import com.bane23.iptvplayer.ui.common.vod.Poster
import com.bane23.iptvplayer.ui.common.vod.VodLoading
import com.bane23.iptvplayer.ui.common.vod.vodLoadingHint
import com.bane23.iptvplayer.ui.common.vod.VodBrowseViewModel
import com.bane23.iptvplayer.ui.common.vod.label
import com.bane23.iptvplayer.data.repo.VodFilter
import com.bane23.iptvplayer.data.repo.VodSort
import com.bane23.iptvplayer.ui.common.summary
import com.bane23.iptvplayer.ui.common.vodFilterChoices
import com.bane23.iptvplayer.ui.common.vod.toVodItem
import kotlinx.coroutines.delay
import com.bane23.iptvplayer.ui.common.theme.Glass
import com.bane23.iptvplayer.ui.common.theme.glass
import com.bane23.iptvplayer.ui.common.theme.glassSelected
import com.bane23.iptvplayer.ui.common.theme.glassFocused
import com.bane23.iptvplayer.ui.common.theme.glassState
import com.bane23.iptvplayer.ui.common.theme.focusHalo

/**
 * Filmovi ili serije na TV-u: levo pretraga, „Nastavi gledanje” i kategorije (bira se sa OK),
 * desno posteri izabrane grupe. Dok je fokus među posterima, kategorije su skupljene u tanku traku
 * i u red staje 7 postera umesto 5; Levo sa prve kolone ili Nazad ih ponovo otvara (Bane).
 */
@Composable
fun TvVodScreen(
    series: Boolean,
    onOpen: (VodItem) -> Unit,
    onSearch: () -> Unit,
    onCategories: () -> Unit = {},
    onUnlock: () -> Unit = {},
    viewModel: VodBrowseViewModel = hiltViewModel(),
    // Filmovi i serije imaju odvojen opis (isti broj može biti i film i serija).
    previewViewModel: TvVodPreviewViewModel = hiltViewModel(key = "vod-preview-$series"),
) {
    LaunchedEffect(series) { viewModel.open(series) }
    val previewEnabled by previewViewModel.enabled.collectAsStateWithLifecycle()
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
    val lockMenu = rememberLockMenu(onUnlock)
    // Film ili serija zaključani dok PIN nije unet odmah nestaju iz mreže: fokus pre toga pređe na susedni poster.
    var lockedAway by remember { mutableIntStateOf(0) }
    val posterLock: (VodItem, List<VodItem>) -> ItemLock? = { item, shown ->
        lockMenu.vodItem(series, item.id, item.name)?.let { lock ->
            ItemLock(lock.actions) { action ->
                if (action == LockAction.LOCK && lockMenu.state.hasPin && !lockMenu.state.unlocked) {
                    val index = shown.indexOfFirst { it.id == item.id }
                    (shown.getOrNull(index + 1) ?: shown.getOrNull(index - 1))?.let { neighbor ->
                        lastItemId = neighbor.id
                        lockedAway++
                    }
                }
                lock.run(action)
            }
        }
    }
    LaunchedEffect(lockedAway) {
        if (lockedAway == 0) return@LaunchedEffect
        repeat(FOCUS_RESTORE_ATTEMPTS) {
            delay(FOCUS_RESTORE_STEP_MS)
            if (posterFocus.tryRequestFocus()) return@LaunchedEffect
        }
    }
    var sortOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    if (filterOpen) {
        TvFilterDialog(
            title = stringResource(if (series) R.string.filter_title_series else R.string.filter_title_movies),
            choices = vodFilterChoices(state.filter, viewModel::setFilter),
            resetLabel = stringResource(R.string.filter_reset),
            onReset = { viewModel.setFilter(VodFilter()) },
            onClose = { filterOpen = false },
        )
    }
    // Kategorija koja čeka sakrivanje i red na koji fokus prelazi pre nego što njen red nestane.
    var pendingHide by remember { mutableStateOf<VodCategory?>(null) }
    var focusTargetId by remember { mutableStateOf<String?>(null) }
    val moveFocus = remember { FocusRequester() }
    // Desno sa leve kolone ide na prvi poster na ekranu, kao u Uživo na prvi kanal, a ne na poster koji
    // je u visini kategorije. Drugi OK na već izabranoj kategoriji radi isto (Bane).
    val focusManager = LocalFocusManager.current
    val gridFocus = remember { FocusRequester() }
    val toItems: () -> Unit = { if (!gridFocus.tryRequestFocus()) focusManager.moveFocus(FocusDirection.Right) }
    // Kategorije su otvorene dok je fokus levo; čim pređe na postere, skupe se u usku traku.
    var categoriesOpen by rememberSaveable { mutableStateOf(true) }
    var gridFocused by remember { mutableStateOf(false) }
    val columns = if (categoriesOpen) GRID_COLUMNS else GRID_COLUMNS_WIDE
    // Grupa izabrana sa OK koja čeka da joj posteri stignu, pa fokus prelazi na njih.
    var enterGrid by remember { mutableStateOf<String?>(null) }
    var reopened by remember { mutableIntStateOf(0) }
    val searchRowFocus = remember { FocusRequester() }
    val openCategories: () -> Unit = {
        categoriesOpen = true
        reopened++
    }
    BackHandler(enabled = !categoriesOpen && gridFocused, onBack = openCategories)
    val toGrid = Modifier.onPreviewKeyEvent { event ->
        if (event.key != Key.DirectionRight) return@onPreviewKeyEvent false
        // Kad postera nema (učitavanje, prazna kategorija), Desno radi kao i pre.
        event.type == KeyEventType.KeyDown && gridFocus.tryRequestFocus()
    }
    // Mesto u mreži postera na kome je fokus; nije stanje, da korak kroz postere ništa ne crta iznova.
    // Kolona se računa tek na taster, jer se broj kolona menja kad se kategorije skupe.
    val focusedIndex = remember { IntArray(1) }
    // Levo sa prve kolone postera otvara kategorije i vraća na izabranu, a ne na red koji je u istoj visini.
    val gridModifier = Modifier
        .focusRequester(gridFocus)
        .onFocusChanged {
            gridFocused = it.hasFocus
            if (it.hasFocus) {
                categoriesOpen = false
                enterGrid = null
            }
        }
        .focusGroup()
        .onPreviewKeyEvent { event ->
            if (event.key != Key.DirectionLeft || focusedIndex[0] % columns != 0) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyDown) openCategories()
            event.type == KeyEventType.KeyDown
        }
    var movedToTop by remember { mutableIntStateOf(0) }
    var shownCategoryIds by remember { mutableStateOf(emptyList<String>()) }
    // Meni postera u „Nastavi gledanje” (dugi OK): skloni iz reda.
    var menuContinue by remember { mutableStateOf<ContinueItem?>(null) }
    // Stavka koja čeka sklanjanje i poster na koji fokus prelazi pre nego što njen poster nestane.
    var pendingDismiss by remember { mutableStateOf<ContinueItem?>(null) }
    var dismissFocusId by remember { mutableStateOf<Long?>(null) }
    // Film ili serija koja čeka uklanjanje iz Favorita dok fokus ne pređe na susedni poster.
    var pendingUnfavorite by remember { mutableStateOf<VodItem?>(null) }

    LaunchedEffect(pendingDismiss) {
        val item = pendingDismiss ?: return@LaunchedEffect
        var moved = false
        var attempts = 0
        // Fokus ide na red levo: red postoji samo dok su kategorije otvorene.
        if (focusTargetId != null) categoriesOpen = true
        while (!moved && attempts < FOCUS_RESTORE_ATTEMPTS) {
            delay(FOCUS_RESTORE_STEP_MS)
            if (focusTargetId != null) categoriesOpen = true
            moved = moveFocus.tryRequestFocus()
            attempts++
        }
        viewModel.dismissContinue(item)
        dismissFocusId = null
        focusTargetId = null
        pendingDismiss = null
    }
    // Uklanjanje iz Favorita dugim OK: fokus prvo pređe na sledeći poster (ako on nije na ekranu, na
    // prethodni), a kad je poster jedini, na red „Favoriti”; tek onda poster nestane.
    LaunchedEffect(pendingUnfavorite) {
        val item = pendingUnfavorite ?: return@LaunchedEffect
        val index = favorites.indexOfFirst { it.id == item.id }
        val neighbors = listOfNotNull(favorites.getOrNull(index + 1), favorites.getOrNull(index - 1))
        var moved = false
        for (neighbor in neighbors) {
            dismissFocusId = neighbor.id
            var attempts = 0
            while (!moved && attempts < FOCUS_RESTORE_ATTEMPTS) {
                delay(FOCUS_RESTORE_STEP_MS)
                moved = moveFocus.tryRequestFocus()
                attempts++
            }
            if (moved) break
        }
        dismissFocusId = null
        if (!moved) {
            focusTargetId = FAVORITES_ID
            var attempts = 0
            while (!moved && attempts < FOCUS_RESTORE_ATTEMPTS) {
                categoriesOpen = true
                delay(FOCUS_RESTORE_STEP_MS)
                moved = moveFocus.tryRequestFocus()
                attempts++
            }
            focusTargetId = null
        }
        viewModel.toggleFavorite(item)
        pendingUnfavorite = null
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
    // Otvaranje kategorija sa postera: fokus ide na izabranu grupu čim se njen red nacrta.
    LaunchedEffect(reopened) {
        if (reopened == 0) return@LaunchedEffect
        if (!showContinue && !showFavorites) {
            val row = shownCategoryIds.indexOf(state.selected?.id)
            if (row > 3) runCatching { categoryListState.scrollToItem(row - 3) }
        }
        repeat(FOCUS_RESTORE_ATTEMPTS) {
            delay(FOCUS_RESTORE_STEP_MS)
            if (firstFocus.tryRequestFocus()) return@LaunchedEffect
        }
        // Izabrana kategorija nije u spisku (filter): fokus ide na pretragu, koja je uvek tu.
        searchRowFocus.tryRequestFocus()
    }
    // OK na grupi je bira i, čim joj posteri stignu, prelazi na njih (tada se kategorije skupe).
    val enterReady = when (enterGrid) {
        null -> false
        CONTINUE_TARGET -> showContinue
        FAVORITES_ID -> showFavorites
        else -> !showContinue && state.selected?.id == enterGrid && !state.loadingItems
    }
    LaunchedEffect(enterGrid, enterReady) {
        if (!enterReady) return@LaunchedEffect
        repeat(FOCUS_RESTORE_ATTEMPTS) {
            if (gridFocus.tryRequestFocus()) return@LaunchedEffect
            delay(FOCUS_RESTORE_STEP_MS)
        }
        // Grupa je prazna ili se nije učitala: fokus ostaje na njoj.
        enterGrid = null
    }
    val categoriesShown = animateFloatAsState(
        targetValue = if (categoriesOpen) 1f else 0f,
        animationSpec = tween(CATEGORIES_ANIMATION_MS),
        label = "categories",
    )
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
        val lockKey = lockMenu.state.vodKey(series, category.id)
        val lockActions = lockMenu.actions(lockKey)
        TvActionDialog(
            title = category.name,
            options = listOf(
                stringResource(R.string.category_hide),
                stringResource(R.string.category_move_top),
                stringResource(R.string.categories_edit),
            ) + lockActions.map { stringResource(it.label) },
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

                    2 -> onCategories()

                    else -> lockActions.getOrNull(option - 3)?.let { lockMenu.run(it, lockKey) }
                }
            },
            onClose = { menuCategory = null },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Pozadinu sa sjajem crta početni ekran, jednom za sve kartice.
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        TvHeader(
            title = stringResource(if (series) R.string.vod_series else R.string.vod_movies),
            modifier = Modifier.padding(bottom = 16.dp),
        )
        when {
            state.loadingCategories -> VodLoading(
                series = series,
                accent = MaterialTheme.colorScheme.primary,
                hint = vodLoadingHint(state.storedOnDevice),
                modifier = Modifier.fillMaxSize(),
            )

            state.categoriesFailed -> {
                TvEmptyState(icon = AppIcons.Warning, text = stringResource(R.string.vod_load_failed), isError = true)
                // Početni fokus mora biti na nekom elementu, inače daljinski ne radi ništa.
                TvButton(onClick = viewModel::loadCategories, modifier = Modifier.focusRequester(firstFocus)) {
                    Text(stringResource(R.string.player_retry))
                }
                LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
            }

            state.categories.isEmpty() -> TvEmptyState(icon = if (series) AppIcons.Series else AppIcons.Movies, text = stringResource(R.string.vod_empty))

            // Širinu leve kolone čita samo raspored, pa animacija ne crta iznova ni spisak ni postere.
            else -> SplitRow(open = { categoriesShown.value }, gap = 24.dp) {
                // Levo: pretraga, Favoriti i „Nastavi gledanje” stoje zakačeni na vrhu, a ispod njih
                // polje za filtriranje i kategorije koje se skroluju.
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        // Gore ili Dole posle OK znači da korisnik bira dalje: fokus ostaje u kategorijama.
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.DirectionUp || event.key == Key.DirectionDown) enterGrid = null
                            false
                        },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (!categoriesOpen) {
                        CategoryStrip()
                        return@Column
                    }
                    TvListRow(onClick = onSearch, modifier = Modifier.focusRequester(searchRowFocus).then(toGrid)) { color ->
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
                    TvListRow(onClick = { sortOpen = true }, modifier = toGrid) { color ->
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
                    // Filter postera: dok je uključen, red je označen i piše šta je izabrano.
                    TvListRow(onClick = { filterOpen = true }, selected = state.filter.active, modifier = toGrid) { color ->
                        Icon(AppIcons.Filter, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                        Text(
                            text = if (state.filter.active) {
                                stringResource(R.string.filter_row_current, state.filter.summary())
                            } else {
                                stringResource(R.string.filter_row)
                            },
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
                            if (showFavorites) {
                                toItems()
                            } else {
                                lastItemId = null
                                continueChosen = false
                                enterGrid = FAVORITES_ID
                                viewModel.selectCategory(VodCategory(FAVORITES_ID, favoritesLabel))
                            }
                        },
                        modifier = (if (showFavorites) Modifier.focusRequester(firstFocus) else Modifier)
                            .then(if (focusTargetId == FAVORITES_ID) Modifier.focusRequester(moveFocus) else Modifier)
                            .then(toGrid),
                    )
                    if (continueItems.isNotEmpty()) {
                        PinnedRow(
                            icon = AppIcons.Play,
                            title = stringResource(R.string.vod_continue_watching),
                            subtitle = stringResource(R.string.vod_total, continueItems.size),
                            selected = showContinue,
                            onClick = {
                                if (showContinue) {
                                    toItems()
                                } else {
                                    lastItemId = null
                                    continueChosen = true
                                    enterGrid = CONTINUE_TARGET
                                    viewModel.rememberContinue()
                                }
                            },
                            modifier = (if (showContinue) Modifier.focusRequester(firstFocus) else Modifier).then(toGrid),
                        )
                    }
                    TvFilterField(
                        value = categoryFilter,
                        onChange = { categoryFilter = it },
                        placeholder = stringResource(R.string.search_categories),
                    )
                    val shownCategories = remember(state.categories, categoryFilter) {
                        val text = categoryFilter.trim()
                        if (text.isEmpty()) state.categories else state.categories.filter { it.name.contains(text, ignoreCase = true) }
                    }
                    LaunchedEffect(shownCategories) { shownCategoryIds = shownCategories.map { it.id } }
                    LazyColumn(
                        state = categoryListState,
                        modifier = Modifier.weight(1f).then(toGrid),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(shownCategories, key = { it.id }) { category ->
                            val selected = !showContinue && category == state.selected
                            TvTextRow(
                                text = category.name,
                                selected = selected,
                                onClick = {
                                    if (selected) {
                                        toItems()
                                    } else {
                                        lastItemId = null
                                        continueChosen = false
                                        enterGrid = category.id
                                        viewModel.selectCategory(category)
                                    }
                                },
                                onLongClick = { menuCategory = category },
                                modifier = (if (selected) Modifier.focusRequester(firstFocus) else Modifier)
                                    .then(if (focusTargetId == category.id) Modifier.focusRequester(moveFocus) else Modifier),
                            )
                        }
                    }
                }
                Column(modifier = Modifier.fillMaxHeight()) {
                    if (previewEnabled) {
                        // Pregled čita svoje stanje sam: korak kroz postere ne crta iznova ceo ekran.
                        val preview by previewViewModel.preview.collectAsStateWithLifecycle()
                        TvVodPreviewHeader(preview = preview)
                    }
                    when {
                        showContinue -> PosterGrid(columns, gridModifier) {
                            itemsIndexed(continueItems, key = { _, item -> item.id }) { index, item ->
                                val isLast = isLastItem(item.id) { lastItemId }
                                PosterCard(
                                    name = item.name,
                                    poster = item.poster,
                                    progress = item.progress,
                                    onLongClick = { menuContinue = item },
                                    onClick = { onOpen(item.toVodItem()) },
                                    onFocused = {
                                        focusedIndex[0] = index
                                        lastItemId = item.id
                                        previewViewModel.focus(item.toVodItem(), series)
                                    },
                                    modifier = when {
                                        item.id == dismissFocusId -> Modifier.focusRequester(moveFocus)
                                        isLast -> Modifier.focusRequester(posterFocus)
                                        else -> Modifier
                                    },
                                )
                            }
                        }

                        showFavorites && favorites.isEmpty() -> TvEmptyState(icon = AppIcons.StarOutline, text = stringResource(R.string.vod_favorites_empty))

                        showFavorites -> PosterGrid(columns, gridModifier) {
                            itemsIndexed(favorites, key = { _, item -> item.id }) { index, item ->
                                PosterCard(
                                    name = item.name,
                                    poster = item.poster,
                                    favorite = true,
                                    watched = watched.of(item),
                                    onToggleFavorite = { if (pendingUnfavorite == null) pendingUnfavorite = item },
                                    lock = posterLock(item, favorites),
                                    onClick = { onOpen(item) },
                                    onFocused = {
                                        focusedIndex[0] = index
                                        lastItemId = item.id
                                        previewViewModel.focus(item, series)
                                    },
                                    modifier = when {
                                        item.id == dismissFocusId -> Modifier.focusRequester(moveFocus)
                                        isLastItem(item.id) { lastItemId } -> Modifier.focusRequester(posterFocus)
                                        else -> Modifier
                                    },
                                )
                            }
                        }

                        state.selected == null -> Note(stringResource(R.string.vod_pick_category))
                        state.loadingItems -> VodLoading(
                            series = series,
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxSize(),
                        )
                        state.itemsFailed -> TvEmptyState(icon = AppIcons.Warning, text = stringResource(R.string.vod_load_failed), isError = true)
                        state.items.isEmpty() -> TvEmptyState(icon = if (series) AppIcons.Series else AppIcons.Movies, text = stringResource(if (state.filter.active) R.string.filter_empty else R.string.vod_empty))

                        else -> PosterGrid(columns, gridModifier) {
                            itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                                PosterCard(
                                    name = item.name,
                                    poster = item.poster,
                                    favorite = item.id in favoriteIds,
                                    watched = watched.of(item),
                                    onToggleFavorite = { viewModel.toggleFavorite(item) },
                                    lock = posterLock(item, state.items),
                                    onClick = { onOpen(item) },
                                    onFocused = {
                                        focusedIndex[0] = index
                                        lastItemId = item.id
                                        previewViewModel.focus(item, series)
                                    },
                                    modifier = if (isLastItem(item.id) { lastItemId }) Modifier.focusRequester(posterFocus) else Modifier,
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
                    // Posteri nisu primili fokus: kategorije moraju biti otvorene da bi ga primio njihov red.
                    categoriesOpen = true
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

/**
 * Da li je [id] poster na kome je fokus bio poslednji. Poster prati samo ovaj odgovor, a ne samu
 * vrednost, pa se pri koraku D-pada iznova crtaju samo dva postera, a ne cela mreža.
 */
@Composable
private fun isLastItem(id: Long, lastItemId: () -> Long?): Boolean =
    remember(id) { derivedStateOf { lastItemId() == id } }.value

@Composable
private fun PosterGrid(columns: Int, modifier: Modifier = Modifier, content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        // Mesto za uvećan poster u fokusu, da ga ivica mreže ne odseče.
        contentPadding = PaddingValues(12.dp),
        content = content,
    )
}

/**
 * Dve kolone jedna pored druge; leva je široka kao tanka traka kad je [open] 0, a kao otvorene
 * kategorije kad je 1. Vrednost se čita tek pri merenju, pa se pri animaciji širine sadržaj kolona
 * ne crta iznova.
 */
@Composable
private fun SplitRow(open: () -> Float, gap: Dp, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val total = (constraints.maxWidth - gapPx).coerceAtLeast(0)
        val strip = CATEGORIES_STRIP_WIDTH.roundToPx().coerceAtMost(total)
        val leftWidth = (strip + (total * CATEGORIES_OPEN_FRACTION - strip) * open()).roundToInt().coerceIn(0, total)
        val left = measurables[0].measure(Constraints.fixed(leftWidth, constraints.maxHeight))
        val right = measurables[1].measure(Constraints.fixed(total - leftWidth, constraints.maxHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            left.place(0, 0)
            right.place(leftWidth + gapPx, 0)
        }
    }
}

/**
 * Skupljene kategorije dok je fokus među posterima: tanka traka samo sa ikonicama (Bane). Ne prima
 * fokus; otvara je Levo sa prve kolone postera ili Nazad.
 */
@Composable
private fun CategoryStrip() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(Glass.RowShape)
            .glass()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(AppIcons.ChevronLeft, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Icon(AppIcons.Categories, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/**
 * Zakačen red levo (Favoriti, „Nastavi gledanje”, grupe kanala u Uživo): pločica sa ikonicom, nazivom
 * i brojem stavki ispod, istog izgleda kao na položenom telefonu.
 */
@Composable
internal fun PinnedRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    TvListRow(
        onClick = onClick,
        selected = selected,
        onFocused = onFocused,
        onLongClick = onLongClick,
        modifier = modifier,
    ) { color ->
        // Ikonica je u akcentnoj boji, kao na telefonu; na fokusiranom redu prati boju teksta.
        Icon(icon, contentDescription = null, tint = if (color == colors.onPrimary) color else colors.primary, modifier = Modifier.size(22.dp))
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


private const val GRID_COLUMNS = 5

// Sa skupljenim kategorijama u red staje 7 malo manjih postera (Bane, 22. 11. 2026).
private const val GRID_COLUMNS_WIDE = 7
private const val CATEGORIES_OPEN_FRACTION = 0.28f
private val CATEGORIES_STRIP_WIDTH = 44.dp
private const val CATEGORIES_ANIMATION_MS = 180
private const val CONTINUE_TARGET = "__continue"
private const val FOCUSED_POSTER_SCALE = 1.08f
private const val FOCUS_RESTORE_ATTEMPTS = 10
private const val FOCUS_RESTORE_STEP_MS = 60L
private const val MOVE_SETTLE_MS = 150L
private const val HOLD_PRESSED = 0
private const val HOLD_LONG_DONE = 1
private const val HOLD_FOREIGN = 2

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
    onLongClick: (() -> Unit)? = null,
    // Sa zaključavanjem dugi OK otvara meni: omiljeni i roditeljska zaštita.
    lock: ItemLock? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var lockMenuOpen by remember { mutableStateOf(false) }
    if (lockMenuOpen && lock != null) {
        val favoriteOption = onToggleFavorite?.let { stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add) }
        TvActionDialog(
            title = name,
            options = listOfNotNull(favoriteOption) + lock.actions.map { stringResource(it.label) },
            onSelect = { option ->
                val shift = if (favoriteOption == null) 0 else 1
                if (option < shift) onToggleFavorite?.invoke() else lock.actions.getOrNull(option - shift)?.let(lock.run)
            },
            onClose = { lockMenuOpen = false },
        )
    }
    // Uklanjanje iz favorita se prvo potvrđuje (Bane); dodavanje ide odmah.
    var confirmRemove by remember { mutableStateOf(false) }
    if (confirmRemove && onToggleFavorite != null) {
        TvActionDialog(
            title = name,
            // „Ukloni” je prva stavka (Bane); meni ne prima OK dok se taster ne pusti, pa držanje ne potvrđuje.
            options = listOf(stringResource(R.string.favorite_remove), stringResource(R.string.action_cancel)),
            onSelect = { option -> if (option == 0) onToggleFavorite() },
            onClose = { confirmRemove = false },
        )
    }
    val longAction: (() -> Unit)? = onLongClick
        ?: lock?.let { { lockMenuOpen = true } }
        ?: onToggleFavorite?.let { toggle -> { if (favorite) confirmRemove = true else toggle() } }
    // Jedno držanje tastera OK važi kao jedan dugi pritisak. [pressed]: pritisak je počeo na ovom posteru;
    // [foreignHold]: taster se drži od ranije (fokus je stigao sa drugog postera), pa se ne računa.
    val hold = remember { BooleanArray(3) }
    val shape = Glass.PosterShape
    val accent = MaterialTheme.colorScheme.primary
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
            // Okvir u boji sa mekim oreolom, kao fokus svuda u izgledu „Staklo”.
            .focusHalo(focused, accent, radius = 12.dp)
            .then(if (focused) Modifier.glassFocused(accent, shape) else Modifier)
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val ok = native.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                    native.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                    native.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                if (!ok) return@onPreviewKeyEvent false
                when {
                    native.action == android.view.KeyEvent.ACTION_DOWN && native.repeatCount == 0 -> {
                        hold[HOLD_PRESSED] = true
                        hold[HOLD_LONG_DONE] = false
                        hold[HOLD_FOREIGN] = false
                        false
                    }

                    native.action == android.view.KeyEvent.ACTION_UP -> {
                        val own = hold[HOLD_PRESSED]
                        hold[HOLD_PRESSED] = false
                        hold[HOLD_LONG_DONE] = false
                        hold[HOLD_FOREIGN] = false
                        !own
                    }

                    // Ponovljen pritisak dok se taster drži: tuđ se guta, da ne pokrene dugi pritisak ovde.
                    else -> {
                        if (!hold[HOLD_PRESSED]) hold[HOLD_FOREIGN] = true
                        !hold[HOLD_PRESSED]
                    }
                }
            }
            // Dug pritisak na OK dodaje u omiljene, odnosno pita za uklanjanje; u „Nastavi gledanje” otvara meni.
            .combinedClickable(
                onClick = onClick,
                onLongClick = longAction?.let { action ->
                    {
                        // Dodir nema taster, pa za njega važi svaki dugi pritisak.
                        if (!hold[HOLD_FOREIGN] && !(hold[HOLD_PRESSED] && hold[HOLD_LONG_DONE])) {
                            hold[HOLD_LONG_DONE] = true
                            action()
                        }
                    }
                },
            )
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
