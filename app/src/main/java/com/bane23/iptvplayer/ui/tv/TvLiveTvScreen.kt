package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.ui.common.ChannelListViewModel
import com.bane23.iptvplayer.ui.common.ChannelLogo
import com.bane23.iptvplayer.ui.common.LiveTvViewModel
import kotlinx.coroutines.delay

/** Oznaka za „Svi kanali” u izboru kategorije. */
private const val ALL_CHANNELS = 0L

/**
 * Početni ekran na TV-u: kategorije levo, kanali izabrane kategorije desno.
 * Kategorija se bira samim prelaskom fokusa na nju; desno se ide u kanale, OK pušta kanal.
 */
@Composable
fun TvLiveTvScreen(
    onPlay: (channelId: Long, categoryId: Long?) -> Unit,
    onManagePlaylists: () -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
    channelsViewModel: ChannelListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedCategory by rememberSaveable { mutableStateOf(ALL_CHANNELS) }
    var focusedCategory by remember { mutableStateOf(selectedCategory) }
    val firstFocus = remember { FocusRequester() }
    val channelFocus = remember { FocusRequester() }
    // Kanal na kome je fokus bio poslednji put, da se posle povratka iz plejera nastavi odatle.
    var lastChannelId by rememberSaveable { mutableStateOf<Long?>(null) }
    val playlistId = state.playlistId

    // Kratka pauza pre učitavanja kanala, da brzo listanje kroz kategorije ne pokreće upit za svaku.
    LaunchedEffect(focusedCategory) {
        delay(CATEGORY_FOCUS_DELAY_MS)
        selectedCategory = focusedCategory
    }
    LaunchedEffect(playlistId, selectedCategory) {
        if (playlistId != null) {
            channelsViewModel.select(playlistId, selectedCategory.takeIf { it != ALL_CHANNELS })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.live_tv_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(end = 24.dp),
            )
        }

        when {
            state.loading -> Unit

            playlistId == null -> {
                Text(
                    text = stringResource(R.string.playlists_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                // Početni fokus mora biti na nekom elementu, inače daljinski ne radi ništa.
                Button(onClick = onManagePlaylists, modifier = Modifier.focusRequester(firstFocus)) {
                    Text(stringResource(R.string.action_add_playlist))
                }
                LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
            }

            else -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LazyColumn(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(key = ChannelGroups.FAVORITES) {
                        TvTextRow(
                            text = stringResource(R.string.category_favorites),
                            trailing = state.favoriteCount.toString(),
                            selected = selectedCategory == ChannelGroups.FAVORITES,
                            onFocused = { focusedCategory = ChannelGroups.FAVORITES; lastChannelId = null },
                            onClick = { focusedCategory = ChannelGroups.FAVORITES },
                            modifier = focusTarget(selectedCategory == ChannelGroups.FAVORITES, firstFocus),
                        )
                    }
                    if (state.recentCount > 0) {
                        item(key = ChannelGroups.RECENT) {
                            TvTextRow(
                                text = stringResource(R.string.category_recent),
                                trailing = state.recentCount.toString(),
                                selected = selectedCategory == ChannelGroups.RECENT,
                                onFocused = { focusedCategory = ChannelGroups.RECENT; lastChannelId = null },
                                onClick = { focusedCategory = ChannelGroups.RECENT },
                                modifier = focusTarget(selectedCategory == ChannelGroups.RECENT, firstFocus),
                            )
                        }
                    }
                    item(key = ALL_CHANNELS) {
                        TvTextRow(
                            text = stringResource(R.string.category_all),
                            trailing = state.totalChannels.toString(),
                            selected = selectedCategory == ALL_CHANNELS,
                            onFocused = { focusedCategory = ALL_CHANNELS; lastChannelId = null },
                            onClick = { focusedCategory = ALL_CHANNELS },
                            modifier = focusTarget(selectedCategory == ALL_CHANNELS, firstFocus),
                        )
                    }
                    items(state.categories, key = { it.id }) { category ->
                        TvTextRow(
                            text = category.name,
                            trailing = category.channelCount.toString(),
                            selected = selectedCategory == category.id,
                            onFocused = { focusedCategory = category.id; lastChannelId = null },
                            onClick = { focusedCategory = category.id },
                            modifier = focusTarget(selectedCategory == category.id, firstFocus),
                        )
                    }
                }

                val channels = channelsViewModel.channels.collectAsLazyPagingItems()
                Column(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight(),
                ) {
                Text(
                    text = stringResource(R.string.favorite_tv_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                        val channel = channels[index]
                        TvListRow(
                            onClick = {
                                channel?.let {
                                    onPlay(it.id, selectedCategory.takeIf { id -> id != ALL_CHANNELS })
                                }
                            },
                            onLongClick = { channel?.let { channelsViewModel.toggleFavorite(it.id) } },
                            onFocused = { channel?.let { lastChannelId = it.id } },
                            modifier = focusTarget(channel != null && channel.id == lastChannelId, channelFocus),
                        ) { color ->
                            ChannelLogo(url = channel?.logo, size = 36.dp)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp),
                            ) {
                                Text(
                                    text = channel?.name.orEmpty(),
                                    color = color,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                channel?.nowTitle?.let { nowTitle ->
                                    Text(
                                        text = nowTitle,
                                        color = color.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (channel?.favorite == true) {
                                Text(text = "★", color = color, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
                }
                // Posle povratka iz plejera fokus ide na kanal koji je gledan, inače na izabranu kategoriju.
                LaunchedEffect(Unit) {
                    if (lastChannelId != null) {
                        // Kanali se učitavaju sa malim zakašnjenjem, pa se pokušava nekoliko puta.
                        repeat(FOCUS_RESTORE_ATTEMPTS) {
                            if (channelFocus.tryRequestFocus()) return@LaunchedEffect
                            delay(FOCUS_RESTORE_STEP_MS)
                        }
                    }
                    firstFocus.tryRequestFocus()
                }
            }
        }
    }
}

/** Samo izabrani red nosi [requester], da bi početni fokus išao baš na njega. */
private fun focusTarget(isTarget: Boolean, requester: FocusRequester): Modifier =
    if (isTarget) Modifier.focusRequester(requester) else Modifier

private const val CATEGORY_FOCUS_DELAY_MS = 250L
private const val FOCUS_RESTORE_ATTEMPTS = 10
private const val FOCUS_RESTORE_STEP_MS = 60L
