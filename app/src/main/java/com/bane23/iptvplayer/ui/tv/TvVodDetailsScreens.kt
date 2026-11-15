package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.xtream.XtreamVodParser
import com.bane23.iptvplayer.ui.common.player.formatDuration
import com.bane23.iptvplayer.ui.common.vod.MovieViewModel
import com.bane23.iptvplayer.ui.common.vod.Poster
import com.bane23.iptvplayer.ui.common.vod.SeriesViewModel
import com.bane23.iptvplayer.ui.common.vod.label
import com.bane23.iptvplayer.ui.common.vod.metaLine
import com.bane23.iptvplayer.ui.common.vod.statusText

/** Detalji filma na TV-u. */
@Composable
fun TvMovieScreen(
    movieId: Long,
    name: String,
    poster: String?,
    extension: String?,
    onPlay: (extension: String, startOver: Boolean) -> Unit,
    viewModel: MovieViewModel = hiltViewModel(),
) {
    LaunchedEffect(movieId) { viewModel.open(movieId, name, poster, extension) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val details = state.details
    val playExtension = state.extension ?: XtreamVodParser.DEFAULT_EXTENSION
    val firstFocus = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Poster(url = state.poster, modifier = Modifier.width(220.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = state.name.ifEmpty { name },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            details?.metaLine()?.takeIf { it.isNotEmpty() }?.let { meta ->
                Text(text = meta, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.progress?.statusText()?.let { status ->
                Text(text = status, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                val progress = state.progress
                if (progress != null && progress.resumable) {
                    Button(onClick = { onPlay(playExtension, false) }, modifier = Modifier.focusRequester(firstFocus)) {
                        Text(stringResource(R.string.vod_resume_from, formatDuration(progress.positionMs)))
                    }
                    OutlinedButton(onClick = { onPlay(playExtension, true) }) {
                        Text(stringResource(R.string.vod_start_over))
                    }
                } else {
                    Button(onClick = { onPlay(playExtension, true) }, modifier = Modifier.focusRequester(firstFocus)) {
                        Text(stringResource(R.string.vod_play))
                    }
                }
            }
            if (state.loading) {
                Text(
                    text = stringResource(R.string.vod_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            details?.plot?.let { plot ->
                Text(
                    text = plot,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    // Dugme se menja kad stigne zapamćena pozicija, pa se fokus postavlja ponovo.
    LaunchedEffect(state.progress?.resumable) { runCatching { firstFocus.requestFocus() } }
}

/** Detalji serije na TV-u: levo opis, u sredini sezone, desno epizode izabrane sezone. */
@Composable
fun TvSeriesScreen(
    seriesId: Long,
    name: String,
    poster: String?,
    onPlay: (episodeId: Long, startOver: Boolean) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    LaunchedEffect(seriesId) { viewModel.open(seriesId, name, poster) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val details = state.details
    val firstFocus = remember { FocusRequester() }
    val resume = state.resumeEpisode

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(modifier = Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Poster(url = state.poster, modifier = Modifier.width(150.dp))
            Text(
                text = state.name.ifEmpty { name },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            details?.metaLine()?.takeIf { it.isNotEmpty() }?.let { meta ->
                Text(text = meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            details?.plot?.let { plot ->
                Text(
                    text = plot,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        when {
            state.loading -> Text(
                text = stringResource(R.string.vod_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.failed -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.vod_load_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                // Početni fokus mora biti na nekom elementu, inače daljinski ne radi ništa.
                Button(onClick = viewModel::load, modifier = Modifier.focusRequester(firstFocus)) {
                    Text(stringResource(R.string.player_retry))
                }
            }

            details == null || details.seasons.isEmpty() -> Text(
                text = stringResource(R.string.vod_no_episodes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .width(190.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (resume != null) {
                        item(key = "resume") {
                            val resumable = state.progress[resume.id]?.resumable == true
                            TvTextRow(
                                text = stringResource(R.string.vod_continue_episode, resume.label()),
                                onClick = { onPlay(resume.id, !resumable) },
                                modifier = Modifier
                                    .focusRequester(firstFocus)
                                    .padding(bottom = 8.dp),
                            )
                        }
                    }
                    items(details.seasons, key = { it.number }) { season ->
                        val isFirst = resume == null && season == details.seasons.first()
                        TvTextRow(
                            text = stringResource(R.string.vod_season, season.number),
                            selected = season.number == state.selectedSeason,
                            onFocused = { viewModel.selectSeason(season.number) },
                            onClick = { viewModel.selectSeason(season.number) },
                            modifier = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(state.episodes, key = { it.id }) { episode ->
                        val progress = state.progress[episode.id]
                        TvTextRow(
                            text = "${episode.number}. ${episode.title}",
                            trailing = progress?.statusText()
                                ?: episode.durationSeconds?.let { formatDuration(it * 1000) },
                            onClick = { onPlay(episode.id, progress?.resumable != true) },
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(state.loading, state.failed) {
        if (!state.loading) runCatching { firstFocus.requestFocus() }
    }
}
