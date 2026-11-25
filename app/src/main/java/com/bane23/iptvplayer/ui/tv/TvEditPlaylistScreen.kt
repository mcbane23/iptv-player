package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.bane23.iptvplayer.ui.tv.text.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.theme.glowBackground
import com.bane23.iptvplayer.domain.PlaylistType
import com.bane23.iptvplayer.ui.common.EditPlaylistViewModel
import com.bane23.iptvplayer.ui.common.PlaylistFormFields
import com.bane23.iptvplayer.ui.common.label
import com.bane23.iptvplayer.ui.common.message

@Composable
fun TvEditPlaylistScreen(
    playlistId: Long?,
    onClose: () -> Unit,
    viewModel: EditPlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(playlistId) { viewModel.load(playlistId) }
    LaunchedEffect(state.saved) { if (state.saved) onClose() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(if (playlistId == null) R.string.edit_title_add else R.string.edit_title_edit),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        // Tip liste se bira samo pri dodavanju; kod izmene ostaje isti.
        if (playlistId == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlaylistType.entries.forEachIndexed { index, type ->
                    val modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                    // Isto dugme za izabran i neizabran tip: zamena dugmeta bi izgubila fokus.
                    TvOutlinedButton(onClick = { viewModel.setType(type) }, modifier = modifier) {
                        Text(if (state.type == type) "● ${type.label()}" else type.label())
                    }
                }
            }
        }

        PlaylistFormFields(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth(0.6f),
            plainKeyboard = true,
            pickFileButton = { label, onClick ->
                TvOutlinedButton(onClick = onClick, enabled = !state.saving) { Text(label) }
            },
        )

        state.error?.let { error ->
            Text(
                text = error.message(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        if (state.saving) {
            Text(
                text = stringResource(R.string.edit_saving),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val saveModifier = if (playlistId != null) Modifier.focusRequester(firstFocus) else Modifier
            // Dok traje čuvanje dugmad samo ne rade ništa: isključeno dugme bi izgubilo fokus.
            TvButton(onClick = viewModel::save, modifier = saveModifier) {
                Text(stringResource(R.string.action_save))
            }
            TvOutlinedButton(onClick = { if (!state.saving) onClose() }) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }

    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
}
