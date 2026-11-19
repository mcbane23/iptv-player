package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.FavoriteList
import com.bane23.iptvplayer.ui.common.FavoriteListsViewModel

/** Najduže ime liste, da stane u čip i u red među kategorijama. */
private const val MAX_LIST_NAME = 30

/**
 * Izbor liste omiljenih za kanal na telefonu. Otvara se sam kad [viewModel] ima kanal za izbor:
 * „Omiljeni” i sve imenovane liste sa kvačicom, a ispod polje za novu listu.
 */
@Composable
fun MobileFavoritePickDialog(viewModel: FavoriteListsViewModel) {
    val pick by viewModel.pick.collectAsStateWithLifecycle()
    val current = pick ?: return
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val nameTaken by viewModel.nameTaken.collectAsStateWithLifecycle()
    var name by remember(current.channelId) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = viewModel::closePick,
        title = {
            Text(
                text = stringResource(R.string.favorite_pick_title, current.channelName),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column {
                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    CheckRow(
                        text = stringResource(R.string.category_favorites),
                        checked = current.favorite,
                        onToggle = viewModel::toggleFavorite,
                    )
                    lists.forEach { list ->
                        CheckRow(
                            text = list.name,
                            checked = list.id in current.listIds,
                            onToggle = { viewModel.toggleList(list.id) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    ListNameField(
                        name = name,
                        onName = {
                            name = it
                            viewModel.clearNameError()
                        },
                        nameTaken = nameTaken,
                        label = R.string.favorite_list_new,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = { viewModel.create(name) { created -> if (created) name = "" } },
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Text(stringResource(R.string.favorite_list_add), maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::closePick) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/**
 * Šta je korisnik dugo pritisnuo među grupama: „Omiljene” ([list] je null) ili imenovanu listu.
 * Sa [create] se odmah traži ime nove liste, bez menija.
 */
data class FavoriteListMenu(val list: FavoriteList?, val create: Boolean = false)

private enum class MenuStep { MENU, NAME_NEW, NAME_RENAME, DELETE }

/**
 * Meni liste omiljenih posle dugog pritiska: za „Omiljene” samo nova lista, za imenovanu listu i
 * preimenovanje i brisanje. Ime i potvrda brisanja se traže u istom prozoru.
 */
@Composable
fun MobileFavoriteListMenuDialog(menu: FavoriteListMenu, viewModel: FavoriteListsViewModel, onClose: () -> Unit) {
    val list = menu.list
    var step by remember(menu) { mutableStateOf(if (menu.create) MenuStep.NAME_NEW else MenuStep.MENU) }
    when (step) {
        MenuStep.MENU -> AlertDialog(
            onDismissRequest = onClose,
            title = {
                Text(
                    text = list?.name ?: stringResource(R.string.category_favorites),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column {
                    if (list != null) {
                        MenuRow(stringResource(R.string.favorite_list_rename)) { step = MenuStep.NAME_RENAME }
                        MenuRow(stringResource(R.string.favorite_list_delete)) { step = MenuStep.DELETE }
                    }
                    MenuRow(stringResource(R.string.favorite_list_new)) { step = MenuStep.NAME_NEW }
                }
            },
            confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) } },
        )

        MenuStep.NAME_NEW, MenuStep.NAME_RENAME -> {
            val renamed = list.takeIf { step == MenuStep.NAME_RENAME }
            val nameTaken by viewModel.nameTaken.collectAsStateWithLifecycle()
            var name by remember(step) { mutableStateOf(renamed?.name.orEmpty()) }
            LaunchedEffect(step) { viewModel.clearNameError() }
            AlertDialog(
                onDismissRequest = onClose,
                title = {
                    Text(
                        text = renamed?.name ?: stringResource(R.string.favorite_list_new),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                text = {
                    ListNameField(
                        name = name,
                        onName = {
                            name = it
                            viewModel.clearNameError()
                        },
                        nameTaken = nameTaken,
                        label = R.string.favorite_list_name,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            val done: (Boolean) -> Unit = { ok -> if (ok) onClose() }
                            if (renamed == null) viewModel.create(name, done) else viewModel.rename(renamed.id, name, done)
                        },
                    ) {
                        Text(stringResource(if (renamed == null) R.string.favorite_list_create else R.string.action_save))
                    }
                },
                dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) } },
            )
        }

        MenuStep.DELETE -> AlertDialog(
            onDismissRequest = onClose,
            text = { Text(stringResource(R.string.favorite_list_delete_confirm, list?.name.orEmpty())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        list?.let { viewModel.delete(it.id) }
                        onClose()
                    },
                ) {
                    Text(stringResource(R.string.favorite_list_delete))
                }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ListNameField(
    name: String,
    onName: (String) -> Unit,
    nameTaken: Boolean,
    label: Int,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = name,
        onValueChange = { onName(it.take(MAX_LIST_NAME)) },
        singleLine = true,
        isError = nameTaken,
        label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingText = if (nameTaken) {
            { Text(stringResource(R.string.favorite_list_name_taken), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        modifier = modifier,
    )
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp, top = 8.dp, bottom = 8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
    )
}
