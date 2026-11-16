package com.bane23.iptvplayer.ui.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.ui.common.guide.GuideCell
import com.bane23.iptvplayer.ui.common.guide.GuideSelection
import com.bane23.iptvplayer.ui.common.guide.GuideViewModel
import com.bane23.iptvplayer.ui.common.guide.formatDayTime
import com.bane23.iptvplayer.ui.common.guide.formatTime
import com.bane23.iptvplayer.ui.common.guide.message
import com.bane23.iptvplayer.ui.common.guide.timeRange
import kotlinx.coroutines.delay

/** Na TV-u se odjednom vide dva i po sata programa. */
private const val WINDOW_MS = 150 * 60_000L

/**
 * TV vodič na TV-u: levo kanal, desno njegove emisije na zajedničkoj vremenskoj osi.
 * Fokus na emisiji prikazuje njene detalje dole, OK pušta kanal. Levo sa naziva kanala
 * pomera vodič unazad, desno sa poslednje emisije unapred. Kod kanala sa arhivom OK na nazivu
 * kanala ili na emisiji koja je završena otvara arhivu tog kanala.
 */
@Composable
fun TvGuideScreen(
    playlistId: Long,
    categoryId: Long?,
    onPlay: (Long) -> Unit,
    onArchive: (Long) -> Unit,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    LaunchedEffect(playlistId, categoryId) { viewModel.open(playlistId, categoryId) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val channels = viewModel.channelList.collectAsLazyPagingItems()
    val firstFocus = remember { FocusRequester() }
    // Kanal na kome je fokus bio poslednji put, da se posle povratka iz plejera nastavi odatle.
    var lastChannelId by rememberSaveable { mutableStateOf<Long?>(null) }
    var restoreChannelId by remember { mutableStateOf(lastChannelId) }
    val now = System.currentTimeMillis()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.guide_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = status.message(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Početni fokus mora biti na nekom elementu, inače daljinski ne radi ništa.
            OutlinedButton(onClick = { viewModel.shift(-1) }, modifier = Modifier.focusRequester(firstFocus)) {
                Text(stringResource(R.string.guide_earlier))
            }
            OutlinedButton(onClick = viewModel::jumpToNow) { Text(stringResource(R.string.guide_now)) }
            OutlinedButton(onClick = { viewModel.shift(1) }) { Text(stringResource(R.string.guide_later)) }
            // Dok traje osvežavanje dugme samo ne radi ništa: isključeno dugme bi izgubilo fokus.
            OutlinedButton(onClick = { if (!status.loading) viewModel.refresh() }) {
                Text(stringResource(R.string.action_refresh))
            }
        }
        Text(
            text = "${formatDayTime(ui.windowStart)} – ${formatTime(ui.windowStart + WINDOW_MS)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = CHANNEL_WIDTH + 8.dp, top = 12.dp, bottom = 8.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                val channel = channels[index]
                if (channel != null) {
                    GuideRow(
                        channel = channel,
                        windowStart = ui.windowStart,
                        reloadKey = status.refreshedAt,
                        now = now,
                        viewModel = viewModel,
                        onPlay = { onPlay(channel.id) },
                        onArchive = { onArchive(channel.id) },
                        onFocused = { lastChannelId = channel.id },
                        restoreFocus = channel.id == restoreChannelId,
                        onFocusRestored = { restoreChannelId = null },
                    )
                } else {
                    Box(modifier = Modifier.height(ROW_HEIGHT))
                }
            }
        }

        SelectionPanel(ui.selection)
    }
    LaunchedEffect(Unit) {
        firstFocus.tryRequestFocus()
        // Ako red sa zapamćenim kanalom nije na ekranu, od vraćanja fokusa se odustaje.
        delay(FOCUS_RESTORE_TIMEOUT_MS)
        restoreChannelId = null
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    windowStart: Long,
    reloadKey: Long?,
    now: Long,
    viewModel: GuideViewModel,
    onPlay: () -> Unit,
    onArchive: () -> Unit,
    onFocused: () -> Unit,
    restoreFocus: Boolean,
    onFocusRestored: () -> Unit,
) {
    val cells by produceState(emptyList<GuideCell>(), channel.id, windowStart, reloadKey) {
        value = viewModel.row(channel.id, windowStart, WINDOW_MS)
    }
    val channelFocus = remember { FocusRequester() }
    if (restoreFocus) {
        LaunchedEffect(Unit) {
            channelFocus.tryRequestFocus()
            onFocusRestored()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val hasArchive = channel.archiveDays != null
        GuideBox(
            text = if (hasArchive) "↺ ${channel.name}" else channel.name,
            highlighted = false,
            onFocused = {
                onFocused()
                viewModel.select(channel, null)
            },
            onClick = if (hasArchive) onArchive else onPlay,
            modifier = Modifier
                .width(CHANNEL_WIDTH)
                .focusRequester(channelFocus)
                .onPreviewKeyEvent { event ->
                    val left = event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT
                    if (left && event.type == KeyEventType.KeyDown) viewModel.shift(-1)
                    left
                },
        )
        val lastIndex = cells.lastIndex
        cells.forEachIndexed { index, cell ->
            val program = cell.program
            GuideBox(
                text = program?.title.orEmpty(),
                highlighted = program != null && program.startMs <= now && program.stopMs > now,
                onFocused = {
                    onFocused()
                    viewModel.select(channel, program)
                },
                onClick = if (hasArchive && program != null && program.stopMs <= now) onArchive else onPlay,
                modifier = Modifier
                    .weight(cell.weight)
                    .onPreviewKeyEvent { event ->
                        val right = index == lastIndex &&
                            event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
                        if (right && event.type == KeyEventType.KeyDown) {
                            // Fokus se prvo vraća na naziv kanala, jer se ćelije posle pomeranja menjaju.
                            runCatching { channelFocus.requestFocus() }
                            viewModel.shift(1)
                        }
                        right
                    },
            )
        }
    }
}

/** Jedno polje u mreži: fokusirano je obojeno akcentnom bojom, emisija koja je u toku blago istaknuta. */
@Composable
private fun GuideBox(
    text: String,
    highlighted: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val background = when {
        focused -> colors.primary
        highlighted -> colors.surfaceVariant
        else -> colors.surfaceVariant.copy(alpha = 0.45f)
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .onFocusChanged {
                if (it.isFocused && !focused) onFocused()
                focused = it.isFocused
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            color = if (focused) colors.onPrimary else colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Detalji emisije koja ima fokus. Visina je stalna, da se mreža ne pomera dok se lista. */
@Composable
private fun SelectionPanel(selection: GuideSelection?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(PANEL_HEIGHT)
            .padding(top = 12.dp),
    ) {
        if (selection == null) return@Column
        val program = selection.program
        // Kod kanala sa arhivom piše šta OK radi na emisiji koja je završena.
        val archiveHint = stringResource(R.string.archive_hint_tv).takeIf {
            selection.hasArchive && program != null && program.stopMs <= System.currentTimeMillis()
        }
        Text(
            text = listOfNotNull(selection.channel.name, program?.timeRange(), archiveHint).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = program?.title ?: stringResource(R.string.guide_no_data),
            style = MaterialTheme.typography.titleMedium,
            color = if (program != null) MaterialTheme.colorScheme.onBackground else Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        program?.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val FOCUS_RESTORE_TIMEOUT_MS = 1_500L
private val CHANNEL_WIDTH = 220.dp
private val ROW_HEIGHT = 56.dp
private val PANEL_HEIGHT = 112.dp
