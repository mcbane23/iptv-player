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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.ui.common.ChannelListViewModel
import com.bane23.iptvplayer.ui.common.LiveTvViewModel
import com.bane23.iptvplayer.ui.common.PlaylistsViewModel
import com.bane23.iptvplayer.ui.common.icons.AppIcons

/** Oznaka za „Svi kanali” među čipovima (pravi ID-jevi kategorija su pozitivni, posebne grupe negativne). */
private const val ALL_CHANNELS = 0L

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
    viewModel: LiveTvViewModel = hiltViewModel(),
    channelsViewModel: ChannelListViewModel = hiltViewModel(),
    playlistsViewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlists by playlistsViewModel.state.collectAsStateWithLifecycle()
    val playlistId = state.playlistId
    // Izabrani čip; null dok korisnik ništa ne izabere (tada omiljeni ako ih ima, inače svi kanali).
    var chosen by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val groups = buildList {
        add(ChannelGroups.FAVORITES)
        if (state.recentCount > 0) add(ChannelGroups.RECENT)
        add(ALL_CHANNELS)
        state.categories.forEach { add(it.id) }
    }
    val selected = chosen?.takeIf { it in groups }
        ?: if (state.favoriteCount > 0) ChannelGroups.FAVORITES else ALL_CHANNELS
    val categoryId = selected.takeIf { it != ALL_CHANNELS }

    LaunchedEffect(playlistId, categoryId) {
        if (playlistId != null) channelsViewModel.select(playlistId, categoryId)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SectionTopBar(title = stringResource(R.string.nav_live)) {
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
                    ToolbarIcon(AppIcons.Search, stringResource(R.string.action_search), onSearch)
                }
            }

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
                    val favoritesLabel = stringResource(R.string.category_favorites)
                    val recentLabel = stringResource(R.string.category_recent)
                    val allLabel = stringResource(R.string.category_all)
                    val pickerGroups = buildList {
                        add(PickerGroup(ChannelGroups.FAVORITES.toString(), favoritesLabel, AppIcons.Star))
                        if (state.recentCount > 0) add(PickerGroup(ChannelGroups.RECENT.toString(), recentLabel))
                        add(PickerGroup(ALL_CHANNELS.toString(), allLabel))
                        state.categories.forEach { add(PickerGroup(it.id.toString(), it.name)) }
                    }
                    QuickGroupChips(
                        selected = selected,
                        showRecent = state.recentCount > 0,
                        categoryName = state.categories.firstOrNull { it.id == selected }?.name,
                        onSelect = { chosen = it },
                        onMore = { pickerOpen = true },
                    )
                    if (pickerOpen) {
                        CategoryPickerDialog(
                            title = stringResource(R.string.nav_live),
                            groups = pickerGroups,
                            selectedKey = selected.toString(),
                            onPick = { key ->
                                pickerOpen = false
                                chosen = key.toLong()
                            },
                            onDismiss = { pickerOpen = false },
                        )
                    }

                    val channels = channelsViewModel.channels.collectAsLazyPagingItems()
                    // Nova kategorija kreće od vrha liste; posle povratka iz plejera ostaje gde je bila.
                    val listState = rememberLazyGridState()
                    // U landscape-u kanali stoje u dve kolone, da se širina ekrana iskoristi.
                    val columns = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 2 else 1
                    var listShows by rememberSaveable { mutableStateOf(selected) }
                    LaunchedEffect(selected) {
                        if (listShows != selected) {
                            listShows = selected
                            listState.scrollToItem(0)
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
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
                            columns = GridCells.Fixed(columns),
                            state = listState,
                            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                        ) {
                            items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                                MobileChannelRow(
                                    channel = channels[index],
                                    onClick = { channelId -> onPlay(channelId, categoryId) },
                                    onToggleFavorite = channelsViewModel::toggleFavorite,
                                    onGuide = { channel -> onGuide(channel.playlistId, categoryId) },
                                )
                            }
                        }
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
