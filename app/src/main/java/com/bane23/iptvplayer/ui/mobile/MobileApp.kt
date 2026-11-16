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
import com.bane23.iptvplayer.ui.common.lock.LockTarget
import com.bane23.iptvplayer.ui.common.navigation.EditPlaylist
import com.bane23.iptvplayer.ui.common.navigation.ParentalControl
import com.bane23.iptvplayer.ui.common.navigation.ChannelArchive
import com.bane23.iptvplayer.ui.common.navigation.Guide
import com.bane23.iptvplayer.ui.common.navigation.PlayArchive
import com.bane23.iptvplayer.ui.common.navigation.LiveTv
import com.bane23.iptvplayer.ui.common.navigation.ManageCategories
import com.bane23.iptvplayer.ui.common.navigation.MovieDetails
import com.bane23.iptvplayer.ui.common.navigation.MultiView
import com.bane23.iptvplayer.ui.common.navigation.PlayEpisode
import com.bane23.iptvplayer.ui.common.navigation.PlayMovie
import com.bane23.iptvplayer.ui.common.navigation.Player
import com.bane23.iptvplayer.ui.common.navigation.Playlists
import com.bane23.iptvplayer.ui.common.navigation.Search
import com.bane23.iptvplayer.ui.common.navigation.SendToTv
import com.bane23.iptvplayer.ui.common.navigation.SeriesDetails
import com.bane23.iptvplayer.ui.common.navigation.VodSearch

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
                    onSearchVod = { series -> backStack.add(VodSearch(series)) },
                    onPlaylists = { backStack.add(Playlists) },
                    onCategories = { backStack.add(ManageCategories) },
                    onParental = { backStack.add(ParentalControl) },
                    onMultiView = { backStack.add(MultiView) },
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
                    onArchive = { channelId -> backStack.add(ChannelArchive(channelId)) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<ChannelArchive> { key ->
                MobilePinGate(LockTarget.Channel(key.channelId), onCancel = { backStack.removeLastOrNull() }) {
                    MobileArchiveScreen(
                        channelId = key.channelId,
                        onPlay = { program ->
                            backStack.add(
                                PlayArchive(key.channelId, program.startMs, program.stopMs, program.title, program.startToken),
                            )
                        },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            }
            entry<PlayArchive> { key ->
                MobilePinGate(LockTarget.Channel(key.channelId), onCancel = { backStack.removeLastOrNull() }) {
                    MobileVodPlayerScreen(
                        start = { it.startArchive(key.channelId, key.startMs, key.stopMs, key.title, key.startToken) },
                        onClose = { backStack.removeLastOrNull() },
                    )
                }
            }
            entry<Player> { key ->
                MobilePinGate(LockTarget.Channel(key.channelId), onCancel = { backStack.removeLastOrNull() }) {
                    MobilePlayerScreen(
                        channelId = key.channelId,
                        categoryId = key.categoryId,
                        onClose = { backStack.removeLastOrNull() },
                    )
                }
            }
            entry<VodSearch> { key ->
                MobileVodSearchScreen(
                    series = key.series,
                    onOpen = { item ->
                        if (key.series) {
                            backStack.add(SeriesDetails(item.id, item.name, item.poster))
                        } else {
                            backStack.add(MovieDetails(item.id, item.name, item.poster, item.extension))
                        }
                    },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<MovieDetails> { key ->
                MobilePinGate(LockTarget.Vod(series = false, itemId = key.movieId), onCancel = { backStack.removeLastOrNull() }) {
                    MobileMovieScreen(
                        movieId = key.movieId,
                        name = key.name,
                        poster = key.poster,
                        extension = key.extension,
                        onPlay = { extension, startOver ->
                            backStack.add(PlayMovie(key.movieId, key.name, extension, startOver, key.poster))
                        },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            }
            entry<SeriesDetails> { key ->
                MobilePinGate(LockTarget.Vod(series = true, itemId = key.seriesId), onCancel = { backStack.removeLastOrNull() }) {
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
            }
            entry<PlayMovie> { key ->
                MobilePinGate(LockTarget.Vod(series = false, itemId = key.movieId), onCancel = { backStack.removeLastOrNull() }) {
                    MobileVodPlayerScreen(
                        start = { it.startMovie(key.movieId, key.name, key.extension, key.startOver, key.poster) },
                        onClose = { backStack.removeLastOrNull() },
                    )
                }
            }
            entry<PlayEpisode> { key ->
                MobilePinGate(LockTarget.Vod(series = true, itemId = key.seriesId), onCancel = { backStack.removeLastOrNull() }) {
                    MobileVodPlayerScreen(
                        start = { it.startEpisode(key.seriesId, key.episodeId, key.startOver) },
                        onClose = { backStack.removeLastOrNull() },
                    )
                }
            }
            entry<SendToTv> { key ->
                MobileSendToTvScreen(target = key.target, onClose = { backStack.removeLastOrNull() })
            }
            entry<MultiView> {
                MobileMultiViewScreen(
                    onOpen = { channelId -> backStack.add(Player(channelId, null)) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<ParentalControl> {
                MobileParentalScreen(onBack = { backStack.removeLastOrNull() })
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
