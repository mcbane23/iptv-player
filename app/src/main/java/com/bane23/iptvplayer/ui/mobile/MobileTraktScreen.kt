package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.pairing.QrCode
import com.bane23.iptvplayer.ui.common.trakt.TraktLogin
import com.bane23.iptvplayer.ui.common.trakt.TraktViewModel
import com.bane23.iptvplayer.ui.common.trakt.formatSyncTime

/** Trakt nalog na telefonu: prijava kodom, sinhronizacija i odjava. */
@Composable
fun MobileTraktScreen(onBack: () -> Unit, viewModel: TraktViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTopBar(title = stringResource(R.string.trakt_title), onBack = onBack)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = stringResource(R.string.trakt_summary), style = MaterialTheme.typography.bodyMedium)
                val login = state.login
                when {
                    state.connected -> {
                        Text(
                            text = state.username?.takeIf { it.isNotEmpty() }
                                ?.let { stringResource(R.string.trakt_connected_as, it) }
                                ?: stringResource(R.string.trakt_connected),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = state.lastSyncMs?.let { stringResource(R.string.trakt_last_sync, formatSyncTime(it)) }
                                ?: stringResource(R.string.trakt_never_synced),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = viewModel::syncNow, enabled = !state.syncing, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (state.syncing) R.string.trakt_syncing else R.string.trakt_sync_now))
                        }
                        OutlinedButton(onClick = viewModel::disconnect, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.trakt_disconnect))
                        }
                    }

                    login is TraktLogin.Waiting -> {
                        Text(
                            text = stringResource(R.string.trakt_connect_steps, login.code.verificationUrl),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = login.code.userCode,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        QrCode(
                            content = login.code.verificationUrl,
                            modifier = Modifier
                                .width(200.dp)
                                .align(Alignment.CenterHorizontally),
                        )
                        Text(
                            text = stringResource(R.string.trakt_waiting),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = viewModel::cancelLogin, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.trakt_cancel))
                        }
                    }

                    else -> {
                        val starting = login is TraktLogin.Starting
                        Button(onClick = viewModel::connect, enabled = !starting, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (starting) R.string.trakt_starting else R.string.trakt_connect))
                        }
                    }
                }
                state.message?.let { message ->
                    Text(text = stringResource(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    text = stringResource(R.string.trakt_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
