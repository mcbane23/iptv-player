package com.bane23.iptvplayer.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.ui.common.multiview.MultiPicker
import com.bane23.iptvplayer.ui.common.multiview.MultiTile
import com.bane23.iptvplayer.ui.common.multiview.MultiViewViewModel
import com.bane23.iptvplayer.ui.common.player.AspectMode
import com.bane23.iptvplayer.ui.common.player.KeepScreenOn
import com.bane23.iptvplayer.ui.common.player.VideoSurface

/**
 * Više kanala odjednom na TV-u: 2 ili 4 polja. Zvuk ide sa polja koje ima fokus, OK ga otvara
 * preko celog ekrana (na praznom polju bira kanal), a dug pritisak na OK menja kanal u polju.
 */
@Composable
fun TvMultiViewScreen(onOpen: (channelId: Long) -> Unit, viewModel: MultiViewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tileFocus = remember { List(MultiViewViewModel.MAX_TILES) { FocusRequester() } }
    val picker = state.picker

    KeepScreenOn()
    DisposableEffect(Unit) {
        viewModel.onForeground()
        onDispose { viewModel.onBackground() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    BackHandler(enabled = picker != null) { viewModel.pickerBack() }

    if (picker != null) {
        Picker(picker, viewModel)
        return
    }
    // Posle izbora kanala (i pri otvaranju ekrana) fokus ide na aktivno polje.
    LaunchedEffect(Unit) { tileFocus[state.active].tryRequestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(2 to R.string.multiview_two, MultiViewViewModel.MAX_TILES to R.string.multiview_four).forEach { (count, label) ->
                Box(modifier = Modifier.padding(0.dp)) {
                    TvCountButton(stringResource(label), selected = state.count == count) { viewModel.setCount(count) }
                }
            }
            Text(
                text = stringResource(R.string.multiview_hint_tv),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        val rows = if (state.count == 2) listOf(listOf(0, 1)) else listOf(listOf(0, 1), listOf(2, 3))
        rows.forEach { row ->
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { index ->
                    val tile = state.tiles[index]
                    Tile(
                        tile = tile,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(tileFocus[index]),
                        onFocused = { viewModel.setActive(index) },
                        onClick = {
                            val channel = tile.channel
                            when {
                                channel == null -> viewModel.openPicker(index)
                                tile.status == PlaybackStatus.FAILED -> viewModel.retry(index)
                                else -> onOpen(channel.id)
                            }
                        },
                        onLongClick = { viewModel.openPicker(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvCountButton(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Text(
        text = label,
        color = if (focused) colors.onPrimary else Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .background(
                when {
                    focused -> colors.primary
                    selected -> Color.White.copy(alpha = 0.25f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .combinedClickableCompat(onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier =
    combinedClickable(onClick = onClick, onLongClick = onLongClick)

@Composable
private fun Tile(
    tile: MultiTile,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val player = tile.player
    Box(
        modifier = modifier
            .fillMaxSize()
            .border(
                width = 3.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
            )
            .onFocusChanged {
                if (it.isFocused && !focused) onFocused()
                focused = it.isFocused
            }
            .combinedClickableCompat(onClick, onLongClick)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (tile.channel != null && player != null) {
            VideoSurface(player = player, aspectMode = AspectMode.FIT, modifier = Modifier.fillMaxSize())
            Text(
                text = (if (focused) "🔊 " else "") + tile.channel.name,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            if (tile.status == PlaybackStatus.FAILED) {
                Text(
                    text = stringResource(R.string.multiview_failed),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(8.dp),
                )
            }
        } else {
            Text(
                text = stringResource(R.string.multiview_add),
                color = Color.White.copy(alpha = if (focused) 1f else 0.6f),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** Izbor kanala za jedno polje: spisak kategorija, pa kanali izabrane kategorije. */
@Composable
private fun Picker(picker: MultiPicker, viewModel: MultiViewViewModel) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val channels = viewModel.pickerChannels.collectAsLazyPagingItems()
    val firstRow = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Text(
            text = picker.categoryName ?: stringResource(R.string.multiview_pick),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        if (picker.categoryId == null) {
            LazyColumn(modifier = Modifier.fillMaxWidth(0.6f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(categories, key = { it.id }) { category ->
                    TvTextRow(
                        text = category.name,
                        trailing = category.channelCount.toString(),
                        onClick = { viewModel.pickCategory(category) },
                        modifier = if (category === categories.firstOrNull()) Modifier.focusRequester(firstRow) else Modifier,
                    )
                }
            }
            LaunchedEffect(categories.isEmpty()) { firstRow.tryRequestFocus() }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth(0.6f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                    val channel = channels[index]
                    TvTextRow(
                        text = channel?.name.orEmpty(),
                        onClick = { channel?.let(viewModel::pickChannel) },
                        modifier = if (index == 0) Modifier.focusRequester(firstRow) else Modifier,
                    )
                }
            }
            LaunchedEffect(channels.itemCount > 0) { firstRow.tryRequestFocus() }
        }
    }
}
