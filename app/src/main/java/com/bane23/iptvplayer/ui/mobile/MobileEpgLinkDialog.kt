package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.ui.common.guide.EpgLinkViewModel
import com.bane23.iptvplayer.ui.common.guide.nowUntil

/**
 * Ručno povezivanje kanala sa programom iz vodiča na telefonu: polje za naziv i ispod kanali vodiča
 * sa najsličnijim nazivom, uz svaki šta je sada na njemu. Dodir bira i zatvara.
 */
@Composable
fun MobileEpgLinkDialog(channel: Channel, onClose: () -> Unit, viewModel: EpgLinkViewModel = hiltViewModel()) {
    LaunchedEffect(channel.id) { viewModel.open(channel) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                text = stringResource(R.string.epg_link_title, channel.name),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.epg_link_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    label = { Text(stringResource(R.string.epg_link_search), maxLines = 1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                )
                if (!ui.loading && ui.candidates.isEmpty()) {
                    Text(
                        text = stringResource(if (ui.guideEmpty) R.string.epg_link_guide_empty else R.string.epg_link_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(ui.candidates, key = { it.key }) { candidate ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.pick(candidate.key)
                                    onClose()
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = candidate.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (candidate.key == ui.linkedKey) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            candidate.nowTitle?.let { title ->
                                Text(
                                    text = nowUntil(title, candidate.nowStopMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } },
        dismissButton = if (ui.linkedKey != null) {
            {
                TextButton(onClick = {
                    viewModel.pick(null)
                    onClose()
                }) { Text(stringResource(R.string.epg_link_remove), maxLines = 1) }
            }
        } else {
            null
        },
    )
}
