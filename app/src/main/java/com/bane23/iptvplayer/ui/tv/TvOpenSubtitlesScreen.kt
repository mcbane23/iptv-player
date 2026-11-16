package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.bane23.iptvplayer.ui.common.subtitles.OpenSubtitlesLoginFields
import com.bane23.iptvplayer.ui.common.subtitles.OpenSubtitlesViewModel
import com.bane23.iptvplayer.ui.common.theme.glowBackground

/**
 * OpenSubtitles nalog na TV-u: prijava korisničkim imenom i lozinkom (tastatura se otvara tasterom OK
 * na polju), i odjava. Dugme koraka dobija fokus čim se korak promeni.
 */
@Composable
fun TvOpenSubtitlesScreen(viewModel: OpenSubtitlesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val primary = remember { FocusRequester() }
    val signedInAs = state.username

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary)
            .verticalScroll(rememberScrollState())
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.opensubtitles_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.opensubtitles_summary),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.7f),
        )
        if (signedInAs != null) {
            Text(
                text = stringResource(R.string.opensubtitles_signed_in_as, signedInAs),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedButton(
                onClick = {
                    password = ""
                    viewModel.signOut()
                },
                modifier = Modifier.focusRequester(primary),
            ) {
                Text(stringResource(R.string.opensubtitles_sign_out))
            }
        } else {
            OpenSubtitlesLoginFields(
                username = username,
                onUsername = { username = it },
                password = password,
                onPassword = { password = it },
                readOnly = state.signingIn,
                onDone = { viewModel.signIn(username, password) },
                modifier = Modifier.fillMaxWidth(0.5f),
            )
            Button(
                onClick = { viewModel.signIn(username, password) },
                modifier = Modifier.focusRequester(primary),
            ) {
                Text(
                    stringResource(if (state.signingIn) R.string.opensubtitles_signing_in else R.string.opensubtitles_sign_in),
                )
            }
        }
        state.message?.let { message ->
            Text(text = stringResource(message), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        }
    }
    LaunchedEffect(signedInAs != null) { primary.tryRequestFocus() }
}
