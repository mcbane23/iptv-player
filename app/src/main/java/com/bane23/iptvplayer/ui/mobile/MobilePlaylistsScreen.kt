package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.Playlist
import com.bane23.iptvplayer.ui.common.PlaylistsViewModel
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.message
import com.bane23.iptvplayer.ui.common.accountExpiresSoon
import com.bane23.iptvplayer.ui.common.accountLine
import com.bane23.iptvplayer.ui.common.summary

@Composable
fun MobilePlaylistsScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Playlist?>(null) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SectionTopBar(title = stringResource(R.string.playlists_title), onBack = onBack) {
                ToolbarIcon(AppIcons.Add, stringResource(R.string.action_add_playlist), onAdd)
            }

            when {
                state.loading -> Unit

                state.playlists.isEmpty() -> EmptyState(
                    icon = AppIcons.Playlists,
                    text = stringResource(R.string.playlists_empty),
                    actionLabel = stringResource(R.string.action_add_playlist),
                    actionIcon = AppIcons.Add,
                    onAction = onAdd,
                )

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                ) {
                    if (state.playlists.size > 1) {
                        item(key = "hint") {
                            Text(
                                text = stringResource(R.string.playlists_select_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Spajanje ima smisla tek kad postoje bar dve liste.
                    if (state.playlists.size > 1) {
                        item(key = "merge") {
                            MergeSwitch(checked = state.merged, onChange = viewModel::setAllMerged)
                        }
                    }
                    items(state.playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            refreshing = playlist.id in state.refreshing,
                            onSelect = { viewModel.setActive(playlist.id) },
                            onRefresh = { viewModel.refresh(playlist.id) },
                            onEdit = { onEdit(playlist.id) },
                            onDelete = { pendingDelete = playlist },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message, playlist.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(playlist.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(error.message()) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

/** Jedan prekidač za sve liste: naziv, opis ispod njega i prekidač desno. */
@Composable
private fun MergeSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.playlists_merge), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.playlists_merge_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

/**
 * Lista: naziv, oznaka aktivne liste i kratki podaci. Dodir na karticu bira listu kao aktivnu;
 * Osveži, Izmeni i Obriši su u meniju iza tri tačke.
 */
@Composable
private fun PlaylistCard(
    playlist: Playlist,
    refreshing: Boolean,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(onClick = { if (!playlist.isActive) onSelect() }, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (playlist.isActive) {
                        Icon(
                            imageVector = AppIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.playlist_active),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                Text(
                    text = if (refreshing) stringResource(R.string.playlist_refreshing) else playlist.summary(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                playlist.accountLine()?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        // Nalog koji uskoro ističe je u boji teme, da se primeti.
                        color = if (playlist.accountExpiresSoon()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Box {
                    ToolbarIcon(AppIcons.More, stringResource(R.string.action_more)) { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_refresh)) },
                            leadingIcon = { Icon(AppIcons.Refresh, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRefresh()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            leadingIcon = { Icon(AppIcons.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(AppIcons.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}
