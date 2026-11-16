package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.bane23.iptvplayer.domain.ArchiveProgram
import com.bane23.iptvplayer.ui.common.archive.ArchiveViewModel
import com.bane23.iptvplayer.ui.common.guide.formatTime

/** Arhiva kanala na telefonu: prošle emisije po danima, dodir pušta emisiju od početka. */
@Composable
fun MobileArchiveScreen(
    channelId: Long,
    onPlay: (ArchiveProgram) -> Unit,
    onBack: () -> Unit,
    viewModel: ArchiveViewModel = hiltViewModel(),
) {
    LaunchedEffect(channelId) { viewModel.open(channelId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SectionTopBar(
                title = listOf(stringResource(R.string.archive_title), state.channelName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                onBack = onBack,
            )
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))

                state.failed -> Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.archive_failed), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = viewModel::retry, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.player_retry))
                    }
                }

                state.days.isEmpty() -> Text(
                    text = stringResource(R.string.archive_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    state.days.forEach { day ->
                        item(key = day.label) {
                            Text(
                                text = day.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(day.programs, key = { it.startMs }) { program ->
                            ProgramRow(program = program, onClick = { onPlay(program) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramRow(program: ArchiveProgram, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = "${formatTime(program.startMs)} – ${formatTime(program.stopMs)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = program.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        program.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
