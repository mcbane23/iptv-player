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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.ContinueItem
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.ui.common.ChannelLogo
import com.bane23.iptvplayer.ui.common.TvStartViewModel
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.rememberNow
import com.bane23.iptvplayer.ui.common.vod.toVodItem
import kotlinx.coroutines.delay
import com.bane23.iptvplayer.ui.common.theme.LocalNeon

/**
 * „Početna” na TV-u: redovi „Nastavi gledanje”, omiljeni kanali sa emisijom koja je sada na
 * programu i nedavno dodati filmovi i serije. Red bez sadržaja se ne prikazuje.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvStartScreen(
    onPlay: (channelId: Long, categoryId: Long?) -> Unit,
    onOpenVod: (item: VodItem, series: Boolean) -> Unit,
    viewModel: TvStartViewModel = hiltViewModel(),
) {
    val continueItems by viewModel.continueItems.collectAsStateWithLifecycle()
    val channelRow by viewModel.channelRow.collectAsStateWithLifecycle()
    val added by viewModel.added.collectAsStateWithLifecycle()
    val now by rememberNow()
    var menuContinue by remember { mutableStateOf<ContinueItem?>(null) }
    // Posle sklanjanja stavke fokus se vraća u redove, da ne ode u bočni meni.
    var refocus by remember { mutableStateOf(0) }
    val rowsFocus = remember { FocusRequester() }
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
    val empty = channelRow != null && channels.isEmpty() && continueItems.isEmpty() &&
        added.loaded && added.movies.isEmpty() && added.series.isEmpty()

    Column(modifier = Modifier.fillMaxSize().padding(top = 27.dp)) {
        TvHeader(
            title = stringResource(R.string.nav_home),
            modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 8.dp),
        )
        if (empty) {
            TvEmptyState(
                icon = AppIcons.Star,
                text = stringResource(R.string.home_empty),
                modifier = Modifier.padding(horizontal = 48.dp),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().focusRequester(rowsFocus).focusGroup(),
            contentPadding = PaddingValues(bottom = 27.dp),
        ) {
            if (continueItems.isNotEmpty()) {
                item(key = "continue") {
                    StartRow(stringResource(R.string.vod_continue_watching)) {
                        items(continueItems, key = { (if (it.isSeries) "s" else "m") + it.id }) { item ->
                            PosterCard(
                                name = item.name,
                                poster = item.poster,
                                progress = item.progress,
                                onClick = { onOpenVod(item.toVodItem(), item.isSeries) },
                                onFocused = {},
                                onLongClick = { menuContinue = item },
                                modifier = Modifier.width(POSTER_WIDTH),
                            )
                        }
                    }
                }
            }
            if (channels.isNotEmpty()) {
                item(key = "channels") {
                    val group = channelRow?.group ?: ChannelGroups.FAVORITES
                    val title = if (group == ChannelGroups.FAVORITES) R.string.home_favorite_channels else R.string.home_recent_channels
                    StartRow(stringResource(title)) {
                        items(channels, key = { it.id }) { channel ->
                            ChannelTile(channel = channel, nowMs = now, onClick = { onPlay(channel.id, group) })
                        }
                    }
                }
            }
            if (added.movies.isNotEmpty()) {
                item(key = "movies") {
                    StartRow(stringResource(R.string.home_added_movies)) {
                        items(added.movies, key = { it.id }) { item ->
                            PosterCard(
                                name = item.name,
                                poster = item.poster,
                                onClick = { onOpenVod(item, false) },
                                onFocused = {},
                                modifier = Modifier.width(POSTER_WIDTH),
                            )
                        }
                    }
                }
            }
            if (added.series.isNotEmpty()) {
                item(key = "series") {
                    StartRow(stringResource(R.string.home_added_series)) {
                        items(added.series, key = { it.id }) { item ->
                            PosterCard(
                                name = item.name,
                                poster = item.poster,
                                onClick = { onOpenVod(item, true) },
                                onFocused = {},
                                modifier = Modifier.width(POSTER_WIDTH),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Jedan red „Početne”: naslov i vodoravan spisak. Fokus se pri povratku u red vraća na istu stavku. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StartRow(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().focusRestorer(),
            // Mesto za uvećan poster u fokusu i margina za overscan.
            contentPadding = PaddingValues(horizontal = 43.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Kanal na „Početnoj”: logo, naziv, emisija koja je sada na programu i tanka traka koliko je odmakla. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelTile(channel: Channel, nowMs: Long, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val content = if (focused) colors.onPrimary else colors.onSurface
    val neon = LocalNeon.current
    Box(
        modifier = Modifier
            .padding(horizontal = 5.dp)
            .width(CHANNEL_WIDTH)
            .height(CHANNEL_HEIGHT)
            .clip(shape)
            .background(if (focused) colors.primary else colors.surface)
            .border(3.dp, if (focused) colors.primary else androidx.compose.ui.graphics.Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
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
                    .background(
                        when {
                            focused -> colors.onPrimary.copy(alpha = 0.6f)
                            else -> neon?.progress ?: colors.primary.copy(alpha = 0.8f)
                        },
                    ),
            )
        }
    }
}

private val POSTER_WIDTH = 132.dp
private val CHANNEL_WIDTH = 240.dp
private val CHANNEL_HEIGHT = 68.dp
private const val REFOCUS_DELAY_MS = 150L
