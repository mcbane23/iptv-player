package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.CategoriesViewModel

/** Sakrivanje kategorija i promena njihovog redosleda na telefonu. */
@Composable
fun MobileCategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val moveUp = stringResource(R.string.category_move_up)
    val moveDown = stringResource(R.string.category_move_down)
    val shown = stringResource(R.string.category_shown)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SectionTopBar(title = stringResource(R.string.categories_manage_title), onBack = onBack)
            Text(
                text = stringResource(R.string.categories_manage_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(state.categories, key = { _, category -> category.id }) { index, category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (category.hidden) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { viewModel.move(category, -1) },
                            enabled = index > 0,
                            modifier = Modifier.semantics { contentDescription = moveUp },
                        ) { Text("▲") }
                        TextButton(
                            onClick = { viewModel.move(category, 1) },
                            enabled = index < state.categories.lastIndex,
                            modifier = Modifier.semantics { contentDescription = moveDown },
                        ) { Text("▼") }
                        Switch(
                            checked = !category.hidden,
                            onCheckedChange = { visible -> viewModel.setHidden(category, !visible) },
                            modifier = Modifier.semantics { contentDescription = shown },
                        )
                    }
                }
            }
        }
    }
}
