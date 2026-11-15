package com.bane23.iptvplayer.ui.common.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.WatchProgress
import com.bane23.iptvplayer.data.xtream.Episode
import com.bane23.iptvplayer.data.xtream.MovieDetails
import com.bane23.iptvplayer.data.xtream.SeriesDetails
import com.bane23.iptvplayer.ui.common.player.formatDuration

/** Poster filma ili serije u odnosu 2:3. Dok se ne učita (ili ako ga nema) ostaje tamno polje. */
@Composable
fun Poster(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(POSTER_RATIO)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF22262E)),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val POSTER_RATIO = 2f / 3f

/** Kratki podaci o filmu u jednom redu: godina, trajanje, ocena, žanr. */
fun MovieDetails.metaLine(): String = listOfNotNull(
    releaseDate?.take(4)?.takeIf { year -> year.all { it.isDigit() } },
    durationSeconds?.let { formatDuration(it * 1000) },
    rating?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "★ $it" },
    genre,
).joinToString(" · ")

fun SeriesDetails.metaLine(): String = listOfNotNull(
    releaseDate?.take(4)?.takeIf { year -> year.all { it.isDigit() } },
    rating?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "★ $it" },
    genre,
).joinToString(" · ")

/** Oznaka epizode, npr. „S2 E5”. */
@Composable
fun episodeLabel(season: Int, number: Int): String = stringResource(R.string.vod_episode_label, season, number)

@Composable
fun Episode.label(): String = episodeLabel(season, number)

/** Šta piše pored epizode ili filma o tome dokle se stiglo, ili null ako gledanje nije ni počelo. */
@Composable
fun WatchProgress.statusText(): String? = when {
    finished -> stringResource(R.string.vod_watched)
    resumable -> stringResource(R.string.vod_progress, formatDuration(positionMs), formatDuration(durationMs))
    else -> null
}
