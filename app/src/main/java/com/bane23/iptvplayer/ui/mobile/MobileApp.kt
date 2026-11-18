package com.bane23.iptvplayer.ui.mobile

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.ui.common.ReminderViewModel
import com.bane23.iptvplayer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
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
import com.bane23.iptvplayer.ui.common.navigation.CastRemote
import com.bane23.iptvplayer.ui.common.navigation.EditPlaylist
import com.bane23.iptvplayer.ui.common.navigation.ParentalControl
import com.bane23.iptvplayer.ui.common.navigation.OpenSubtitlesAccount
import com.bane23.iptvplayer.ui.common.navigation.TraktAccount
import com.bane23.iptvplayer.ui.common.navigation.ChannelArchive
import com.bane23.iptvplayer.ui.common.navigation.Guide
import com.bane23.iptvplayer.ui.common.navigation.PlayArchive
import com.bane23.iptvplayer.ui.common.navigation.PlayRecording
import com.bane23.iptvplayer.ui.common.navigation.Recordings
import com.bane23.iptvplayer.ui.common.navigation.LiveTv
import com.bane23.iptvplayer.ui.common.categorySection
import com.bane23.iptvplayer.ui.common.navigation.ManageCategories
import com.bane23.iptvplayer.ui.common.navigation.MovieDetails
import com.bane23.iptvplayer.ui.common.navigation.MultiView
import com.bane23.iptvplayer.ui.common.navigation.PlayEpisode
import com.bane23.iptvplayer.ui.common.navigation.PlayMovie
import com.bane23.iptvplayer.ui.common.navigation.Player
import com.bane23.iptvplayer.ui.common.navigation.Playlists
import com.bane23.iptvplayer.ui.common.navigation.Search
import com.bane23.iptvplayer.ui.common.navigation.SendToTv
import com.bane23.iptvplayer.ui.common.navigation.TransferToTv
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
    // Kad puštanje na TV-u krene, plejer na telefonu se zatvara i otvara se daljinski.
    val castStarted = {
        backStack.removeLastOrNull()
        backStack.add(CastRemote)
        Unit
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
                    onCategories = { section -> backStack.add(ManageCategories(section.name)) },
                    onParental = { backStack.add(ParentalControl) },
                    onTrakt = { backStack.add(TraktAccount) },
                    onTransfer = { backStack.add(TransferToTv) },
                    onOpenSubtitles = { backStack.add(OpenSubtitlesAccount) },
                    onRecordings = { backStack.add(Recordings) },
                    onMultiView = { backStack.add(MultiView) },
                )
            }
            entry<Search> {
                MobileSearchScreen(
                    onPlay = { channelId -> backStack.add(Player(channelId, null)) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<ManageCategories> { key ->
                MobileCategoriesScreen(
                    onBack = { backStack.removeLastOrNull() },
                    initialSection = categorySection(key.section),
                )
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
                        onCastStarted = castStarted,
                    )
                }
            }
            entry<Player> { key ->
                MobilePinGate(LockTarget.Channel(key.channelId), onCancel = { backStack.removeLastOrNull() }) {
                    MobilePlayerScreen(
                        channelId = key.channelId,
                        categoryId = key.categoryId,
                        onClose = { backStack.removeLastOrNull() },
                        onCastStarted = castStarted,
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
                        onCastStarted = castStarted,
                    )
                }
            }
            entry<PlayEpisode> { key ->
                MobilePinGate(LockTarget.Vod(series = true, itemId = key.seriesId), onCancel = { backStack.removeLastOrNull() }) {
                    MobileVodPlayerScreen(
                        start = { it.startEpisode(key.seriesId, key.episodeId, key.startOver) },
                        onClose = { backStack.removeLastOrNull() },
                        onCastStarted = castStarted,
                    )
                }
            }
            entry<CastRemote> {
                MobileCastRemoteScreen(
                    onClose = { if (backStack.lastOrNull() is CastRemote) backStack.removeLastOrNull() },
                )
            }
            entry<TransferToTv> {
                MobileSendToTvScreen(target = null, onClose = { backStack.removeLastOrNull() })
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
            entry<OpenSubtitlesAccount> {
                MobileOpenSubtitlesScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<TraktAccount> {
                MobileTraktScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<Recordings> {
                MobileRecordingsScreen(
                    onPlay = { id -> backStack.add(PlayRecording(id)) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<PlayRecording> { key ->
                MobileVodPlayerScreen(
                    start = { it.startRecording(key.recordingId) },
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
    // Podsetnik za emisiju se javlja preko bilo kog ekrana.
    val reminderViewModel: ReminderViewModel = hiltViewModel()
    val dueReminder by reminderViewModel.due.collectAsStateWithLifecycle()
    dueReminder?.let { reminder ->
        AlertDialog(
            onDismissRequest = { reminderViewModel.dismiss(reminder) },
            title = { Text(stringResource(R.string.reminder_starts, reminder.title)) },
            text = { Text(reminder.channelName) },
            confirmButton = {
                TextButton(
                    onClick = {
                        reminderViewModel.watch(reminder) { channelId ->
                            if (backStack.lastOrNull() is Player) backStack.removeLastOrNull()
                            backStack.add(Player(channelId, null))
                        }
                    },
                ) { Text(stringResource(R.string.reminder_watch)) }
            },
            dismissButton = {
                TextButton(onClick = { reminderViewModel.dismiss(reminder) }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}
