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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.cast.rememberLocalNetworkPermission
import com.bane23.iptvplayer.ui.common.pairing.SendError
import com.bane23.iptvplayer.ui.common.pairing.SendToTvViewModel
import com.bane23.iptvplayer.ui.common.pairing.TvTraktLogin
import com.bane23.iptvplayer.data.trakt.TraktLoginResult

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
    val context = LocalContext.current
    // Od Androida 17 telefon ne sme da se obrati TV-u u kućnoj mreži dok korisnik to ne dozvoli.
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val localNetwork = rememberLocalNetworkPermission { granted ->
        permissionDenied = !granted
        if (granted) viewModel.send()
    }
    val needsPermission = permissionDenied && !localNetwork.granted

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
                    result.openSubtitles?.let { ok ->
                        Body(stringResource(if (ok) R.string.transfer_opensubtitles_ok else R.string.transfer_opensubtitles_failed))
                    }
                    result.trakt?.let { ok ->
                        Body(stringResource(if (ok) R.string.transfer_trakt_ok else R.string.transfer_trakt_failed))
                    }
                    Body(stringResource(R.string.transfer_done_hint))
                }

                !state.canSend && !state.traktAvailable -> {
                    Body(stringResource(R.string.transfer_nothing))
                    if (state.skipped > 0) Body(stringResource(R.string.transfer_skipped_files, state.skipped))
                }

                else -> {
                    Body(stringResource(R.string.transfer_target, state.address))
                    if (state.playlistCount > 0) {
                        Body(
                            pluralStringResource(R.plurals.transfer_count, state.playlistCount, state.playlistCount),
                        )
                    }
                    if (state.skipped > 0) Body(stringResource(R.string.transfer_skipped_files, state.skipped))
                    state.openSubtitlesUser?.let { Body(stringResource(R.string.transfer_opensubtitles, it)) }
                    if (state.traktAvailable && !state.sending) {
                        TraktForTv(
                            login = state.trakt,
                            onRequest = viewModel::requestTrakt,
                            onCancel = viewModel::cancelTrakt,
                        )
                    }
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
                    if (needsPermission) {
                        Text(
                            text = stringResource(R.string.transfer_permission_needed),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    ),
                                )
                            },
                        ) {
                            Text(stringResource(R.string.transfer_open_settings))
                        }
                    } else if (error != null) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        val detail = state.errorDetail
                        if (state.error == SendError.UNREACHABLE && detail != null) {
                            Text(
                                text = stringResource(R.string.transfer_error_detail, detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val canSend = !state.loading && result == null && state.canSend &&
                    state.error != SendError.BAD_TARGET
                if (canSend) {
                    Button(onClick = localNetwork.request, enabled = !state.sending) {
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

/** Posebna Trakt prijava za TV: jedan dodir ovde, pa potvrda na Trakt stranici. */
@Composable
private fun TraktForTv(login: TvTraktLogin, onRequest: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // Nema pregledača: kod ostaje na ekranu i može da se unese na drugom uređaju.
        }
    }
    // Stranica se otvara sama čim kod stigne, da korisniku ostane samo potvrda.
    val waiting = login as? TvTraktLogin.Waiting
    LaunchedEffect(waiting?.url) { waiting?.let { open(it.url) } }
    when (login) {
        TvTraktLogin.None -> OutlinedButton(onClick = onRequest) { Text(stringResource(R.string.transfer_trakt_add)) }

        TvTraktLogin.Starting -> Body(stringResource(R.string.trakt_waiting))

        is TvTraktLogin.Waiting -> {
            Body(stringResource(R.string.transfer_trakt_waiting, login.code))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { open(login.url) }) { Text(stringResource(R.string.trakt_open_activation)) }
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            }
        }

        TvTraktLogin.Ready -> Body(stringResource(R.string.transfer_trakt_ready))

        is TvTraktLogin.Failed -> {
            Text(
                text = stringResource(
                    when (login.reason) {
                        TraktLoginResult.EXPIRED -> R.string.trakt_error_expired
                        TraktLoginResult.DENIED -> R.string.trakt_error_denied
                        else -> R.string.trakt_error_network
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = onRequest) { Text(stringResource(R.string.transfer_trakt_add)) }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge)
}
