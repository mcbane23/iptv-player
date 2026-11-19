package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bane23.iptvplayer.ui.common.vod.firstUnwatchedIndex
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.WatchProgress
import com.bane23.iptvplayer.data.xtream.Episode
import com.bane23.iptvplayer.data.xtream.XtreamVodParser
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.formatDuration
import com.bane23.iptvplayer.ui.common.vod.MovieViewModel
import com.bane23.iptvplayer.ui.common.vod.Poster
import com.bane23.iptvplayer.ui.common.vod.SeriesViewModel
import com.bane23.iptvplayer.ui.common.vod.isSeasonWatched
import com.bane23.iptvplayer.ui.common.vod.label
import com.bane23.iptvplayer.ui.common.vod.GenreChips
import com.bane23.iptvplayer.ui.common.vod.genresOf
import com.bane23.iptvplayer.ui.common.vod.metaLine
import com.bane23.iptvplayer.ui.common.vod.rememberOpenTrailer
import com.bane23.iptvplayer.ui.common.vod.statusText

/** Detalji filma na TV-u. */
@OptIn(ExperimentalLayoutApi::class)
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
    val openTrailer = rememberOpenTrailer()

    Backdrop(state.poster, details?.backdrop) {
        Row(
            modifier = Modifier
                .fillMaxSize()
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
                GenreChips(details?.genre)
                state.progress?.statusText()?.let { status ->
                    Text(text = status, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                }
                // Dugmad prelaze u drugi red kad ne stanu (dugi natpisi), da se tekst ne preklapa.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    val progress = state.progress
                    if (progress != null && progress.resumable) {
                        TvButton(onClick = { onPlay(playExtension, false) }, modifier = Modifier.focusRequester(firstFocus)) {
                            ButtonIcon(AppIcons.Play)
                            Text(stringResource(R.string.vod_resume_from, formatDuration(progress.positionMs)))
                        }
                        TvOutlinedButton(onClick = { onPlay(playExtension, true) }) {
                            ButtonIcon(AppIcons.Previous)
                            Text(stringResource(R.string.vod_start_over))
                        }
                    } else {
                        TvButton(onClick = { onPlay(playExtension, true) }, modifier = Modifier.focusRequester(firstFocus)) {
                            ButtonIcon(AppIcons.Play)
                            Text(stringResource(R.string.vod_play))
                        }
                    }
                    // Dugme ostaje isto, menja se samo ikonica i natpis, da fokus ne pobegne.
                    TvOutlinedButton(onClick = viewModel::toggleFavorite) {
                        ButtonIcon(if (state.favorite) AppIcons.Star else AppIcons.StarOutline)
                        Text(stringResource(if (state.favorite) R.string.favorite_remove else R.string.favorite_add))
                    }
                    val watched = state.progress?.watched == true
                    TvOutlinedButton(onClick = viewModel::toggleWatched) {
                        ButtonIcon(AppIcons.Check)
                        Text(stringResource(if (watched) R.string.vod_mark_unwatched else R.string.vod_mark_watched))
                    }
                    details?.trailer?.let { trailer ->
                        TvOutlinedButton(onClick = { openTrailer(trailer) }) {
                            ButtonIcon(AppIcons.Movies)
                            Text(stringResource(R.string.vod_trailer))
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
    val openTrailer = rememberOpenTrailer()
    val resume = state.resumeEpisode
    // Prelazak sa sezona na epizode vodi na prvu neodgledanu epizodu sezone.
    val episodeFocus = remember { FocusRequester() }
    val episodeListState = rememberLazyListState()
    var targetEpisodeId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.selectedSeason, details != null, state.progress.isEmpty()) {
        val index = firstUnwatchedIndex(state.episodes, state.progress)
        targetEpisodeId = state.episodes.getOrNull(index)?.id
        // Red sa tom epizodom mora da postoji da bi primio fokus; ispred epizoda je red „označi sezonu”.
        if (details != null) runCatching { episodeListState.scrollToItem(if (index == 0) 0 else index + 1) }
    }

    Backdrop(state.poster, details?.backdrop) {
        Row(
            modifier = Modifier
                .fillMaxSize()
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
                    Text(text = meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GenreChips(genresOf(details?.genre, limit = 2).joinToString(","))
                details?.plot?.let { plot ->
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                details?.trailer?.let { trailer ->
                    TvOutlinedButton(onClick = { openTrailer(trailer) }) {
                        ButtonIcon(AppIcons.Movies)
                        Text(stringResource(R.string.vod_trailer))
                    }
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
                    TvButton(onClick = viewModel::load, modifier = Modifier.focusRequester(firstFocus)) {
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
                        item(key = "favorite") {
                            TvListRow(onClick = viewModel::toggleFavorite, modifier = Modifier.padding(bottom = 8.dp)) { color ->
                                Icon(
                                    imageVector = if (state.favorite) AppIcons.Star else AppIcons.StarOutline,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(R.string.category_favorites),
                                    color = color,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 12.dp),
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
                                modifier = (if (isFirst) Modifier.focusRequester(firstFocus) else Modifier)
                                    // Desno sa sezone vodi na prvu neodgledanu epizodu; ako njen red još
                                    // ne postoji, fokus ide uobičajenim putem.
                                    .onPreviewKeyEvent { event ->
                                        event.type == KeyEventType.KeyDown &&
                                            event.key == Key.DirectionRight &&
                                            episodeFocus.tryRequestFocus()
                                    },
                            )
                        }
                    }
                    LazyColumn(
                        state = episodeListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        state.selectedSeason?.let { season ->
                            item(key = "season-watched") {
                                // Isti red ostaje i posle izbora, menja se samo natpis, da fokus ne pobegne.
                                val seasonWatched = state.isSeasonWatched(season)
                                TvListRow(
                                    onClick = { viewModel.toggleSeasonWatched(season) },
                                    modifier = Modifier.padding(bottom = 8.dp),
                                ) { color ->
                                    Icon(
                                        imageVector = AppIcons.Check,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = stringResource(
                                            if (seasonWatched) R.string.vod_mark_season_unwatched else R.string.vod_mark_season_watched,
                                            season,
                                        ),
                                        color = color,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 12.dp),
                                    )
                                }
                            }
                        }
                        items(state.episodes, key = { it.id }) { episode ->
                            val progress = state.progress[episode.id]
                            EpisodeRow(
                                episode = episode,
                                progress = progress,
                                fallbackImage = state.poster,
                                onClick = { onPlay(episode.id, progress?.resumable != true) },
                                // Dug pritisak na OK označava epizodu kao odgledanu, odnosno poništava.
                                onLongClick = { viewModel.toggleWatched(episode) },
                                modifier = if (episode.id == targetEpisodeId) Modifier.focusRequester(episodeFocus) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(state.loading, state.failed) {
        if (!state.loading) runCatching { firstFocus.requestFocus() }
    }
}

/**
 * Pozadina detalja. Kad provajder šalje široku sliku iz filma ([backdrop]), ona ide preko celog
 * ekrana, zatamnjena ka levoj strani i dnu, gde je tekst. Inače je to zatamnjen poster na desnoj
 * strani, koji se ka levo stapa sa pozadinom.
 */
@Composable
private fun Backdrop(poster: String?, backdrop: String?, content: @Composable () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    // Ako slika ne može da se učita, ostaje poster kao i do sada.
    var backdropFailed by remember(backdrop) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        if (!backdrop.isNullOrBlank() && !backdropFailed) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { backdropFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.55f),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to background.copy(alpha = 0.92f),
                            0.55f to background.copy(alpha = 0.7f),
                            1f to background.copy(alpha = 0.25f),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to background)),
            )
        } else if (!poster.isNullOrBlank()) {
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.6f)
                    .alpha(0.25f),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0.4f to background,
                            1f to background.copy(alpha = 0.3f),
                        ),
                    ),
            )
        }
        content()
    }
}

@Composable
private fun ButtonIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier
            .padding(end = 8.dp)
            .size(20.dp),
    )
}

/** Epizoda: sličica sa trakom odgledanog, naziv i, desno, trajanje ili stanje gledanja. */
@Composable
private fun EpisodeRow(
    episode: Episode,
    progress: WatchProgress?,
    fallbackImage: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvListRow(onClick = onClick, onLongClick = onLongClick, modifier = modifier) { color ->
        Box(
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF22262E)),
        ) {
            val image = episode.image ?: fallbackImage
            if (!image.isNullOrBlank()) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (progress != null && progress.durationMs > 0 && progress.positionMs > 0) {
                TvProgressBar(
                    progress = (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f),
                    trackColor = Color.Black.copy(alpha = 0.5f),
                    barColor = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                )
            }
        }
        Text(
            text = "${episode.number}. ${episode.title}",
            color = color,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        )
        (progress?.statusText() ?: episode.durationSeconds?.takeIf { it > 0 }?.let { formatDuration(it * 1000) })?.let {
            if (progress?.finished == true) {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(16.dp),
                )
            }
            Text(
                text = it,
                color = color.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
    }
}
