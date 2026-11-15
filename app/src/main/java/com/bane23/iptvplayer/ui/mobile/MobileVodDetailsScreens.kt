package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.xtream.XtreamVodParser
import com.bane23.iptvplayer.ui.common.player.formatDuration
import com.bane23.iptvplayer.ui.common.vod.MovieViewModel
import com.bane23.iptvplayer.ui.common.vod.Poster
import com.bane23.iptvplayer.ui.common.vod.SeriesViewModel
import com.bane23.iptvplayer.ui.common.vod.label
import com.bane23.iptvplayer.ui.common.vod.metaLine
import com.bane23.iptvplayer.ui.common.vod.statusText

/** Detalji filma na telefonu. */
@Composable
fun MobileMovieScreen(
    movieId: Long,
    name: String,
    poster: String?,
    extension: String?,
    onPlay: (extension: String, startOver: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: MovieViewModel = hiltViewModel(),
) {
    LaunchedEffect(movieId) { viewModel.open(movieId, name, poster, extension) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val details = state.details
    val playExtension = state.extension ?: XtreamVodParser.DEFAULT_EXTENSION

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.action_back))
            }
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                Poster(url = state.poster, modifier = Modifier.width(120.dp))
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = state.name.ifEmpty { name }, style = MaterialTheme.typography.titleLarge)
                    details?.metaLine()?.takeIf { it.isNotEmpty() }?.let { meta ->
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    state.progress?.statusText()?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val progress = state.progress
                if (progress != null && progress.resumable) {
                    Button(onClick = { onPlay(playExtension, false) }) {
                        Text(stringResource(R.string.vod_resume_from, formatDuration(progress.positionMs)))
                    }
                    OutlinedButton(onClick = { onPlay(playExtension, true) }) {
                        Text(stringResource(R.string.vod_start_over))
                    }
                } else {
                    Button(onClick = { onPlay(playExtension, true) }) { Text(stringResource(R.string.vod_play)) }
                }
            }
            if (state.loading) {
                Text(
                    text = stringResource(R.string.vod_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            details?.plot?.let { plot ->
                Text(
                    text = plot,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            val credits = listOfNotNull(
                details?.director?.let { stringResource(R.string.vod_director, it) },
                details?.cast?.let { stringResource(R.string.vod_cast, it) },
            )
            for (line in credits) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }
        }
    }
}

/** Detalji serije na telefonu: opis, sezone i epizode. */
@Composable
fun MobileSeriesScreen(
    seriesId: Long,
    name: String,
    poster: String?,
    onPlay: (episodeId: Long, startOver: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    LaunchedEffect(seriesId) { viewModel.open(seriesId, name, poster) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val details = state.details

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.action_back))
                }
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Poster(url = state.poster, modifier = Modifier.width(120.dp))
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text(text = state.name.ifEmpty { name }, style = MaterialTheme.typography.titleLarge)
                        details?.metaLine()?.takeIf { it.isNotEmpty() }?.let { meta ->
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        details?.plot?.let { plot ->
                            Text(
                                text = plot,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            when {
                state.loading -> item {
                    Text(
                        text = stringResource(R.string.vod_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                state.failed -> item {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = stringResource(R.string.vod_load_failed), color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::load) { Text(stringResource(R.string.player_retry)) }
                    }
                }

                details == null || details.seasons.isEmpty() -> item {
                    Text(
                        text = stringResource(R.string.vod_no_episodes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                else -> {
                    state.resumeEpisode?.let { episode ->
                        item {
                            val resumable = state.progress[episode.id]?.resumable == true
                            Button(
                                onClick = { onPlay(episode.id, !resumable) },
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                            ) {
                                Text(stringResource(R.string.vod_continue_episode, episode.label()))
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (season in details.seasons) {
                                val label = stringResource(R.string.vod_season, season.number)
                                if (season.number == state.selectedSeason) {
                                    FilledTonalButton(onClick = {}) { Text(label) }
                                } else {
                                    OutlinedButton(onClick = { viewModel.selectSeason(season.number) }) { Text(label) }
                                }
                            }
                        }
                    }
                    items(state.episodes, key = { it.id }) { episode ->
                        val progress = state.progress[episode.id]
                        Column(
                            modifier = Modifier.clickable { onPlay(episode.id, progress?.resumable != true) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${episode.number}. ${episode.title}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    progress?.statusText()?.let { status ->
                                        Text(
                                            text = status,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                episode.durationSeconds?.let { seconds ->
                                    Text(
                                        text = formatDuration(seconds * 1000),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 12.dp),
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
