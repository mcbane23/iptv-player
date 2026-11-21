package com.bane23.iptvplayer.ui.common.vod

import com.bane23.iptvplayer.ui.common.FailedImages
import com.bane23.iptvplayer.ui.common.AppMessages
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalContext
import com.bane23.iptvplayer.ui.common.theme.OnBackground
import com.bane23.iptvplayer.ui.common.theme.LocalPlaceholderColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.bane23.iptvplayer.ui.common.theme.OnSurfaceVariant
import coil3.compose.AsyncImage
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.ContinueItem
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.data.repo.WatchProgress
import com.bane23.iptvplayer.data.xtream.Episode
import com.bane23.iptvplayer.data.xtream.MovieDetails
import com.bane23.iptvplayer.data.xtream.SeriesDetails
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.player.formatDuration

/**
 * Poster filma ili serije u odnosu 2:3. Dok se učitava ostaje tamno polje; ako postera nema ili ne
 * može da se učita, na tamnom prelivu piše [name]. [progress] (0 do 1) crta tanku traku na dnu:
 * koliko je odgledano.
 */
@Composable
fun Poster(
    url: String?,
    modifier: Modifier = Modifier,
    name: String? = null,
    progress: Float? = null,
    watched: Boolean = false,
) {
    var failed by remember(url) { mutableStateOf(FailedImages.contains(url)) }
    Box(
        modifier = modifier
            .aspectRatio(POSTER_RATIO)
            .clip(RoundedCornerShape(8.dp))
            .background(LocalPlaceholderColors.current.image),
    ) {
        if (!url.isNullOrBlank() && !failed) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = {
                    FailedImages.add(url, it)
                    failed = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!name.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(LocalPlaceholderColors.current.gradientTop, LocalPlaceholderColors.current.gradientBottom)))
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = name,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = OnSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
        if (watched) {
            Image(
                imageVector = AppIcons.Check,
                contentDescription = stringResource(R.string.vod_watched),
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(WATCHED_BADGE, CircleShape)
                    .padding(4.dp),
            )
        }
        if (progress != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.Black.copy(alpha = 0.5f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.85f)),
                )
            }
        }
    }
}

/** Stavka „Nastavi gledanje” kao stavka spiska, da bi otvorila iste detalje. */
fun ContinueItem.toVodItem() = VodItem(id = id, name = name, poster = poster, rating = null, extension = extension)

private const val POSTER_RATIO = 2f / 3f

/** Zelena podloga kvačice „odgledano” na posteru, ista u svetloj i tamnoj temi. */
private val WATCHED_BADGE = Color(0xE02E7D32)

/** Kratki podaci o filmu u jednom redu: ocena, godina, trajanje. Žanrovi idu posebno, vidi [GenreChips]. */
fun MovieDetails.metaLine(): String = listOfNotNull(
    rating?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "★ $it" },
    releaseDate?.take(4)?.takeIf { year -> year.all { it.isDigit() } },
    durationSeconds?.let { formatDuration(it * 1000) },
).joinToString(" · ")

fun SeriesDetails.metaLine(): String = listOfNotNull(
    rating?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "★ $it" },
    releaseDate?.take(4)?.takeIf { year -> year.all { it.isDigit() } },
).joinToString(" · ")

/** Žanrovi iz teksta koji šalje panel („Drama, Misterija” ili „Drama / Misterija”), najviše [limit]. */
fun genresOf(genre: String?, limit: Int = 4): List<String> =
    genre.orEmpty().split(',', '/', '|').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(limit)

/** Žanrovi kao male pločice u jednom redu; bez žanrova ne crta ništa. */
@Composable
fun GenreChips(genre: String?, modifier: Modifier = Modifier) {
    val genres = genresOf(genre)
    if (genres.isEmpty()) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        genres.forEach { name ->
            BasicText(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = OnBackground, fontSize = 12.sp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .background(LocalPlaceholderColors.current.logo.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * Otvara trejler u aplikaciji koja ume da ga pusti (YouTube ili pregledač). Kad takve nema, što je
 * na nekim TV uređajima slučaj, prikaže kratku poruku.
 */
@Composable
fun rememberOpenTrailer(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                AppMessages.show(context, R.string.vod_trailer_no_app)
            }
        }
    }
}

/** Oznaka epizode, npr. „S2 E5”. */
@Composable
fun episodeLabel(season: Int, number: Int): String = stringResource(R.string.vod_episode_label, season, number)

@Composable
fun Episode.label(): String = episodeLabel(season, number)

/** Šta piše pored epizode ili filma o tome dokle se stiglo, ili null ako gledanje nije ni počelo. */
@Composable
fun WatchProgress.statusText(): String? = when {
    resumable -> stringResource(R.string.vod_progress, formatDuration(positionMs), formatDuration(durationMs))
    finished -> stringResource(R.string.vod_watched)
    else -> null
}

/**
 * Učitavanje filmova ili serija, isto na telefonu i TV-u: krug u akcentnoj boji i kratka poruka u
 * sredini. [hint] je druga, sitnija rečenica (samo pri prvom učitavanju sekcije).
 */
@Composable
fun VodLoading(series: Boolean, accent: Color, modifier: Modifier = Modifier, hint: String? = null) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            color = accent,
            trackColor = accent.copy(alpha = 0.18f),
            strokeWidth = 4.dp,
            modifier = Modifier.size(44.dp),
        )
        BasicText(
            text = stringResource(if (series) R.string.vod_loading_series else R.string.vod_loading_movies),
            style = TextStyle(color = OnBackground, fontSize = 17.sp, textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = 16.dp),
        )
        if (hint != null) {
            BasicText(
                text = hint,
                style = TextStyle(color = com.bane23.iptvplayer.ui.common.theme.OnSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center),
                modifier = Modifier.padding(top = 8.dp).widthIn(max = 280.dp),
            )
        }
    }
}

/** Druga rečenica poruke pri prvom učitavanju sekcije. */
@Composable
fun vodLoadingHint(storedOnDevice: Boolean): String =
    stringResource(if (storedOnDevice) R.string.vod_loading_hint_stored else R.string.vod_loading_hint)
