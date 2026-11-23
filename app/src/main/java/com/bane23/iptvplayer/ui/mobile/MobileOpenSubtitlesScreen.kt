package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.subtitles.OpenSubtitlesLoginFields
import com.bane23.iptvplayer.ui.common.subtitles.OpenSubtitlesViewModel

/** OpenSubtitles nalog na telefonu: prijava korisničkim imenom i lozinkom, i odjava. */
@Composable
fun MobileOpenSubtitlesScreen(onBack: () -> Unit, viewModel: OpenSubtitlesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    // Lozinka se ne čuva u sačuvanom stanju ekrana.
    var password by androidx.compose.runtime.remember { mutableStateOf("") }

    AppScaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTopBar(title = stringResource(R.string.opensubtitles_title), onBack = onBack)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = stringResource(R.string.opensubtitles_summary), style = MaterialTheme.typography.bodyMedium)
                val signedInAs = state.username
                if (signedInAs != null) {
                    Text(
                        text = stringResource(R.string.opensubtitles_signed_in_as, signedInAs),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(
                        onClick = {
                            password = ""
                            viewModel.signOut()
                        },
                        modifier = Modifier.fillMaxWidth(),
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
                    )
                    Button(
                        onClick = { viewModel.signIn(username, password) },
                        enabled = !state.signingIn && username.isNotBlank() && password.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (state.signingIn) R.string.opensubtitles_signing_in else R.string.opensubtitles_sign_in,
                            ),
                        )
                    }
                }
                state.message?.let { message ->
                    Text(text = stringResource(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
