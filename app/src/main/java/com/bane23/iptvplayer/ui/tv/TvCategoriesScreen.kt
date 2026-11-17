package com.bane23.iptvplayer.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.CategoryArrange
import com.bane23.iptvplayer.ui.common.CategoriesViewModel
import com.bane23.iptvplayer.ui.common.CategorySection
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.label
import com.bane23.iptvplayer.ui.common.theme.glowBackground
import com.bane23.iptvplayer.ui.common.vod.VodSearch
import kotlinx.coroutines.delay

/**
 * Sakrivanje kategorija i promena njihovog redosleda na TV-u, za kanale, filmove i serije.
 * [initialSection] je kartica koja se otvara prva.
 */
@Composable
fun TvCategoriesScreen(
    initialSection: CategorySection = CategorySection.LIVE,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    var sectionApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!sectionApplied) {
            sectionApplied = true
            viewModel.setSection(initialSection)
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val firstRowFocus = remember { FocusRequester() }
    val headerFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var filter by rememberSaveable { mutableStateOf("") }
    // Posle „Na vrh” spisak se vraća na početak, gde je kategorija (i fokus) sada.
    var movedToTop by remember { mutableIntStateOf(0) }
    val needle = remember(filter) { VodSearch.normalize(filter.trim()) }
    // Redosled na ekranu: premeštanje ga menja odmah, a u bazu ide tek kad se kategorija spusti.
    var order by remember(state.categories) { mutableStateOf(state.categories) }
    val shown = remember(order, needle) {
        if (needle.isEmpty()) order else order.filter { VodSearch.normalize(it.name).contains(needle) }
    }
    // Kategorija koja je „podignuta”: strelice gore i dole je nose kroz spisak, OK ili Nazad je spuštaju.
    var movingKey by remember { mutableStateOf<String?>(null) }
    val moving = order.firstOrNull { it.key == movingKey }
    val movingIndex = if (moving == null) -1 else order.indexOf(moving)
    val drop = {
        if (movingKey != null) {
            movingKey = null
            viewModel.reorder(order)
        }
    }
    BackHandler(enabled = movingKey != null) { drop() }
    val moveTop = stringResource(R.string.category_move_top)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        TvHeader(title = stringResource(R.string.categories_manage_title))
        Text(
            text = if (moving != null) {
                stringResource(R.string.categories_move_hint, moving.name)
            } else {
                stringResource(R.string.categories_manage_hint)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.hasVod) {
                CategorySection.entries.forEach { section ->
                    // Izabrana kartica nosi kvačicu; dugme ostaje isto, da fokus ne nestane sa njega.
                    OutlinedButton(onClick = {
                        filter = ""
                        viewModel.setSection(section)
                    }) {
                        Text((if (state.section == section) "✓ " else "") + stringResource(section.label()))
                    }
                }
            }
            // Dugme koje uvek postoji: fokus ide na njega dok spisak još nije učitan.
            // Uz pretragu dugmad važe samo za pronađene kategorije.
            val filtered = needle.isNotEmpty()
            OutlinedButton(
                onClick = { if (filtered) viewModel.setHidden(shown, false) else viewModel.setAllHidden(false) },
                modifier = Modifier.focusRequester(headerFocus),
            ) {
                Text(stringResource(if (filtered) R.string.categories_show_found else R.string.categories_show_all))
            }
            OutlinedButton(onClick = { if (filtered) viewModel.setHidden(shown, true) else viewModel.setAllHidden(true) }) {
                Text(stringResource(if (filtered) R.string.categories_hide_found else R.string.categories_hide_all))
            }
            TvFilterField(
                value = filter,
                onChange = { filter = it },
                placeholder = stringResource(R.string.search_categories),
                modifier = Modifier.width(280.dp),
            )
        }
        when {
            state.loading -> Unit

            state.failed -> {
                TvEmptyState(icon = AppIcons.Warning, text = stringResource(R.string.vod_load_failed), isError = true)
                OutlinedButton(onClick = viewModel::retry) { Text(stringResource(R.string.player_retry)) }
            }

            shown.isEmpty() -> TvEmptyState(icon = AppIcons.Categories, text = stringResource(R.string.search_empty_categories))

            else -> LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 27.dp),
            ) {
                itemsIndexed(shown, key = { _, category -> category.key }) { index, category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val isMoving = category.key == movingKey
                        Text(
                            text = if (isMoving) "↕ " + category.name else category.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isMoving) {
                                MaterialTheme.colorScheme.primary
                            } else if (category.hidden) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        category.count?.let { count ->
                            Text(
                                text = pluralStringResource(R.plurals.channel_count, count, count),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.setHidden(category, !category.hidden) },
                            modifier = if (index == 0) Modifier.focusRequester(firstRowFocus) else Modifier,
                        ) {
                            Text(
                                stringResource(
                                    if (category.hidden) R.string.category_show else R.string.category_hide,
                                ),
                            )
                        }
                        OutlinedButton(onClick = {
                            viewModel.moveToTop(category)
                            movedToTop++
                        }) { Text(moveTop) }
                        // Dok je spisak filtriran, mesta u njemu nisu prava mesta, pa premeštanja nema.
                        if (needle.isEmpty()) {
                            OutlinedButton(
                                onClick = { if (isMoving) drop() else movingKey = category.key },
                                modifier = Modifier.onPreviewKeyEvent { event ->
                                    if (category.key != movingKey) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.DirectionUp, Key.DirectionDown -> {
                                            // Držanje strelice ponavlja pritisak, pa kategorija brzo prolazi kroz spisak.
                                            if (event.type == KeyEventType.KeyDown) {
                                                val delta = if (event.key == Key.DirectionUp) -1 else 1
                                                order = CategoryArrange.moved(order, order.indexOfFirst { it.key == movingKey }, delta)
                                            }
                                            true
                                        }

                                        // Dok je kategorija podignuta, fokus ne napušta njeno dugme.
                                        Key.DirectionLeft, Key.DirectionRight -> true

                                        else -> false
                                    }
                                },
                            ) { Text(stringResource(if (isMoving) R.string.category_move_done else R.string.category_move)) }
                        }
                    }
                }
            }
        }
    }
    // Podignuta kategorija mora da ostane na ekranu dok putuje kroz spisak.
    LaunchedEffect(movingIndex) {
        if (movingIndex < 0) return@LaunchedEffect
        // Jedan kadar, da spisak rasporedi redove po novom redosledu.
        withFrameNanos { }
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == movingIndex }
        val bottom = info.viewportEndOffset - info.afterContentPadding
        val above = if (item != null) item.offset < info.viewportStartOffset else movingIndex <= listState.firstVisibleItemIndex
        val below = item == null || item.offset + item.size > bottom
        if (above) {
            runCatching { listState.scrollToItem((movingIndex - 1).coerceAtLeast(0)) }
        } else if (below) {
            val size = item?.size ?: info.visibleItemsInfo.firstOrNull()?.size ?: 0
            // Red staje pri dnu, sa mestom za još jedan ispod njega.
            val offset = (bottom - info.viewportStartOffset - 2 * size).coerceAtLeast(0)
            runCatching { listState.scrollToItem(movingIndex, -offset) }
        }
    }
    LaunchedEffect(movedToTop) {
        if (movedToTop > 0) {
            // Kratka pauza da novi redosled stigne iz baze pre vraćanja na vrh.
            delay(MOVE_SETTLE_MS)
            runCatching { listState.scrollToItem(0) }
        }
    }
    // Početni fokus mora biti na nekom elementu, inače daljinski ne radi ništa: na prvoj kategoriji
    // ako je spisak već tu, inače na dugmetu u zaglavlju.
    LaunchedEffect(Unit) {
        repeat(FIRST_FOCUS_ATTEMPTS) {
            if (firstRowFocus.tryRequestFocus()) return@LaunchedEffect
            delay(FIRST_FOCUS_STEP_MS)
        }
        headerFocus.tryRequestFocus()
    }
}

private const val MOVE_SETTLE_MS = 150L
private const val FIRST_FOCUS_ATTEMPTS = 5
private const val FIRST_FOCUS_STEP_MS = 60L
