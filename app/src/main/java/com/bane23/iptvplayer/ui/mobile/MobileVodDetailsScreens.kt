package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import com.bane23.iptvplayer.ui.common.vod.firstUnwatchedIndex
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.WatchProgress
import com.bane23.iptvplayer.data.xtream.Episode
import com.bane23.iptvplayer.data.xtream.XtreamVodParser
import com.bane23.iptvplayer.data.download.DownloadState
import com.bane23.iptvplayer.ui.common.download.DownloadItem
import com.bane23.iptvplayer.ui.common.download.DownloadNotices
import com.bane23.iptvplayer.ui.common.download.DownloadsViewModel
import com.bane23.iptvplayer.ui.common.download.rememberDownloadStart
import com.bane23.iptvplayer.ui.common.download.shortLabel
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

/** Detalji filma na telefonu: zaglavlje sa posterom, dugmad za puštanje, opis i ekipa. */
@Composable
fun MobileMovieScreen(
    movieId: Long,
    name: String,
    poster: String?,
    extension: String?,
    onPlay: (extension: String, startOver: Boolean) -> Unit,
    onDownloads: () -> Unit,
    onBack: () -> Unit,
    viewModel: MovieViewModel = hiltViewModel(),
    downloads: DownloadsViewModel = hiltViewModel(),
) {
    LaunchedEffect(movieId) { viewModel.open(movieId, name, poster, extension) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val details = state.details
    val playExtension = state.extension ?: XtreamVodParser.DEFAULT_EXTENSION
    val openTrailer = rememberOpenTrailer()
    val progress = state.progress
    val download = downloads.movies.collectAsStateWithLifecycle().value[movieId]
    DownloadNotices(downloads.notices)
    val startDownload = rememberDownloadStart {
        downloads.downloadMovie(movieId, state.name.ifEmpty { name }, state.poster, playExtension)
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                DetailsHeader(
                    title = state.name.ifEmpty { name },
                    poster = state.poster,
                    backdrop = details?.backdrop,
                    genre = details?.genre,
                    meta = details?.metaLine(),
                    status = progress?.statusText(),
                    favorite = state.favorite,
                    onFavorite = viewModel::toggleFavorite,
                    onBack = onBack,
                )
            }
            item {
                PlayButtons(
                    primaryLabel = if (progress?.resumable == true) {
                        stringResource(R.string.vod_resume_from, formatDuration(progress.positionMs))
                    } else {
                        stringResource(R.string.vod_play)
                    },
                    onPrimary = { onPlay(playExtension, progress?.resumable != true) },
                    onStartOver = { onPlay(playExtension, true) }.takeIf { progress?.resumable == true },
                )
            }
            item {
                DownloadButton(
                    label = download?.shortLabel() ?: stringResource(R.string.download_action),
                    onClick = if (download == null) startDownload else onDownloads,
                )
            }
            item {
                WatchedButton(
                    label = stringResource(if (progress?.watched == true) R.string.vod_mark_unwatched else R.string.vod_mark_watched),
                    onClick = viewModel::toggleWatched,
                )
            }
            details?.trailer?.let { trailer ->
                item { TrailerButton { openTrailer(trailer) } }
            }
            if (state.loading) {
                item { Message(stringResource(R.string.vod_loading)) }
            }
            item {
                Overview(
                    plot = details?.plot,
                    credits = listOfNotNull(
                        details?.director?.let { stringResource(R.string.vod_director, it) },
                        details?.cast?.let { stringResource(R.string.vod_cast, it) },
                    ),
                )
            }
        }
    }
}

/** Redovi ispred epizoda u spisku: zaglavlje, dugmad, opis, sezone i „označi sezonu”. */
private const val ROWS_BEFORE_EPISODES = 5

/** Detalji serije na telefonu: zaglavlje, nastavak gledanja, sezone kao čipovi i spisak epizoda. */
@Composable
fun MobileSeriesScreen(
    seriesId: Long,
    name: String,
    poster: String?,
    onPlay: (episodeId: Long, startOver: Boolean) -> Unit,
    onDownloads: () -> Unit,
    onBack: () -> Unit,
    viewModel: SeriesViewModel = hiltViewModel(),
    downloads: DownloadsViewModel = hiltViewModel(),
) {
    LaunchedEffect(seriesId) { viewModel.open(seriesId, name, poster) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val details = state.details
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val openTrailer = rememberOpenTrailer()
    // Red sa dugmetom „Trejler” postoji samo kad trejlera ima, pa pomera redove epizoda za jedan.
    val rowsBeforeEpisodes = ROWS_BEFORE_EPISODES + if (details?.trailer != null) 1 else 0
    val episodeDownloads by downloads.episodes.collectAsStateWithLifecycle()
    DownloadNotices(downloads.notices)
    // Dozvola za obaveštenja se traži jednom za ceo ekran, pa se pamti koja epizoda je čekala na nju.
    var pendingDownload by remember { mutableStateOf<Episode?>(null) }
    val startDownload = rememberDownloadStart {
        pendingDownload?.let { downloads.downloadEpisode(seriesId, state.name.ifEmpty { name }, state.poster, it) }
        pendingDownload = null
    }

    Scaffold { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                DetailsHeader(
                    title = state.name.ifEmpty { name },
                    poster = state.poster,
                    backdrop = details?.backdrop,
                    genre = details?.genre,
                    meta = details?.metaLine(),
                    status = null,
                    favorite = state.favorite,
                    onFavorite = viewModel::toggleFavorite,
                    onBack = onBack,
                )
            }

            when {
                state.loading -> item { Message(stringResource(R.string.vod_loading)) }
                state.failed -> item { Failure(onRetry = viewModel::load) }
                details == null || details.seasons.isEmpty() -> item {
                    Overview(plot = details?.plot, credits = emptyList())
                    Message(stringResource(R.string.vod_no_episodes))
                }

                else -> {
                    item {
                        val episode = state.resumeEpisode ?: details.seasons.first().episodes.firstOrNull()
                        if (episode != null) {
                            val resumable = state.progress[episode.id]?.resumable == true
                            PlayButtons(
                                primaryLabel = if (state.resumeEpisode != null) {
                                    stringResource(R.string.vod_continue_episode, episode.label())
                                } else {
                                    stringResource(R.string.vod_play)
                                },
                                onPrimary = { onPlay(episode.id, !resumable) },
                                onStartOver = { onPlay(episode.id, true) }.takeIf { resumable },
                            )
                        }
                    }
                    details.trailer?.let { trailer ->
                        item { TrailerButton { openTrailer(trailer) } }
                    }
                    item { Overview(plot = details.plot, credits = emptyList(), maxLines = 4) }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(details.seasons, key = { it.number }) { season ->
                                FilterChip(
                                    selected = season.number == state.selectedSeason,
                                    onClick = {
                                        viewModel.selectSeason(season.number)
                                        // Sezona sa odgledanim epizodama se otvara na prvoj neodgledanoj.
                                        val index = firstUnwatchedIndex(season.episodes, state.progress)
                                        if (index > 0) {
                                            scope.launch { listState.animateScrollToItem(rowsBeforeEpisodes + index) }
                                        }
                                    },
                                    label = { Text(stringResource(R.string.vod_season, season.number)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                )
                            }
                        }
                    }
                    state.selectedSeason?.let { season ->
                        item(key = "season-watched") {
                            WatchedButton(
                                label = stringResource(
                                    if (state.isSeasonWatched(season)) R.string.vod_mark_season_unwatched else R.string.vod_mark_season_watched,
                                    season,
                                ),
                                onClick = { viewModel.toggleSeasonWatched(season) },
                            )
                        }
                    }
                    items(state.episodes, key = { it.id }) { episode ->
                        val watched = state.progress[episode.id]
                        EpisodeRow(
                            episode = episode,
                            progress = watched,
                            fallbackImage = state.poster,
                            download = episodeDownloads[episode.id],
                            onClick = { onPlay(episode.id, watched?.resumable != true) },
                            onDownload = {
                                pendingDownload = episode
                                startDownload()
                            },
                            onDownloads = onDownloads,
                            onToggleWatched = { viewModel.toggleWatched(episode) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Zaglavlje detalja: poster preko cele širine, zatamnjen i izbledeo u pozadinu, a preko njega
 * dugme nazad, mali poster, naziv i kratki podaci.
 */
@Composable
private fun DetailsHeader(
    title: String,
    poster: String?,
    backdrop: String?,
    genre: String?,
    meta: String?,
    status: String?,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier = Modifier.fillMaxWidth()) {
        // Široka slika iz filma, kad je provajder šalje, vidi se jače od postera; inače poster kao do sada.
        var backdropFailed by remember(backdrop) { mutableStateOf(false) }
        val wide = backdrop?.takeIf { it.isNotBlank() && !backdropFailed }
        val image = wide ?: poster
        if (!image.isNullOrBlank()) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { if (wide != null) backdropFailed = true },
                modifier = Modifier
                    .matchParentSize()
                    .alpha(if (wide != null) 0.55f else 0.35f),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(background.copy(alpha = 0.3f), background))),
        )
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ToolbarIcon(AppIcons.Back, stringResource(R.string.action_back), onBack)
                FavoriteButton(favorite = favorite, onClick = onFavorite)
            }
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Poster(url = poster, modifier = Modifier.width(112.dp))
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    meta?.takeIf { it.isNotEmpty() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    GenreChips(genresOf(genre, limit = 3).joinToString(","), modifier = Modifier.padding(top = 6.dp))
                    status?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Glavno dugme (Pusti / Nastavi) preko cele širine i, kad ima smisla, „Od početka” ispod. */
@Composable
private fun PlayButtons(primaryLabel: String, onPrimary: () -> Unit, onStartOver: (() -> Unit)?) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
            ButtonIcon(AppIcons.Play)
            Text(text = primaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onStartOver != null) {
            OutlinedButton(onClick = onStartOver, modifier = Modifier.fillMaxWidth()) {
                ButtonIcon(AppIcons.Previous)
                Text(text = stringResource(R.string.vod_start_over), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Dugme „Označi kao odgledano” (film ili cela sezona) preko cele širine. */
@Composable
private fun WatchedButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        ButtonIcon(AppIcons.Check)
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Dugme „Preuzmi” preko cele širine; kad je film već u preuzimanjima, pokazuje stanje i vodi na „Preuzeto”. */
@Composable
private fun DownloadButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        ButtonIcon(AppIcons.Download)
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Dugme „Trejler” preko cele širine; otvara trejler u drugoj aplikaciji. */
@Composable
private fun TrailerButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        ButtonIcon(AppIcons.Movies)
        Text(text = stringResource(R.string.vod_trailer), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ButtonIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier
            .padding(end = ButtonDefaults.IconSpacing)
            .size(ButtonDefaults.IconSize),
    )
}

/** Opis i ekipa. */
@Composable
private fun Overview(plot: String?, credits: List<String>, maxLines: Int = Int.MAX_VALUE) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        plot?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        for (line in credits) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** Epizoda: sličica sa trakom odgledanog, naziv (najviše dva reda) i trajanje ili stanje gledanja. */
@Composable
private fun EpisodeRow(
    episode: Episode,
    progress: WatchProgress?,
    fallbackImage: String?,
    download: DownloadItem?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onDownloads: () -> Unit,
    onToggleWatched: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            val fraction = progress?.takeIf { it.durationMs > 0 }?.let { it.positionMs.toFloat() / it.durationMs }
            if (progress != null && fraction != null && progress.positionMs > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.85f)),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = "${episode.number}. ${episode.title}",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val status = progress?.statusText()
            val duration = episode.durationSeconds?.takeIf { it > 0 }?.let { formatDuration(it * 1000) }
            (status ?: duration)?.let {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    if (progress?.finished == true) {
                        Icon(
                            imageVector = AppIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(14.dp),
                        )
                    }
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // Preuzimanje: strelica dok epizoda nije preuzeta, procenat dok se preuzima; posle vodi na „Preuzeto”.
        val percent = if (download?.state == DownloadState.DOWNLOADING) download.percent else null
        IconButton(onClick = if (download == null) onDownload else onDownloads, modifier = Modifier.size(40.dp)) {
            if (percent != null) {
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            } else {
                Icon(
                    imageVector = AppIcons.Download,
                    contentDescription = download?.shortLabel() ?: stringResource(R.string.download_action),
                    tint = when (download?.state) {
                        null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        DownloadState.DONE -> MaterialTheme.colorScheme.primary
                        DownloadState.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        // Kvačica desno: označava epizodu kao odgledanu, odnosno poništava.
        val watched = progress?.watched == true
        IconButton(onClick = onToggleWatched, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = stringResource(if (watched) R.string.vod_mark_unwatched else R.string.vod_mark_watched),
                tint = if (watched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}
