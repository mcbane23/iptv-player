package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.theme.glowBackground
import com.bane23.iptvplayer.ui.common.theme.LocalNeon
import com.bane23.iptvplayer.ui.common.theme.neonHalo
import com.bane23.iptvplayer.domain.Category
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.domain.FavoriteList
import com.bane23.iptvplayer.ui.common.FavoriteListsViewModel
import com.bane23.iptvplayer.ui.common.ChannelListViewModel
import com.bane23.iptvplayer.ui.common.ChannelLogo
import com.bane23.iptvplayer.ui.common.SkeletonLogo
import com.bane23.iptvplayer.ui.common.SkeletonTextLines
import com.bane23.iptvplayer.ui.common.skeleton
import com.bane23.iptvplayer.ui.common.LiveTvViewModel
import com.bane23.iptvplayer.ui.common.PlaylistsViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Icon
import com.bane23.iptvplayer.ui.common.ChannelPreviewViewModel
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.guide.formatTime
import com.bane23.iptvplayer.ui.common.guide.nowUntil
import com.bane23.iptvplayer.ui.common.guide.timeRangeLeft
import androidx.compose.foundation.border
import androidx.compose.ui.res.pluralStringResource
import com.bane23.iptvplayer.ui.common.player.AspectMode
import com.bane23.iptvplayer.ui.common.player.VideoSurface
import com.bane23.iptvplayer.ui.common.player.statusMessage
import com.bane23.iptvplayer.ui.common.rememberNow
import kotlinx.coroutines.delay

/** Oznaka za „Svi kanali” u izboru kategorije. */
private const val ALL_CHANNELS = ChannelGroups.ALL

/**
 * Uživo na TV-u, u tri kolone: kategorije (sa pretragom na vrhu), kanali izabrane kategorije
 * i pregled kanala na kome je fokus (slika bez zvuka i šta je na programu).
 * Kategorija se bira samim prelaskom fokusa na nju; desno se ide u kanale, OK pušta kanal.
 */
@Composable
fun TvLiveTvScreen(
    onPlay: (channelId: Long, categoryId: Long?) -> Unit,
    onManagePlaylists: () -> Unit,
    onSearch: () -> Unit,
    onCategories: () -> Unit = {},
    viewModel: LiveTvViewModel = hiltViewModel(),
    channelsViewModel: ChannelListViewModel = hiltViewModel(),
    previewViewModel: ChannelPreviewViewModel = hiltViewModel(),
    playlistsViewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlists by playlistsViewModel.state.collectAsStateWithLifecycle()
    val playlistId = state.playlistId
    // Kreće se od nedavno gledanih (bez njih od kategorije otvorene prošli put). Ključevi vraćaju izbor na početak
    // kad se lista učita i kad se promeni aktivna lista.
    var selectedCategory by rememberSaveable(state.loading, playlistId) {
        mutableStateOf(state.lastCategoryId ?: ALL_CHANNELS)
    }
    var focusedCategory by remember(state.loading, playlistId) { mutableStateOf(selectedCategory) }
    val firstFocus = remember { FocusRequester() }
    val channelFocus = remember { FocusRequester() }
    val categoryListState = rememberLazyListState()
    // Kanal na kome je fokus bio poslednji put, da se posle povratka iz plejera nastavi odatle.
    var lastChannelId by rememberSaveable(playlistId) { mutableStateOf<Long?>(null) }
    // Kanal na kome je fokus, za logo i naziv u pregledu kad slike nema.
    var focusedChannel by remember { mutableStateOf<Channel?>(null) }
    val now by rememberNow()
    // Meni kategorije (dugi OK): sakrij, pomeri na vrh, uredi.
    var menuCategory by remember { mutableStateOf<Category?>(null) }
    // Kategorija koja čeka sakrivanje i red na koji fokus prelazi pre nego što njen red nestane.
    var pendingHide by remember { mutableStateOf<Long?>(null) }
    var focusTargetId by remember { mutableStateOf<Long?>(null) }
    val moveFocus = remember { FocusRequester() }
    var movedToTop by remember { mutableIntStateOf(0) }
    val favoriteLists: FavoriteListsViewModel = hiltViewModel()
    // Meni liste omiljenih (dugi OK): za „Omiljene” nova lista, za imenovanu listu ime i brisanje.
    var favoritesMenu by remember { mutableStateOf(false) }
    var menuList by remember { mutableStateOf<FavoriteList?>(null) }
    var nameDialog by remember { mutableStateOf<ListNameRequest?>(null) }
    var deleteList by remember { mutableStateOf<FavoriteList?>(null) }
    // Lista koja čeka brisanje dok fokus ne pređe sa njenog reda na „Omiljene”.
    var pendingListDelete by remember { mutableStateOf<Long?>(null) }
    val favoritesFocus = remember { FocusRequester() }
    // Kartica omiljenih koja je bila otvorena poslednji put: red „Omiljeni” se vraća na nju.
    var favoritesTab by rememberSaveable(playlistId) { mutableStateOf(ChannelGroups.FAVORITES) }
    LaunchedEffect(selectedCategory) {
        if (state.isFavoritesGroup(selectedCategory)) favoritesTab = selectedCategory
    }
    // Fokus na redu „Omiljeni” ne menja karticu koja je već otvorena.
    val focusFavorites = {
        if (!state.isFavoritesGroup(focusedCategory)) {
            focusedCategory = favoritesTab.takeIf { state.isFavoritesGroup(it) } ?: ChannelGroups.FAVORITES
        }
    }

    // Pregled ne sme da drži vezu kad se izađe sa ekrana ili aplikacija ode u pozadinu.
    DisposableEffect(Unit) { onDispose { previewViewModel.stop() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { previewViewModel.stop() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { lastChannelId?.let(previewViewModel::focus) }

    // Kratka pauza pre učitavanja kanala, da brzo listanje kroz kategorije ne pokreće upit za svaku.
    LaunchedEffect(focusedCategory) {
        delay(CATEGORY_FOCUS_DELAY_MS)
        selectedCategory = focusedCategory
    }
    LaunchedEffect(playlistId, selectedCategory) {
        if (playlistId != null) {
            channelsViewModel.select(playlistId, selectedCategory.takeIf { it != ALL_CHANNELS })
        }
    }
    // Izbor se pamti tek kad se korisnik zadrži na kategoriji, ne pri svakom prolasku preko nje.
    LaunchedEffect(playlistId, selectedCategory, state.loading) {
        if (playlistId != null && !state.loading) {
            delay(REMEMBER_CATEGORY_DELAY_MS)
            viewModel.rememberCategory(selectedCategory)
        }
    }
    // Sakrivanje: fokus prvo pređe na susednu kategoriju, pa tek onda red nestane.
    LaunchedEffect(pendingHide) {
        val id = pendingHide ?: return@LaunchedEffect
        var moved = false
        var attempts = 0
        while (!moved && attempts < FOCUS_RESTORE_ATTEMPTS) {
            delay(FOCUS_RESTORE_STEP_MS)
            moved = moveFocus.tryRequestFocus()
            attempts++
        }
        viewModel.hideCategory(id)
        focusTargetId = null
        pendingHide = null
    }
    LaunchedEffect(pendingListDelete) {
        val id = pendingListDelete ?: return@LaunchedEffect
        var moved = false
        var attempts = 0
        while (!moved && attempts < FOCUS_RESTORE_ATTEMPTS) {
            delay(FOCUS_RESTORE_STEP_MS)
            moved = favoritesFocus.tryRequestFocus()
            attempts++
        }
        focusedCategory = ChannelGroups.FAVORITES
        favoriteLists.delete(id)
        pendingListDelete = null
    }
    TvFavoritePickDialog(favoriteLists)
    if (favoritesMenu) {
        TvActionDialog(
            title = stringResource(R.string.category_favorites),
            options = listOf(stringResource(R.string.favorite_list_new)),
            onSelect = { nameDialog = ListNameRequest(null) },
            onClose = { favoritesMenu = false },
        )
    }
    menuList?.let { list ->
        TvActionDialog(
            title = list.name,
            options = listOf(
                stringResource(R.string.favorite_list_rename),
                stringResource(R.string.favorite_list_delete),
                stringResource(R.string.favorite_list_new),
            ),
            onSelect = { option ->
                when (option) {
                    0 -> nameDialog = ListNameRequest(list)
                    1 -> deleteList = list
                    else -> nameDialog = ListNameRequest(null)
                }
            },
            onClose = { menuList = null },
        )
    }
    nameDialog?.let { request ->
        TvFavoriteListNameDialog(list = request.list, viewModel = favoriteLists, onClose = { nameDialog = null })
    }
    deleteList?.let { list ->
        TvActionDialog(
            title = stringResource(R.string.favorite_list_delete_confirm, list.name),
            options = listOf(stringResource(R.string.action_cancel), stringResource(R.string.favorite_list_delete)),
            onSelect = { option -> if (option == 1) pendingListDelete = list.id },
            onClose = { deleteList = null },
        )
    }
    LaunchedEffect(movedToTop) {
        if (movedToTop > 0) {
            // Kratka pauza da novi redosled stigne iz baze; kategorija (i fokus) je sada na vrhu.
            delay(MOVE_SETTLE_MS)
            runCatching { categoryListState.scrollToItem(0) }
        }
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
                        val list = state.categories
                        val index = list.indexOfFirst { it.id == category.id }
                        focusTargetId = (list.getOrNull(index + 1) ?: list.getOrNull(index - 1))?.id ?: ALL_CHANNELS
                        pendingHide = category.id
                    }

                    1 -> {
                        viewModel.moveCategoryToTop(category.id)
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
            .glowBackground(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.nav_live),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 24.dp),
            )
            // Brza promena aktivne liste: OK prelazi na sledeću. Postoji samo kad lista ima više.
            val all = playlists.playlists
            val activeIndex = all.indexOfFirst { it.id == playlistId }
            if (all.size > 1 && activeIndex >= 0) {
                TvOutlinedButton(
                    onClick = { playlistsViewModel.setActive(all[(activeIndex + 1) % all.size].id) },
                    modifier = Modifier
                        .padding(end = 24.dp)
                        .widthIn(max = 360.dp),
                ) {
                    Text(
                        text = stringResource(R.string.playlist_switch, all[activeIndex].name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TvClock()
        }

        when {
            state.loading -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Obrisi kolona dok se lista učitava; ništa nije fokusirano, pa daljinski čeka.
                Column(modifier = Modifier.weight(0.26f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    repeat(SKELETON_ROWS) {
                        Box(
                            modifier = Modifier
                                .padding(start = 16.dp, top = 8.dp)
                                .fillMaxWidth(0.7f)
                                .height(16.dp)
                                .skeleton(4.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(0.42f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(SKELETON_ROWS) {
                        Row(
                            modifier = Modifier.padding(start = 62.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SkeletonLogo(size = 36.dp)
                            SkeletonTextLines(modifier = Modifier.weight(1f).padding(start = 16.dp))
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(0.32f)
                        .aspectRatio(16f / 9f)
                        .skeleton(12.dp),
                )
            }

            playlistId == null -> {
                TvEmptyState(icon = AppIcons.Playlists, text = stringResource(R.string.playlists_empty))
                // Početni fokus mora biti na nekom elementu, inače daljinski ne radi ništa.
                TvButton(onClick = onManagePlaylists, modifier = Modifier.focusRequester(firstFocus)) {
                    Text(stringResource(R.string.action_add_playlist))
                }
                LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
            }

            else -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LazyColumn(
                    state = categoryListState,
                    modifier = Modifier
                        .weight(0.26f)
                        .fillMaxHeight(),
                    // Isti izgled kao spisak levo kod filmova i serija i kao na telefonu: redovi su pločice.
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item(key = "search") {
                        TvListRow(onClick = onSearch) { color ->
                            Icon(AppIcons.Search, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                            Text(
                                text = stringResource(R.string.search_channels),
                                color = color,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                    item(key = ChannelGroups.FAVORITES) {
                        PinnedRow(
                            icon = AppIcons.Star,
                            title = stringResource(R.string.category_favorites),
                            subtitle = stringResource(R.string.vod_total, state.favoriteCount),
                            // Jedan red za sve liste omiljenih; liste su kartice iznad kanala.
                            selected = state.isFavoritesGroup(selectedCategory),
                            onFocused = { focusFavorites(); lastChannelId = null },
                            onClick = focusFavorites,
                            onLongClick = { favoritesMenu = true },
                            modifier = focusTarget(state.isFavoritesGroup(selectedCategory), firstFocus)
                                .focusRequester(favoritesFocus),
                        )
                    }
                    if (state.recentCount > 0) {
                        item(key = ChannelGroups.RECENT) {
                            PinnedRow(
                                icon = AppIcons.History,
                                title = stringResource(R.string.category_recent),
                                subtitle = stringResource(R.string.vod_total, state.recentCount),
                                selected = selectedCategory == ChannelGroups.RECENT,
                                onFocused = { focusedCategory = ChannelGroups.RECENT; lastChannelId = null },
                                onClick = { focusedCategory = ChannelGroups.RECENT },
                                modifier = focusTarget(selectedCategory == ChannelGroups.RECENT, firstFocus),
                            )
                        }
                    }
                    item(key = ALL_CHANNELS) {
                        PinnedRow(
                            icon = AppIcons.LiveTv,
                            title = stringResource(R.string.category_all),
                            subtitle = stringResource(R.string.vod_total, state.totalChannels),
                            selected = selectedCategory == ALL_CHANNELS,
                            onFocused = { focusedCategory = ALL_CHANNELS; lastChannelId = null },
                            onClick = { focusedCategory = ALL_CHANNELS },
                            modifier = focusTarget(selectedCategory == ALL_CHANNELS, firstFocus)
                                .then(focusTarget(focusTargetId == ALL_CHANNELS, moveFocus)),
                        )
                    }
                    items(state.categories, key = { it.id }) { category ->
                        TvTextRow(
                            text = category.name,
                            trailing = category.channelCount.toString(),
                            selected = selectedCategory == category.id,
                            onFocused = { focusedCategory = category.id; lastChannelId = null },
                            onClick = { focusedCategory = category.id },
                            onLongClick = { menuCategory = category },
                            card = true,
                            modifier = focusTarget(selectedCategory == category.id, firstFocus)
                                .then(focusTarget(focusTargetId == category.id, moveFocus)),
                        )
                    }
                }

                val channels = channelsViewModel.channels.collectAsLazyPagingItems()
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                ) {
                if (state.favoriteLists.isNotEmpty() && state.isFavoritesGroup(selectedCategory)) {
                    // Liste omiljenih: Gore iz kanala, Levo/Desno po karticama, OK otvara listu.
                    TvFavoriteListTabs(
                        lists = state.favoriteLists,
                        selected = selectedCategory,
                        onSelect = { group ->
                            lastChannelId = null
                            focusedCategory = group
                            selectedCategory = group
                        },
                        onListMenu = { menuList = it },
                        onNew = { nameDialog = ListNameRequest(null) },
                    )
                } else {
                ChannelsHeading(
                    title = when (selectedCategory) {
                        ChannelGroups.FAVORITES -> stringResource(R.string.category_favorites)
                        ChannelGroups.RECENT -> stringResource(R.string.category_recent)
                        ALL_CHANNELS -> stringResource(R.string.category_all)
                        else -> state.favoriteList(selectedCategory)?.name
                            ?: state.categories.firstOrNull { it.id == selectedCategory }?.name.orEmpty()
                    },
                    count = when (selectedCategory) {
                        ChannelGroups.FAVORITES -> state.favoriteCount
                        ChannelGroups.RECENT -> state.recentCount
                        ALL_CHANNELS -> state.totalChannels
                        else -> state.favoriteList(selectedCategory)?.channelCount
                            ?: state.categories.firstOrNull { it.id == selectedCategory }?.channelCount ?: 0
                    },
                )
                }
                TvFavoriteHint()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                        val channel = channels[index]
                        TvListRow(
                            onClick = {
                                channel?.let {
                                    // Veza pregleda se zatvara pre pravog plejera (provajderi sa jednom vezom).
                                    previewViewModel.stop()
                                    onPlay(it.id, selectedCategory.takeIf { id -> id != ALL_CHANNELS })
                                }
                            },
                            onLongClick = { channel?.let { favoriteLists.favoriteAction(it.id) } },
                            onFocused = {
                                channel?.let {
                                    lastChannelId = it.id
                                    focusedChannel = it
                                    previewViewModel.focus(it.id)
                                }
                            },
                            modifier = focusTarget(channel != null && channel.id == lastChannelId, channelFocus),
                            container = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            // Niži red i manji logo, da na ekran stane kanal više.
                            verticalPadding = 6.dp,
                        ) { color ->
                            Text(
                                text = channel?.number?.toString().orEmpty(),
                                color = color.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                modifier = Modifier
                                    .width(36.dp)
                                    .padding(end = 10.dp),
                            )
                            if (channel == null) {
                                // Strana liste se još učitava: obrisi umesto praznog reda.
                                SkeletonLogo(size = 32.dp)
                                SkeletonTextLines(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                                )
                            } else {
                                ChannelLogo(url = channel.logo, name = channel.name, size = 32.dp)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 16.dp),
                                ) {
                                    Text(
                                        text = channel.name,
                                        color = color,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    channel.nowTitle?.let { nowTitle ->
                                        Text(
                                            text = nowUntil(nowTitle, channel.nowStopMs),
                                            color = color.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    channel.nowProgress(now)?.let { progress ->
                                        TvProgressBar(
                                            progress = progress,
                                            trackColor = color.copy(alpha = 0.15f),
                                            // Na redu u fokusu (pun akcentom) traka ostaje u boji teksta.
                                            barColor = LocalNeon.current?.progress
                                                ?.takeIf { color != MaterialTheme.colorScheme.onPrimary }
                                                ?: color.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(top = 3.dp),
                                        )
                                    }
                                }
                                if (channel.favorite) {
                                    Icon(
                                        AppIcons.Star,
                                        contentDescription = null,
                                        tint = if (color == MaterialTheme.colorScheme.onSurface) FavoriteGold else color,
                                        modifier = Modifier
                                            .padding(start = 12.dp)
                                            .size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                }
                ChannelPreviewPane(
                    viewModel = previewViewModel,
                    channel = focusedChannel?.takeIf { it.id == lastChannelId },
                    now = now,
                    modifier = Modifier
                        .weight(0.32f)
                        .fillMaxHeight(),
                )
                // Posle povratka iz plejera fokus ide na kanal koji je gledan, inače na izabranu kategoriju.
                LaunchedEffect(Unit) {
                    if (lastChannelId != null) {
                        // Kanali se učitavaju sa malim zakašnjenjem, pa se pokušava nekoliko puta.
                        repeat(FOCUS_RESTORE_ATTEMPTS) {
                            if (channelFocus.tryRequestFocus()) return@LaunchedEffect
                            delay(FOCUS_RESTORE_STEP_MS)
                        }
                    }
                    // Izabrana kategorija može biti daleko niz spisak; njen red mora da postoji da bi primio fokus.
                    // Redovi pre kategorija: pretraga, omiljeni (sa svim listama), (nedavno), svi kanali.
                    val allRow = if (state.recentCount > 0) 3 else 2
                    val selectedRow = when {
                        state.isFavoritesGroup(selectedCategory) -> 1
                        selectedCategory == ChannelGroups.RECENT -> 2
                        selectedCategory == ALL_CHANNELS -> allRow
                        else -> allRow + 1 + state.categories.indexOfFirst { it.id == selectedCategory }.coerceAtLeast(0)
                    }
                    runCatching { categoryListState.scrollToItem((selectedRow - 3).coerceAtLeast(0)) }
                    repeat(FOCUS_RESTORE_ATTEMPTS) {
                        if (firstFocus.tryRequestFocus()) return@LaunchedEffect
                        delay(FOCUS_RESTORE_STEP_MS)
                    }
                }
            }
        }
    }
}

/** Samo izabrani red nosi [requester], da bi početni fokus išao baš na njega. */
/** Zahtev za prozor sa imenom liste: [list] je null za novu listu. */
private data class ListNameRequest(val list: FavoriteList?)

private fun focusTarget(isTarget: Boolean, requester: FocusRequester): Modifier =
    if (isTarget) Modifier.focusRequester(requester) else Modifier

private const val SKELETON_ROWS = 8
private const val CATEGORY_FOCUS_DELAY_MS = 250L
private const val FOCUS_RESTORE_ATTEMPTS = 10
private const val FOCUS_RESTORE_STEP_MS = 60L
private const val REMEMBER_CATEGORY_DELAY_MS = 1500L
private const val MOVE_SETTLE_MS = 150L

/** Zlatna zvezdica za omiljene kanale. */
private val FavoriteGold = Color(0xFFFFD166)

/** Diskretna traka koliko je emisija odmakla ([progress] od 0 do 1). */
@Composable
fun TvProgressBar(progress: Float, trackColor: Color, barColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(barColor),
        )
    }
}

/**
 * Desna kolona: slika kanala na kome je fokus (bez zvuka) i šta je na njemu sada i sledeće.
 * Ako je pregled isključen u podešavanjima, umesto slike stoji samo prazna površina.
 */
@Composable
private fun ChannelPreviewPane(
    viewModel: ChannelPreviewViewModel,
    channel: Channel?,
    now: Long,
    modifier: Modifier = Modifier,
) {
    val player by viewModel.player.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val nowNext by viewModel.nowNext.collectAsStateWithLifecycle()
    val later by viewModel.later.collectAsStateWithLifecycle()
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val current = player
            if (current != null) {
                VideoSurface(player = current, aspectMode = AspectMode.FIT, modifier = Modifier.fillMaxSize())
            } else if (channel != null) {
                // Slike nema (pregled isključen, kanal zaključan ili se tek pokreće): veliki logo i naziv.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    ChannelLogo(url = channel.logo, name = channel.name, size = 72.dp)
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                // Fokus još nije na kanalu: uputstvo u crnom okviru.
                Text(
                    text = stringResource(R.string.live_preview_hint_tv),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (current != null) {
                Text(
                    text = stringResource(R.string.live_badge),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(LiveRed, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            val status = statusMessage(sourceMissing = false, playback = playback)
            if (current != null && status != null) {
                Text(
                    text = status.lineSequence().first(),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        if (channel != null) {
            Row(modifier = Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(url = channel.logo, name = channel.name, size = 28.dp)
                Text(
                    text = listOfNotNull(channel.number?.toString(), channel.name).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        val program = nowNext.now
        if (program != null) {
            Text(
                text = program.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = program.timeRangeLeft(now),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (program.stopMs > program.startMs) {
                TvProgressBar(
                    progress = ((now - program.startMs).toFloat() / (program.stopMs - program.startMs)).coerceIn(0f, 1f),
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                    barColor = LocalNeon.current?.progress ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            program.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        // Program za naredne sate: vreme i naziv, po jedan red.
        Column(modifier = Modifier.padding(top = 12.dp)) {
            later.forEach { next ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.07f)),
                )
                Row(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(
                        text = formatTime(next.startMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        modifier = Modifier.width(56.dp),
                    )
                    Text(
                        text = next.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Naslov nad spiskom kanala: izabrana kategorija i koliko kanala ima. */
@Composable
private fun ChannelsHeading(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = pluralStringResource(R.plurals.channel_count, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 12.dp)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/**
 * Liste omiljenih kao red kartica iznad kanala: „Omiljeni”, imenovane liste i „+” za novu. OK
 * otvara listu, dugi OK na imenovanoj listi otvara meni (preimenuj, obriši).
 */
@Composable
private fun TvFavoriteListTabs(
    lists: List<FavoriteList>,
    selected: Long,
    onSelect: (Long) -> Unit,
    onListMenu: (FavoriteList) -> Unit,
    onNew: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvTabChip(
            text = stringResource(R.string.category_favorites),
            selected = selected == ChannelGroups.FAVORITES,
            onClick = { onSelect(ChannelGroups.FAVORITES) },
        )
        lists.forEach { list ->
            TvTabChip(
                text = list.name,
                selected = selected == list.groupId,
                onClick = { onSelect(list.groupId) },
                onLongClick = { onListMenu(list) },
            )
        }
        TvTabChip(text = "+", description = stringResource(R.string.favorite_list_new), selected = false, onClick = onNew)
    }
}

/** Kartica u redu lista: u fokusu puna u akcentnoj boji, izabrana bez fokusa ima akcentni okvir. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvTabChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    description: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        color = when {
            focused -> colors.onPrimary
            selected -> colors.primary
            else -> colors.onSurface
        },
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 200.dp)
            // Neonska tema: blago svetlo oko izabrane kartice dok nije u fokusu.
            .neonHalo(LocalNeon.current, selected && !focused)
            .clip(shape)
            .background(
                when {
                    focused -> colors.primary
                    selected -> colors.primary.copy(alpha = 0.16f)
                    else -> colors.surfaceVariant.copy(alpha = 0.6f)
                },
            )
            .then(if (selected && !focused) Modifier.border(1.5.dp, colors.primary, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** Oznaka „uživo” na slici pregleda. */
private val LiveRed = Color(0xFFE5484D)
