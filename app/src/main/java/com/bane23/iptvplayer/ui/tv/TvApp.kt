package com.bane23.iptvplayer.ui.tv

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
import com.bane23.iptvplayer.ui.common.navigation.SeriesDetails
import com.bane23.iptvplayer.ui.common.navigation.Settings
import com.bane23.iptvplayer.ui.common.navigation.Vod

/** Koren UI-ja za TV. */
@Composable
fun TvApp() {
    val backStack = rememberNavBackStack(LiveTv)
    // Poslednji kanal se pušta samo jednom, pri startu aplikacije.
    var resumeChecked by rememberSaveable { mutableStateOf(false) }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
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
                TvLiveTvScreen(
                    onPlay = { channelId, categoryId -> backStack.add(Player(channelId, categoryId)) },
                    onManagePlaylists = { backStack.add(Playlists) },
                    onSearch = { backStack.add(Search) },
                    onGuide = { playlistId, categoryId -> backStack.add(Guide(playlistId, categoryId)) },
                    onManageCategories = { backStack.add(ManageCategories) },
                    onSettings = { backStack.add(Settings) },
                    onMovies = { backStack.add(Vod(series = false)) },
                    onSeries = { backStack.add(Vod(series = true)) },
                    viewModel = liveTvViewModel,
                )
            }
            entry<Search> {
                TvSearchScreen(onPlay = { channelId -> backStack.add(Player(channelId, null)) })
            }
            entry<ManageCategories> { TvCategoriesScreen() }
            entry<Settings> { TvSettingsScreen() }
            entry<Guide> { key ->
                TvGuideScreen(
                    playlistId = key.playlistId,
                    categoryId = key.categoryId,
                    onPlay = { channelId -> backStack.add(Player(channelId, key.categoryId)) },
                )
            }
            entry<Player> { key ->
                TvPlayerScreen(channelId = key.channelId, categoryId = key.categoryId)
            }
            entry<Vod> { key ->
                TvVodScreen(
                    series = key.series,
                    onOpen = { item ->
                        if (key.series) {
                            backStack.add(SeriesDetails(item.id, item.name, item.poster))
                        } else {
                            backStack.add(MovieDetails(item.id, item.name, item.poster, item.extension))
                        }
                    },
                )
            }
            entry<MovieDetails> { key ->
                TvMovieScreen(
                    movieId = key.movieId,
                    name = key.name,
                    poster = key.poster,
                    extension = key.extension,
                    onPlay = { extension, startOver ->
                        backStack.add(PlayMovie(key.movieId, key.name, extension, startOver))
                    },
                )
            }
            entry<SeriesDetails> { key ->
                TvSeriesScreen(
                    seriesId = key.seriesId,
                    name = key.name,
                    poster = key.poster,
                    onPlay = { episodeId, startOver ->
                        backStack.add(PlayEpisode(key.seriesId, episodeId, startOver))
                    },
                )
            }
            entry<PlayMovie> { key ->
                TvVodPlayerScreen(
                    start = { it.startMovie(key.movieId, key.name, key.extension, key.startOver) },
                )
            }
            entry<PlayEpisode> { key ->
                TvVodPlayerScreen(
                    start = { it.startEpisode(key.seriesId, key.episodeId, key.startOver) },
                )
            }
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
