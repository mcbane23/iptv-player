package com.bane23.iptvplayer.ui.common

import com.bane23.iptvplayer.ui.common.theme.AccentColor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.domain.Category
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.player.BufferSize
import com.bane23.iptvplayer.player.DecoderMode
import com.bane23.iptvplayer.player.PlayerSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LiveTvUiState(
    val loading: Boolean = true,
    /** Aktivna lista, ili null ako nijedna nije izabrana. */
    val playlistId: Long? = null,
    val categories: List<Category> = emptyList(),
    val totalChannels: Int = 0,
    val favoriteCount: Int = 0,
    val recentCount: Int = 0,
    /** Lista je Xtream nalog, pa ima i filmove i serije. */
    val hasVod: Boolean = false,
)

/** Kategorije aktivne liste. */
@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repository: ChannelRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<LiveTvUiState> = repository.activePlaylistId
        .flatMapLatest { playlistId ->
            if (playlistId == null) {
                flowOf(LiveTvUiState(loading = false))
            } else {
                combine(
                    repository.observeCategories(playlistId),
                    repository.observeChannelCount(playlistId),
                    repository.observeFavoriteCount(playlistId),
                    repository.observeRecentCount(playlistId),
                    repository.observeIsXtream(playlistId),
                ) { categories, total, favorites, recent, xtream ->
                    LiveTvUiState(
                        loading = false,
                        playlistId = playlistId,
                        categories = categories,
                        totalChannels = total,
                        favoriteCount = favorites,
                        recentCount = recent,
                        hasVod = xtream,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveTvUiState())

    /** Kanal koji treba pustiti odmah pri startu aplikacije, ili null. */
    suspend fun channelToResume(): Long? = repository.channelToResume()
}

/** Kanali jedne kategorije (ili cele liste), učitavani stranu po stranu. */
@HiltViewModel
class ChannelListViewModel @Inject constructor(
    private val repository: ChannelRepository,
) : ViewModel() {

    private data class Selection(val playlistId: Long, val categoryId: Long?)

    private val selection = MutableStateFlow<Selection?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val channels: Flow<PagingData<Channel>> = selection.filterNotNull()
        .flatMapLatest { repository.channels(it.playlistId, it.categoryId) }
        .cachedIn(viewModelScope)

    fun select(playlistId: Long, categoryId: Long?) {
        selection.value = Selection(playlistId, categoryId)
    }

    fun toggleFavorite(channelId: Long) {
        viewModelScope.launch { repository.toggleFavorite(channelId) }
    }
}

data class SearchUiState(val playlistId: Long? = null, val query: String = "")

/** Pretraga kanala po nazivu kroz celu aktivnu listu. */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ChannelRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<SearchUiState> = combine(repository.activePlaylistId, query) { playlistId, text ->
        SearchUiState(playlistId, text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    /** Rezultati; upit se šalje tek kad korisnik na trenutak prestane da kuca. */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val results: Flow<PagingData<Channel>> = combine(
        repository.activePlaylistId,
        query.debounce(SEARCH_DELAY_MS),
    ) { playlistId, text -> playlistId to text }
        .flatMapLatest { (playlistId, text) ->
            if (playlistId == null) flowOf(PagingData.empty()) else repository.search(playlistId, text)
        }
        .cachedIn(viewModelScope)

    fun setQuery(text: String) {
        query.value = text
    }

    fun toggleFavorite(channelId: Long) {
        viewModelScope.launch { repository.toggleFavorite(channelId) }
    }

    private companion object {
        const val SEARCH_DELAY_MS = 300L
    }
}

data class CategoriesUiState(val playlistId: Long? = null, val categories: List<Category> = emptyList())

/** Sakrivanje kategorija i promena njihovog redosleda. */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: ChannelRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CategoriesUiState> = repository.activePlaylistId
        .flatMapLatest { playlistId ->
            if (playlistId == null) {
                flowOf(CategoriesUiState())
            } else {
                repository.observeAllCategories(playlistId).map { CategoriesUiState(playlistId, it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun setHidden(category: Category, hidden: Boolean) {
        viewModelScope.launch { repository.setCategoryHidden(category.id, hidden) }
    }

    fun move(category: Category, delta: Int) {
        val playlistId = state.value.playlistId ?: return
        viewModelScope.launch { repository.moveCategory(playlistId, category.id, delta) }
    }
}

/** Podešavanja aplikacije. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ChannelRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    val playerSettings: StateFlow<PlayerSettings> = settings.playerSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerSettings())

    fun setBuffer(buffer: BufferSize) {
        viewModelScope.launch { settings.setPlayerBuffer(buffer) }
    }

    fun setSubtitleBackground(enabled: Boolean) {
        viewModelScope.launch { settings.setSubtitleBackground(enabled) }
    }

    fun setSubtitleSize(size: com.bane23.iptvplayer.player.SubtitleSize) {
        viewModelScope.launch { settings.setSubtitleSize(size) }
    }

    fun setSubtitlePosition(position: com.bane23.iptvplayer.player.SubtitlePosition) {
        viewModelScope.launch { settings.setSubtitlePosition(position) }
    }

    fun setSeekStep(seconds: Int) {
        viewModelScope.launch { settings.setSeekStep(seconds) }
    }

    fun setDecoder(decoder: DecoderMode) {
        viewModelScope.launch { settings.setPlayerDecoder(decoder) }
    }

    fun setAudioLanguage(language: String) {
        viewModelScope.launch { settings.setAudioLanguage(language) }
    }

    fun setSubtitleLanguage(language: String) {
        viewModelScope.launch { settings.setSubtitleLanguage(language) }
    }

    val resumeLastChannel: StateFlow<Boolean> = repository.resumeLastChannel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Akcentna boja aplikacije; null dok se ne pročita, da se u podešavanjima ne trepne pogrešna. */
    val accentColor: StateFlow<AccentColor?> = settings.accentColor
        .map<String?, AccentColor?> { AccentColor.fromName(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setAccentColor(color: AccentColor) {
        viewModelScope.launch { settings.setAccentColor(color.name) }
    }

    val tvChannelPreview: StateFlow<Boolean> = settings.tvChannelPreview
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val autostart: StateFlow<Boolean> = settings.autostart
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAutostart(enabled: Boolean) {
        viewModelScope.launch { settings.setAutostart(enabled) }
    }

    val tvVodPreview: StateFlow<Boolean> = settings.tvVodPreview
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setTvVodPreview(enabled: Boolean) {
        viewModelScope.launch { settings.setTvVodPreview(enabled) }
    }

    fun setTvChannelPreview(enabled: Boolean) {
        viewModelScope.launch { settings.setTvChannelPreview(enabled) }
    }

    fun setResumeLastChannel(enabled: Boolean) {
        viewModelScope.launch { repository.setResumeLastChannel(enabled) }
    }
}
