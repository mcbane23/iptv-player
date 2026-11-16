package com.bane23.iptvplayer.ui.common.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.repo.ContinueItem
import com.bane23.iptvplayer.data.repo.VodCategory
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.data.repo.VodKind
import com.bane23.iptvplayer.data.repo.VodRepository
import com.bane23.iptvplayer.data.repo.WatchProgress
import com.bane23.iptvplayer.data.repo.WatchTarget
import com.bane23.iptvplayer.data.trakt.TraktKey
import com.bane23.iptvplayer.data.trakt.TraktRepository
import com.bane23.iptvplayer.data.xtream.Episode
import com.bane23.iptvplayer.data.xtream.MovieDetails
import com.bane23.iptvplayer.data.xtream.SeriesDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VodBrowseUiState(
    val series: Boolean = false,
    val loadingCategories: Boolean = true,
    val categoriesFailed: Boolean = false,
    val categories: List<VodCategory> = emptyList(),
    val selected: VodCategory? = null,
    val loadingItems: Boolean = false,
    val itemsFailed: Boolean = false,
    val items: List<VodItem> = emptyList(),
)

/** Kategorije filmova ili serija i sadržaj izabrane kategorije. */
@HiltViewModel
class VodBrowseViewModel @Inject constructor(
    private val repository: VodRepository,
    private val trakt: TraktRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VodBrowseUiState())
    val state: StateFlow<VodBrowseUiState> = _state.asStateFlow()

    private var opened = false
    private var itemsJob: Job? = null
    private val kind get() = if (_state.value.series) VodKind.SERIES else VodKind.MOVIES

    private val openedKind = MutableStateFlow<VodKind?>(null)

    /** Red „Nastavi gledanje” za filmove, odnosno serije aktivne liste. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val continueItems: StateFlow<List<ContinueItem>> = combine(repository.activePlaylistId, openedKind) { id, kind -> id to kind }
        .flatMapLatest { (playlistId, kind) ->
            if (playlistId == null || kind == null) flowOf(emptyList()) else repository.observeContinue(playlistId, kind)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Omiljeni filmovi, odnosno serije, aktivne liste. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val favorites: StateFlow<List<VodItem>> = combine(repository.activePlaylistId, openedKind) { id, kind -> id to kind }
        .flatMapLatest { (playlistId, kind) ->
            if (playlistId == null || kind == null) flowOf(emptyList()) else repository.observeFavorites(playlistId, kind)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Odgledani filmovi, za kvačicu na posteru (serije je nemaju). */
    val watched: StateFlow<WatchedMarks> = watchedMarks(repository, trakt, openedKind)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchedMarks())

    /** Dodaje film ili seriju u omiljene, odnosno uklanja (zvezdica na posteru). */
    fun toggleFavorite(item: VodItem) {
        val isFavorite = favorites.value.any { it.id == item.id }
        viewModelScope.launch { repository.toggleFavorite(kind, item, isFavorite) }
    }

    fun open(series: Boolean) {
        if (opened) return
        opened = true
        _state.update { it.copy(series = series) }
        openedKind.value = if (series) VodKind.SERIES else VodKind.MOVIES
        loadCategories()
    }

    fun loadCategories() {
        _state.update { it.copy(loadingCategories = true, categoriesFailed = false) }
        viewModelScope.launch {
            try {
                val categories = repository.categories(kind)
                _state.update { it.copy(loadingCategories = false, categories = categories) }
                // Kategorije su čipovi iznad postera, pa je jedna uvek izabrana: prva, dok korisnik ne izabere.
                if (_state.value.selected == null) categories.firstOrNull()?.let(::selectCategory)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loadingCategories = false, categoriesFailed = true) }
            }
        }
    }

    fun selectCategory(category: VodCategory) {
        if (category.id == FAVORITES_ID) {
            // Omiljeni su u bazi i stižu kroz [favorites], pa nema šta da se učitava.
            itemsJob?.cancel()
            _state.update { it.copy(selected = category, items = emptyList(), loadingItems = false, itemsFailed = false) }
            return
        }
        _state.update { it.copy(selected = category, items = emptyList(), loadingItems = true, itemsFailed = false) }
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            try {
                val items = repository.items(kind, category.id)
                _state.update { it.copy(loadingItems = false, items = items) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loadingItems = false, itemsFailed = true) }
            }
        }
    }

    fun retryItems() {
        _state.value.selected?.let(::selectCategory)
    }

    fun clearCategory() {
        itemsJob?.cancel()
        _state.update { it.copy(selected = null, items = emptyList(), loadingItems = false, itemsFailed = false) }
    }
}

/** Oznaka za grupu „Omiljeni” među kategorijama (pravi ID-jevi kategorija nikad nisu ovakvi). */
const val FAVORITES_ID = "__favorites"

data class MovieUiState(
    val movieId: Long = 0,
    val name: String = "",
    val poster: String? = null,
    val extension: String? = null,
    val loading: Boolean = true,
    val details: MovieDetails? = null,
    val progress: WatchProgress? = null,
    val favorite: Boolean = false,
)

/** Detalji jednog filma. Ako detalji ne mogu da se učitaju, film i dalje može da se pusti. */
@HiltViewModel
class MovieViewModel @Inject constructor(
    private val repository: VodRepository,
    private val trakt: TraktRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MovieUiState())
    val state: StateFlow<MovieUiState> = _state.asStateFlow()
    private val tmdbId = MutableStateFlow<Long?>(null)

    private var opened = false

    @OptIn(ExperimentalCoroutinesApi::class)
    fun open(movieId: Long, name: String, poster: String?, extension: String?) {
        if (opened) return
        opened = true
        _state.update { it.copy(movieId = movieId, name = name, poster = poster, extension = extension) }
        viewModelScope.launch {
            val details = try {
                repository.movie(movieId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            tmdbId.value = details?.tmdbId
            _state.update {
                it.copy(
                    loading = false,
                    details = details,
                    poster = it.poster ?: details?.poster,
                    extension = details?.extension ?: it.extension,
                )
            }
            // Neki paneli šalju TMDB ID samo u spisku filmova; bez njega nema oznake sa Trakt-a.
            if (tmdbId.value == null) tmdbId.value = listTmdbId { repository.movieListTmdbId(movieId) }
        }
        viewModelScope.launch {
            val local = repository.activePlaylistId.filterNotNull().flatMapLatest { repository.observeMovieProgress(it, movieId) }
            combine(local, trakt.watchedMovies, tmdbId) { progress, traktMovies, tmdb ->
                // Odgledano na Trakt nalogu računa se kao odgledano i ovde.
                if (tmdb != null && tmdb in traktMovies) {
                    (progress ?: WatchProgress(movieId, 0, 0)).copy(watched = true)
                } else {
                    progress
                }
            }.collect { progress -> _state.update { it.copy(progress = progress) } }
        }
        viewModelScope.launch {
            repository.activePlaylistId.filterNotNull()
                .flatMapLatest { repository.observeIsFavorite(it, VodKind.MOVIES, movieId) }
                .collect { favorite -> _state.update { it.copy(favorite = favorite) } }
        }
    }

    fun toggleFavorite() {
        val current = _state.value
        val item = VodItem(current.movieId, current.name, current.poster, rating = null, extension = current.extension)
        viewModelScope.launch { repository.toggleFavorite(VodKind.MOVIES, item, current.favorite) }
    }

    /** Označava film kao odgledan, odnosno poništava oznaku. */
    fun toggleWatched() {
        val current = _state.value
        val target = WatchTarget(current.movieId, current.name, current.poster, current.extension)
        val keys = listOfNotNull(tmdbId.value?.let { TraktKey.movie(it) })
        viewModelScope.launch {
            repository.setWatched(seriesId = null, listOf(target), watched = current.progress?.watched != true, traktKeys = keys)
        }
    }
}

data class SeriesUiState(
    val seriesId: Long = 0,
    val name: String = "",
    val poster: String? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val details: SeriesDetails? = null,
    val selectedSeason: Int? = null,
    /** Napredak po ID-ju epizode. */
    val progress: Map<Long, WatchProgress> = emptyMap(),
    /** Epizoda od koje se nastavlja gledanje: poslednje gledana, ili sledeća ako je ta odgledana. */
    val resumeEpisode: Episode? = null,
    val favorite: Boolean = false,
) {
    val episodes: List<Episode>
        get() = details?.seasons?.firstOrNull { it.number == selectedSeason }?.episodes.orEmpty()
}

/** Detalji serije: sezone, epizode i dokle se stiglo sa gledanjem. */
@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val repository: VodRepository,
    private val trakt: TraktRepository,
) : ViewModel() {

    private val base = MutableStateFlow(SeriesUiState())
    private val seriesId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val progress = combine(repository.activePlaylistId, seriesId) { playlistId, id -> playlistId to id }
        .flatMapLatest { (playlistId, id) ->
            if (playlistId == null || id == null) flowOf(emptyList()) else repository.observeSeriesProgress(playlistId, id)
        }

    /** TMDB ID serije, kad stignu detalji (ili iz spiska serija). */
    private val showTmdbId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val traktEpisodes = showTmdbId.flatMapLatest { tmdb ->
        if (tmdb == null) flowOf(emptySet()) else trakt.watchedEpisodes(tmdb)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val favorite = combine(repository.activePlaylistId, seriesId) { playlistId, id -> playlistId to id }
        .flatMapLatest { (playlistId, id) ->
            if (playlistId == null || id == null) flowOf(false) else repository.observeIsFavorite(playlistId, VodKind.SERIES, id)
        }

    val state: StateFlow<SeriesUiState> = combine(base, progress, favorite, traktEpisodes) { current, watched, isFavorite, onTrakt ->
        val details = current.details
        val merged = watched.associateByTo(LinkedHashMap<Long, WatchProgress>()) { it.itemId }
        // Epizode odgledane na Trakt nalogu računaju se kao odgledane i ovde.
        if (details != null && onTrakt.isNotEmpty()) {
            for (episode in details.episodes) {
                if ((episode.season to episode.number) !in onTrakt) continue
                merged[episode.id] = (merged[episode.id] ?: WatchProgress(episode.id, 0, 0)).copy(watched = true)
            }
        }
        val last = watched.firstOrNull()
        val resume = if (details == null) {
            null
        } else if (last == null) {
            // Ovde još ništa nije gledano: nastavlja se posle poslednje epizode odgledane na Trakt-u.
            details.episodes.lastOrNull { merged[it.id]?.watched == true }?.let { details.episodeAfter(it.id) }
        } else if (!last.resumable && merged[last.itemId]?.finished == true) {
            details.episodeAfter(last.itemId)
        } else {
            details.episodes.firstOrNull { it.id == last.itemId }
        }
        current.copy(
            favorite = isFavorite,
            progress = merged,
            resumeEpisode = resume,
            // Dok korisnik sam ne izabere sezonu, prikazuje se ona u kojoj je stao.
            selectedSeason = current.selectedSeason ?: resume?.season ?: details?.seasons?.firstOrNull()?.number,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesUiState())

    private var opened = false

    fun open(id: Long, name: String, poster: String?) {
        if (opened) return
        opened = true
        seriesId.value = id
        base.update { it.copy(seriesId = id, name = name, poster = poster) }
        load()
    }

    fun load() {
        val id = seriesId.value ?: return
        base.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            try {
                val details = repository.series(id)
                showTmdbId.value = details.tmdbId
                base.update { it.copy(loading = false, details = details, poster = it.poster ?: details.poster) }
                // Neki paneli šalju TMDB ID samo u spisku serija; bez njega nema oznaka sa Trakt-a.
                if (details.tmdbId == null) showTmdbId.value = listTmdbId { repository.seriesListTmdbId(id) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                base.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun selectSeason(number: Int) = base.update { it.copy(selectedSeason = number) }

    fun toggleFavorite() {
        val current = state.value
        val item = VodItem(current.seriesId, current.name, current.poster, rating = null, extension = null)
        viewModelScope.launch { repository.toggleFavorite(VodKind.SERIES, item, current.favorite) }
    }

    /** Označava epizodu kao odgledanu, odnosno poništava oznaku. */
    fun toggleWatched(episode: Episode) {
        val watched = state.value.progress[episode.id]?.watched != true
        setWatched(listOf(episode), watched)
    }

    /** Cela sezona: ako su sve epizode odgledane, oznaka se skida, inače se sve označavaju. */
    fun toggleSeasonWatched(season: Int) {
        val current = state.value
        val episodes = current.details?.seasons?.firstOrNull { it.number == season }?.episodes.orEmpty()
        setWatched(episodes, watched = !current.isSeasonWatched(season))
    }

    private fun setWatched(episodes: List<Episode>, watched: Boolean) {
        val current = state.value
        val id = seriesId.value ?: return
        val targets = episodes.map { WatchTarget(it.id, current.name, current.poster) }
        val keys = showTmdbId.value?.let { tmdb -> episodes.map { TraktKey.episode(tmdb, it.season, it.number) } }.orEmpty()
        viewModelScope.launch { repository.setWatched(id, targets, watched, keys) }
    }
}

/** Da li su sve epizode sezone odgledane. */
fun SeriesUiState.isSeasonWatched(season: Int): Boolean {
    val episodes = details?.seasons?.firstOrNull { it.number == season }?.episodes.orEmpty()
    return episodes.isNotEmpty() && episodes.all { progress[it.id]?.watched == true }
}

data class VodSearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val failed: Boolean = false,
    val results: List<VodItem> = emptyList(),
)

/**
 * Pretraga filmova, odnosno serija, kroz sve kategorije aktivne liste. Ceo spisak se učitava jednom
 * (pri prvom kucanju), a traži se po nazivu bez obzira na velika slova i kvačice (č, ć, š, ž, đ).
 */
@HiltViewModel
class VodSearchViewModel @Inject constructor(
    private val repository: VodRepository,
    private val trakt: TraktRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VodSearchUiState())
    val state: StateFlow<VodSearchUiState> = _state.asStateFlow()

    private var kind = VodKind.MOVIES
    private val openedKind = MutableStateFlow<VodKind?>(null)
    private var all: List<Pair<String, VodItem>>? = null
    private var searchJob: Job? = null

    /** ID-jevi omiljenih, za zvezdicu na posteru. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteIds: StateFlow<Set<Long>> = combine(repository.activePlaylistId, openedKind) { id, kind -> id to kind }
        .flatMapLatest { (playlistId, kind) ->
            if (playlistId == null || kind == null) flowOf(emptyList()) else repository.observeFavorites(playlistId, kind)
        }
        .map { list -> list.mapTo(HashSet()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun open(series: Boolean) {
        kind = if (series) VodKind.SERIES else VodKind.MOVIES
        openedKind.value = kind
    }

    /** Odgledani filmovi, za kvačicu na posteru. */
    val watched: StateFlow<WatchedMarks> = watchedMarks(repository, trakt, openedKind)
        .stateIn(viewModelScope, SharingStarted.Eagerly, WatchedMarks())

    fun toggleFavorite(item: VodItem) {
        val isFavorite = item.id in favoriteIds.value
        viewModelScope.launch { repository.toggleFavorite(kind, item, isFavorite) }
    }

    fun setQuery(text: String) {
        _state.update { it.copy(query = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DELAY_MS)
            val needle = VodSearch.normalize(text)
            if (needle.isEmpty()) {
                _state.update { it.copy(results = emptyList(), loading = false, failed = false) }
                return@launch
            }
            val items = all ?: try {
                _state.update { it.copy(loading = true, failed = false) }
                repository.allItems(kind).map { VodSearch.normalize(it.name) to it }.also { all = it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            val results = withContext(Dispatchers.Default) {
                items.asSequence().filter { (name, _) -> needle in name }.map { it.second }.take(MAX_RESULTS).toList()
            }
            _state.update { it.copy(loading = false, results = results) }
        }
    }

    fun retry() = setQuery(_state.value.query)

    private companion object {
        const val SEARCH_DELAY_MS = 300L
        const val MAX_RESULTS = 300
    }
}

/** Poređenje naziva za pretragu: mala slova, bez kvačica, „đ” kao „dj”. */
object VodSearch {
    private val marks = Regex("\\p{Mn}+")

    fun normalize(text: String): String =
        java.text.Normalizer.normalize(text.trim().lowercase().replace("đ", "dj"), java.text.Normalizer.Form.NFD)
            .replace(marks, "")
}

/** TMDB ID iz spiska na serveru; kad spisak ne može da se učita, ekran radi i bez njega. */
private suspend fun listTmdbId(lookup: suspend () -> Long?): Long? = try {
    lookup()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

/** Šta je odgledano: ID-jevi sa liste (označeno u aplikaciji) i TMDB ID-jevi (odgledano na Trakt nalogu). */
data class WatchedMarks(val ids: Set<Long> = emptySet(), val tmdbIds: Set<Long> = emptySet()) {
    fun of(item: VodItem): Boolean = item.id in ids || (item.tmdbId != null && item.tmdbId in tmdbIds)
}

/** Odgledani filmovi aktivne liste; za serije prazno. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun watchedMarks(repository: VodRepository, trakt: TraktRepository, kind: Flow<VodKind?>): Flow<WatchedMarks> =
    combine(repository.activePlaylistId, kind) { id, opened -> id to opened }
        .flatMapLatest { (playlistId, opened) ->
            if (playlistId == null || opened != VodKind.MOVIES) {
                flowOf(WatchedMarks())
            } else {
                combine(repository.observeWatchedMovieIds(playlistId), trakt.watchedMovies) { ids, tmdb -> WatchedMarks(ids, tmdb) }
            }
        }
