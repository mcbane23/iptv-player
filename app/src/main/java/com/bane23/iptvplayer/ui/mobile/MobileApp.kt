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
import com.bane23.iptvplayer.ui.common.navigation.EditPlaylist
import com.bane23.iptvplayer.ui.common.navigation.Guide
import com.bane23.iptvplayer.ui.common.navigation.LiveTv
import com.bane23.iptvplayer.ui.common.navigation.ManageCategories
import com.bane23.iptvplayer.ui.common.navigation.MovieDetails
import com.bane23.iptvplayer.ui.common.navigation.PlayEpisode
import com.bane23.iptvplayer.ui.common.navigation.PlayMovie
import com.bane23.iptvplayer.ui.common.navigation.Player
import com.bane23.iptvplayer.ui.common.navigation.Playlists
import com.bane23.iptvplayer.ui.common.navigation.Search
import com.bane23.iptvplayer.ui.common.navigation.SendToTv
import com.bane23.iptvplayer.ui.common.navigation.SeriesDetails

/**
 * Koren UI-ja za telefon. [sendTarget] je adresa TV-a kada je aplikacija otvorena linkom sa
 * njegove stranice; tada se odmah otvara ekran za slanje lista, a [onSendTargetHandled] javlja
 * da je link iskorišćen.
 */
@Composable
fun MobileApp(sendTarget: String? = null, onSendTargetHandled: () -> Unit = {}) {
    val backStack = rememberNavBackStack(LiveTv)
    // Poslednji kanal se pušta samo jednom, pri startu aplikacije, i ne kada je otvorena linkom.
    var resumeChecked by rememberSaveable { mutableStateOf(sendTarget != null) }
    LaunchedEffect(sendTarget) {
        if (sendTarget != null) {
            backStack.removeAll { it is SendToTv }
            backStack.add(SendToTv(sendTarget))
            onSendTargetHandled()
        }
    }
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
                MobileHomeScreen(
                    liveTvViewModel = liveTvViewModel,
                    onPlay = { channelId, categoryId -> backStack.add(Player(channelId, categoryId)) },
                    onSearch = { backStack.add(Search) },
                    onGuide = { playlistId, categoryId -> backStack.add(Guide(playlistId, categoryId)) },
                    onOpenVod = { item, series ->
                        if (series) {
                            backStack.add(SeriesDetails(item.id, item.name, item.poster))
                        } else {
                            backStack.add(MovieDetails(item.id, item.name, item.poster, item.extension))
                        }
                    },
                    onPlaylists = { backStack.add(Playlists) },
                    onCategories = { backStack.add(ManageCategories) },
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
            entry<MovieDetails> { key ->
                MobileMovieScreen(
                    movieId = key.movieId,
                    name = key.name,
                    poster = key.poster,
                    extension = key.extension,
                    onPlay = { extension, startOver ->
                        backStack.add(PlayMovie(key.movieId, key.name, extension, startOver))
                    },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<SeriesDetails> { key ->
                MobileSeriesScreen(
                    seriesId = key.seriesId,
                    name = key.name,
                    poster = key.poster,
                    onPlay = { episodeId, startOver ->
                        backStack.add(PlayEpisode(key.seriesId, episodeId, startOver))
                    },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<PlayMovie> { key ->
                MobileVodPlayerScreen(
                    start = { it.startMovie(key.movieId, key.name, key.extension, key.startOver) },
                    onClose = { backStack.removeLastOrNull() },
                )
            }
            entry<PlayEpisode> { key ->
                MobileVodPlayerScreen(
                    start = { it.startEpisode(key.seriesId, key.episodeId, key.startOver) },
                    onClose = { backStack.removeLastOrNull() },
                )
            }
            entry<SendToTv> { key ->
                MobileSendToTvScreen(target = key.target, onClose = { backStack.removeLastOrNull() })
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
