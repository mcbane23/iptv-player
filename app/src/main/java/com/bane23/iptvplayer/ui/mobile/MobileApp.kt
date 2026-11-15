package com.bane23.iptvplayer.ui.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.bane23.iptvplayer.ui.common.LiveTvViewModel
import com.bane23.iptvplayer.ui.common.navigation.Channels
import com.bane23.iptvplayer.ui.common.navigation.EditPlaylist
import com.bane23.iptvplayer.ui.common.navigation.Guide
import com.bane23.iptvplayer.ui.common.navigation.LiveTv
import com.bane23.iptvplayer.ui.common.navigation.ManageCategories
import com.bane23.iptvplayer.ui.common.navigation.Player
import com.bane23.iptvplayer.ui.common.navigation.Playlists
import com.bane23.iptvplayer.ui.common.navigation.Search
import com.bane23.iptvplayer.ui.common.navigation.Settings

/** Koren UI-ja za telefon. */
@Composable
fun MobileApp() {
    val backStack = rememberNavBackStack(LiveTv)
    // Poslednji kanal se pušta samo jednom, pri startu aplikacije.
    var resumeChecked by rememberSaveable { mutableStateOf(false) }
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
                val liveTvViewModel: LiveTvViewModel = hiltViewModel()
                LaunchedEffect(Unit) {
                    if (!resumeChecked) {
                        resumeChecked = true
                        liveTvViewModel.channelToResume()?.let { backStack.add(Player(it, null)) }
                    }
                }
                MobileLiveTvScreen(
                    onOpenCategory = { playlistId, categoryId, title ->
                        backStack.add(Channels(playlistId, categoryId, title))
                    },
                    onManagePlaylists = { backStack.add(Playlists) },
                    onSearch = { backStack.add(Search) },
                    onManageCategories = { backStack.add(ManageCategories) },
                    onSettings = { backStack.add(Settings) },
                    viewModel = liveTvViewModel,
                )
            }
            entry<Search> {
                MobileSearchScreen(
                    onPlay = { channelId -> backStack.add(Player(channelId, null)) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<ManageCategories> {
                MobileCategoriesScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<Settings> {
                MobileSettingsScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<Channels> { key ->
                MobileChannelsScreen(
                    playlistId = key.playlistId,
                    categoryId = key.categoryId,
                    title = key.title,
                    onPlay = { channelId -> backStack.add(Player(channelId, key.categoryId)) },
                    onBack = { backStack.removeLastOrNull() },
                    onGuide = { backStack.add(Guide(key.playlistId, key.categoryId)) },
                )
            }
            entry<Guide> { key ->
                MobileGuideScreen(
                    playlistId = key.playlistId,
                    categoryId = key.categoryId,
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
