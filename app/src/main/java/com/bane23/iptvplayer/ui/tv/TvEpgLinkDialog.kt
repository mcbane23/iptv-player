package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.ui.common.guide.EpgLinkViewModel
import com.bane23.iptvplayer.ui.common.guide.nowUntil

/**
 * Ručno povezivanje kanala sa programom iz vodiča, uz desnu ivicu ekrana: polje za naziv i ispod
 * kanali vodiča sa najsličnijim nazivom, uz svaki šta je sada na njemu. OK bira i zatvara.
 */
@Composable
fun TvEpgLinkDialog(channel: Channel, onClose: () -> Unit, viewModel: EpgLinkViewModel = hiltViewModel()) {
    LaunchedEffect(channel.id) { viewModel.open(channel) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setWindowAnimations(0) }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.44f)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                    .padding(start = 16.dp, end = 48.dp, top = 27.dp, bottom = 27.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.epg_link_title, channel.name),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                    Text(
                        text = stringResource(R.string.epg_link_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 12.dp),
                    )
                }
                item {
                    TvFilterField(
                        value = ui.query,
                        onChange = viewModel::setQuery,
                        placeholder = stringResource(R.string.epg_link_search),
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            // Kad nema nijednog predloga, fokus mora da stane na polje.
                            .then(if (ui.candidates.isEmpty() && ui.linkedKey == null) Modifier.focusRequester(firstFocus) else Modifier),
                    )
                }
                if (!ui.loading && ui.candidates.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(if (ui.guideEmpty) R.string.epg_link_guide_empty else R.string.epg_link_none),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(ui.candidates, key = { it.key }) { candidate ->
                    val first = candidate.key == ui.candidates.firstOrNull()?.key
                    TvListRow(
                        onClick = {
                            viewModel.pick(candidate.key)
                            onClose()
                        },
                        selected = candidate.key == ui.linkedKey,
                        modifier = if (first) Modifier.focusRequester(firstFocus) else Modifier,
                    ) { color ->
                        Text(
                            text = candidate.name,
                            color = color,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        candidate.nowTitle?.let { title ->
                            Text(
                                text = nowUntil(title, candidate.nowStopMs),
                                color = color.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            )
                        }
                    }
                }
                if (ui.linkedKey != null) {
                    item {
                        TvTextRow(
                            text = stringResource(R.string.epg_link_remove),
                            onClick = {
                                viewModel.pick(null)
                                onClose()
                            },
                            modifier = if (ui.candidates.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
            }
        }
    }
    // Fokus ide na prvi predlog čim stigne; polje za naziv ostaje iznad za drugačiju pretragu.
    LaunchedEffect(ui.loading) { if (!ui.loading) firstFocus.tryRequestFocus() }
}
