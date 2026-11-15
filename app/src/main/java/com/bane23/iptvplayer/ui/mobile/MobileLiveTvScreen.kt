package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
    viewModel: LiveTvViewModel = hiltViewModel(),
    channelsViewModel: ChannelListViewModel = hiltViewModel(),
    playlistsViewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlists by playlistsViewModel.state.collectAsStateWithLifecycle()
    val playlistId = state.playlistId
    // Izabrani čip; null dok korisnik ništa ne izabere (tada omiljeni ako ih ima, inače svi kanali).
    var chosen by rememberSaveable { mutableStateOf<Long?>(null) }
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
                    ToolbarIcon(AppIcons.Search, stringResource(R.string.action_search), onSearch)
                }
            }

            when {
                state.loading -> Unit

                playlistId == null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.playlists_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onManagePlaylists) {
                        Text(stringResource(R.string.action_add_playlist))
                    }
                }

                else -> {
                    val chipState = rememberLazyListState()
                    LazyRow(
                        state = chipState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = ChannelGroups.FAVORITES) {
                            GroupChip(
                                label = stringResource(R.string.category_favorites),
                                selected = selected == ChannelGroups.FAVORITES,
                                onClick = { chosen = ChannelGroups.FAVORITES },
                                leadingStar = true,
                            )
                        }
                        if (state.recentCount > 0) {
                            item(key = ChannelGroups.RECENT) {
                                GroupChip(
                                    label = stringResource(R.string.category_recent),
                                    selected = selected == ChannelGroups.RECENT,
                                    onClick = { chosen = ChannelGroups.RECENT },
                                )
                            }
                        }
                        item(key = ALL_CHANNELS) {
                            GroupChip(
                                label = stringResource(R.string.category_all),
                                selected = selected == ALL_CHANNELS,
                                onClick = { chosen = ALL_CHANNELS },
                            )
                        }
                        items(state.categories, key = { it.id }) { category ->
                            GroupChip(
                                label = category.name,
                                selected = selected == category.id,
                                onClick = { chosen = category.id },
                            )
                        }
                    }

                    val channels = channelsViewModel.channels.collectAsLazyPagingItems()
                    // Nova kategorija kreće od vrha liste; posle povratka iz plejera ostaje gde je bila.
                    val listState = rememberLazyListState()
                    var listShows by rememberSaveable { mutableStateOf(selected) }
                    LaunchedEffect(selected) {
                        if (listShows != selected) {
                            listShows = selected
                            listState.scrollToItem(0)
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (channels.itemCount == 0 && channels.loadState.refresh is LoadState.NotLoading) {
                            Text(
                                text = stringResource(
                                    if (selected == ChannelGroups.FAVORITES) R.string.favorites_empty else R.string.channels_empty,
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                        LazyColumn(
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

/** Čip kategorije. Naziv se skraćuje ako je predugačak, da čipovi ostanu pregledni. */
@Composable
private fun GroupChip(label: String, selected: Boolean, onClick: () -> Unit, leadingStar: Boolean = false) {
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
        leadingIcon = if (leadingStar) {
            { Icon(AppIcons.Star, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
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
