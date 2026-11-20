package com.bane23.iptvplayer.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.data.repo.ContinueItem
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.data.repo.VodKind
import com.bane23.iptvplayer.data.repo.VodRepository
import com.bane23.iptvplayer.data.repo.mergeContinue
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.ui.common.vod.vodFavoriteIds
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Kanali za red na „Početnoj”: [group] je grupa iz [ChannelGroups] (omiljeni ili nedavno gledani). */
data class StartChannels(val group: Long = ChannelGroups.FAVORITES, val channels: List<Channel> = emptyList())

/**
 * Nedavno dodati filmovi i serije. [loaded] je false dok se spisak još čita; [loading] je vrsta koja
 * se upravo preuzima prvi put (na uređaju još nema sačuvanog reda), za poruku na ekranu.
 */
data class StartAdded(
    val movies: List<VodItem> = emptyList(),
    val series: List<VodItem> = emptyList(),
    val loaded: Boolean = false,
    val loading: VodKind? = null,
)

/** Redovi „Početne” na TV-u za aktivnu listu: nastavak gledanja, kanali i nedavno dodato. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TvStartViewModel @Inject constructor(
    private val channels: ChannelRepository,
    private val vod: VodRepository,
) : ViewModel() {

    /** Započeti filmovi i serije u jednom redu, poslednje gledano prvo. */
    val continueItems: StateFlow<List<ContinueItem>> = vod.activePlaylistId
        .flatMapLatest { playlistId ->
            if (playlistId == null) {
                flowOf(emptyList())
            } else {
                combine(
                    vod.observeContinue(playlistId, VodKind.MOVIES),
                    vod.observeContinue(playlistId, VodKind.SERIES),
                ) { movies, series -> mergeContinue(movies, series, CONTINUE_LIMIT) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Omiljeni kanali (ili nedavno gledani kad omiljenih nema) sa emisijom koja je sada na programu.
     * Čita se iznova svakog minuta i kad se promeni broj omiljenih, da „sada” ne zaostaje.
     */
    val channelRow: StateFlow<StartChannels?> = vod.activePlaylistId
        .flatMapLatest { playlistId ->
            if (playlistId == null) {
                flowOf(StartChannels())
            } else {
                combine(
                    channels.observeFavoriteCount(playlistId),
                    channels.observeRecentCount(playlistId),
                    minuteTicks(),
                ) { favorites, recent, tick -> Triple(favorites, recent, tick) }
                    .mapLatest {
                        val (group, list) = channels.homeChannels(playlistId, CHANNEL_LIMIT)
                        StartChannels(group, list)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Nedavno dodati filmovi i serije; samo za liste koje ih imaju (Xtream). */
    val added: StateFlow<StartAdded> = vod.activePlaylistId
        .flatMapLatest { playlistId ->
            if (playlistId == null) flowOf(false) else channels.observeIsXtream(playlistId)
        }
        .distinctUntilChanged()
        .flatMapLatest { xtream ->
            if (!xtream) {
                flowOf(StartAdded(loaded = true))
            } else {
                channelFlow {
                    // Redovi sačuvani pri prošlom pokretanju se prikazuju odmah; ceo spisak liste se
                    // za to vreme čita u pozadini i redovi se tiho zamene ako ima novijeg sadržaja.
                    val storedMovies = stored(VodKind.MOVIES)
                    val storedSeries = stored(VodKind.SERIES)
                    var state = StartAdded(
                        movies = storedMovies.orEmpty(),
                        series = storedSeries.orEmpty(),
                        loaded = storedMovies != null && storedSeries != null,
                    )
                    val lock = Mutex()
                    suspend fun refresh(kind: VodKind, first: Boolean) = lock.withLock {
                        val had = if (kind == VodKind.MOVIES) storedMovies != null else storedSeries != null
                        if (first && !had) {
                            state = state.copy(loading = kind)
                            send(state)
                        }
                        // Bez mreže ostaje red koji je već prikazan.
                        val fresh = load(kind)
                        state = when {
                            fresh == null -> state
                            kind == VodKind.MOVIES -> state.copy(movies = fresh)
                            else -> state.copy(series = fresh)
                        }
                        if (first) state = state.copy(loading = null, loaded = state.loaded || kind == VodKind.SERIES)
                        send(state)
                    }
                    send(state)
                    // Lista je osvežena (dnevno ili ručno) i noviji spisak je stigao u pozadini.
                    launch { vod.updates.collect { kind -> refresh(kind, first = false) } }
                    refresh(VodKind.MOVIES, first = true)
                    refresh(VodKind.SERIES, first = true)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, StartAdded())

    private suspend fun stored(kind: VodKind): List<VodItem>? = try {
        vod.storedRecentlyAdded(kind, ADDED_LIMIT)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Null kad spisak nije stigao (bez mreže ili ga panel ne šalje); red tada ostaje kakav je bio. */
    private suspend fun load(kind: VodKind): List<VodItem>? = try {
        vod.recentlyAdded(kind, ADDED_LIMIT)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Omiljeni filmovi i serije, za zvezdicu na posteru. */
    val favoriteMovies: StateFlow<Set<Long>> = vodFavoriteIds(vod, VodKind.MOVIES)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val favoriteSeries: StateFlow<Set<Long>> = vodFavoriteIds(vod, VodKind.SERIES)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Dodaje film ili seriju u omiljene, odnosno uklanja (dugi OK na posteru). */
    fun toggleFavorite(item: VodItem, series: Boolean) {
        val favorite = item.id in (if (series) favoriteSeries else favoriteMovies).value
        viewModelScope.launch { vod.toggleFavorite(if (series) VodKind.SERIES else VodKind.MOVIES, item, favorite) }
    }

    fun dismissContinue(item: ContinueItem) {
        viewModelScope.launch { vod.dismissContinue(item) }
    }

    private fun minuteTicks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(REFRESH_MS)
        }
    }

    private companion object {
        const val CONTINUE_LIMIT = 20
        const val CHANNEL_LIMIT = 20
        const val ADDED_LIMIT = 20
        const val REFRESH_MS = 60_000L
    }
}
