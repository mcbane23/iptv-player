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
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.bane23.iptvplayer.ui.common.LiveTvViewModel
import com.bane23.iptvplayer.ui.common.lock.LockTarget
import com.bane23.iptvplayer.ui.common.navigation.push
import com.bane23.iptvplayer.ui.common.navigation.CastRemote
import com.bane23.iptvplayer.ui.common.navigation.EditPlaylist
import com.bane23.iptvplayer.ui.common.navigation.ParentalControl
import com.bane23.iptvplayer.ui.common.navigation.OpenSubtitlesAccount
import com.bane23.iptvplayer.ui.common.navigation.TraktAccount
import com.bane23.iptvplayer.ui.common.navigation.ChannelArchive
import com.bane23.iptvplayer.ui.common.navigation.Guide
import com.bane23.iptvplayer.ui.common.navigation.PlayArchive
import com.bane23.iptvplayer.ui.common.navigation.Downloads
import com.bane23.iptvplayer.ui.common.navigation.PlayDownload
import com.bane23.iptvplayer.ui.common.navigation.PlayRecording
import com.bane23.iptvplayer.ui.common.navigation.Unlock
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
import com.bane23.iptvplayer.ui.common.navigation.BackupRestore
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
            backStack.push(SendToTv(sendTarget))
            onSendTargetHandled()
        }
    }
    // Kad puštanje na TV-u krene, plejer na telefonu se zatvara i otvara se daljinski.
    val castStarted = {
        backStack.removeLastOrNull()
        backStack.push(CastRemote)
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
        // Kratko pretapanje umesto podrazumevanog od 700 ms, posle kog je svaki ekran kasnio.
        transitionSpec = { QuickFade },
        popTransitionSpec = { QuickFade },
        predictivePopTransitionSpec = { QuickFade },
        entryProvider = entryProvider {
            entry<LiveTv> {
                val liveTvViewModel: LiveTvViewModel = hiltViewModel()
                LaunchedEffect(Unit) {
                    if (!resumeChecked) {
                        resumeChecked = true
                        liveTvViewModel.channelToResume()?.let { backStack.push(Player(it, null)) }
                    }
                }
                MobileHomeScreen(
                    liveTvViewModel = liveTvViewModel,
                    onPlay = { channelId, categoryId -> backStack.push(Player(channelId, categoryId)) },
                    onSearch = { backStack.push(Search) },
                    onGuide = { playlistId, categoryId -> backStack.push(Guide(playlistId, categoryId)) },
                    onOpenVod = { item, series ->
                        if (series) {
                            backStack.push(SeriesDetails(item.id, item.name, item.poster))
                        } else {
                            backStack.push(MovieDetails(item.id, item.name, item.poster, item.extension))
                        }
                    },
                    onSearchVod = { series -> backStack.push(VodSearch(series)) },
                    onPlaylists = { backStack.push(Playlists) },
                    onCategories = { section -> backStack.push(ManageCategories(section.name)) },
                    onParental = { backStack.push(ParentalControl) },
                    onUnlock = { backStack.push(Unlock) },
                    onTrakt = { backStack.push(TraktAccount) },
                    onTransfer = { backStack.push(TransferToTv) },
                    onBackup = { backStack.push(BackupRestore) },
                    onOpenSubtitles = { backStack.push(OpenSubtitlesAccount) },
                    onRecordings = { backStack.push(Recordings) },
                    onDownloads = { backStack.push(Downloads) },
                    onMultiView = { backStack.push(MultiView) },
                )
            }
            entry<Search> {
                MobileSearchScreen(
                    onPlay = { channelId -> backStack.push(Player(channelId, null)) },
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
                    onPlay = { channelId -> backStack.push(Player(channelId, key.categoryId)) },
                    onArchive = { channelId -> backStack.push(ChannelArchive(channelId)) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<ChannelArchive> { key ->
                MobilePinGate(LockTarget.Channel(key.channelId), onCancel = { backStack.removeLastOrNull() }) {
                    MobileArchiveScreen(
                        channelId = key.channelId,
                        onPlay = { program ->
                            backStack.push(
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
                        // Arhiva zamenjuje plejer, da kanal ne nastavi da troši vezu u pozadini.
                        onArchive = { channelId ->
                            backStack.removeLastOrNull()
                            backStack.push(ChannelArchive(channelId))
                        },
                        onCastStarted = castStarted,
                    )
                }
            }
            entry<VodSearch> { key ->
                MobileVodSearchScreen(
                    series = key.series,
                    onOpen = { item ->
                        if (key.series) {
                            backStack.push(SeriesDetails(item.id, item.name, item.poster))
                        } else {
                            backStack.push(MovieDetails(item.id, item.name, item.poster, item.extension))
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
                            backStack.push(PlayMovie(key.movieId, key.name, extension, startOver, key.poster))
                        },
                        onDownloads = { backStack.push(Downloads) },
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
                            backStack.push(PlayEpisode(key.seriesId, episodeId, startOver))
                        },
                        onDownloads = { backStack.push(Downloads) },
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
            entry<BackupRestore> {
                MobileBackupScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<TransferToTv> {
                MobileSendToTvScreen(target = null, onClose = { backStack.removeLastOrNull() })
            }
            entry<SendToTv> { key ->
                MobileSendToTvScreen(target = key.target, onClose = { backStack.removeLastOrNull() })
            }
            entry<MultiView> {
                MobileMultiViewScreen(
                    onOpen = { channelId -> backStack.push(Player(channelId, null)) },
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
                    onPlay = { id -> backStack.push(PlayRecording(id)) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<Downloads> {
                MobileDownloadsScreen(
                    onPlay = { item ->
                        val series = item.entity.seriesId
                        backStack.push(PlayDownload(item.id, series = series != null, lockId = series ?: item.entity.itemId))
                    },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<PlayDownload> { key ->
                MobilePinGate(LockTarget.Vod(series = key.series, itemId = key.lockId), onCancel = { backStack.removeLastOrNull() }) {
                    MobileVodPlayerScreen(
                        start = { it.startDownload(key.downloadId) },
                        onClose = { backStack.removeLastOrNull() },
                    )
                }
            }
            entry<Unlock> {
                // Tačan PIN otključava sve; ekran se zatvara i vraća na spisak kategorija.
                MobilePinGate(LockTarget.Everything, onCancel = { backStack.removeLastOrNull() }) {
                    LaunchedEffect(Unit) { backStack.remove(Unlock) }
                }
            }
            entry<PlayRecording> { key ->
                MobilePinGate(LockTarget.Recording(key.recordingId), onCancel = { backStack.removeLastOrNull() }) {
                    MobileVodPlayerScreen(
                        start = { it.startRecording(key.recordingId) },
                        onClose = { backStack.removeLastOrNull() },
                    )
                }
            }
            entry<Playlists> {
                MobilePlaylistsScreen(
                    onAdd = { backStack.push(EditPlaylist()) },
                    onEdit = { id -> backStack.push(EditPlaylist(id)) },
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
                            backStack.push(Player(channelId, null))
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

/** Prelaz između ekrana na telefonu: kratko pretapanje. */
private val QuickFade = ContentTransform(fadeIn(tween(FADE_MS)), fadeOut(tween(FADE_MS)))

private const val FADE_MS = 150
