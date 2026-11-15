package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.Playlist
import com.bane23.iptvplayer.ui.common.PlaylistsViewModel
import com.bane23.iptvplayer.ui.common.message
import com.bane23.iptvplayer.ui.common.summary

@Composable
fun TvPlaylistsScreen(
    onAdd: () -> Unit,
    onAddFromPhone: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                text = stringResource(R.string.playlists_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            // Početni fokus mora biti na nekom elementu, inače daljinski ne radi ništa.
            Button(onClick = onAdd, modifier = Modifier.focusRequester(addFocus)) {
                Text(stringResource(R.string.action_add_playlist))
            }
            OutlinedButton(onClick = onAddFromPhone, modifier = Modifier.padding(start = 12.dp)) {
                Text(stringResource(R.string.action_add_from_phone))
            }
        }

        state.error?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = error.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = viewModel::dismissError) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        }

        when {
            state.loading -> Unit

            state.playlists.isEmpty() -> Text(
                text = stringResource(R.string.playlists_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 27.dp),
            ) {
                items(state.playlists, key = { it.id }) { playlist ->
                    TvPlaylistRow(
                        playlist = playlist,
                        refreshing = playlist.id in state.refreshing,
                        onSelect = { viewModel.setActive(playlist.id) },
                        onRefresh = { viewModel.refresh(playlist.id) },
                        onEdit = { onEdit(playlist.id) },
                        onDelete = { viewModel.delete(playlist.id) },
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) { addFocus.tryRequestFocus() }
    // Kad se lista obriše, njen red nestaje zajedno sa fokusom, pa se fokus vraća na dugme za dodavanje.
    var knownCount by remember { mutableStateOf(state.playlists.size) }
    LaunchedEffect(state.playlists.size) {
        if (state.playlists.size < knownCount) addFocus.tryRequestFocus()
        knownCount = state.playlists.size
    }
}

@Composable
private fun TvPlaylistRow(
    playlist: Playlist,
    refreshing: Boolean,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // Brisanje se potvrđuje u samom redu: dijalozi na TV-u lako izgube fokus.
    var confirmDelete by remember { mutableStateOf(false) }
    val cancelFocus = remember { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }
    val deleteFocus = remember { FocusRequester() }
    // Dugmad se menjaju u mestu, pa fokus mora ručno da pređe na novo dugme.
    var confirmWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) cancelFocus.tryRequestFocus() else if (confirmWasOpen) deleteFocus.tryRequestFocus()
        confirmWasOpen = confirmDelete
    }
    var wasActive by remember { mutableStateOf(playlist.isActive) }
    LaunchedEffect(playlist.isActive) {
        // Dugme „Izaberi” nestaje kad lista postane aktivna.
        if (playlist.isActive && !wasActive) refreshFocus.tryRequestFocus()
        wasActive = playlist.isActive
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (playlist.isActive) {
                Text(
                    text = stringResource(R.string.playlist_active),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = if (refreshing) stringResource(R.string.playlist_refreshing) else playlist.summary(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (confirmDelete) {
                Button(onClick = onDelete) { Text(stringResource(R.string.action_confirm_delete)) }
                OutlinedButton(onClick = { confirmDelete = false }, modifier = Modifier.focusRequester(cancelFocus)) {
                    Text(stringResource(R.string.action_cancel))
                }
            } else {
                if (!playlist.isActive) {
                    OutlinedButton(onClick = onSelect) { Text(stringResource(R.string.action_select)) }
                }
                // Tokom osvežavanja dugmad samo ne rade ništa: isključeno dugme bi izgubilo fokus.
                OutlinedButton(onClick = { if (!refreshing) onRefresh() }, modifier = Modifier.focusRequester(refreshFocus)) {
                    Text(stringResource(R.string.action_refresh))
                }
                OutlinedButton(onClick = { if (!refreshing) onEdit() }) {
                    Text(stringResource(R.string.action_edit))
                }
                OutlinedButton(
                    onClick = { if (!refreshing) confirmDelete = true },
                    modifier = Modifier.focusRequester(deleteFocus),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }
}
