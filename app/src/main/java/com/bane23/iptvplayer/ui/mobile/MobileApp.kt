package com.bane23.iptvplayer.ui.mobile

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.bane23.iptvplayer.ui.common.navigation.EditPlaylist
import com.bane23.iptvplayer.ui.common.navigation.Playlists

/** Koren UI-ja za telefon. */
@Composable
fun MobileApp() {
    val backStack = rememberNavBackStack(Playlists)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            // ViewModel živi koliko i ekran na steku, ne koliko cela aplikacija.
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Playlists> {
                MobilePlaylistsScreen(
                    onAdd = { backStack.add(EditPlaylist()) },
                    onEdit = { id -> backStack.add(EditPlaylist(id)) },
                )
            }
            entry<EditPlaylist> { key ->
                MobileEditPlaylistScreen(
                    playlistId = key.playlistId,
                    onClose = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}
