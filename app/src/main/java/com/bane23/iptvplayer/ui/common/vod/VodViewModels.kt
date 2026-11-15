package com.bane23.iptvplayer.ui.common.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.repo.VodCategory
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.data.repo.VodKind
import com.bane23.iptvplayer.data.repo.VodRepository
import com.bane23.iptvplayer.data.repo.WatchProgress
import com.bane23.iptvplayer.data.xtream.Episode
import com.bane23.iptvplayer.data.xtream.MovieDetails
import com.bane23.iptvplayer.data.xtream.SeriesDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) : ViewModel() {

    private val _state = MutableStateFlow(VodBrowseUiState())
    val state: StateFlow<VodBrowseUiState> = _state.asStateFlow()

    private var opened = false
    private var itemsJob: Job? = null
    private val kind get() = if (_state.value.series) VodKind.SERIES else VodKind.MOVIES

    fun open(series: Boolean) {
        if (opened) return
        opened = true
        _state.update { it.copy(series = series) }
        loadCategories()
    }

    fun loadCategories() {
        _state.update { it.copy(loadingCategories = true, categoriesFailed = false) }
        viewModelScope.launch {
            try {
                val categories = repository.categories(kind)
                _state.update { it.copy(loadingCategories = false, categories = categories) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loadingCategories = false, categoriesFailed = true) }
            }
        }
    }

    fun selectCategory(category: VodCategory) {
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

data class MovieUiState(
    val movieId: Long = 0,
    val name: String = "",
    val poster: String? = null,
    val extension: String? = null,
    val loading: Boolean = true,
    val details: MovieDetails? = null,
    val progress: WatchProgress? = null,
)

/** Detalji jednog filma. Ako detalji ne mogu da se učitaju, film i dalje može da se pusti. */
@HiltViewModel
class MovieViewModel @Inject constructor(
    private val repository: VodRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MovieUiState())
    val state: StateFlow<MovieUiState> = _state.asStateFlow()

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
            _state.update {
                it.copy(
                    loading = false,
                    details = details,
                    poster = it.poster ?: details?.poster,
                    extension = details?.extension ?: it.extension,
                )
            }
        }
        viewModelScope.launch {
            repository.activePlaylistId.filterNotNull()
                .flatMapLatest { repository.observeMovieProgress(it, movieId) }
                .collect { progress -> _state.update { it.copy(progress = progress) } }
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
) {
    val episodes: List<Episode>
        get() = details?.seasons?.firstOrNull { it.number == selectedSeason }?.episodes.orEmpty()
}

/** Detalji serije: sezone, epizode i dokle se stiglo sa gledanjem. */
@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val repository: VodRepository,
) : ViewModel() {

    private val base = MutableStateFlow(SeriesUiState())
    private val seriesId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val progress = combine(repository.activePlaylistId, seriesId) { playlistId, id -> playlistId to id }
        .flatMapLatest { (playlistId, id) ->
            if (playlistId == null || id == null) flowOf(emptyList()) else repository.observeSeriesProgress(playlistId, id)
        }

    val state: StateFlow<SeriesUiState> = combine(base, progress) { current, watched ->
        val details = current.details
        val last = watched.firstOrNull()
        val resume = if (details == null || last == null) {
            null
        } else if (last.finished) {
            details.episodeAfter(last.itemId)
        } else {
            details.episodes.firstOrNull { it.id == last.itemId }
        }
        current.copy(
            progress = watched.associateBy { it.itemId },
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
                base.update { it.copy(loading = false, details = details, poster = it.poster ?: details.poster) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                base.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun selectSeason(number: Int) = base.update { it.copy(selectedSeason = number) }
}
