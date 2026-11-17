package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.bane23.iptvplayer.ui.common.LiveTvUiState
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.ui.common.ChannelListViewModel
import com.bane23.iptvplayer.ui.common.LiveTvViewModel
import com.bane23.iptvplayer.ui.common.PlaylistsViewModel
import com.bane23.iptvplayer.ui.common.icons.AppIcons

/** Oznaka za „Svi kanali” među čipovima (pravi ID-jevi kategorija su pozitivni, posebne grupe negativne). */
private const val ALL_CHANNELS = ChannelGroups.ALL

/**
 * Sekcija Uživo na telefonu: kategorije kao čipovi na vrhu, ispod kanali izabrane kategorije.
 * U zaglavlju je izbor aktivne liste, vodič i pretraga kanala.
 */
@Composable
fun MobileLiveTvScreen(
    onPlay: (channelId: Long, categoryId: Long?) -> Unit,
    onManagePlaylists: () -> Unit,
    onSearch: () -> Unit,
    onGuide: (playlistId: Long, categoryId: Long?) -> Unit,
    onMultiView: () -> Unit,
    onCategories: () -> Unit = {},
    viewModel: LiveTvViewModel = hiltViewModel(),
    channelsViewModel: ChannelListViewModel = hiltViewModel(),
    playlistsViewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlists by playlistsViewModel.state.collectAsStateWithLifecycle()
    val playlistId = state.playlistId
    // Izabrani čip; null dok korisnik ništa ne izabere (tada grupa od prošlog puta, inače omiljeni
    // ako ih ima, pa svi kanali).
    var chosen by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val groups = buildList {
        add(ChannelGroups.FAVORITES)
        if (state.recentCount > 0) add(ChannelGroups.RECENT)
        add(ALL_CHANNELS)
        state.categories.forEach { add(it.id) }
    }
    val selected = chosen?.takeIf { it in groups }
        ?: state.lastCategoryId?.takeIf { it in groups }
        ?: if (state.favoriteCount > 0) ChannelGroups.FAVORITES else ALL_CHANNELS
    val choose: (Long) -> Unit = { group ->
        chosen = group
        viewModel.rememberCategory(group)
    }
    val categoryId = selected.takeIf { it != ALL_CHANNELS }

    LaunchedEffect(playlistId, categoryId) {
        if (playlistId != null) channelsViewModel.select(playlistId, categoryId)
    }

    // Providno: pozadinu sa sjajem crta početni ekran.
    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            // Izbor liste, vodič, više kanala i pretraga. Položen telefon ih nosi u naslovu nad kanalima.
            val toolbar: @Composable RowScope.(Boolean) -> Unit = { withSearch ->
                val active = playlists.playlists.firstOrNull { it.id == playlistId }
                if (active != null) {
                    PlaylistSwitcher(
                        activeName = active.name,
                        names = playlists.playlists.map { it.id to it.name },
                        onSelect = { id ->
                            chosen = null
                            playlistsViewModel.setActive(id)
                        },
                    )
                }
                if (playlistId != null) {
                    ToolbarIcon(AppIcons.Guide, stringResource(R.string.guide_title)) { onGuide(playlistId, categoryId) }
                    ToolbarIcon(AppIcons.MultiView, stringResource(R.string.multiview_title), onMultiView)
                    if (withSearch) ToolbarIcon(AppIcons.Search, stringResource(R.string.action_search), onSearch)
                }
            }
            // Položen telefon sa učitanom listom nema zaglavlje: visina ide kanalima, kao na TV-u.
            val twoPane = landscape && !state.loading && playlistId != null
            if (!twoPane) SectionTopBar(title = stringResource(R.string.nav_live)) { toolbar(true) }

            when {
                state.loading -> MobileChannelSkeletonList()

                playlistId == null -> EmptyState(
                    icon = AppIcons.Playlists,
                    text = stringResource(R.string.playlists_empty),
                    actionLabel = stringResource(R.string.action_add_playlist),
                    actionIcon = AppIcons.Add,
                    onAction = onManagePlaylists,
                )

                else -> {
                    val channels = channelsViewModel.channels.collectAsLazyPagingItems()
                    // Nova kategorija kreće od vrha liste; posle povratka iz plejera ostaje gde je bila.
                    val listState = rememberLazyGridState()
                    var listShows by rememberSaveable { mutableStateOf(selected) }
                    LaunchedEffect(selected) {
                        if (listShows != selected) {
                            listShows = selected
                            listState.scrollToItem(0)
                        }
                    }
                    // Naslov nad kanalima: izabrana grupa i koliko kanala ima, kao na TV-u.
                    val headingTitle = when (selected) {
                        ChannelGroups.FAVORITES -> stringResource(R.string.category_favorites)
                        ChannelGroups.RECENT -> stringResource(R.string.category_recent)
                        ALL_CHANNELS -> stringResource(R.string.category_all)
                        else -> state.categories.firstOrNull { it.id == selected }?.name.orEmpty()
                    }
                    val headingCount = when (selected) {
                        ChannelGroups.FAVORITES -> state.favoriteCount
                        ChannelGroups.RECENT -> state.recentCount
                        ALL_CHANNELS -> state.totalChannels
                        else -> state.categories.firstOrNull { it.id == selected }?.channelCount ?: 0
                    }
                    // Kanali kao pločice u dve kolone (logo levo), i uspravno i položeno.
                    val channelGrid: @Composable (Modifier) -> Unit = { gridModifier ->
                        Box(modifier = gridModifier) {
                            if (channels.itemCount == 0 && channels.loadState.refresh is LoadState.Loading) {
                                MobileChannelSkeletonList()
                            }
                            if (channels.itemCount == 0 && channels.loadState.refresh is LoadState.NotLoading) {
                                val favorites = selected == ChannelGroups.FAVORITES
                                EmptyState(
                                    icon = if (favorites) AppIcons.StarOutline else AppIcons.LiveTv,
                                    text = stringResource(if (favorites) R.string.favorites_empty else R.string.channels_empty),
                                )
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                state = listState,
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                                    MobileChannelTile(
                                        channel = channels[index],
                                        onClick = { channelId -> onPlay(channelId, categoryId) },
                                        onToggleFavorite = channelsViewModel::toggleFavorite,
                                        onGuide = { channel -> onGuide(channel.playlistId, categoryId) },
                                    )
                                }
                            }
                        }
                    }

                    if (landscape) {
                        // Položen telefon: raspored kao na TV-u, grupe levo, kanali desno.
                        Row(modifier = Modifier.weight(1f)) {
                            GroupPanel(
                                state = state,
                                selected = selected,
                                onSelect = choose,
                                onSearch = onSearch,
                                onHide = viewModel::hideCategory,
                                onMoveToTop = viewModel::moveCategoryToTop,
                                onManage = onCategories,
                                modifier = Modifier
                                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                                    .width(210.dp)
                                    .fillMaxHeight(),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                ChannelsHeading(title = headingTitle, count = headingCount) { toolbar(false) }
                                channelGrid(Modifier.weight(1f))
                            }
                        }
                    } else {
                        // Favoriti, Nedavno i Svi imaju svoje čipove, pa su u spisku samo prave kategorije.
                        val pickerGroups = state.categories.map { PickerGroup(it.id.toString(), it.name, count = it.channelCount) }
                        QuickGroupChips(
                            selected = selected,
                            showRecent = state.recentCount > 0,
                            categoryName = state.categories.firstOrNull { it.id == selected }?.name,
                            onSelect = choose,
                            onMore = { pickerOpen = true },
                        )
                        if (pickerOpen) {
                            CategoryPickerDialog(
                                title = stringResource(R.string.nav_live),
                                groups = pickerGroups,
                                selectedKey = selected.toString(),
                                onPick = { key ->
                                    pickerOpen = false
                                    choose(key.toLong())
                                },
                                onDismiss = { pickerOpen = false },
                                actions = CategoryActions(
                                    onHide = { key -> viewModel.hideCategory(key.toLong()) },
                                    onMoveToTop = { key -> viewModel.moveCategoryToTop(key.toLong()) },
                                    onManage = {
                                        pickerOpen = false
                                        onCategories()
                                    },
                                ),
                            )
                        }
                        ChannelsHeading(title = headingTitle, count = headingCount, modifier = Modifier.padding(top = 8.dp))
                        channelGrid(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Naziv aktivne liste u zaglavlju; ako lista ima više, dodir otvara izbor druge liste. */
@Composable
private fun PlaylistSwitcher(activeName: String, names: List<Pair<Long, String>>, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { if (names.size > 1) open = true },
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text(
                text = activeName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            if (names.size > 1) {
                Icon(AppIcons.ArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            names.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        open = false
                        onSelect(id)
                    },
                )
            }
        }
    }
}

/**
 * Red čipova iznad liste kanala: Favoriti, Nedavno, Svi i, na kraju, čip koji otvara spisak svih
 * kategorija. Kad je izabrana neka kategorija, taj poslednji čip nosi njen naziv i označen je.
 * Najčešći izbori su tako jedan dodir, a duga lista kategorija ostaje u spisku.
 */
@Composable
private fun QuickGroupChips(
    selected: Long,
    showRecent: Boolean,
    categoryName: String?,
    onSelect: (Long) -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupChip(
            label = stringResource(R.string.category_favorites),
            selected = selected == ChannelGroups.FAVORITES,
            onClick = { onSelect(ChannelGroups.FAVORITES) },
            leading = AppIcons.Star,
        )
        if (showRecent) {
            GroupChip(
                label = stringResource(R.string.category_chip_recent),
                selected = selected == ChannelGroups.RECENT,
                onClick = { onSelect(ChannelGroups.RECENT) },
            )
        }
        GroupChip(
            label = stringResource(R.string.category_chip_all),
            selected = selected == ALL_CHANNELS,
            onClick = { onSelect(ALL_CHANNELS) },
        )
        GroupChip(
            label = categoryName ?: stringResource(R.string.category_chip_more),
            selected = categoryName != null,
            onClick = onMore,
            trailing = AppIcons.ArrowDown,
        )
    }
}

@Composable
internal fun GroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
        },
        leadingIcon = leading?.let { icon -> { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        trailingIcon = trailing?.let { icon -> { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) } },
    )
}

/** Naslov nad kanalima: izabrana grupa i broj kanala; desno, po želji, dugmad ([actions]). */
@Composable
private fun ChannelsHeading(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
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
                .padding(start = 10.dp, end = 12.dp)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        actions()
    }
}

/**
 * Spisak grupa uz levu ivicu položenog telefona, kao na TV-u: pretraga, omiljeni, nedavno, svi
 * kanali, pa kategorije. Dugi pritisak na kategoriju otvara meni (sakrij, na vrh, uredi).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupPanel(
    state: LiveTvUiState,
    selected: Long,
    onSelect: (Long) -> Unit,
    onSearch: () -> Unit,
    onHide: (Long) -> Unit,
    onMoveToTop: (Long) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    LazyColumn(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .padding(6.dp),
    ) {
        item(key = "search") {
            PanelRow(text = stringResource(R.string.search_channels), icon = AppIcons.Search, onClick = onSearch)
            PanelDivider()
        }
        item(key = ChannelGroups.FAVORITES) {
            PanelRow(
                text = stringResource(R.string.category_favorites),
                icon = AppIcons.Star,
                count = state.favoriteCount,
                selected = selected == ChannelGroups.FAVORITES,
                onClick = { onSelect(ChannelGroups.FAVORITES) },
            )
        }
        if (state.recentCount > 0) {
            item(key = ChannelGroups.RECENT) {
                PanelRow(
                    text = stringResource(R.string.category_recent),
                    icon = AppIcons.History,
                    count = state.recentCount,
                    selected = selected == ChannelGroups.RECENT,
                    onClick = { onSelect(ChannelGroups.RECENT) },
                )
            }
        }
        item(key = ALL_CHANNELS) {
            PanelRow(
                text = stringResource(R.string.category_all),
                icon = AppIcons.LiveTv,
                count = state.totalChannels,
                selected = selected == ALL_CHANNELS,
                onClick = { onSelect(ALL_CHANNELS) },
            )
        }
        if (state.categories.isNotEmpty()) {
            item(key = "categories-label") {
                PanelDivider()
                Text(
                    text = stringResource(R.string.categories_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 2.dp),
                )
            }
        }
        items(state.categories, key = { it.id }) { category ->
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                PanelRow(
                    text = category.name,
                    count = category.channelCount,
                    selected = selected == category.id,
                    onClick = { onSelect(category.id) },
                    onLongClick = { menuOpen = true },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.category_hide)) },
                        onClick = {
                            menuOpen = false
                            onHide(category.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.category_move_top)) },
                        onClick = {
                            menuOpen = false
                            onMoveToTop(category.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.categories_edit)) },
                        onClick = {
                            menuOpen = false
                            onManage()
                        },
                    )
                }
            }
        }
    }
}

/** Red u spisku grupa: ikonica (po želji), naziv i broj kanala. Izabrani red je u akcentnoj boji. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanelRow(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    count: Int? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(18.dp),
            )
        }
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(
                text = count.toString(),
                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun PanelDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.08f)),
    )
}
