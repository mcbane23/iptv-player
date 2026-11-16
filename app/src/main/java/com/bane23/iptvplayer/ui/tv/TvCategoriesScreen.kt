package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.bane23.iptvplayer.ui.common.theme.glowBackground
import com.bane23.iptvplayer.ui.common.CategoriesViewModel

/** Sakrivanje kategorija i promena njihovog redosleda na TV-u. */
@Composable
fun TvCategoriesScreen(viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }
    val moveUp = stringResource(R.string.category_move_up)
    val moveDown = stringResource(R.string.category_move_down)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Text(
            text = stringResource(R.string.categories_manage_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.categories_manage_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 27.dp),
        ) {
            itemsIndexed(state.categories, key = { _, category -> category.id }) { index, category ->
                Row(
                    modifier = Modifier.fillMaxWidth(0.8f),
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
                    // Početni fokus mora biti na nekom elementu, inače daljinski ne radi ništa.
                    OutlinedButton(
                        onClick = { viewModel.setHidden(category, !category.hidden) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    ) {
                        Text(
                            stringResource(
                                if (category.hidden) R.string.category_show else R.string.category_hide,
                            ),
                        )
                    }
                    OutlinedButton(
                        // Dugme se ne isključuje na kraju spiska: isključeno dugme bi izgubilo fokus.
                        onClick = { if (index > 0) viewModel.move(category, -1) },
                        modifier = Modifier.semantics { contentDescription = moveUp },
                    ) { Text("▲") }
                    OutlinedButton(
                        onClick = { if (index < state.categories.lastIndex) viewModel.move(category, 1) },
                        modifier = Modifier.semantics { contentDescription = moveDown },
                    ) { Text("▼") }
                }
            }
        }
    }
    LaunchedEffect(state.categories.isNotEmpty()) {
        if (state.categories.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }
}
