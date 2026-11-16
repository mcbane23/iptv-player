package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.vod.VodSearch

/** Stavka u izboru kategorije. [icon] se prikazuje ispred naziva (npr. zvezdica za omiljene). */
data class PickerGroup(val key: String, val label: String, val icon: ImageVector? = null, val count: Int? = null)

/**
 * Radnje iz menija koji se otvara dugim pritiskom na kategoriju u spisku: sakrivanje, prebacivanje
 * na vrh i odlazak na ekran za uređivanje svih kategorija.
 */
class CategoryActions(
    val onHide: (key: String) -> Unit,
    val onMoveToTop: (key: String) -> Unit,
    val onManage: () -> Unit,
)

/**
 * Dugme sa izabranom kategorijom preko cele širine; dodir otvara spisak svih kategorija.
 * Zamenjuje red čipova, koji je nepregledan kad kategorija ima mnogo.
 */
@Composable
fun CategoryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    horizontalPadding: Dp = 16.dp,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon ?: AppIcons.Categories,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Icon(imageVector = AppIcons.ArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Spisak svih kategorija preko celog ekrana, sa poljem za pretragu na vrhu.
 * Izbor zatvara spisak.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryPickerDialog(
    title: String,
    groups: List<PickerGroup>,
    selectedKey: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    actions: CategoryActions? = null,
) {
    var menuFor by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val needle = remember(query) { VodSearch.normalize(query.trim()) }
    val shown = remember(groups, needle) {
        if (needle.isEmpty()) groups else groups.filter { VodSearch.normalize(it.label).contains(needle) }
    }
    val startIndex = remember { groups.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding(),
            ) {
                SectionTopBar(title = title, onBack = onDismiss)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_categories)) },
                    leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                if (shown.isEmpty()) {
                    Message(stringResource(R.string.search_empty_categories))
                }
                LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(shown, key = { it.key }) { group ->
                        val selected = group.key == selectedKey
                        Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onPick(group.key) },
                                    onLongClick = actions?.let { { menuFor = group.key } },
                                )
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (group.icon != null) {
                                Icon(
                                    imageVector = group.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(20.dp),
                                )
                            }
                            Text(
                                text = group.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (group.count != null) {
                                Text(
                                    text = group.count.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .size(20.dp),
                                )
                            }
                        }
                        if (actions != null) {
                            CategoryActionMenu(
                                expanded = menuFor == group.key,
                                actions = actions,
                                key = group.key,
                                onDismiss = { menuFor = null },
                            )
                        }
                        }
                    }
                }
            }
        }
    }
}

/** Meni kategorije (dugi pritisak): Sakrij, Pomeri na vrh, Uredi kategorije. */
@Composable
fun CategoryActionMenu(expanded: Boolean, actions: CategoryActions, key: String, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.category_hide)) },
            onClick = {
                onDismiss()
                actions.onHide(key)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.category_move_top)) },
            onClick = {
                onDismiss()
                actions.onMoveToTop(key)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.categories_edit)) },
            onClick = {
                onDismiss()
                actions.onManage()
            },
        )
    }
}
