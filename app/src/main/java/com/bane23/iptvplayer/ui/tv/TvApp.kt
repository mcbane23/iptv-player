package com.bane23.iptvplayer.ui.tv

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import com.bane23.iptvplayer.ui.common.ReminderViewModel
import com.bane23.iptvplayer.ui.common.UserActivity
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
import com.bane23.iptvplayer.data.watchnext.WatchNextLink
import com.bane23.iptvplayer.ui.common.LiveTvViewModel
import com.bane23.iptvplayer.ui.common.WatchLinkViewModel
import com.bane23.iptvplayer.ui.common.cast.CastHostViewModel
import com.bane23.iptvplayer.ui.common.cast.rememberLocalNetworkAccess
import com.bane23.iptvplayer.ui.common.navigation.push
import com.bane23.iptvplayer.ui.common.navigation.CastPlayback
import com.bane23.iptvplayer.ui.common.navigation.SearchAll
import com.bane23.iptvplayer.ui.common.lock.LockTarget
import com.bane23.iptvplayer.ui.common.navigation.EditPlaylist
import com.bane23.iptvplayer.ui.common.navigation.ParentalControl
import com.bane23.iptvplayer.ui.common.navigation.OpenSubtitlesAccount
import com.bane23.iptvplayer.ui.common.navigation.BackupRestore
import com.bane23.iptvplayer.ui.common.navigation.TraktAccount
import com.bane23.iptvplayer.ui.common.navigation.ChannelArchive
import com.bane23.iptvplayer.ui.common.navigation.Guide
import com.bane23.iptvplayer.ui.common.navigation.PlayArchive
import com.bane23.iptvplayer.ui.common.navigation.PlayRecording
import com.bane23.iptvplayer.ui.common.navigation.Unlock
import com.bane23.iptvplayer.ui.common.navigation.RemoteButtons
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
fun TvApp(watchLink: WatchNextLink? = null, onWatchLinkHandled: () -> Unit = {}) {
    val backStack = rememberNavBackStack(LiveTv)
    // Poslednji kanal se pušta samo jednom, pri startu aplikacije; ne i kad je aplikacija otvorena
    // izborom filma ili serije sa početnog ekrana TV-a.
    var resumeChecked by rememberSaveable { mutableStateOf(watchLink != null) }
    val watchLinks: WatchLinkViewModel = hiltViewModel()
    // Izbor iz reda „Nastavite sa gledanjem” otvara detalje, odakle se nastavlja jednim klikom.
    LaunchedEffect(watchLink) {
        val link = watchLink ?: return@LaunchedEffect
        onWatchLinkHandled()
        if (!watchLinks.isActive(link.playlistId)) return@LaunchedEffect
        // Ono što je bilo otvoreno (npr. plejer) se zatvara, da ne ostane da radi ispod detalja.
        while (backStack.size > 1) backStack.removeLastOrNull()
        backStack.push(
            if (link.series) {
                SeriesDetails(link.itemId, link.name, link.poster)
            } else {
                MovieDetails(link.itemId, link.name, link.poster, link.extension)
            },
        )
    }

    // Prijem sa telefona (cast na ovu aplikaciju) radi dok je aplikacija vidljiva.
    val castHost: CastHostViewModel = hiltViewModel()
    val localNetwork = rememberLocalNetworkAccess()
    LifecycleStartEffect(localNetwork) {
        if (localNetwork) castHost.start()
        onStopOrDispose { castHost.stop() }
    }
    val castSession by castHost.session.collectAsStateWithLifecycle()
    val pairingCode by castHost.pairingCode.collectAsStateWithLifecycle()
    val incomingTransfer by castHost.transfer.collectAsStateWithLifecycle()
    LaunchedEffect(castSession?.id) {
        if (castSession != null && backStack.lastOrNull() !is CastPlayback) backStack.push(CastPlayback)
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
                        liveTvViewModel.channelToResume()?.let { backStack.push(Player(it, null)) }
                    }
                }
                TvHomeScreen(
                    liveTvViewModel = liveTvViewModel,
                    onUnlock = { backStack.push(Unlock) },
                    onPlay = { channelId, categoryId -> backStack.push(Player(channelId, categoryId)) },
                    onSearch = { backStack.push(Search) },
                    onArchive = { channelId -> backStack.push(ChannelArchive(channelId)) },
                    onOpenVod = { item, series ->
                        if (series) {
                            backStack.push(SeriesDetails(item.id, item.name, item.poster))
                        } else {
                            backStack.push(MovieDetails(item.id, item.name, item.poster, item.extension))
                        }
                    },
                    onSearchVod = { series -> backStack.push(VodSearch(series)) },
                    onAddPlaylist = { backStack.push(EditPlaylist()) },
                    onAddFromPhone = { backStack.push(PairPlaylist) },
                    onEditPlaylist = { id -> backStack.push(EditPlaylist(id)) },
                    onCategories = { section -> backStack.push(ManageCategories(section.name)) },
                    onParental = { backStack.push(ParentalControl) },
                    onTrakt = { backStack.push(TraktAccount) },
                    onOpenSubtitles = { backStack.push(OpenSubtitlesAccount) },
                    onRecordings = { backStack.push(Recordings) },
                    onRemoteButtons = { backStack.push(RemoteButtons) },
                    onBackup = { backStack.push(BackupRestore) },
                    onMultiView = { backStack.push(MultiView) },
                )
            }
            entry<SearchAll> {
                TvSearchAllScreen(
                    onPlay = { channelId -> backStack.push(Player(channelId, null)) },
                    onOpenVod = { item, series ->
                        if (series) {
                            backStack.push(SeriesDetails(item.id, item.name, item.poster))
                        } else {
                            backStack.push(MovieDetails(item.id, item.name, item.poster, item.extension))
                        }
                    },
                )
            }
            entry<Search> {
                TvSearchScreen(onPlay = { channelId -> backStack.push(Player(channelId, null)) })
            }
            entry<VodSearch> { key ->
                TvVodSearchScreen(
                    series = key.series,
                    onOpen = { item ->
                        if (key.series) {
                            backStack.push(SeriesDetails(item.id, item.name, item.poster))
                        } else {
                            backStack.push(MovieDetails(item.id, item.name, item.poster, item.extension))
                        }
                    },
                )
            }
            entry<ManageCategories> { key -> TvCategoriesScreen(initialSection = categorySection(key.section)) }
            entry<Guide> { key ->
                TvGuideScreen(
                    playlistId = key.playlistId,
                    categoryId = key.categoryId,
                    onPlay = { channelId -> backStack.push(Player(channelId, key.categoryId)) },
                    onArchive = { channelId -> backStack.push(ChannelArchive(channelId)) },
                    onPlayArchive = { channelId, program ->
                        backStack.push(PlayArchive(channelId, program.startMs, program.stopMs, program.title, startToken = null))
                    },
                )
            }
            entry<ChannelArchive> { key ->
                TvPinGate(LockTarget.Channel(key.channelId)) {
                    TvArchiveScreen(
                        channelId = key.channelId,
                        onPlay = { program ->
                            backStack.push(
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
                        onClose = { backStack.removeLastOrNull() },
                    )
                }
            }
            entry<Player> { key ->
                TvPinGate(LockTarget.Channel(key.channelId)) {
                    TvPlayerScreen(
                        channelId = key.channelId,
                        categoryId = key.categoryId,
                        onClose = { backStack.removeLastOrNull() },
                        // Arhiva zamenjuje plejer, da kanal ne nastavi da troši vezu u pozadini.
                        onArchive = { channelId ->
                            backStack.removeLastOrNull()
                            backStack.push(ChannelArchive(channelId))
                        },
                    )
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
                            backStack.push(PlayMovie(key.movieId, key.name, extension, startOver, key.poster))
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
                            backStack.push(PlayEpisode(key.seriesId, episodeId, startOver))
                        },
                    )
                }
            }
            entry<PlayMovie> { key ->
                TvPinGate(LockTarget.Vod(series = false, itemId = key.movieId)) {
                    TvVodPlayerScreen(
                        start = { it.startMovie(key.movieId, key.name, key.extension, key.startOver, key.poster) },
                        onClose = { backStack.removeLastOrNull() },
                    )
                }
            }
            entry<PlayEpisode> { key ->
                TvPinGate(LockTarget.Vod(series = true, itemId = key.seriesId)) {
                    TvVodPlayerScreen(
                        start = { it.startEpisode(key.seriesId, key.episodeId, key.startOver) },
                        onClose = { backStack.removeLastOrNull() },
                    )
                }
            }
            entry<MultiView> {
                TvMultiViewScreen(onOpen = { channelId -> backStack.push(Player(channelId, null)) })
            }
            entry<CastPlayback> {
                TvCastPlayerScreen(
                    onClose = { if (backStack.lastOrNull() is CastPlayback) backStack.removeLastOrNull() },
                )
            }
            entry<ParentalControl> { TvParentalScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<TraktAccount> { TvTraktScreen() }
            entry<OpenSubtitlesAccount> { TvOpenSubtitlesScreen() }
            entry<RemoteButtons> { TvRemoteButtonsScreen() }
            entry<BackupRestore> { TvBackupScreen() }
            entry<Recordings> {
                TvRecordingsScreen(onPlay = { id -> backStack.push(PlayRecording(id)) })
            }
            entry<Unlock> {
                // Tačan PIN otključava sve; ekran se zatvara i vraća na spisak kategorija.
                TvPinGate(LockTarget.Everything) {
                    LaunchedEffect(Unit) { backStack.remove(Unlock) }
                }
            }
            entry<PlayRecording> { key ->
                TvPinGate(LockTarget.Recording(key.recordingId)) {
                    TvVodPlayerScreen(
                        start = { it.startRecording(key.recordingId) },
                        onClose = { backStack.removeLastOrNull() },
                    )
                }
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
                        backStack.push(Player(channelId, null))
                    }
                }
            },
            onClose = { reminderViewModel.dismiss(reminder) },
        )
    }
    // Čuvar ekrana se ne pali preko plejera (pauza ima svoj ekran) ni dok se čeka telefon ili unos.
    val top = backStack.lastOrNull()
    val screensaverBlocked = top is Player || top is PlayMovie || top is PlayEpisode || top is PlayArchive ||
        top is PlayRecording || top is MultiView || top is CastPlayback || top is PairPlaylist ||
        top is EditPlaylist || top is TraktAccount || top is OpenSubtitlesAccount
    // Podsetnik, kod za uparivanje i prenos sa telefona ga gase, da se vide.
    LaunchedEffect(dueReminder, pairingCode, incomingTransfer) { UserActivity.touch() }
    TvScreensaver(blocked = screensaverBlocked)
    TvCastPairingCode(
        code = pairingCode,
        onExpired = castHost::dismissPairing,
        modifier = Modifier.align(Alignment.TopEnd),
    )
    TvIncomingTransfer(
        transfer = incomingTransfer,
        onDone = castHost::dismissTransfer,
        modifier = Modifier.align(Alignment.TopEnd),
    )
    }
}

/** Ekran se menja odmah, bez animacije. */
private val NoTransition = ContentTransform(EnterTransition.None, ExitTransition.None)
