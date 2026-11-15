package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.ui.common.LiveTvViewModel

/** Početni ekran na telefonu: kategorije aktivne liste. */
@Composable
fun MobileLiveTvScreen(
    onOpenCategory: (playlistId: Long, categoryId: Long?, title: String) -> Unit,
    onManagePlaylists: () -> Unit,
    onSearch: () -> Unit,
    onManageCategories: () -> Unit,
    onSettings: () -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val allChannelsTitle = stringResource(R.string.category_all)
    val favoritesTitle = stringResource(R.string.category_favorites)
    val recentTitle = stringResource(R.string.category_recent)
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.live_tv_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.playlistId != null) {
                    TextButton(onClick = onSearch) { Text(stringResource(R.string.action_search)) }
                }
                Box {
                    TextButton(onClick = { menuOpen = true }) { Text(stringResource(R.string.action_menu)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlists_title)) },
                            onClick = {
                                menuOpen = false
                                onManagePlaylists()
                            },
                        )
                        if (state.playlistId != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.categories_manage_title)) },
                                onClick = {
                                    menuOpen = false
                                    onManageCategories()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_title)) },
                            onClick = {
                                menuOpen = false
                                onSettings()
                            },
                        )
                    }
                }
            }

            val playlistId = state.playlistId
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

                else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    item(key = "favorites") {
                        CategoryRow(
                            name = favoritesTitle,
                            count = state.favoriteCount,
                            onClick = { onOpenCategory(playlistId, ChannelGroups.FAVORITES, favoritesTitle) },
                        )
                    }
                    if (state.recentCount > 0) {
                        item(key = "recent") {
                            CategoryRow(
                                name = recentTitle,
                                count = state.recentCount,
                                onClick = { onOpenCategory(playlistId, ChannelGroups.RECENT, recentTitle) },
                            )
                        }
                    }
                    item(key = "all") {
                        CategoryRow(
                            name = allChannelsTitle,
                            count = state.totalChannels,
                            onClick = { onOpenCategory(playlistId, null, allChannelsTitle) },
                        )
                    }
                    items(state.categories, key = { it.id }) { category ->
                        CategoryRow(
                            name = category.name,
                            count = category.channelCount,
                            onClick = { onOpenCategory(playlistId, category.id, category.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(name: String, count: Int, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}
