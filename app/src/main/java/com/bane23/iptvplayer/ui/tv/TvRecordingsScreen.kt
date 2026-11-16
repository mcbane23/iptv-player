package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.theme.glowBackground
import com.bane23.iptvplayer.data.db.RecordingEntity
import com.bane23.iptvplayer.ui.common.recording.RecordingsViewModel
import com.bane23.iptvplayer.ui.common.recording.details
import com.bane23.iptvplayer.ui.common.recording.label
import com.bane23.iptvplayer.ui.common.recording.playable

/**
 * Snimci na TV-u: levo spisak, desno radnje za izabrani snimak (pusti, zaustavi, obriši).
 * OK na snimku otvara radnje; brisanje traži drugi pritisak.
 */
@Composable
fun TvRecordingsScreen(onPlay: (Long) -> Unit, viewModel: RecordingsViewModel = hiltViewModel()) {
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val list = recordings.orEmpty()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshLocations() }
    val listFocus = remember { FocusRequester() }
    val actionFocus = remember { FocusRequester() }
    // Snimak čije su radnje otvorene, ili null.
    var openId by remember { mutableStateOf<Long?>(null) }
    // Snimak na kome je fokus u spisku; na njega se fokus vraća kad se radnje zatvore.
    var focusedId by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val open = list.firstOrNull { it.id == openId }
    val anchorId = focusedId?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Text(
            text = stringResource(R.string.recordings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = storage?.label().orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (storage?.low == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        if (recordings != null && list.isEmpty()) {
            Text(
                text = stringResource(R.string.recordings_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            LazyColumn(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Izbor mesta ima smisla samo kad je priključena SD kartica ili USB memorija. OK menja mesto.
                if (locations.size > 1) {
                    item(key = "location") {
                        val selected = locations.indexOfFirst { it.selected }.coerceAtLeast(0)
                        TvTextRow(
                            text = stringResource(R.string.recording_location),
                            trailing = locations[selected].label(selected),
                            onClick = viewModel::nextLocation,
                            onFocused = { openId = null },
                            // Bez snimaka je ovo jedini red, pa mora da primi fokus.
                            modifier = if (anchorId == null) Modifier.focusRequester(listFocus) else Modifier,
                        )
                    }
                }
                items(list, key = { it.id }) { recording ->
                    TvListRow(
                        onClick = {
                            confirmDelete = false
                            openId = recording.id
                        },
                        selected = openId == recording.id,
                        onFocused = {
                            focusedId = recording.id
                            if (openId != recording.id) openId = null
                        },
                        modifier = if (recording.id == anchorId) Modifier.focusRequester(listFocus) else Modifier,
                    ) { color ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = recording.title,
                                color = color,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = recording.details(),
                                color = color.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.weight(0.35f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (open != null) {
                    val recordingNow = open.status == RecordingEntity.STATUS_RECORDING
                    // Prva radnja nosi fokus kad se radnje otvore; koja je prva zavisi od snimka.
                    val firstAction = when {
                        open.playable -> 0
                        recordingNow -> 1
                        else -> 2
                    }
                    fun actionModifier(index: Int): Modifier =
                        if (index == firstAction) Modifier.focusRequester(actionFocus) else Modifier
                    if (open.playable) {
                        TvTextRow(
                            text = stringResource(R.string.recording_play),
                            onClick = { onPlay(open.id) },
                            modifier = actionModifier(0),
                        )
                    }
                    if (recordingNow) {
                        TvTextRow(
                            text = stringResource(R.string.recording_stop),
                            onClick = {
                                // Fokus se vraća na spisak pre nego što ova radnja nestane.
                                listFocus.tryRequestFocus()
                                openId = null
                                viewModel.stop(open.id)
                            },
                            modifier = actionModifier(1),
                        )
                    }
                    TvTextRow(
                        text = stringResource(if (confirmDelete) R.string.recording_delete_confirm else R.string.action_delete),
                        onClick = {
                            if (confirmDelete) {
                                // Snimak nestaje iz spiska: fokus prvo prelazi na susedni red.
                                focusedId = list.firstOrNull { it.id != open.id }?.id
                                openId = null
                                confirmDelete = false
                                viewModel.delete(open.id)
                            } else {
                                confirmDelete = true
                            }
                        },
                        modifier = actionModifier(2),
                    )
                    LaunchedEffect(open.id) { actionFocus.tryRequestFocus() }
                }
            }
        }
    }
    // Fokus na spisak pri otvaranju ekrana i kad snimak sa fokusom nestane (brisanje).
    LaunchedEffect(anchorId, openId == null, locations.size > 1) {
        if (openId == null) listFocus.tryRequestFocus()
    }
}
