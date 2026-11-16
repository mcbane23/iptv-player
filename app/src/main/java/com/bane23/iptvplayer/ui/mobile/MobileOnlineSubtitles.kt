package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.subtitles.SubtitleResult
import com.bane23.iptvplayer.ui.common.subtitles.appliedSubtitleToast
import com.bane23.iptvplayer.ui.common.subtitles.details
import com.bane23.iptvplayer.ui.common.subtitles.message
import com.bane23.iptvplayer.ui.common.vod.OnlineSubtitlesState

/**
 * Titlovi nađeni na internetu za ono što se gleda. Dodir preuzima titl i uključuje ga, posle čega
 * se dijalog sam zatvara.
 */
@Composable
internal fun OnlineSubtitlesDialog(
    state: OnlineSubtitlesState,
    onPick: (SubtitleResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(state.appliedId) {
        if (state.appliedId != null) {
            appliedSubtitleToast(context, state.remaining)
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subtitles_online_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val results = state.results.orEmpty()
                when {
                    state.searching -> Text(stringResource(R.string.subtitles_searching))
                    state.failure != null -> Text(
                        text = stringResource(state.failure.message()),
                        color = MaterialTheme.colorScheme.error,
                    )
                    state.results != null && results.isEmpty() -> Text(stringResource(R.string.subtitles_none_found))
                }
                results.forEach { result ->
                    TextButton(
                        onClick = { onPick(result) },
                        enabled = state.downloadingId == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(text = result.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = if (state.downloadingId == result.fileId) {
                                    stringResource(R.string.subtitles_downloading)
                                } else {
                                    result.details()
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
