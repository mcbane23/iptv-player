package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.theme.glowBackground
import com.bane23.iptvplayer.domain.ArchiveProgram
import com.bane23.iptvplayer.ui.common.archive.ArchiveViewModel
import com.bane23.iptvplayer.ui.common.guide.formatTime

/** Arhiva kanala na TV-u: prošle emisije po danima, OK pušta emisiju od početka. */
@Composable
fun TvArchiveScreen(
    channelId: Long,
    onPlay: (ArchiveProgram) -> Unit,
    viewModel: ArchiveViewModel = hiltViewModel(),
) {
    LaunchedEffect(channelId) { viewModel.open(channelId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }
    val firstProgram = state.days.firstOrNull()?.programs?.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Text(
            text = listOf(stringResource(R.string.archive_title), state.channelName)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        when {
            state.loading -> Note(stringResource(R.string.vod_loading))

            state.failed -> {
                Note(stringResource(R.string.archive_failed))
                // Jedini element na ekranu: mora da ima fokus, inače daljinski ne radi ništa.
                TvButton(
                    onClick = viewModel::retry,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .focusRequester(firstFocus),
                ) {
                    Text(stringResource(R.string.player_retry))
                }
                LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
            }

            state.days.isEmpty() -> Note(stringResource(R.string.archive_empty))

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(0.75f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.days.forEach { day ->
                        item(key = day.label) {
                            Text(
                                text = day.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(day.programs, key = { it.startMs }) { program ->
                            TvListRow(
                                onClick = { onPlay(program) },
                                modifier = if (program === firstProgram) Modifier.focusRequester(firstFocus) else Modifier,
                            ) { color ->
                                Text(
                                    text = "${formatTime(program.startMs)} – ${formatTime(program.stopMs)}",
                                    color = color.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    modifier = Modifier.width(150.dp),
                                )
                                Text(
                                    text = program.title,
                                    color = color,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                LaunchedEffect(firstProgram?.startMs) { firstFocus.tryRequestFocus() }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
