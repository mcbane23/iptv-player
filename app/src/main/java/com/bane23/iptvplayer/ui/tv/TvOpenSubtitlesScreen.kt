package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.tv.material3.MaterialTheme
import com.bane23.iptvplayer.ui.tv.text.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.pairing.QrCode
import com.bane23.iptvplayer.ui.common.subtitles.OpenSubtitlesLoginFields
import com.bane23.iptvplayer.ui.common.subtitles.OpenSubtitlesPairingViewModel
import com.bane23.iptvplayer.ui.common.subtitles.OpenSubtitlesViewModel
import com.bane23.iptvplayer.ui.common.theme.glowBackground

/**
 * OpenSubtitles nalog na TV-u: prijava korisničkim imenom i lozinkom (tastatura se otvara tasterom OK
 * na polju, ili se podaci unesu na telefonu preko QR koda sa desne strane), i odjava. Dugme koraka
 * dobija fokus čim se korak promeni.
 */
@Composable
fun TvOpenSubtitlesScreen(
    viewModel: OpenSubtitlesViewModel = hiltViewModel(),
    pairing: OpenSubtitlesPairingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pairingState by pairing.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val primary = remember { FocusRequester() }
    val signedInAs = state.username

    val pairingUrl = pairingState.url.takeIf { signedInAs == null }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
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
                modifier = Modifier.fillMaxWidth(if (pairingUrl == null) 0.7f else 1f),
            )
            if (signedInAs != null) {
                Text(
                    text = stringResource(R.string.opensubtitles_signed_in_as, signedInAs),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                TvOutlinedButton(
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
                    readOnly = state.signingIn || pairingState.working,
                    onDone = { viewModel.signIn(username, password) },
                    modifier = Modifier.fillMaxWidth(if (pairingUrl == null) 0.5f else 0.8f),
                )
                TvButton(
                    onClick = { viewModel.signIn(username, password) },
                    modifier = Modifier.focusRequester(primary),
                ) {
                    Text(
                        stringResource(
                            if (state.signingIn || pairingState.working) {
                                R.string.opensubtitles_signing_in
                            } else {
                                R.string.opensubtitles_sign_in
                            },
                        ),
                    )
                }
            }
            state.message?.let { message ->
                Text(text = stringResource(message), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            }
        }
        if (pairingUrl != null) {
            // Prijava preko telefona: isti podaci, ali se kucaju na telefonu umesto daljinskim.
            Column(modifier = Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QrCode(content = pairingUrl, modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.opensubtitles_qr_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.pairing_manual_address, pairingUrl),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    LaunchedEffect(signedInAs != null) { primary.tryRequestFocus() }
}
