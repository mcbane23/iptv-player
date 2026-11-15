package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.pairing.SendError
import com.bane23.iptvplayer.ui.common.pairing.SendToTvViewModel

/**
 * Potvrda i slanje svih lista i podešavanja na TV. Otvara se kada korisnik na stranici TV-a
 * (posle skeniranja QR koda) izabere slanje iz aplikacije.
 */
@Composable
fun MobileSendToTvScreen(
    target: String,
    onClose: () -> Unit,
    viewModel: SendToTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(target) { viewModel.prepare(target) }
    val result = state.result

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = stringResource(R.string.transfer_title), style = MaterialTheme.typography.headlineSmall)
            when {
                state.loading -> CircularProgressIndicator()

                state.error == SendError.BAD_TARGET -> Body(stringResource(R.string.transfer_bad_target))

                result != null -> {
                    Body(stringResource(R.string.transfer_summary, result.added, result.existing, result.failed))
                    Body(stringResource(R.string.transfer_done_hint))
                }

                state.playlistCount == 0 -> {
                    Body(stringResource(R.string.transfer_nothing))
                    if (state.skipped > 0) Body(stringResource(R.string.transfer_skipped_files, state.skipped))
                }

                else -> {
                    Body(stringResource(R.string.transfer_target, state.address))
                    Body(
                        pluralStringResource(R.plurals.transfer_count, state.playlistCount, state.playlistCount),
                    )
                    if (state.skipped > 0) Body(stringResource(R.string.transfer_skipped_files, state.skipped))
                    Text(
                        text = stringResource(R.string.transfer_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.sending) {
                        CircularProgressIndicator()
                        Body(stringResource(R.string.transfer_sending))
                    }
                    val error = when (state.error) {
                        SendError.UNREACHABLE -> stringResource(R.string.transfer_unreachable)
                        SendError.REFUSED -> stringResource(R.string.transfer_refused)
                        else -> null
                    }
                    if (error != null) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val canSend = !state.loading && result == null && state.playlistCount > 0 &&
                    state.error != SendError.BAD_TARGET
                if (canSend) {
                    Button(onClick = viewModel::send, enabled = !state.sending) {
                        Text(stringResource(R.string.transfer_send))
                    }
                }
                OutlinedButton(onClick = onClose) {
                    Text(stringResource(if (canSend) R.string.action_cancel else R.string.action_close))
                }
            }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge)
}
