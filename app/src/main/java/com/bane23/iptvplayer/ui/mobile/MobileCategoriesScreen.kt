package com.bane23.iptvplayer.ui.mobile

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
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
    var menuOpen by remember { mutableStateOf(false) }
    val needle = remember(filter) { VodSearch.normalize(filter.trim()) }
    val shown = remember(state.categories, needle) {
        if (needle.isEmpty()) state.categories else state.categories.filter { VodSearch.normalize(it.name).contains(needle) }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            SectionTopBar(title = stringResource(R.string.categories_manage_title), onBack = onBack) {
                Box {
                    ToolbarIcon(AppIcons.More, stringResource(R.string.action_more)) { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.categories_show_all)) },
                            onClick = {
                                menuOpen = false
                                viewModel.setAllHidden(false)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.categories_hide_all)) },
                            onClick = {
                                menuOpen = false
                                viewModel.setAllHidden(true)
                            },
                        )
                    }
                }
            }
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

                else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(shown, key = { _, category -> category.key }) { index, category ->
                        CategoryRow(
                            category = category,
                            // Dok je spisak filtriran, mesta u njemu nisu prava mesta, pa se pomera samo na vrh.
                            canMoveUp = needle.isEmpty() && index > 0,
                            canMoveDown = needle.isEmpty() && index < shown.lastIndex,
                            showArrows = needle.isEmpty(),
                            onTop = { viewModel.moveToTop(category) },
                            onMove = { delta -> viewModel.move(category, delta) },
                            onVisible = { visible -> viewModel.setHidden(category, !visible) },
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    showArrows: Boolean,
    onTop: () -> Unit,
    onMove: (Int) -> Unit,
    onVisible: (Boolean) -> Unit,
) {
    val shownLabel = stringResource(R.string.category_shown)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        MoveButton(AppIcons.MoveTop, stringResource(R.string.category_move_top), enabled = true, onClick = onTop)
        if (showArrows) {
            MoveButton(AppIcons.ArrowUp, stringResource(R.string.category_move_up), canMoveUp) { onMove(-1) }
            MoveButton(AppIcons.ArrowDown, stringResource(R.string.category_move_down), canMoveDown) { onMove(1) }
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

@Composable
private fun MoveButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(imageVector = icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}
