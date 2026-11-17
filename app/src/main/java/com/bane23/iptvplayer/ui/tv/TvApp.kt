package com.bane23.iptvplayer.ui.tv

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import com.bane23.iptvplayer.ui.common.ReminderViewModel
import com.bane23.iptvplayer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.bane23.iptvplayer.ui.common.LiveTvViewModel
import com.bane23.iptvplayer.ui.common.cast.CastHostViewModel
import com.bane23.iptvplayer.ui.common.cast.rememberLocalNetworkAccess
import com.bane23.iptvplayer.ui.common.navigation.CastPlayback
import com.bane23.iptvplayer.ui.common.lock.LockTarget
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
import com.bane23.iptvplayer.ui.common.navigation.PairPlaylist
import com.bane23.iptvplayer.ui.common.navigation.PlayEpisode
import com.bane23.iptvplayer.ui.common.navigation.PlayMovie
import com.bane23.iptvplayer.ui.common.navigation.Player
import com.bane23.iptvplayer.ui.common.navigation.Search
import com.bane23.iptvplayer.ui.common.navigation.SeriesDetails
import com.bane23.iptvplayer.ui.common.navigation.VodSearch

/** Koren UI-ja za TV. */
@Composable
fun TvApp() {
    val backStack = rememberNavBackStack(LiveTv)
    // Poslednji kanal se pušta samo jednom, pri startu aplikacije.
    var resumeChecked by rememberSaveable { mutableStateOf(false) }

    // Prijem sa telefona (cast na ovu aplikaciju) radi dok je aplikacija vidljiva.
    val castHost: CastHostViewModel = hiltViewModel()
    val localNetwork = rememberLocalNetworkAccess()
    LifecycleStartEffect(localNetwork) {
        if (localNetwork) castHost.start()
        onStopOrDispose { castHost.stop() }
    }
    val castSession by castHost.session.collectAsStateWithLifecycle()
    val pairingCode by castHost.pairingCode.collectAsStateWithLifecycle()
    LaunchedEffect(castSession?.id) {
        if (castSession != null && backStack.lastOrNull() !is CastPlayback) backStack.add(CastPlayback)
    }

    Box(modifier = Modifier.fillMaxSize()) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        // Bez prelaza između ekrana: podrazumevani je pretapanje od 700 ms, u kome se crtaju oba ekrana.
        // Na slabijim TV uređajima to se oseća kao kašnjenje posle svakog OK i Nazad.
        transitionSpec = { NoTransition },
        popTransitionSpec = { NoTransition },
        predictivePopTransitionSpec = { NoTransition },
        entryProvider = entryProvider {
            entry<LiveTv> {
                val liveTvViewModel: LiveTvViewModel = hiltViewModel()
                LaunchedEffect(Unit) {
                    if (!resumeChecked) {
                        resumeChecked = true
                        liveTvViewModel.channelToResume()?.let { backStack.add(Player(it, null)) }
                    }
                }
                TvHomeScreen(
                    liveTvViewModel = liveTvViewModel,
                    onPlay = { channelId, categoryId -> backStack.add(Player(channelId, categoryId)) },
                    onSearch = { backStack.add(Search) },
                    onGuide = { playlistId -> backStack.add(Guide(playlistId, null)) },
                    onOpenVod = { item, series ->
                        if (series) {
                            backStack.add(SeriesDetails(item.id, item.name, item.poster))
                        } else {
                            backStack.add(MovieDetails(item.id, item.name, item.poster, item.extension))
                        }
                    },
                    onSearchVod = { series -> backStack.add(VodSearch(series)) },
                    onAddPlaylist = { backStack.add(EditPlaylist()) },
                    onAddFromPhone = { backStack.add(PairPlaylist) },
                    onEditPlaylist = { id -> backStack.add(EditPlaylist(id)) },
                    onCategories = { section -> backStack.add(ManageCategories(section.name)) },
                    onParental = { backStack.add(ParentalControl) },
                    onTrakt = { backStack.add(TraktAccount) },
                    onOpenSubtitles = { backStack.add(OpenSubtitlesAccount) },
                    onRecordings = { backStack.add(Recordings) },
                    onMultiView = { backStack.add(MultiView) },
                )
            }
            entry<Search> {
                TvSearchScreen(onPlay = { channelId -> backStack.add(Player(channelId, null)) })
            }
            entry<VodSearch> { key ->
                TvVodSearchScreen(
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
            entry<ManageCategories> { key -> TvCategoriesScreen(initialSection = categorySection(key.section)) }
            entry<Guide> { key ->
                TvGuideScreen(
                    playlistId = key.playlistId,
                    categoryId = key.categoryId,
                    onPlay = { channelId -> backStack.add(Player(channelId, key.categoryId)) },
                    onArchive = { channelId -> backStack.add(ChannelArchive(channelId)) },
                )
            }
            entry<ChannelArchive> { key ->
                TvPinGate(LockTarget.Channel(key.channelId)) {
                    TvArchiveScreen(
                        channelId = key.channelId,
                        onPlay = { program ->
                            backStack.add(
                                PlayArchive(key.channelId, program.startMs, program.stopMs, program.title, program.startToken),
                            )
                        },
                    )
                }
            }
            entry<PlayArchive> { key ->
                TvPinGate(LockTarget.Channel(key.channelId)) {
                    TvVodPlayerScreen(
                        start = { it.startArchive(key.channelId, key.startMs, key.stopMs, key.title, key.startToken) },
                    )
                }
            }
            entry<Player> { key ->
                TvPinGate(LockTarget.Channel(key.channelId)) {
                    TvPlayerScreen(channelId = key.channelId, categoryId = key.categoryId)
                }
            }
            entry<MovieDetails> { key ->
                TvPinGate(LockTarget.Vod(series = false, itemId = key.movieId)) {
                    TvMovieScreen(
                        movieId = key.movieId,
                        name = key.name,
                        poster = key.poster,
                        extension = key.extension,
                        onPlay = { extension, startOver ->
                            backStack.add(PlayMovie(key.movieId, key.name, extension, startOver, key.poster))
                        },
                    )
                }
            }
            entry<SeriesDetails> { key ->
                TvPinGate(LockTarget.Vod(series = true, itemId = key.seriesId)) {
                    TvSeriesScreen(
                        seriesId = key.seriesId,
                        name = key.name,
                        poster = key.poster,
                        onPlay = { episodeId, startOver ->
                            backStack.add(PlayEpisode(key.seriesId, episodeId, startOver))
                        },
                    )
                }
            }
            entry<PlayMovie> { key ->
                TvPinGate(LockTarget.Vod(series = false, itemId = key.movieId)) {
                    TvVodPlayerScreen(
                        start = { it.startMovie(key.movieId, key.name, key.extension, key.startOver, key.poster) },
                    )
                }
            }
            entry<PlayEpisode> { key ->
                TvPinGate(LockTarget.Vod(series = true, itemId = key.seriesId)) {
                    TvVodPlayerScreen(
                        start = { it.startEpisode(key.seriesId, key.episodeId, key.startOver) },
                    )
                }
            }
            entry<MultiView> {
                TvMultiViewScreen(onOpen = { channelId -> backStack.add(Player(channelId, null)) })
            }
            entry<CastPlayback> {
                TvCastPlayerScreen(
                    onClose = { if (backStack.lastOrNull() is CastPlayback) backStack.removeLastOrNull() },
                )
            }
            entry<ParentalControl> { TvParentalScreen() }
            entry<TraktAccount> { TvTraktScreen() }
            entry<OpenSubtitlesAccount> { TvOpenSubtitlesScreen() }
            entry<Recordings> {
                TvRecordingsScreen(onPlay = { id -> backStack.add(PlayRecording(id)) })
            }
            entry<PlayRecording> { key ->
                TvVodPlayerScreen(start = { it.startRecording(key.recordingId) })
            }
            entry<PairPlaylist> {
                TvPairingScreen(onClose = { backStack.removeLastOrNull() })
            }
            entry<EditPlaylist> { key ->
                TvEditPlaylistScreen(
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
        TvActionDialog(
            title = stringResource(R.string.reminder_starts, reminder.title) + " · " + reminder.channelName,
            options = listOf(stringResource(R.string.reminder_watch), stringResource(R.string.action_close)),
            onSelect = { option ->
                if (option == 0) {
                    reminderViewModel.watch(reminder) { channelId ->
                        if (backStack.lastOrNull() is Player) backStack.removeLastOrNull()
                        backStack.add(Player(channelId, null))
                    }
                }
            },
            onClose = { reminderViewModel.dismiss(reminder) },
        )
    }
    TvCastPairingCode(
        code = pairingCode,
        onExpired = castHost::dismissPairing,
        modifier = Modifier.align(Alignment.TopEnd),
    )
    }
}

/** Ekran se menja odmah, bez animacije. */
private val NoTransition = ContentTransform(EnterTransition.None, ExitTransition.None)
