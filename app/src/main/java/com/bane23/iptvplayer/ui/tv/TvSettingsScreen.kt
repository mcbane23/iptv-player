package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.SettingsViewModel

/** Podešavanja aplikacije na TV-u. */
@Composable
fun TvSettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val resumeLastChannel by viewModel.resumeLastChannel.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        TvTextRow(
            text = stringResource(R.string.settings_resume_last),
            trailing = stringResource(if (resumeLastChannel) R.string.setting_on else R.string.setting_off),
            onClick = { viewModel.setResumeLastChannel(!resumeLastChannel) },
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .focusRequester(firstFocus),
        )
        Text(
            text = stringResource(R.string.settings_resume_last_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        )
    }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}
