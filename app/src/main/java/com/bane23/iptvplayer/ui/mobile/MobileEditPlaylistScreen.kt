package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.PlaylistType
import com.bane23.iptvplayer.ui.common.EditPlaylistViewModel
import com.bane23.iptvplayer.ui.common.PlaylistFormFields
import com.bane23.iptvplayer.ui.common.label
import com.bane23.iptvplayer.ui.common.message

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MobileEditPlaylistScreen(
    playlistId: Long?,
    onClose: () -> Unit,
    viewModel: EditPlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(playlistId) { viewModel.load(playlistId) }
    LaunchedEffect(state.saved) { if (state.saved) onClose() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose, enabled = !state.saving) {
                    Text(stringResource(R.string.action_back))
                }
                Text(
                    text = stringResource(
                        if (playlistId == null) R.string.edit_title_add else R.string.edit_title_edit,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // Tip liste se bira samo pri dodavanju; kod izmene ostaje isti.
            if (playlistId == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaylistType.entries.forEach { type ->
                        FilterChip(
                            selected = state.type == type,
                            onClick = { viewModel.setType(type) },
                            enabled = !state.saving,
                            label = { Text(type.label()) },
                        )
                    }
                }
            }

            PlaylistFormFields(
                state = state,
                viewModel = viewModel,
                pickFileButton = { label, onClick ->
                    OutlinedButton(onClick = onClick, enabled = !state.saving) { Text(label) }
                },
            )

            state.error?.let { error ->
                Text(
                    text = error.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (state.saving) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.edit_saving))
                }
            } else {
                Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}
