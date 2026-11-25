package com.bane23.iptvplayer.ui.tv

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.bane23.iptvplayer.ui.tv.text.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.db.ProgramHit
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.ui.common.ChannelLogo
import com.bane23.iptvplayer.ui.common.SearchAllViewModel
import com.bane23.iptvplayer.ui.common.guide.formatDayTime
import com.bane23.iptvplayer.ui.common.guide.formatTime
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.recording.RecordingNotices

/**
 * Jedna pretraga za sve na TV-u: polje za unos gore, ispod rezultati po grupama (kanali, emisije
 * iz vodiča, filmovi, serije). OK na emisiji koja traje pušta kanal; na budućoj uključuje podsetnik.
 */
@Composable
fun TvSearchAllScreen(
    onPlay: (Long) -> Unit,
    onOpenVod: (item: VodItem, series: Boolean) -> Unit,
    viewModel: SearchAllViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    RecordingNotices(viewModel.notice)
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val favoriteMovies by viewModel.favoriteMovies.collectAsStateWithLifecycle()
    val favoriteSeries by viewModel.favoriteSeries.collectAsStateWithLifecycle()
    val fieldFocus = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val now = System.currentTimeMillis()
    // Posle izabranog nedavnog ili izgovorenog upita fokus prelazi na prvi rezultat čim stigne.
    var focusResults by remember { mutableStateOf(false) }
    val showRecent = state.query.isBlank() && recent.isNotEmpty()
    // Posle Nazad iz plejera ili detalja fokus se vraća na otvoreni rezultat, ne u polje za kucanje.
    val back = rememberTvReturnFocus()
    val play: (Long) -> Unit = { id ->
        viewModel.remember()
        back.opened()
        onPlay(id)
    }
    val openVod: (VodItem, Boolean) -> Unit = { item, series ->
        viewModel.remember()
        back.opened()
        onOpenVod(item, series)
    }

    // Dugi OK na nedavnoj pretrazi nudi da se ukloni; fokus posle toga prelazi na susednu stavku
    // (sledeću, a ako je bila poslednja, prethodnu), a kad lista ostane prazna, na polje za kucanje.
    var removeMenu by remember { mutableStateOf<String?>(null) }
    val recentFocus = remember { HashMap<String, FocusRequester>() }
    var removed by remember { mutableStateOf<Pair<String, String?>?>(null) }
    removeMenu?.let { query ->
        TvActionDialog(
            title = query,
            options = listOf(stringResource(R.string.search_recent_remove)),
            onSelect = {
                val index = recent.indexOf(query)
                removed = query to (recent.getOrNull(index + 1) ?: recent.getOrNull(index - 1))
                viewModel.removeRecent(query)
            },
            onClose = { removeMenu = null },
        )
    }
    LaunchedEffect(recent, removed, removeMenu) {
        val (query, neighbor) = removed ?: return@LaunchedEffect
        if (removeMenu != null || query in recent) return@LaunchedEffect
        recentFocus.remove(query)
        val target = neighbor?.let { recentFocus[it] }
        if (target == null || !target.tryRequestFocus()) fieldFocus.tryRequestFocus()
        removed = null
    }

    // Glasovna pretraga preko sistemskog prepoznavanja govora; aplikacija sama ništa ne snima.
    val context = LocalContext.current
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    }
    val canVoice = remember { voiceIntent.resolveActivity(context.packageManager) != null }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrEmpty()) {
            focusResults = true
            viewModel.submit(spoken)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        // Isto zaglavlje kao na ostalim TV ekranima: naslov levo, sat desno.
        TvHeader(
            title = stringResource(R.string.action_search),
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(0.7f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { androidx.compose.material3.Text(stringResource(R.string.search_all_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.remember()
                    keyboard?.hide()
                }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(fieldFocus)
                    .tvFieldExit(),
            )
            if (canVoice) {
                TvButton(onClick = { runCatching { voice.launch(voiceIntent) } }) {
                    Icon(AppIcons.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(text = stringResource(R.string.search_voice), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        when {
            state.nothingFound -> TvEmptyState(icon = AppIcons.Search, text = stringResource(R.string.search_empty_vod))
            state.searching && state.channels.isEmpty() ->
                TvEmptyState(icon = AppIcons.Search, text = stringResource(R.string.search_all_searching))
            state.searched.isEmpty() && !showRecent ->
                TvEmptyState(icon = AppIcons.Search, text = stringResource(R.string.search_all_intro))
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .padding(top = 12.dp)
                .focusRequester(listFocus),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showRecent) {
                item(key = "h-recent") { TvSearchGroupTitle(stringResource(R.string.search_recent), recent.size) }
                items(recent, key = { "q$it" }) { query ->
                    val rowFocus = remember(query) { recentFocus.getOrPut(query) { FocusRequester() } }
                    TvListRow(
                        onClick = {
                            focusResults = true
                            viewModel.submit(query)
                        },
                        onLongClick = { removeMenu = query },
                        modifier = Modifier.focusRequester(rowFocus),
                    ) { color ->
                        RowIcon(AppIcons.History, color)
                        RowText(query, color, Modifier.weight(1f))
                    }
                }
                item(key = "q-clear") {
                    TvListRow(onClick = {
                        viewModel.clearRecent()
                        fieldFocus.tryRequestFocus()
                    }) { color ->
                        RowIcon(AppIcons.Delete, color)
                        RowText(stringResource(R.string.search_recent_clear_all), color, Modifier.weight(1f))
                    }
                }
            }
            if (state.channels.isNotEmpty()) {
                item(key = "h-channels") { TvSearchGroupTitle(stringResource(R.string.search_group_channels), state.channels.size) }
                items(state.channels, key = { "c${it.id}" }) { channel ->
                    val focusKey = "c${channel.id}"
                    TvListRow(
                        onClick = { play(channel.id) },
                        onFocused = { back.focused(focusKey) },
                        modifier = back.target(focusKey),
                    ) { color ->
                        ChannelLogo(url = channel.logo, name = channel.name, size = 36.dp)
                        RowText(channel.name, color, Modifier.weight(1f))
                    }
                }
            }
            if (state.programs.isNotEmpty()) {
                item(key = "h-programs") { TvSearchGroupTitle(stringResource(R.string.search_group_programs), state.programs.size) }
                items(state.programs, key = { "p${it.channelId}-${it.startMs}" }) { hit ->
                    val focusKey = "p${hit.channelId}-${hit.startMs}"
                    TvProgramHitRow(
                        hit = hit,
                        now = now,
                        reminded = hit.key in reminders,
                        onPlay = { play(hit.channelId) },
                        onToggleReminder = { viewModel.toggleReminder(hit) },
                        onFocused = { back.focused(focusKey) },
                        modifier = back.target(focusKey),
                    )
                }
            }
            if (state.movies.isNotEmpty()) {
                item(key = "h-movies") { TvSearchGroupTitle(stringResource(R.string.vod_movies), state.movies.size) }
                item(key = "r-movies") { PosterRow(state.movies, favoriteMovies, back, "m", { viewModel.toggleFavorite(it, series = false) }) { openVod(it, false) } }
            }
            if (state.series.isNotEmpty()) {
                item(key = "h-series") { TvSearchGroupTitle(stringResource(R.string.vod_series), state.series.size) }
                item(key = "r-series") { PosterRow(state.series, favoriteSeries, back, "s", { viewModel.toggleFavorite(it, series = true) }) { openVod(it, true) } }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!back.restoreIfBack()) fieldFocus.tryRequestFocus()
    }
    val hasResults = state.channels.isNotEmpty() || state.programs.isNotEmpty() ||
        state.movies.isNotEmpty() || state.series.isNotEmpty()
    LaunchedEffect(focusResults, hasResults, state.searching) {
        if (!focusResults) return@LaunchedEffect
        if (hasResults) {
            if (listFocus.tryRequestFocus()) focusResults = false
        } else if (!state.searching && state.searched.isNotEmpty()) {
            // Ništa nije nađeno: fokus se vraća na polje, da daljinski ne ostane bez fokusa.
            focusResults = false
            fieldFocus.tryRequestFocus()
        }
    }
}

/**
 * Emisija iz vodiča u rezultatima pretrage. OK na emisiji koja traje pušta kanal, a na budućoj
 * uključuje ili uklanja podsetnik.
 */
@Composable
internal fun TvProgramHitRow(
    hit: ProgramHit,
    now: Long,
    reminded: Boolean,
    onPlay: () -> Unit,
    onToggleReminder: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    val onAir = hit.startMs <= now
    TvListRow(
        onClick = { if (onAir) onPlay() else onToggleReminder() },
        onFocused = onFocused,
        modifier = modifier,
    ) { color ->
        RowIcon(AppIcons.Guide, color)
        RowText(if (reminded) "🔔 ${hit.title}" else hit.title, color, Modifier.weight(1f))
        Text(
            text = listOf(
                hit.channelName,
                if (onAir) stringResource(R.string.search_on_air, formatTime(hit.stopMs)) else formatDayTime(hit.startMs),
                when {
                    onAir -> stringResource(R.string.guide_watch)
                    reminded -> stringResource(R.string.reminder_remove)
                    else -> stringResource(R.string.reminder_add)
                },
            ).joinToString(" · "),
            color = color,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
    }
}

/** Naslov grupe sa brojem pogodaka, kao naslov nad kanalima u Uživo. */
@Composable
internal fun TvSearchGroupTitle(text: String, count: Int) {
    Row(
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 12.dp)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** Red postera za filmove ili serije u rezultatima, kao u karticama Filmovi i Serije. */
@Composable
private fun PosterRow(
    list: List<VodItem>,
    favoriteIds: Set<Long>,
    back: TvReturnFocus,
    keyPrefix: String,
    onToggleFavorite: (VodItem) -> Unit,
    onOpen: (VodItem) -> Unit,
) {
    LazyRow(
        // Mesto za uvećan poster u fokusu, da ga ivica reda ne odseče.
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(list, key = { it.id }) { item ->
            val focusKey = keyPrefix + item.id
            PosterCard(
                name = item.name,
                poster = item.poster,
                onClick = { onOpen(item) },
                onFocused = { back.focused(focusKey) },
                favorite = item.id in favoriteIds,
                onToggleFavorite = { onToggleFavorite(item) },
                modifier = Modifier.width(120.dp).then(back.target(focusKey)),
            )
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector, color: Color) {
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
}

@Composable
private fun RowText(text: String, color: Color, modifier: Modifier) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = 16.dp),
    )
}
