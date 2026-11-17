package com.bane23.iptvplayer.ui.tv

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
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
    val shown = remember(state.categories, needle) {
        if (needle.isEmpty()) state.categories else state.categories.filter { VodSearch.normalize(it.name).contains(needle) }
    }
    val moveTop = stringResource(R.string.category_move_top)
    val moveUp = stringResource(R.string.category_move_up)
    val moveDown = stringResource(R.string.category_move_down)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        TvHeader(title = stringResource(R.string.categories_manage_title))
        Text(
            text = stringResource(R.string.categories_manage_hint),
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
            OutlinedButton(onClick = { viewModel.setAllHidden(false) }, modifier = Modifier.focusRequester(headerFocus)) {
                Text(stringResource(R.string.categories_show_all))
            }
            OutlinedButton(onClick = { viewModel.setAllHidden(true) }) {
                Text(stringResource(R.string.categories_hide_all))
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
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (category.hidden) {
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
                        // Dok je spisak filtriran, mesta u njemu nisu prava mesta, pa strelica nema.
                        if (needle.isEmpty()) {
                            OutlinedButton(
                                // Dugme se ne isključuje na kraju spiska: isključeno dugme bi izgubilo fokus.
                                onClick = { if (index > 0) viewModel.move(category, -1) },
                                modifier = Modifier.semantics { contentDescription = moveUp },
                            ) { Text("▲") }
                            OutlinedButton(
                                onClick = { if (index < shown.lastIndex) viewModel.move(category, 1) },
                                modifier = Modifier.semantics { contentDescription = moveDown },
                            ) { Text("▼") }
                        }
                    }
                }
            }
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
