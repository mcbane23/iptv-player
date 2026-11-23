package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.ContinueItem
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.data.repo.VodKind
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.ui.common.ChannelLogo
import com.bane23.iptvplayer.ui.common.StartPreview
import com.bane23.iptvplayer.ui.common.TvStartViewModel
import com.bane23.iptvplayer.ui.common.guide.formatTime
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.rememberNow
import com.bane23.iptvplayer.ui.common.vod.VodLoading
import com.bane23.iptvplayer.ui.common.vod.toVodItem
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import com.bane23.iptvplayer.ui.common.theme.fadeMask
import com.bane23.iptvplayer.ui.common.theme.Glass
import com.bane23.iptvplayer.ui.common.theme.glass
import com.bane23.iptvplayer.ui.common.theme.glassSelected
import com.bane23.iptvplayer.ui.common.theme.glassFocused
import com.bane23.iptvplayer.ui.common.theme.glassState
import com.bane23.iptvplayer.ui.common.theme.focusHalo

/**
 * „Početna” na TV-u: redovi „Nastavi gledanje”, omiljeni kanali sa emisijom koja je sada na
 * programu i nedavno dodati filmovi i serije. Red bez sadržaja se ne prikazuje.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun TvStartScreen(
    onPlay: (channelId: Long, categoryId: Long?) -> Unit,
    onOpenVod: (item: VodItem, series: Boolean) -> Unit,
    autoFocus: Boolean = false,
    onAutoFocused: () -> Unit = {},
    viewModel: TvStartViewModel = hiltViewModel(),
) {
    val continueItems by viewModel.continueItems.collectAsStateWithLifecycle()
    val channelRow by viewModel.channelRow.collectAsStateWithLifecycle()
    val added by viewModel.added.collectAsStateWithLifecycle()
    val favoriteMovies by viewModel.favoriteMovies.collectAsStateWithLifecycle()
    val favoriteSeries by viewModel.favoriteSeries.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val now by rememberNow()
    var menuContinue by remember { mutableStateOf<ContinueItem?>(null) }
    // Posle sklanjanja stavke fokus se vraća u redove, da ne ode u bočni meni.
    var refocus by remember { mutableStateOf(0) }
    val rowsFocus = remember { FocusRequester() }
    // Stavka na kojoj je fokus bio poslednji put. Posle povratka dugmetom Nazad (iz detalja filma ili
    // serije, iz plejera) fokus se vraća baš na nju, da ne ode u bočni meni.
    var lastFocusKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Sa „Početne” je nešto otvoreno, pa se pri sledećem prikazu fokus vraća na tu stavku.
    var returning by rememberSaveable { mutableStateOf(false) }
    val itemFocus = remember { FocusRequester() }
    val lastKey: () -> String? = { lastFocusKey }
    // Ovaj prikaz je povratak sa otvorene stavke: fokus na prvu stavku ([autoFocus]) tada ne važi.
    val cameBack = remember { BooleanArray(1) { returning } }
    LaunchedEffect(Unit) {
        if (!returning) return@LaunchedEffect
        returning = false
        // Redovi se slažu tek pri prvom crtanju, pa se pokušava nekoliko kadrova zaredom.
        repeat(RETURN_FOCUS_FRAMES) {
            if (itemFocus.tryRequestFocus()) return@LaunchedEffect
            withFrameNanos { }
        }
        // Stavke više nema u redovima (npr. odgledan film): fokus ide na prvu stavku.
        rowsFocus.tryRequestFocus()
    }
    LaunchedEffect(refocus) {
        if (refocus == 0) return@LaunchedEffect
        delay(REFOCUS_DELAY_MS)
        rowsFocus.tryRequestFocus()
    }
    menuContinue?.let { item ->
        TvActionDialog(
            title = item.name,
            options = listOf(stringResource(R.string.vod_continue_remove)),
            onSelect = {
                viewModel.dismissContinue(item)
                refocus++
            },
            onClose = { menuContinue = null },
        )
    }

    val channels = channelRow?.channels.orEmpty()
    // Red kanala stiže malo posle ostalih. Ostali redovi ga kratko sačekaju, da ih on ne gurne nadole
    // kad se pojavi; ako kasni duže, prikazuju se bez njega.
    var channelsWaited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(CHANNELS_WAIT_MS)
        channelsWaited = true
    }
    val holdRows = channelRow == null && !channelsWaited
    val nothing = holdRows ||
        (channels.isEmpty() && continueItems.isEmpty() && added.movies.isEmpty() && added.series.isEmpty())
    val empty = channelRow != null && nothing && added.loaded
    // Poruka o učitavanju tek posle kratkog čekanja, da ne zatreperi kad redovi stignu odmah.
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_DELAY_MS)
        waited = true
    }

    // Fokus odmah na prvu stavku prvog reda čim redovi stignu (pokretanje aplikacije, povratak sa Nazad).
    LaunchedEffect(autoFocus, nothing) {
        if (!autoFocus || nothing) return@LaunchedEffect
        if (cameBack[0]) {
            cameBack[0] = false
            onAutoFocused()
            return@LaunchedEffect
        }
        delay(REFOCUS_DELAY_MS)
        if (rowsFocus.tryRequestFocus()) onAutoFocused()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (empty || nothing) {
            TvHeader(
                title = stringResource(R.string.nav_home),
                modifier = Modifier.padding(start = 48.dp, top = 27.dp, end = 48.dp, bottom = 8.dp),
            )
        }
        if (empty) {
            TvEmptyState(
                icon = AppIcons.Star,
                text = stringResource(R.string.home_empty),
                modifier = Modifier.padding(horizontal = 48.dp),
            )
            return@Column
        }
        if (nothing) {
            if (waited && !holdRows) {
                VodLoading(
                    series = added.loading == VodKind.SERIES,
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            return@Column
        }
        StartHero(preview = preview, nowMs = now)
        // Redovi se pomeraju red po red: red sa fokusom je uvek ceo na vrhu, ispod zaglavlja (Bane).
        // Spisak se zato ne pomera sam za fokusom, nego ga [showRow] dovodi na red čim stavka dobije fokus.
        val rowsState = rememberLazyListState()
        val rowsScope = rememberCoroutineScope()
        val edgeSpec = LocalBringIntoViewSpec.current
        // Redni broj reda se računa iz toga koji redovi postoje, a ne iz onoga što je na ekranu: red iznad,
        // na koji fokus pređe strelicom Gore, u tom trenutku još nije među vidljivim redovima.
        val rowKeys by rememberUpdatedState(
            listOfNotNull(
                "continue".takeIf { continueItems.isNotEmpty() },
                "channels".takeIf { channels.isNotEmpty() },
                "movies".takeIf { added.movies.isNotEmpty() },
                "series".takeIf { added.series.isNotEmpty() },
            ),
        )
        val showRow: (String) -> Unit = { rowKey ->
            val index = rowKeys.indexOf(rowKey)
            if (index >= 0 && (index != rowsState.firstVisibleItemIndex || rowsState.firstVisibleItemScrollOffset != 0)) {
                rowsScope.launch { rowsState.animateScrollToItem(index) }
            }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides NoFocusScroll) {
        LazyColumn(
            state = rowsState,
            modifier = Modifier.fillMaxSize().focusRequester(rowsFocus).focusGroup(),
            contentPadding = PaddingValues(bottom = 27.dp),
        ) {
            if (continueItems.isNotEmpty()) {
                item(key = "continue") {
                    StartRow(stringResource(R.string.vod_continue_watching), edgeSpec) {
                        items(continueItems, key = { (if (it.isSeries) "s" else "m") + it.id }) { item ->
                            val focusKey = "continue-" + (if (item.isSeries) "s" else "m") + item.id
                            PosterCard(
                                name = item.name,
                                poster = item.poster,
                                progress = item.progress,
                                onClick = {
                                    returning = true
                                    onOpenVod(item.toVodItem(), item.isSeries)
                                },
                                onFocused = {
                                    lastFocusKey = focusKey
                                    showRow("continue")
                                    viewModel.focusVod(item.id, item.name, item.poster, item.isSeries)
                                },
                                onLongClick = { menuContinue = item },
                                modifier = Modifier.width(POSTER_WIDTH).returnFocus(isLastFocused(focusKey, lastKey), itemFocus),
                            )
                        }
                    }
                }
            }
            if (channels.isNotEmpty()) {
                item(key = "channels") {
                    val group = channelRow?.group ?: ChannelGroups.FAVORITES
                    val title = if (group == ChannelGroups.FAVORITES) R.string.home_favorite_channels else R.string.home_recent_channels
                    StartRow(stringResource(title), edgeSpec) {
                        items(channels, key = { it.id }) { channel ->
                            val focusKey = "channel-${channel.id}"
                            ChannelTile(
                                channel = channel,
                                nowMs = now,
                                onClick = {
                                    returning = true
                                    onPlay(channel.id, group)
                                },
                                onFocused = {
                                    lastFocusKey = focusKey
                                    showRow("channels")
                                    viewModel.focusChannel(channel)
                                },
                                modifier = Modifier.returnFocus(isLastFocused(focusKey, lastKey), itemFocus),
                            )
                        }
                    }
                }
            }
            if (added.movies.isNotEmpty()) {
                item(key = "movies") {
                    StartRow(stringResource(R.string.home_added_movies), edgeSpec) {
                        items(added.movies, key = { it.id }) { item ->
                            val focusKey = "movies-${item.id}"
                            PosterCard(
                                name = item.name,
                                poster = item.poster,
                                onClick = {
                                    returning = true
                                    onOpenVod(item, false)
                                },
                                onFocused = {
                                    lastFocusKey = focusKey
                                    showRow("movies")
                                    viewModel.focusVod(item.id, item.name, item.poster, series = false, year = item.year, rating = item.rating)
                                },
                                favorite = item.id in favoriteMovies,
                                onToggleFavorite = { viewModel.toggleFavorite(item, series = false) },
                                modifier = Modifier.width(POSTER_WIDTH).returnFocus(isLastFocused(focusKey, lastKey), itemFocus),
                            )
                        }
                    }
                }
            }
            if (added.series.isNotEmpty()) {
                item(key = "series") {
                    StartRow(stringResource(R.string.home_added_series), edgeSpec) {
                        items(added.series, key = { it.id }) { item ->
                            val focusKey = "series-${item.id}"
                            PosterCard(
                                name = item.name,
                                poster = item.poster,
                                onClick = {
                                    returning = true
                                    onOpenVod(item, true)
                                },
                                onFocused = {
                                    lastFocusKey = focusKey
                                    showRow("series")
                                    viewModel.focusVod(item.id, item.name, item.poster, series = true, year = item.year, rating = item.rating)
                                },
                                favorite = item.id in favoriteSeries,
                                onToggleFavorite = { viewModel.toggleFavorite(item, series = true) },
                                modifier = Modifier.width(POSTER_WIDTH).returnFocus(isLastFocused(focusKey, lastKey), itemFocus),
                            )
                        }
                    }
                }
            }
            // Prvo preuzimanje spiska sa servera (na uređaju još nema sačuvanog reda).
            added.loading?.let { kind ->
                item(key = "loading") { LoadingRow(series = kind == VodKind.SERIES) }
            }
            // Prazno mesto na kraju, da i poslednji red može da dođe na vrh.
            item(key = "end") { Spacer(modifier = Modifier.fillParentMaxHeight()) }
        }
        }
    }
}

/** Mala poruka ispod redova dok se red „nedavno dodato” prvi put preuzima. */
/**
 * Veliki prikaz stavke u fokusu, iznad redova: naziv, godina, ocena, žanr i opis filma ili serije sa
 * slikom na desnoj strani, odnosno logo kanala i emisija koja traje. Visina je stalna, da redovi
 * ispod ne skaču dok opis stiže. Dok fokus nije ni na jednoj stavci, tu je naslov ekrana.
 */
@Composable
private fun StartHero(preview: StartPreview?, nowMs: Long) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth().height(HERO_HEIGHT)) {
        val image = preview?.image
        if (preview != null && !preview.channel && !image.isNullOrBlank()) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.62f)
                    // Slika se ka levo i nadole pretapa u pozadinu ekrana (preliv), bez ravne boje preko nje.
                    .fadeMask(
                        opacity = if (preview.wide) 0.75f else 0.35f,
                        Brush.horizontalGradient(0f to Color.Transparent, 0.6f to Color.Black.copy(alpha = 0.8f), 1f to Color.Black),
                        Brush.verticalGradient(0.55f to Color.Black, 1f to Color.Transparent),
                    ),
            )
        }
        TvClock(modifier = Modifier.align(Alignment.TopEnd).padding(top = 27.dp, end = 48.dp))
        if (preview == null) {
            Text(
                text = stringResource(R.string.nav_home),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onBackground,
                modifier = Modifier.padding(start = 48.dp, top = 27.dp),
            )
            return@Box
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 48.dp, top = 20.dp)
                .fillMaxWidth(0.58f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (preview.channel) {
                ChannelLogo(url = preview.image, name = preview.title, size = 64.dp)
            }
            Column(modifier = Modifier.padding(start = if (preview.channel) 18.dp else 0.dp)) {
                Text(
                    text = preview.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = if (preview.channel) {
                    val start = preview.startMs
                    val stop = preview.stopMs
                    if (start != null && stop != null && stop > nowMs) formatTime(start) + " – " + formatTime(stop) else ""
                } else {
                    listOfNotNull(
                        preview.year.takeIf { it > 0 }?.toString(),
                        preview.rating?.let { "★ $it" },
                        preview.genre,
                    ).joinToString(" · ")
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                preview.text?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = if (preview.channel) 1 else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingRow(series: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.padding(start = 48.dp, end = 48.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            color = accent,
            trackColor = accent.copy(alpha = 0.18f),
            strokeWidth = 3.dp,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = stringResource(if (series) R.string.vod_loading_series else R.string.vod_loading_movies),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Jedan red „Početne”: naslov i vodoravan spisak. Fokus se pri povratku u red vraća na istu stavku. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun StartRow(
    title: String,
    // Vodoravno se red i dalje pomera za fokusom, kao svi spiskovi na TV-u.
    scrollSpec: BringIntoViewSpec,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides scrollSpec) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().focusRestorer(),
                // Mesto za uvećan poster u fokusu i margina za overscan.
                contentPadding = PaddingValues(horizontal = 43.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/**
 * Da li je stavka [key] ona na kojoj je fokus bio poslednji. Stavka prati samo ovaj odgovor, a ne samu
 * vrednost, pa se pri koraku D-pada iznova crtaju samo dve stavke, a ne svi redovi.
 */
@Composable
private fun isLastFocused(key: String, lastKey: () -> String?): Boolean =
    remember(key) { derivedStateOf { lastKey() == key } }.value

/** Samo stavka na koju se fokus vraća nosi [requester]. */
private fun Modifier.returnFocus(isTarget: Boolean, requester: FocusRequester): Modifier =
    if (isTarget) focusRequester(requester) else this

/** Kanal na „Početnoj”: logo, naziv, emisija koja je sada na programu i tanka traka koliko je odmakla. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelTile(
    channel: Channel,
    nowMs: Long,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = Glass.RowShape
    val content = if (focused) colors.onPrimary else colors.onSurface
    Box(
        modifier = modifier
            .padding(horizontal = 5.dp)
            .width(CHANNEL_WIDTH)
            .height(CHANNEL_HEIGHT)
            .focusHalo(focused, colors.primary)
            .clip(shape)
            .glassState(colors.primary, focused, selected = false, shape = shape)
            .onFocusChanged {
                if (it.isFocused && !focused) onFocused()
                focused = it.isFocused
            }
            .combinedClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .tvItemSemantics(listOfNotNull(channel.name, channel.nowTitle).joinToString(", "))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(url = channel.logo, name = channel.name, size = 40.dp)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                channel.nowTitle?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        channel.nowProgress(nowMs)?.let { progress ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(if (focused) colors.onPrimary.copy(alpha = 0.6f) else colors.primary.copy(alpha = 0.8f)),
            )
        }
    }
}

private val POSTER_WIDTH = 132.dp
private val HERO_HEIGHT = 176.dp
private val CHANNEL_WIDTH = 240.dp
private val CHANNEL_HEIGHT = 68.dp
private const val REFOCUS_DELAY_MS = 150L
private const val RETURN_FOCUS_FRAMES = 30
private const val LOADING_DELAY_MS = 300L
private const val CHANNELS_WAIT_MS = 700L
