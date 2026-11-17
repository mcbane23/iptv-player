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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.theme.glowBackground
import com.bane23.iptvplayer.ui.common.pairing.QrCode
import com.bane23.iptvplayer.ui.common.trakt.TraktLogin
import com.bane23.iptvplayer.ui.common.trakt.TraktViewModel
import com.bane23.iptvplayer.ui.common.trakt.formatSyncTime

/**
 * Trakt nalog na TV-u. Prijava je kodom: TV prikazuje kod i QR, a korisnik ga unosi na telefonu.
 * Prvo dugme svakog koraka dobija fokus čim se korak promeni, da daljinski nikad ne ostane bez fokusa.
 */
@Composable
fun TvTraktScreen(viewModel: TraktViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val primary = remember { FocusRequester() }
    val login = state.login
    val step = when {
        state.connected -> 0
        login is TraktLogin.Waiting -> 1
        else -> 2
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.trakt_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.trakt_summary),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                state.connected -> {
                    Text(
                        text = state.username?.takeIf { it.isNotEmpty() }
                            ?.let { stringResource(R.string.trakt_connected_as, it) }
                            ?: stringResource(R.string.trakt_connected),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = state.lastSyncMs?.let { stringResource(R.string.trakt_last_sync, formatSyncTime(it)) }
                            ?: stringResource(R.string.trakt_never_synced),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.syncPaused) {
                        Text(
                            text = stringResource(R.string.trakt_sync_paused),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Dugme ostaje isto i dok traje sinhronizacija (menja se samo natpis), da fokus ne pobegne.
                        Button(onClick = viewModel::syncNow, modifier = Modifier.focusRequester(primary)) {
                            Text(stringResource(if (state.syncing) R.string.trakt_syncing else R.string.trakt_sync_now))
                        }
                        OutlinedButton(onClick = viewModel::disconnect) {
                            Text(stringResource(R.string.trakt_disconnect))
                        }
                    }
                }

                login is TraktLogin.Waiting -> {
                    Text(
                        text = stringResource(R.string.trakt_connect_steps, login.code.verificationUrl),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = login.code.userCode,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.trakt_waiting),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = viewModel::cancelLogin, modifier = Modifier.focusRequester(primary)) {
                        Text(stringResource(R.string.trakt_cancel))
                    }
                }

                else -> {
                    val starting = login is TraktLogin.Starting
                    Button(
                        onClick = { if (!starting) viewModel.connect() },
                        modifier = Modifier.focusRequester(primary),
                    ) {
                        Text(stringResource(if (starting) R.string.trakt_starting else R.string.trakt_connect))
                    }
                }
            }
            state.message?.let { message ->
                Text(text = stringResource(message), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            }
            Text(
                text = stringResource(R.string.trakt_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (login is TraktLogin.Waiting && !state.connected) {
            QrCode(content = login.code.verificationUrl, modifier = Modifier.width(260.dp))
        }
    }
    LaunchedEffect(step) { primary.tryRequestFocus() }
}
