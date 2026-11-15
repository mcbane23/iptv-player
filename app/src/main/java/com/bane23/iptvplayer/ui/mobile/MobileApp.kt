package com.bane23.iptvplayer.ui.mobile

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.bane23.iptvplayer.ui.common.navigation.Channels
import com.bane23.iptvplayer.ui.common.navigation.EditPlaylist
import com.bane23.iptvplayer.ui.common.navigation.LiveTv
import com.bane23.iptvplayer.ui.common.navigation.Player
import com.bane23.iptvplayer.ui.common.navigation.Playlists

/** Koren UI-ja za telefon. */
@Composable
fun MobileApp() {
    val backStack = rememberNavBackStack(LiveTv)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            // ViewModel živi koliko i ekran na steku, ne koliko cela aplikacija.
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<LiveTv> {
                MobileLiveTvScreen(
                    onOpenCategory = { playlistId, categoryId, title ->
                        backStack.add(Channels(playlistId, categoryId, title))
                    },
                    onManagePlaylists = { backStack.add(Playlists) },
                )
            }
            entry<Channels> { key ->
                MobileChannelsScreen(
                    playlistId = key.playlistId,
                    categoryId = key.categoryId,
                    title = key.title,
                    onPlay = { channelId -> backStack.add(Player(channelId, key.categoryId)) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<Player> { key ->
                MobilePlayerScreen(
                    channelId = key.channelId,
                    categoryId = key.categoryId,
                    onClose = { backStack.removeLastOrNull() },
                )
            }
            entry<Playlists> {
                MobilePlaylistsScreen(
                    onAdd = { backStack.add(EditPlaylist()) },
                    onEdit = { id -> backStack.add(EditPlaylist(id)) },
                    onBack = { backStack.removeLastOrNull() },
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
