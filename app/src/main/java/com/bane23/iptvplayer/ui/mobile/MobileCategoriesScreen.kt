package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.CategoryArrange
import com.bane23.iptvplayer.ui.common.CategoriesViewModel
import com.bane23.iptvplayer.ui.common.CategorySection
import com.bane23.iptvplayer.ui.common.ManagedCategory
import com.bane23.iptvplayer.ui.common.label
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.vod.VodSearch

/**
 * Sakrivanje kategorija i promena njihovog redosleda na telefonu, za kanale, filmove i serije.
 * [initialSection] je kartica koja se otvara prva.
 */
@Composable
fun MobileCategoriesScreen(
    onBack: () -> Unit,
    initialSection: CategorySection = CategorySection.LIVE,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    // Samo pri otvaranju ekrana; posle okretanja telefona ostaje kartica koju je korisnik izabrao.
    var sectionApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!sectionApplied) {
            sectionApplied = true
            viewModel.setSection(initialSection)
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    val needle = remember(filter) { VodSearch.normalize(filter.trim()) }
    // Redosled na ekranu: prevlačenje ga menja odmah, a u bazu ide tek kad se prst podigne.
    var order by remember(state.categories) { mutableStateOf(state.categories) }
    val shown = remember(order, needle) {
        if (needle.isEmpty()) order else order.filter { VodSearch.normalize(it.name).contains(needle) }
    }
    val listState = rememberLazyListState()
    val drag = rememberDragReorderState(
        listState = listState,
        onMove = { from, to -> order = CategoryArrange.moved(order, from, to - from) },
        onDrop = { viewModel.reorder(order) },
    )
    val haptics = LocalHapticFeedback.current

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            SectionTopBar(title = stringResource(R.string.categories_manage_title), onBack = onBack)
            if (state.hasVod) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategorySection.entries.forEach { section ->
                        GroupChip(
                            label = stringResource(section.label()),
                            selected = state.section == section,
                            onClick = {
                                filter = ""
                                viewModel.setSection(section)
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text(stringResource(R.string.search_categories)) },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (!state.loading && !state.failed && shown.isNotEmpty()) {
                // Uz pretragu dugmad važe samo za pronađene kategorije.
                val filtered = needle.isNotEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { if (filtered) viewModel.setHidden(shown, false) else viewModel.setAllHidden(false) }) {
                        Text(stringResource(if (filtered) R.string.categories_show_found else R.string.categories_show_all))
                    }
                    TextButton(onClick = { if (filtered) viewModel.setHidden(shown, true) else viewModel.setAllHidden(true) }) {
                        Text(stringResource(if (filtered) R.string.categories_hide_found else R.string.categories_hide_all))
                    }
                }
            }
            when {
                state.loading -> Unit

                state.failed -> EmptyState(
                    icon = AppIcons.Warning,
                    text = stringResource(R.string.vod_load_failed),
                    actionLabel = stringResource(R.string.player_retry),
                    actionIcon = AppIcons.Refresh,
                    onAction = viewModel::retry,
                )

                shown.isEmpty() -> EmptyState(
                    icon = AppIcons.Categories,
                    text = stringResource(R.string.search_empty_categories),
                )

                else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(shown, key = { _, category -> category.key }) { index, category ->
                        val dragging = drag.draggedKey == category.key
                        CategoryRow(
                            category = category,
                            // Dok je spisak filtriran, mesta u njemu nisu prava mesta, pa se pomera samo na vrh.
                            drag = drag.takeIf { needle.isEmpty() },
                            dragging = dragging,
                            onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            // Za čitač ekrana, koji ne može da prevlači.
                            onStep = { delta -> viewModel.reorder(CategoryArrange.moved(order, index, delta)) },
                            onTop = { viewModel.moveToTop(category) },
                            onVisible = { visible -> viewModel.setHidden(category, !visible) },
                            modifier = if (dragging) {
                                Modifier
                                    .zIndex(1f)
                                    .graphicsLayer { translationY = drag.offsetOf(category.key) }
                            } else {
                                Modifier.animateItem()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: ManagedCategory,
    drag: DragReorderState?,
    dragging: Boolean,
    onDragStart: () -> Unit,
    onStep: (Int) -> Unit,
    onTop: () -> Unit,
    onVisible: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shownLabel = stringResource(R.string.category_shown)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (dragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .padding(start = if (drag == null) 16.dp else 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (drag != null) {
            val dragLabel = stringResource(R.string.category_drag)
            val upLabel = stringResource(R.string.category_move_up)
            val downLabel = stringResource(R.string.category_move_down)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .dragHandle(drag, category.key, onDragStart)
                    .semantics {
                        contentDescription = dragLabel
                        customActions = listOf(
                            CustomAccessibilityAction(upLabel) {
                                onStep(-1)
                                true
                            },
                            CustomAccessibilityAction(downLabel) {
                                onStep(1)
                                true
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp),
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (category.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            category.count?.let { count ->
                Text(
                    text = pluralStringResource(R.plurals.channel_count, count, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        IconButton(onClick = onTop, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = AppIcons.MoveTop,
                contentDescription = stringResource(R.string.category_move_top),
                modifier = Modifier.size(22.dp),
            )
        }
        Switch(
            checked = !category.hidden,
            onCheckedChange = onVisible,
            modifier = Modifier
                .padding(start = 4.dp)
                .semantics { contentDescription = shownLabel },
        )
    }
}
