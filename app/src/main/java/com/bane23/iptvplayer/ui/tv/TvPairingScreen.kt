package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.bane23.iptvplayer.ui.common.pairing.PairingViewModel
import com.bane23.iptvplayer.ui.common.pairing.QrCode

/**
 * Dodavanje liste sa telefona: TV prikazuje QR kod, telefon u istoj mreži ga skenira kamerom i
 * u pregledaču unosi podatke liste. Dok je ovaj ekran otvoren TV čeka podatke.
 */
@Composable
fun TvPairingScreen(
    onClose: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }
    val url = state.url

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (url != null) QrCode(content = url, modifier = Modifier.width(300.dp))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(R.string.pairing_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            when {
                state.starting -> Note(stringResource(R.string.vod_loading))

                url == null -> Note(stringResource(R.string.pairing_unavailable))

                else -> {
                    Note(stringResource(R.string.pairing_step_scan))
                    Note(stringResource(R.string.pairing_step_fill))
                    Note(stringResource(R.string.pairing_step_app))
                    Text(
                        text = stringResource(R.string.pairing_manual_address, url),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val transfer = state.transferResult
            val status = when {
                state.working && state.transferTotal > 0 ->
                    stringResource(R.string.transfer_tv_working, state.transferStep.coerceAtLeast(1), state.transferTotal)

                state.working -> stringResource(R.string.pairing_working)

                transfer != null ->
                    stringResource(R.string.transfer_summary, transfer.added, transfer.existing, transfer.failed)

                state.addedName != null -> stringResource(R.string.pairing_added, state.addedName.orEmpty())

                else -> null
            }
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                // Početni fokus mora biti na nekom elementu, inače daljinski ne radi ništa.
                Button(onClick = onClose, modifier = Modifier.focusRequester(firstFocus)) {
                    Text(stringResource(if (state.addedName != null || transfer != null) R.string.pairing_finish else R.string.action_cancel))
                }
                if (!state.starting && url == null) {
                    OutlinedButton(onClick = viewModel::start) { Text(stringResource(R.string.player_retry)) }
                }
            }
        }
    }
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}
