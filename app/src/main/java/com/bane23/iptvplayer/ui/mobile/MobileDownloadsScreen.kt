package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.download.DownloadState
import com.bane23.iptvplayer.ui.common.download.DownloadItem
import com.bane23.iptvplayer.ui.common.download.DownloadsViewModel
import com.bane23.iptvplayer.ui.common.download.details
import com.bane23.iptvplayer.ui.common.download.label
import com.bane23.iptvplayer.ui.common.icons.AppIcons

/**
 * „Preuzeto” na telefonu: filmovi i epizode preuzeti za gledanje bez interneta. Dodir pušta
 * preuzeto; preuzimanje koje traje ili čeka može da se pauzira, nastavi i obriše.
 */
@Composable
fun MobileDownloadsScreen(
    onPlay: (DownloadItem) -> Unit,
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<DownloadItem?>(null) }

    AppScaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SectionTopBar(title = stringResource(R.string.downloads_title), onBack = onBack)
            storage?.let {
                Text(
                    text = it.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            val list = downloads
            when {
                list == null -> Unit

                list.isEmpty() -> Text(
                    text = stringResource(R.string.downloads_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            onPlay = { onPlay(item) },
                            onPause = { viewModel.pause(item.id) },
                            onResume = { viewModel.resume(item.id) },
                            onDelete = { pendingDelete = item },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.download_delete_title)) },
            text = { Text(stringResource(R.string.download_delete_message, item.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.playable, onClick = onPlay)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.details(),
                style = MaterialTheme.typography.bodyMedium,
                color = when (item.state) {
                    DownloadState.DOWNLOADING -> MaterialTheme.colorScheme.primary
                    DownloadState.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.state == DownloadState.DOWNLOADING) {
                val fraction = item.fraction
                val bar = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, end = 8.dp)
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = bar)
                } else {
                    LinearProgressIndicator(modifier = bar)
                }
            }
        }
        if (item.pausable) {
            TextButton(onClick = onPause) { Text(stringResource(R.string.download_pause), maxLines = 1) }
        } else if (item.resumable) {
            TextButton(onClick = onResume) { Text(stringResource(R.string.download_resume), maxLines = 1) }
        }
        ToolbarIcon(AppIcons.Delete, stringResource(R.string.action_delete), onDelete)
    }
}
