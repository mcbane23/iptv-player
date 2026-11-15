package com.bane23.iptvplayer.ui.tv

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.bane23.iptvplayer.ui.common.navigation.EditPlaylist
import com.bane23.iptvplayer.ui.common.navigation.Playlists

/** Koren UI-ja za TV. */
@Composable
fun TvApp() {
    val backStack = rememberNavBackStack(Playlists)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Playlists> {
                TvPlaylistsScreen(
                    onAdd = { backStack.add(EditPlaylist()) },
                    onEdit = { id -> backStack.add(EditPlaylist(id)) },
                )
            }
            entry<EditPlaylist> { key ->
                TvEditPlaylistScreen(
                    playlistId = key.playlistId,
                    onClose = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}
