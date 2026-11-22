package com.bane23.iptvplayer.ui.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import com.bane23.iptvplayer.domain.Program
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.ui.common.guide.GuideCell
import com.bane23.iptvplayer.ui.common.guide.GuideSelection
import com.bane23.iptvplayer.ui.common.guide.GuideRecordings
import com.bane23.iptvplayer.ui.common.guide.GuideViewModel
import com.bane23.iptvplayer.ui.common.recording.RecordingNotices
import com.bane23.iptvplayer.ui.common.rememberNow
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
 * pomera vodič unazad do sadašnjeg vremena (pa u bočni meni), desno sa poslednje emisije unapred.
 * Kod kanala sa arhivom OK na nazivu kanala ili na emisiji koja je završena otvara arhivu tog kanala.
 */
@Composable
fun TvGuideScreen(
    playlistId: Long,
    categoryId: Long?,
    onPlay: (Long) -> Unit,
    onArchive: (Long) -> Unit,
    onPlayArchive: (Long, Program) -> Unit,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    LaunchedEffect(playlistId, categoryId) { viewModel.open(playlistId, categoryId) }
    // Izabrana emisija se menja na svaki korak D-pada; čita je samo donji panel, pa se mreža ne crta iznova.
    val uiState = viewModel.ui.collectAsStateWithLifecycle()
    val windowStart by remember { derivedStateOf { uiState.value.windowStart } }
    val status by viewModel.status.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    // Buduća emisija na kojoj je pritisnut OK: nudi se snimanje i podsetnik.
    var futureMenu by remember { mutableStateOf<GuideSelection?>(null) }
    futureMenu?.let { picked ->
        val program = picked.program
        if (program != null) {
            val recorded = recordings.marks(picked.channel, program, System.currentTimeMillis())
            val reminded = GuideRecordings.key(picked.channel, program.startMs) in reminders
            TvActionDialog(
                title = program.title,
                options = listOf(
                    stringResource(if (reminded) R.string.reminder_remove else R.string.reminder_add),
                    stringResource(if (recorded) R.string.recording_schedule_cancel else R.string.recording_schedule),
                ),
                onSelect = { option ->
                    if (option == 0) viewModel.toggleReminder(picked.channel, program) else viewModel.toggleRecording(picked.channel, program)
                },
                onClose = { futureMenu = null },
            )
        }
    }
    // Završena emisija kanala sa arhivom na kojoj je pritisnut OK: pušta se ona, kanal uživo ili spisak arhive.
    var pastMenu by remember { mutableStateOf<GuideSelection?>(null) }
    pastMenu?.let { picked ->
        val program = picked.program
        if (program != null) {
            TvActionDialog(
                title = program.title,
                options = listOf(
                    stringResource(R.string.archive_play_program),
                    stringResource(R.string.archive_watch_live),
                    stringResource(R.string.archive_title),
                ),
                onSelect = { option ->
                    when (option) {
                        0 -> onPlayArchive(picked.channel.id, program)
                        1 -> onPlay(picked.channel.id)
                        else -> onArchive(picked.channel.id)
                    }
                },
                onClose = { pastMenu = null },
            )
        }
    }
    // Kanal na čijem je nazivu dugo držan OK: ručno povezivanje sa programom iz vodiča.
    var linkChannel by remember { mutableStateOf<Channel?>(null) }
    linkChannel?.let { channel -> TvEpgLinkDialog(channel = channel, onClose = { linkChannel = null }) }
    RecordingNotices(viewModel.recordingNotice)
    val channels = viewModel.channelList.collectAsLazyPagingItems()
    val firstFocus = remember { FocusRequester() }
    // Kanal na kome je fokus bio poslednji put, da se posle povratka iz plejera nastavi odatle.
    var lastChannelId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Početak emisije na kojoj je fokus bio (null na nazivu kanala), da se vrati baš na nju.
    var lastProgramStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var restoreChannelId by remember { mutableStateOf(lastChannelId) }
    val restoreProgramStart = remember { lastProgramStart }
    // Prvi ulazak u vodič: fokus ide na emisiju koja traje na prvom kanalu, ne na dugmad gore.
    var focusFirstRow by remember { mutableStateOf(lastChannelId == null) }
    // Vreme se osvežava povremeno, ne pri svakom crtanju: inače svaki korak iznova crta sve redove vodiča.
    val now by rememberNow()

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Pozadinu (i svetlo) crta početni ekran; Vodič je njegova kartica.
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
            TvOutlinedButton(onClick = { viewModel.shift(-1) }, modifier = Modifier.focusRequester(firstFocus)) {
                Text(stringResource(R.string.guide_earlier))
            }
            TvOutlinedButton(onClick = viewModel::jumpToNow) { Text(stringResource(R.string.guide_now)) }
            TvOutlinedButton(onClick = { viewModel.shift(1) }) { Text(stringResource(R.string.guide_later)) }
            // Dok traje osvežavanje dugme samo ne radi ništa: isključeno dugme bi izgubilo fokus.
            TvOutlinedButton(onClick = { if (!status.loading) viewModel.refresh() }) {
                Text(stringResource(R.string.action_refresh))
            }
            TvClock(modifier = Modifier.padding(start = 12.dp))
        }
        Text(
            text = "${formatDayTime(windowStart)} – ${formatTime(windowStart + WINDOW_MS)}",
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
                        windowStart = windowStart,
                        reloadKey = status.refreshedAt,
                        now = now,
                        recordings = recordings,
                        reminders = reminders,
                        onFuture = { program -> futureMenu = GuideSelection(channel, program) },
                        onPast = { program -> pastMenu = GuideSelection(channel, program) },
                        viewModel = viewModel,
                        onPlay = { onPlay(channel.id) },
                        onArchive = { onArchive(channel.id) },
                        onLink = { linkChannel = channel },
                        onFocused = { programStart ->
                            lastChannelId = channel.id
                            lastProgramStart = programStart
                        },
                        restoreFocus = channel.id == restoreChannelId || (focusFirstRow && index == 0),
                        restoreProgramStart = if (focusFirstRow) NOW_PROGRAM else restoreProgramStart,
                        onFocusRestored = {
                            restoreChannelId = null
                            focusFirstRow = false
                        },
                    )
                } else {
                    Box(modifier = Modifier.height(ROW_HEIGHT))
                }
            }
        }

        SelectionPanel(selected = { uiState.value.selection })
    }
    LaunchedEffect(Unit) {
        // Fokus ide na zapamćen kanal (pri prvom ulasku na prvi kanal) čim njegov red stigne. Ako red
        // ne stigne na ekran, od toga se odustaje i fokus ide na dugmad gore, da daljinski ne ostane bez fokusa.
        delay(FOCUS_RESTORE_TIMEOUT_MS)
        if (restoreChannelId != null || focusFirstRow) {
            restoreChannelId = null
            focusFirstRow = false
            firstFocus.tryRequestFocus()
        }
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    windowStart: Long,
    reloadKey: Long?,
    now: Long,
    recordings: GuideRecordings,
    reminders: Set<String>,
    onFuture: (Program) -> Unit,
    onPast: (Program) -> Unit,
    viewModel: GuideViewModel,
    onPlay: () -> Unit,
    onArchive: () -> Unit,
    onLink: () -> Unit,
    onFocused: (programStart: Long?) -> Unit,
    restoreFocus: Boolean,
    restoreProgramStart: Long?,
    onFocusRestored: () -> Unit,
) {
    val cells by produceState(emptyList<GuideCell>(), channel.id, windowStart, reloadKey) {
        value = viewModel.row(channel.id, windowStart, WINDOW_MS)
    }
    val channelFocus = remember { FocusRequester() }
    // Desno na poslednjoj emisiji pomera vreme; fokus ostaje desno, na istoj emisiji (ako se nastavlja)
    // ili na poslednjoj, umesto da skoči na naziv kanala.
    val shiftFocus = remember { FocusRequester() }
    var shiftTarget by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(cells) {
        if (shiftTarget != null) {
            shiftFocus.tryRequestFocus()
            shiftTarget = null
        }
    }
    // Povratak iz plejera: fokus ide na emisiju na kojoj je bio, a ako je više nema, na naziv kanala.
    val restoreCellFocus = remember { FocusRequester() }
    if (restoreFocus) {
        val waitForCells = restoreProgramStart != null && cells.isEmpty()
        LaunchedEffect(waitForCells) {
            if (waitForCells) return@LaunchedEffect
            if (restoreProgramStart != null) withFrameNanos { }
            if (restoreProgramStart == null || !restoreCellFocus.tryRequestFocus()) channelFocus.tryRequestFocus()
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
                onFocused(null)
                viewModel.select(channel, null)
            },
            onClick = if (hasArchive) onArchive else onPlay,
            onLongClick = onLink,
            modifier = Modifier
                .width(CHANNEL_WIDTH)
                .focusRequester(channelFocus)
                .onPreviewKeyEvent { event ->
                    // Levo vraća vreme do sadašnjeg; odatle levo ide u bočni meni (ranije: dugme „Ranije”).
                    val left = event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT &&
                        !viewModel.atOrBeforeNow()
                    if (left && event.type == KeyEventType.KeyDown) viewModel.shift(-1)
                    left
                },
        )
        val lastIndex = cells.lastIndex
        val targetIndex = shiftTarget?.let { start ->
            cells.indexOfFirst { it.program?.startMs == start }.takeIf { it >= 0 }
        } ?: lastIndex
        cells.forEachIndexed { index, cell ->
            val program = cell.program
            val marked = program != null && recordings.marks(channel, program, now)
            GuideBox(
                text = when {
                    marked -> "● ${program?.title.orEmpty()}"
                    program != null && GuideRecordings.key(channel, program.startMs) in reminders -> "🔔 ${program.title}"
                    else -> program?.title.orEmpty()
                },
                highlighted = program != null && program.startMs <= now && program.stopMs > now,
                onFocused = {
                    onFocused(program?.startMs)
                    viewModel.select(channel, program)
                },
                // Buduća emisija nudi podsetnik i snimanje, završena (uz arhivu) puštanje; ostalo pušta kanal.
                onClick = when {
                    program != null && program.startMs > now -> ({ onFuture(program) })
                    hasArchive && program != null && program.stopMs <= now -> ({ onPast(program) })
                    else -> onPlay
                },
                modifier = Modifier
                    .weight(cell.weight)
                    .then(if (index == targetIndex) Modifier.focusRequester(shiftFocus) else Modifier)
                    .then(
                        if (restoreFocus && program != null && program.isRestoreTarget(restoreProgramStart, now)) {
                            Modifier.focusRequester(restoreCellFocus)
                        } else {
                            Modifier
                        },
                    )
                    .onPreviewKeyEvent { event ->
                        val right = index == lastIndex &&
                            event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
                        if (right && event.type == KeyEventType.KeyDown) {
                            shiftTarget = program?.startMs ?: -1L
                            viewModel.shift(1)
                        }
                        right
                    },
            )
        }
    }
}

/** Jedno polje u mreži: fokusirano je obojeno akcentnom bojom, emisija koja je u toku blago istaknuta. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuideBox(
    text: String,
    highlighted: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
private fun SelectionPanel(selected: () -> GuideSelection?) {
    val selection = selected()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(PANEL_HEIGHT)
            .padding(top = 12.dp),
    ) {
        if (selection == null) return@Column
        val program = selection.program
        // Kod kanala sa arhivom piše šta OK radi na emisiji koja je završena.
        val now = System.currentTimeMillis()
        val archiveHint = stringResource(R.string.archive_hint_tv).takeIf {
            selection.hasArchive && program != null && program.stopMs <= now
        }
        // Kod buduće emisije piše šta OK radi: nudi podsetnik i snimanje.
        val recordHint = if (program != null && program.startMs > now) stringResource(R.string.guide_hint_future_tv) else null
        // Na nazivu kanala piše da dugi OK povezuje kanal sa programom iz vodiča.
        val linkHint = if (program == null) stringResource(R.string.epg_link_hint_tv) else null
        Text(
            text = listOfNotNull(selection.channel.name, program?.timeRange(), archiveHint, recordHint, linkHint).joinToString(" · "),
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

/** Umesto početka emisije: fokus ide na emisiju koja upravo traje. */
private const val NOW_PROGRAM = Long.MIN_VALUE

private fun Program.isRestoreTarget(start: Long?, now: Long): Boolean =
    if (start == NOW_PROGRAM) startMs <= now && stopMs > now else startMs == start
private val CHANNEL_WIDTH = 220.dp
private val ROW_HEIGHT = 56.dp
private val PANEL_HEIGHT = 112.dp
