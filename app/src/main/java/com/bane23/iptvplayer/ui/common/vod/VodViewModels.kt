package com.bane23.iptvplayer.ui.common.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.repo.ContinueItem
import com.bane23.iptvplayer.data.repo.VodCategory
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.data.repo.VodKind
import com.bane23.iptvplayer.data.repo.VodRepository
import com.bane23.iptvplayer.data.repo.VodSort
import com.bane23.iptvplayer.data.repo.sortVodItems
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filter
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
    /** Redosled postera u kategoriji. */
    val sort: VodSort = VodSort.DEFAULT,
    /**
     * Da li se sekcija otvara na „Nastavi gledanje” (kad ga ima). Netačno ako je korisnik poslednji
     * put izašao sa neke kategorije; tada se otvara ona.
     */
    val startOnContinue: Boolean = true,
    /** Da li se spiskovi čuvaju na uređaju; tada poruka pri učitavanju kaže da je to samo prvi put. */
    val storedOnDevice: Boolean = true,
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

    /** Sadržaj izabrane kategorije redom kojim ga šalje provajder. */
    private var rawItems: List<VodItem> = emptyList()
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
        viewModelScope.launch {
            val sort = repository.sort(kind)
            if (sort != VodSort.DEFAULT) applySort(sort)
        }
        loadCategories()
        viewModelScope.launch {
            repository.updates.filter { it == kind }.collect { refreshQuietly() }
        }
        viewModelScope.launch {
            repository.localCacheEnabled.collect { enabled -> _state.update { it.copy(storedOnDevice = enabled) } }
        }
    }

    /** Lista je upravo ručno osvežena: kategorije i otvorena kategorija se učitavaju iznova. */
    fun reload() {
        if (!opened) return
        viewModelScope.launch { refreshQuietly() }
    }

    /**
     * U pozadini je stigao noviji spisak od onog sačuvanog na uređaju: kategorije i izabrana
     * kategorija se zamene bez obrisa učitavanja.
     */
    private suspend fun refreshQuietly() {
        try {
            val categories = repository.categories(kind)
            _state.update { it.copy(categories = categories) }
            val selected = _state.value.selected ?: return
            if (selected.id == FAVORITES_ID || _state.value.loadingItems) return
            if (categories.none { it.id == selected.id }) return
            val items = repository.items(kind, selected.id)
            if (_state.value.selected?.id != selected.id || _state.value.loadingItems) return
            rawItems = items
            val sort = _state.value.sort
            val sorted = withContext(Dispatchers.Default) { sortVodItems(items, sort) }
            if (rawItems === items && _state.value.sort == sort) _state.update { it.copy(items = sorted) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Ostaje spisak koji se već vidi.
        }
    }

    /** Menja redosled postera i pamti ga za sledeći put. */
    fun setSort(sort: VodSort) {
        if (sort == _state.value.sort) return
        viewModelScope.launch { repository.setSort(kind, sort) }
        viewModelScope.launch { applySort(sort) }
    }

    private suspend fun applySort(sort: VodSort) {
        _state.update { it.copy(sort = sort) }
        val raw = rawItems
        val sorted = withContext(Dispatchers.Default) { sortVodItems(raw, sort) }
        // Ako je u međuvremenu izabrana druga kategorija ili redosled, ovaj rezultat više ne važi.
        if (rawItems === raw && _state.value.sort == sort) _state.update { it.copy(items = sorted) }
    }

    fun loadCategories() {
        _state.update { it.copy(loadingCategories = true, categoriesFailed = false) }
        viewModelScope.launch {
            try {
                val categories = repository.categories(kind)
                val first = _state.value.selected == null
                val last = if (first) repository.lastCategory(kind) else null
                _state.update {
                    it.copy(
                        loadingCategories = false,
                        categories = categories,
                        startOnContinue = if (first) last == null || last == CONTINUE_ID else it.startOnContinue,
                    )
                }
                // Jedna kategorija je uvek izabrana: ona od prošlog puta, inače prva.
                if (_state.value.selected == null) {
                    val restored = when (last) {
                        FAVORITES_ID -> VodCategory(FAVORITES_ID, "")
                        else -> categories.firstOrNull { it.id == last } ?: categories.firstOrNull()
                    }
                    restored?.let { select(it, remember = false) }
                }
                watchCategoryPrefs()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loadingCategories = false, categoriesFailed = true) }
            }
        }
    }

    private var prefsJob: Job? = null

    /** Kad korisnik sakrije ili premesti kategoriju (i na ekranu za uređivanje), spisak se slaže iznova. */
    private fun watchCategoryPrefs() {
        if (prefsJob != null) return
        prefsJob = viewModelScope.launch {
            repository.observeActiveCategoryPrefs(kind).drop(1).collect {
                try {
                    val categories = repository.categories(kind)
                    _state.update { it.copy(categories = categories) }
                    val selected = _state.value.selected
                    if (selected != null && selected.id != FAVORITES_ID && categories.none { it.id == selected.id }) {
                        // Izabrana kategorija je upravo sakrivena.
                        categories.firstOrNull()?.let { select(it, remember = false) } ?: clearCategory()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Spisak ostaje kakav je bio; sledeće otvaranje sekcije ga učitava iznova.
                }
            }
        }
    }

    /** Sakriva kategoriju; vraća se na ekranu „Uredi kategorije”. */
    fun hideCategory(category: VodCategory) {
        viewModelScope.launch { repository.setCategoriesHidden(kind, listOf(category.id), hidden = true) }
    }

    fun moveCategoryToTop(category: VodCategory) {
        viewModelScope.launch {
            try {
                repository.moveCategoryToTop(kind, category.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Bez spiska sa servera nema šta da se premesti.
            }
        }
    }

    /** Pamti da je korisnik otvorio „Nastavi gledanje”, da se sekcija sledeći put otvori na njemu. */
    fun rememberContinue() {
        viewModelScope.launch { repository.setLastCategory(kind, CONTINUE_ID) }
    }

    /** Sklanja film, odnosno seriju, iz reda „Nastavi gledanje” (vraća se kad se ponovo gleda). */
    fun dismissContinue(item: ContinueItem) {
        viewModelScope.launch { repository.dismissContinue(item) }
    }

    fun selectCategory(category: VodCategory) = select(category, remember = true)

    private fun select(category: VodCategory, remember: Boolean) {
        if (remember) viewModelScope.launch { repository.setLastCategory(kind, category.id) }
        if (category.id == FAVORITES_ID) {
            // Omiljeni su u bazi i stižu kroz [favorites], pa nema šta da se učitava.
            itemsJob?.cancel()
            rawItems = emptyList()
            _state.update { it.copy(selected = category, items = emptyList(), loadingItems = false, itemsFailed = false) }
            return
        }
        rawItems = emptyList()
        _state.update { it.copy(selected = category, items = emptyList(), loadingItems = true, itemsFailed = false) }
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            try {
                val items = repository.items(kind, category.id)
                rawItems = items
                val sort = _state.value.sort
                val sorted = withContext(Dispatchers.Default) { sortVodItems(items, sort) }
                _state.update { it.copy(loadingItems = false, items = sorted) }
                // Redosled je promenjen dok se spisak slagao.
                if (_state.value.sort != sort) applySort(_state.value.sort)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loadingItems = false, itemsFailed = true) }
            }
        }
    }

    fun retryItems() {
        _state.value.selected?.let { select(it, remember = false) }
    }

    fun clearCategory() {
        itemsJob?.cancel()
        rawItems = emptyList()
        _state.update { it.copy(selected = null, items = emptyList(), loadingItems = false, itemsFailed = false) }
    }
}

/** Oznaka za grupu „Omiljeni” među kategorijama (pravi ID-jevi kategorija nikad nisu ovakvi). */
const val FAVORITES_ID = "__favorites"

/** Oznaka za „Nastavi gledanje” kad se pamti šta je poslednje bilo otvoreno. */
const val CONTINUE_ID = "__continue"

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
        trakt.refreshIfStale()
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
    /** Epizoda od koje se nastavlja gledanje, vidi [resumeEpisode]. */
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
        if (tmdb == null) flowOf(emptyMap<Pair<Int, Int>, Long>()) else trakt.watchedEpisodes(tmdb)
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
        val resume = details?.let { resumeEpisode(it, watched, onTrakt) }
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
        trakt.refreshIfStale()
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

/**
 * Epizoda od koje se nastavlja gledanje serije: ono što je poslednje gledano na bilo kom uređaju,
 * a ako je to odgledano do kraja, sledeća epizoda. Null kad nema šta da se nastavi.
 *
 * [local] je napredak zapamćen na ovom uređaju, poslednje gledano prvo. [onTrakt] su epizode
 * odgledane na Trakt nalogu, kao (sezona, epizoda), sa vremenom gledanja (0 kad se ne zna).
 * Epizoda odgledana drugde posle onoga što je ovde zapamćeno ima prednost: bez toga bi stara
 * pozicija sa ovog uređaja nudila nastavak epizode koja je u međuvremenu odgledana na drugom.
 */
fun resumeEpisode(details: SeriesDetails, local: List<WatchProgress>, onTrakt: Map<Pair<Int, Int>, Long>): Episode? {
    val episodes = details.episodes
    val last = local.firstOrNull()
    // Poslednje odgledana na nalogu; od istovremeno označenih (cela sezona) ona koja je dalje u seriji.
    val remote = episodes.withIndex()
        .filter { (it.value.season to it.value.number) in onTrakt }
        .maxWithOrNull(compareBy({ onTrakt.getValue(it.value.season to it.value.number) }, { it.index }))
        ?.value
    val remoteAt = remote?.let { onTrakt.getValue(it.season to it.number) } ?: 0L
    if (remote != null && (last == null || remoteAt > last.updatedAt)) return details.episodeAfter(remote.id)
    if (last == null) return null
    val episode = episodes.firstOrNull { it.id == last.itemId } ?: return null
    val lastOnTrakt = onTrakt[episode.season to episode.number]
    return when {
        // Odgledana do kraja (ovde ili na nalogu), a pozicija ne nudi nastavak.
        !last.resumable && (last.watched || lastOnTrakt != null) -> details.episodeAfter(episode.id)
        // Ovde započeta, a na nalogu odgledana ne zna se kada: računa se da je završena drugde.
        !last.watched && lastOnTrakt == 0L -> details.episodeAfter(episode.id)
        else -> episode
    }
}

/**
 * Mesto prve neodgledane epizode u spisku, da se spisak otvori na njoj. Kad nijedna nije odgledana
 * ili su sve odgledane, to je početak spiska.
 */
fun firstUnwatchedIndex(episodes: List<Episode>, progress: Map<Long, WatchProgress>): Int =
    episodes.indexOfFirst { progress[it.id]?.watched != true }.coerceAtLeast(0)

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
                // Priprema naziva za ceo spisak traje, pa ne ide na glavnoj niti.
                val list = repository.allItems(kind)
                withContext(Dispatchers.Default) { list.map { VodSearch.normalize(it.name) to it } }.also { all = it }
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
