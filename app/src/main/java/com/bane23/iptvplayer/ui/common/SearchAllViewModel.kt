package com.bane23.iptvplayer.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.db.ChannelHit
import com.bane23.iptvplayer.data.db.ProgramHit
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.reminder.Reminder
import com.bane23.iptvplayer.data.reminder.ReminderRepository
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.data.repo.VodKind
import com.bane23.iptvplayer.data.repo.VodRepository
import com.bane23.iptvplayer.ui.common.vod.VodSearch
import com.bane23.iptvplayer.ui.common.vod.vodFavoriteIds
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchAllUiState(
    val query: String = "",
    val searching: Boolean = false,
    /** Upit za koji važe rezultati ispod; prazan dok se ništa nije tražilo. */
    val searched: String = "",
    val channels: List<ChannelHit> = emptyList(),
    val programs: List<ProgramHit> = emptyList(),
    val movies: List<VodItem> = emptyList(),
    val series: List<VodItem> = emptyList(),
) {
    val nothingFound: Boolean
        get() = searched.isNotEmpty() && !searching &&
            channels.isEmpty() && programs.isEmpty() && movies.isEmpty() && series.isEmpty()
}

/**
 * Jedna pretraga kroz kanale, emisije u vodiču (one koje traju ili tek počinju), filmove i serije
 * aktivne liste. Svaka grupa prikazuje najviše [GROUP_LIMIT] rezultata.
 */
@HiltViewModel
class SearchAllViewModel @Inject constructor(
    private val channels: ChannelRepository,
    private val vod: VodRepository,
    private val reminderRepository: ReminderRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    /** Nedavni upiti, najnoviji prvi; prikazuju se dok je polje prazno. */
    val recent: StateFlow<List<String>> = settings.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(SearchAllUiState())
    val state: StateFlow<SearchAllUiState> = _state.asStateFlow()

    /** Ključevi emisija koje imaju podsetnik, za zvonce pored rezultata. */
    val reminders: StateFlow<Set<String>> = reminderRepository.reminders
        .map { list -> list.mapTo(HashSet<String>()) { it.key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

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

    private val _notice = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /** Kratka poruka (tekst iz `strings.xml`). */
    val notice: SharedFlow<Int> = _notice.asSharedFlow()

    private var searchJob: Job? = null

    /** Spiskovi filmova i serija sa nazivima spremnim za poređenje; učitavaju se pri prvoj pretrazi. */
    private val vodLists = HashMap<VodKind, List<Pair<String, VodItem>>>()

    /**
     * Pamti trenutni upit među nedavnim. Poziva se kad korisnik potvrdi pretragu (taster na
     * tastaturi, izgovoren upit) ili otvori neki rezultat, a ne pri svakom otkucanom slovu.
     */
    fun remember() {
        val query = _state.value.query.trim()
        if (query.length < MIN_QUERY) return
        viewModelScope.launch { settings.addRecentSearch(query) }
    }

    /** Briše jedan nedavni upit. */
    fun removeRecent(query: String) {
        viewModelScope.launch { settings.removeRecentSearch(query) }
    }

    fun clearRecent() {
        viewModelScope.launch { settings.clearRecentSearches() }
    }

    /** Gotov upit (izabran među nedavnim ili izgovoren): traži odmah, bez čekanja na kucanje. */
    fun submit(text: String) {
        setQuery(text, wait = false)
        remember()
    }

    fun setQuery(text: String) = setQuery(text, wait = true)

    private fun setQuery(text: String, wait: Boolean) {
        _state.update { it.copy(query = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (wait) delay(SEARCH_DELAY_MS)
            val query = text.trim()
            if (query.length < MIN_QUERY) {
                _state.update { SearchAllUiState(query = it.query) }
                return@launch
            }
            _state.update { it.copy(searching = true) }
            val playlistId = channels.activePlaylistIdNow()
            if (playlistId != null) {
                val channelHits = channels.searchTop(playlistId, query, GROUP_LIMIT)
                _state.update { it.copy(searched = query, channels = channelHits, programs = emptyList(), movies = emptyList(), series = emptyList()) }
                val programHits = channels.searchPrograms(playlistId, query, GROUP_LIMIT)
                _state.update { it.copy(programs = programHits) }
                val needle = VodSearch.normalize(query)
                val movies = searchVod(VodKind.MOVIES, needle)
                _state.update { it.copy(movies = movies) }
                val series = searchVod(VodKind.SERIES, needle)
                _state.update { it.copy(series = series) }
            }
            _state.update { it.copy(searching = false, searched = query) }
        }
    }

    /** Liste bez filmova i serija (M3U) ovde samo ne daju rezultate. */
    private suspend fun searchVod(kind: VodKind, needle: String): List<VodItem> {
        val all = vodLists[kind] ?: try {
            vod.allItems(kind).map { VodSearch.normalize(it.name) to it }.also { vodLists[kind] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return emptyList()
        }
        return withContext(Dispatchers.Default) {
            com.bane23.iptvplayer.data.text.SearchText.search(all, needle, GROUP_LIMIT)
        }
    }

    /** Podsetnik za emisiju koja još nije počela: uključuje ga, odnosno uklanja. */
    fun toggleReminder(hit: ProgramHit) {
        val reminder = Reminder(hit.playlistId, hit.stableKey, hit.startMs, hit.channelName, hit.title)
        val exists = reminder.key in reminders.value
        if (!exists && hit.startMs <= System.currentTimeMillis()) return
        viewModelScope.launch {
            if (exists) reminderRepository.remove(reminder) else reminderRepository.add(reminder)
            _notice.tryEmit(if (exists) R.string.reminder_removed else R.string.reminder_set)
        }
    }

    private companion object {
        const val SEARCH_DELAY_MS = 400L
        const val MIN_QUERY = 2
        const val GROUP_LIMIT = 30
    }
}
