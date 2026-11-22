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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.domain.Playlist
import com.bane23.iptvplayer.ui.common.PlaylistsViewModel
import com.bane23.iptvplayer.ui.common.message
import com.bane23.iptvplayer.ui.common.accountExpiresSoon
import com.bane23.iptvplayer.ui.common.accountLine
import com.bane23.iptvplayer.ui.common.summary

@Composable
fun TvPlaylistsScreen(
    onAdd: () -> Unit,
    onAddFromPhone: () -> Unit,
    onEdit: (Long) -> Unit,
    onRestore: (() -> Unit)? = null,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addFocus = remember { FocusRequester() }
    // Lista kojoj je otvoreno „Izmeni”: posle povratka fokus ide na nju, ne na „Dodaj listu”.
    var editedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editedFocus = remember { FocusRequester() }
    // Pri ulasku fokus ide na aktivnu listu, a ne na „Dodaj listu”.
    val activeFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Pozadinu sa sjajem crta početni ekran, jednom za sve kartice.
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Početni fokus mora biti na nekom elementu, inače daljinski ne radi ništa.
            TvButton(onClick = onAdd, modifier = Modifier.focusRequester(addFocus)) {
                Text(stringResource(R.string.action_add_playlist))
            }
            TvOutlinedButton(onClick = onAddFromPhone, modifier = Modifier.padding(start = 12.dp)) {
                Text(stringResource(R.string.action_add_from_phone))
            }
            // Dok nema nijedne liste nudi se i vraćanje svega iz fajla izvoza.
            if (onRestore != null && !state.loading && state.playlists.isEmpty()) {
                TvOutlinedButton(onClick = onRestore, modifier = Modifier.padding(start = 12.dp)) {
                    Text(stringResource(R.string.backup_restore_from_file))
                }
            }
            TvClock(modifier = Modifier.padding(start = 24.dp))
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
                TvOutlinedButton(onClick = viewModel::dismissError) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        }

        when {
            state.loading -> Unit

            state.playlists.isEmpty() -> TvEmptyState(icon = AppIcons.Playlists, text = stringResource(R.string.playlists_empty))

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 27.dp),
            ) {
                item(key = "hint") {
                    Text(
                        text = stringResource(R.string.playlists_tv_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Spajanje ima smisla tek kad postoje bar dve liste.
                if (state.playlists.size > 1) {
                    item(key = "merge") {
                        Column {
                            TvTextRow(
                                text = stringResource(R.string.playlists_merge),
                                onClick = { viewModel.setAllMerged(!state.merged) },
                                checked = state.merged,
                            )
                            Text(
                                text = stringResource(R.string.playlists_merge_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                            )
                        }
                    }
                }
                items(state.playlists, key = { it.id }) { playlist ->
                    TvPlaylistRow(
                        playlist = playlist,
                        refreshing = playlist.id in state.refreshing,
                        onSelect = { viewModel.setActive(playlist.id) },
                        onRefresh = { viewModel.refresh(playlist.id) },
                        onEdit = {
                            editedId = playlist.id
                            onEdit(playlist.id)
                        },
                        rowModifier = (if (playlist.id == editedId) Modifier.focusRequester(editedFocus) else Modifier)
                            .then(if (playlist.isActive) Modifier.focusRequester(activeFocus) else Modifier),
                        onDelete = { viewModel.delete(playlist.id) },
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (editedId != null) {
            // Spisak stiže iz baze, pa se pokušava nekoliko kadrova zaredom.
            repeat(RETURN_FOCUS_FRAMES) {
                if (editedFocus.tryRequestFocus()) {
                    editedId = null
                    return@LaunchedEffect
                }
                withFrameNanos { }
            }
            editedId = null
        }
        repeat(RETURN_FOCUS_FRAMES) {
            if (activeFocus.tryRequestFocus()) return@LaunchedEffect
            // Bez lista nema šta da se čeka.
            if (!state.loading && state.playlists.isEmpty()) return@repeat
            withFrameNanos { }
        }
        addFocus.tryRequestFocus()
    }
    // Kad se lista obriše, njen red nestaje zajedno sa fokusom, pa se fokus vraća na dugme za dodavanje.
    var knownCount by remember { mutableStateOf(state.playlists.size) }
    LaunchedEffect(state.playlists.size) {
        if (state.playlists.size < knownCount) addFocus.tryRequestFocus()
        knownCount = state.playlists.size
    }
}

/**
 * Lista na TV-u: OK na samoj listi je bira kao aktivnu, a dugme „Opcije” otvara Osveži, Izmeni i
 * Obriši ispod nje. Brisanje se potvrđuje u istom redu, uz naziv liste.
 */
@Composable
private fun TvPlaylistRow(
    playlist: Playlist,
    refreshing: Boolean,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    rowModifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Brisanje se potvrđuje u samom redu: dijalozi na TV-u lako izgube fokus.
    var confirmDelete by remember { mutableStateOf(false) }
    val cancelFocus = remember { FocusRequester() }
    val deleteFocus = remember { FocusRequester() }
    // Dugmad se menjaju u mestu, pa fokus mora ručno da pređe na novo dugme.
    var confirmWasOpen by remember { mutableStateOf(false) }
    // Dugi OK na listi otvara iste opcije kao dugme „Opcije”, bez šetnje do njega.
    var menuOpen by remember { mutableStateOf(false) }
    if (menuOpen) {
        val actions = buildList<Pair<String, () -> Unit>> {
            add(stringResource(R.string.action_refresh) to { if (!refreshing) onRefresh() })
            add(stringResource(R.string.action_edit) to { if (!refreshing) onEdit() })
            // Brisanje se i ovde potvrđuje u samom redu liste.
            add(
                stringResource(R.string.action_delete) to {
                    if (!refreshing) {
                        expanded = true
                        confirmDelete = true
                    }
                },
            )
        }
        TvActionDialog(
            title = playlist.name,
            options = actions.map { it.first },
            onSelect = { index -> actions.getOrNull(index)?.second?.invoke() },
            onClose = { menuOpen = false },
        )
    }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) cancelFocus.tryRequestFocus() else if (confirmWasOpen) deleteFocus.tryRequestFocus()
        confirmWasOpen = confirmDelete
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvListRow(
                onClick = { if (!playlist.isActive) onSelect() },
                onLongClick = { menuOpen = true },
                modifier = rowModifier.weight(1f),
            ) { color ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (refreshing) stringResource(R.string.playlist_refreshing) else playlist.summary(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    playlist.accountLine()?.let { line ->
                        // Nalog koji uskoro ističe je u boji teme; na fokusiranom redu ostaje boja teksta.
                        val warn = playlist.accountExpiresSoon() && color == MaterialTheme.colorScheme.onSurface
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (warn) MaterialTheme.colorScheme.primary else color.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (playlist.isActive) {
                    Text(
                        text = stringResource(R.string.playlist_active),
                        style = MaterialTheme.typography.labelLarge,
                        // Na fokusiranom redu (akcentna podloga) oznaka je u boji teksta, da se vidi.
                        color = if (color == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.primary else color,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            // Dugme ostaje isto i kad su opcije otvorene, da fokus ne nestane sa njega.
            TvOutlinedButton(onClick = {
                expanded = !expanded
                if (!expanded) confirmDelete = false
            }) {
                Text(stringResource(R.string.action_options))
            }
        }
        if (expanded) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (confirmDelete) {
                    Text(
                        text = stringResource(R.string.delete_message, playlist.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TvButton(onClick = onDelete) { Text(stringResource(R.string.action_confirm_delete)) }
                    TvOutlinedButton(onClick = { confirmDelete = false }, modifier = Modifier.focusRequester(cancelFocus)) {
                        Text(stringResource(R.string.action_cancel))
                    }
                } else {
                    // Tokom osvežavanja dugmad samo ne rade ništa: isključeno dugme bi izgubilo fokus.
                    TvOutlinedButton(onClick = { if (!refreshing) onRefresh() }) {
                        Text(stringResource(R.string.action_refresh))
                    }
                    TvOutlinedButton(onClick = { if (!refreshing) onEdit() }) {
                        Text(stringResource(R.string.action_edit))
                    }
                    TvOutlinedButton(
                        onClick = { if (!refreshing) confirmDelete = true },
                        modifier = Modifier.focusRequester(deleteFocus),
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
            }
        }
    }
}

/** Koliko kadrova se čeka red liste na koju se fokus vraća posle izmene. */
private const val RETURN_FOCUS_FRAMES = 30
