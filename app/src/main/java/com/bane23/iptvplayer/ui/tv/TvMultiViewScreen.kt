package com.bane23.iptvplayer.ui.tv

import android.view.KeyEvent as AndroidKeyEvent
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.bane23.iptvplayer.ui.common.multiview.ConnectionLimitWarning
import com.bane23.iptvplayer.ui.common.multiview.MultiViewViewModel
import kotlinx.coroutines.delay
import com.bane23.iptvplayer.ui.common.multiview.label
import com.bane23.iptvplayer.ui.common.player.AspectMode
import com.bane23.iptvplayer.ui.common.player.KeepScreenOn
import com.bane23.iptvplayer.ui.common.player.VideoSurface

/**
 * Više kanala odjednom na TV-u: 2 ili 4 polja preko celog ekrana. Zvuk ide sa polja koje ima
 * fokus, a OK otvara opcije preko slike (ceo ekran, promena kanala, broj polja).
 */
@Composable
fun TvMultiViewScreen(onOpen: (channelId: Long) -> Unit, viewModel: MultiViewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tileFocus = remember { List(MultiViewViewModel.MAX_TILES) { FocusRequester() } }
    val picker = state.picker
    // Polje za koje su opcije otvorene, ili null.
    var menu by remember { mutableStateOf<Int?>(null) }

    KeepScreenOn()
    DisposableEffect(Unit) {
        viewModel.onForeground()
        onDispose { viewModel.onBackground() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
    BackHandler(enabled = picker != null || menu != null) {
        if (picker != null) viewModel.pickerBack() else menu = null
    }

    if (picker != null) {
        Picker(picker, viewModel)
        return
    }
    // Pri otvaranju ekrana i posle zatvaranja opcija ili izbora kanala fokus ide na aktivno polje.
    LaunchedEffect(menu == null) {
        if (menu == null) tileFocus[state.active].tryRequestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val rows = if (state.count == 2) listOf(listOf(0, 1)) else listOf(listOf(0, 1), listOf(2, 3))
            rows.forEach { row ->
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { index ->
                        Tile(
                            tile = state.tiles[index],
                            showInfo = state.showInfo,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(tileFocus[index]),
                            onFocused = { viewModel.setActive(index) },
                            onClick = { menu = index },
                            onLongClick = { viewModel.openPicker(index) },
                        )
                    }
                }
            }
        }
        // Upozorenje se prikaže na kratko svaki put kad se broj kanala promeni, pa se skloni sa slike.
        val limit = state.connectionLimit
        var warning by remember { mutableStateOf(false) }
        LaunchedEffect(state.overLimit, state.channelCount) {
            warning = state.overLimit
            if (warning) {
                delay(WARNING_MS)
                warning = false
            }
        }
        if (warning && limit != null && menu == null) {
            ConnectionLimitWarning(limit, Modifier.align(Alignment.TopCenter))
        }
        menu?.let { index ->
            TileMenu(
                tile = state.tiles[index],
                count = state.count,
                onOpen = { channelId ->
                    menu = null
                    onOpen(channelId)
                },
                onPick = {
                    menu = null
                    viewModel.openPicker(index)
                },
                onRetry = {
                    menu = null
                    viewModel.retry(index)
                },
                onCount = { count ->
                    menu = null
                    viewModel.setCount(count)
                },
            )
        }
    }
}

private const val WARNING_MS = 10_000L

/**
 * Opcije jednog polja, preko slike. Nazad ih zatvara. Strelice se obrađuju ovde, da fokus ne
 * pobegne sa opcija na polja iza njih.
 */
@Composable
private fun TileMenu(
    tile: MultiTile,
    count: Int,
    onOpen: (Long) -> Unit,
    onPick: () -> Unit,
    onRetry: () -> Unit,
    onCount: (Int) -> Unit,
) {
    val channel = tile.channel
    val check = "\u2713"
    val options = buildList<Triple<String, String?, () -> Unit>> {
        if (channel == null) {
            add(Triple(stringResource(R.string.multiview_pick), null, onPick))
        } else {
            if (tile.status == PlaybackStatus.FAILED) add(Triple(stringResource(R.string.player_retry), null, onRetry))
            add(Triple(stringResource(R.string.multiview_fullscreen), null) { onOpen(channel.id) })
            add(Triple(stringResource(R.string.multiview_change), null, onPick))
        }
        add(Triple(stringResource(R.string.multiview_two), check.takeIf { count == 2 }) { onCount(2) })
        add(
            Triple(stringResource(R.string.multiview_four), check.takeIf { count == MultiViewViewModel.MAX_TILES }) {
                onCount(MultiViewViewModel.MAX_TILES)
            },
        )
    }
    val focus = remember(options.size) { List(options.size) { FocusRequester() } }
    var selected by remember(options.size) { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.36f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .onPreviewKeyEvent { event ->
                    val step = when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> -1
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> 1
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> 0
                        else -> return@onPreviewKeyEvent false
                    }
                    if (step != 0 && event.type == KeyEventType.KeyDown) {
                        selected = (selected + step).coerceIn(0, options.lastIndex)
                        focus[selected].tryRequestFocus()
                    }
                    true
                },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (channel != null) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            options.forEachIndexed { index, (label, trailing, action) ->
                TvTextRow(
                    text = label,
                    trailing = trailing,
                    onClick = action,
                    modifier = Modifier.focusRequester(focus[index]),
                )
            }
        }
    }
    LaunchedEffect(Unit) { focus[0].tryRequestFocus() }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier =
    combinedClickable(onClick = onClick, onLongClick = onLongClick)

@Composable
private fun Tile(
    tile: MultiTile,
    showInfo: Boolean,
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
            val info = if (showInfo) tile.video.label() else null
            if (info != null) {
                Text(
                    text = info,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
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
