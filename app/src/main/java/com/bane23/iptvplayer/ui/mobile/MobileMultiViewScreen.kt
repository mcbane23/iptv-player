package com.bane23.iptvplayer.ui.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.ui.common.multiview.MultiPicker
import com.bane23.iptvplayer.ui.common.multiview.MultiTile
import com.bane23.iptvplayer.ui.common.multiview.MultiViewViewModel
import com.bane23.iptvplayer.ui.common.player.AspectMode
import com.bane23.iptvplayer.ui.common.player.KeepScreenOn
import com.bane23.iptvplayer.ui.common.player.VideoSurface

/**
 * Više kanala odjednom na telefonu: 2 ili 4 polja. Dodir bira polje čiji se zvuk čuje, drugi dodir
 * na isto polje otvara kanal preko celog ekrana, a dug pritisak menja kanal u polju.
 */
@Composable
fun MobileMultiViewScreen(
    onOpen: (channelId: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: MultiViewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SectionTopBar(title = stringResource(R.string.multiview_title), onBack = onBack) {
                listOf(2 to R.string.multiview_two, MultiViewViewModel.MAX_TILES to R.string.multiview_four).forEach { (count, label) ->
                    TextButton(onClick = { viewModel.setCount(count) }) {
                        Text(
                            text = stringResource(label),
                            color = if (state.count == count) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                val landscape = maxWidth > maxHeight

                @Composable
                fun tile(index: Int, modifier: Modifier) {
                    val tile = state.tiles[index]
                    Tile(
                        tile = tile,
                        active = state.active == index,
                        modifier = modifier,
                        onClick = {
                            val channel = tile.channel
                            when {
                                channel == null -> viewModel.openPicker(index)
                                tile.status == PlaybackStatus.FAILED -> viewModel.retry(index)
                                state.active == index -> onOpen(channel.id)
                                else -> viewModel.setActive(index)
                            }
                        },
                        onLongClick = { viewModel.openPicker(index) },
                    )
                }

                if (state.count == 2 && !landscape) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        tile(0, Modifier.weight(1f))
                        tile(1, Modifier.weight(1f))
                    }
                } else {
                    val rows = if (state.count == 2) listOf(listOf(0, 1)) else listOf(listOf(0, 1), listOf(2, 3))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        rows.forEach { row ->
                            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { index -> tile(index, Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    tile: MultiTile,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val player = tile.player
    Box(
        modifier = modifier
            .fillMaxSize()
            .border(
                width = 2.dp,
                color = if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (tile.channel != null && player != null) {
            VideoSurface(player = player, aspectMode = AspectMode.FIT, modifier = Modifier.fillMaxSize())
            Text(
                text = (if (active) "🔊 " else "") + tile.channel.name,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
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
                color = Color.White.copy(alpha = 0.7f),
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
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SectionTopBar(
                title = picker.categoryName ?: stringResource(R.string.multiview_pick),
                onBack = { viewModel.pickerBack() },
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (picker.categoryId == null) {
                    items(categories, key = { it.id }) { category ->
                        PickerRow(category.name, category.channelCount.toString()) { viewModel.pickCategory(category) }
                    }
                } else {
                    items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                        val channel = channels[index]
                        PickerRow(channel?.name.orEmpty(), null) { channel?.let(viewModel::pickChannel) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(text: String, trailing: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
