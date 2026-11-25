package com.bane23.iptvplayer.ui.tv

import com.bane23.iptvplayer.ui.common.userActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.bane23.iptvplayer.ui.tv.text.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.FavoriteList
import com.bane23.iptvplayer.ui.common.FavoriteListsViewModel
import com.bane23.iptvplayer.ui.common.icons.AppIcons

/**
 * Izbor sekcije omiljenih za kanal, uz desnu ivicu ekrana. Otvara se sam kad [viewModel] ima kanal
 * za izbor: „Bez sekcije” i sve sekcije (kvačica je na onoj u kojoj je kanal), polje za novu sekciju
 * i, za kanal koji je već među omiljenima, uklanjanje. OK bira i zatvara; Nazad zatvara.
 */
@Composable
fun TvFavoritePickDialog(viewModel: FavoriteListsViewModel) {
    val pick by viewModel.pick.collectAsStateWithLifecycle()
    val current = pick ?: return
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val nameTaken by viewModel.nameTaken.collectAsStateWithLifecycle()
    var name by remember(current.channelId) { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }
    TvSideDialog(onClose = viewModel::closePick) {
        item {
            SideDialogTitle(
                title = stringResource(R.string.favorite_pick_title, current.channelName),
                subtitle = stringResource(R.string.favorite_pick_subtitle),
            )
        }
        item {
            TvTextRow(
                text = stringResource(R.string.favorite_section_none),
                onClick = { viewModel.choose(null) },
                checked = current.favorite && current.sectionId == null,
                modifier = Modifier.focusRequester(firstFocus),
            )
        }
        items(lists, key = { it.id }) { list ->
            TvTextRow(
                text = list.name,
                onClick = { viewModel.choose(list.id) },
                checked = current.favorite && list.id == current.sectionId,
            )
        }
        newListRows(
            name = name,
            onName = {
                name = it
                viewModel.clearNameError()
            },
            nameTaken = nameTaken,
            action = R.string.favorite_list_create,
            onConfirm = { viewModel.create(name) },
        )
        if (current.favorite) {
            item { TvTextRow(text = stringResource(R.string.favorite_remove), onClick = viewModel::remove) }
        }
        item { TvTextRow(text = stringResource(R.string.action_close), onClick = viewModel::closePick) }
    }
    LaunchedEffect(current.channelId) { firstFocus.tryRequestFocus() }
}

/**
 * Ime sekcije omiljenih: nova sekcija kad je [list] null, inače preimenovanje. Zatvara se kad je ime
 * prihvaćeno.
 */
@Composable
fun TvFavoriteListNameDialog(list: FavoriteList?, viewModel: FavoriteListsViewModel, onClose: () -> Unit) {
    val nameTaken by viewModel.nameTaken.collectAsStateWithLifecycle()
    var name by remember(list?.id) { mutableStateOf(list?.name.orEmpty()) }
    LaunchedEffect(list?.id) { viewModel.clearNameError() }
    TvSideDialog(onClose = onClose) {
        item {
            SideDialogTitle(
                title = list?.name ?: stringResource(R.string.favorite_list_new),
                subtitle = null,
            )
        }
        newListRows(
            name = name,
            onName = {
                name = it
                viewModel.clearNameError()
            },
            nameTaken = nameTaken,
            action = if (list == null) R.string.favorite_list_create else R.string.action_save,
            onConfirm = {
                val done: (Boolean) -> Unit = { ok -> if (ok) onClose() }
                if (list == null) viewModel.create(name, done) else viewModel.rename(list.id, name, done)
            },
        )
        item { TvTextRow(text = stringResource(R.string.action_cancel), onClick = onClose) }
    }
}

/** Polje za ime sekcije, upozorenje kad ime ne valja i red koji potvrđuje. */
private fun LazyListScope.newListRows(
    name: String,
    onName: (String) -> Unit,
    nameTaken: Boolean,
    action: Int,
    onConfirm: () -> Unit,
) {
    item {
        TvFilterField(
            value = name,
            onChange = { onName(it.take(MAX_LIST_NAME)) },
            placeholder = stringResource(R.string.favorite_list_name),
            icon = AppIcons.Edit,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        )
    }
    if (nameTaken) {
        item {
            Text(
                text = stringResource(R.string.favorite_list_name_taken),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
    if (name.isNotBlank()) {
        item { TvTextRow(text = stringResource(action), onClick = onConfirm, icon = AppIcons.Add) }
    }
}

@Composable
internal fun SideDialogTitle(title: String, subtitle: String?) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.dp, bottom = if (subtitle == null) 12.dp else 0.dp),
    )
    if (subtitle != null) {
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 12.dp),
        )
    }
}

/** Prozor uz desnu ivicu ekrana sa redovima jedan ispod drugog, kao ostali izbori na TV-u. */
@Composable
internal fun TvSideDialog(onClose: () -> Unit, content: LazyListScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setWindowAnimations(0) }
        Box(modifier = Modifier.fillMaxSize().userActivity(), contentAlignment = Alignment.CenterEnd) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.36f)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                    .padding(start = 16.dp, end = 48.dp, top = 27.dp, bottom = 27.dp),
                content = content,
            )
        }
    }
}

/** Najduže ime sekcije, da stane u red levog panela. */
const val MAX_LIST_NAME = 30
