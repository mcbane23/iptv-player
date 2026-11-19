package com.bane23.iptvplayer.ui.common

import com.bane23.iptvplayer.ui.common.theme.AccentColor
import com.bane23.iptvplayer.ui.common.theme.AppTheme
import com.bane23.iptvplayer.ui.common.theme.UiScale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.domain.Category
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.domain.FavoriteList
import com.bane23.iptvplayer.data.repo.LockRepository
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveTvUiState(
    val loading: Boolean = true,
    /** Aktivna lista, ili null ako nijedna nije izabrana. */
    val playlistId: Long? = null,
    val categories: List<Category> = emptyList(),
    val totalChannels: Int = 0,
    val favoriteCount: Int = 0,
    val recentCount: Int = 0,
    /** Liste omiljenih kojima je korisnik dao ime. */
    val favoriteLists: List<FavoriteList> = emptyList(),
    /** Lista je Xtream nalog, pa ima i filmove i serije. */
    val hasVod: Boolean = false,
    /**
     * Grupa koja je bila otvorena poslednji put (ID kategorije ili vrednost iz [ChannelGroups]),
     * ili null ako se ne zna ili te kategorije više nema.
     */
    val lastCategoryId: Long? = null,
) {
    /** Da li grupa [groupId] (kategorija ili vrednost iz [ChannelGroups]) trenutno postoji. */
    fun hasGroup(groupId: Long): Boolean = when (groupId) {
        ChannelGroups.FAVORITES, ChannelGroups.ALL -> true
        ChannelGroups.RECENT -> recentCount > 0
        else -> categories.any { it.id == groupId } || favoriteList(groupId) != null
    }

    /** Grupa je „Omiljeni” ili neka imenovana lista omiljenih. */
    fun isFavoritesGroup(groupId: Long?): Boolean = groupId == ChannelGroups.FAVORITES || favoriteList(groupId) != null

    /** Imenovana lista omiljenih koju grupa [groupId] predstavlja, ili null. */
    fun favoriteList(groupId: Long?): FavoriteList? =
        ChannelGroups.favoriteListId(groupId)?.let { id -> favoriteLists.firstOrNull { it.id == id } }
}

/** Kategorije aktivne liste. */
@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repository: ChannelRepository,
) : ViewModel() {

    /** Grupa koju je korisnik poslednju otvorio u ovom pokretanju aplikacije, po listi. */
    private val picked = MutableStateFlow<Map<Long, Long>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<LiveTvUiState> = repository.activePlaylistId
        .flatMapLatest { playlistId ->
            if (playlistId == null) {
                flowOf(LiveTvUiState(loading = false))
            } else {
                flow<LiveTvUiState> {
                    // Čita se jednom po listi: dok je ekran otvoren, izbor vodi sam ekran.
                    val last = repository.lastCategory(playlistId)
                    val loaded = combine(
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
                    }.combine(repository.observeFavoriteLists(playlistId)) { ui, lists ->
                        ui.copy(
                            favoriteLists = lists,
                            // Uživo kreće od nedavno gledanih; bez njih od grupe otvorene prošli put.
                            lastCategoryId = if (ui.recentCount > 0) {
                                ChannelGroups.RECENT
                            } else {
                                LastLiveCategory.resolve(last, ui.categories, false, lists)
                            },
                        )
                    }
                    // Izbor napravljen dok je aplikacija otvorena ima prednost nad sačuvanim, da grupa
                    // ostane ista i kad se ekran iscrta iznova (npr. posle okretanja telefona).
                    emitAll(
                        combine(loaded, picked) { ui, pickedGroups ->
                            val group = pickedGroups[playlistId]?.takeIf { ui.hasGroup(it) }
                            if (group == null) ui else ui.copy(lastCategoryId = group)
                        },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveTvUiState())

    /** Kanal koji treba pustiti odmah pri startu aplikacije, ili null. */
    suspend fun channelToResume(): Long? = repository.channelToResume()

    /** Pamti grupu koju je korisnik otvorio; sačuvana grupa važi dok nema nedavno gledanih. */
    fun rememberCategory(groupId: Long) {
        val current = state.value
        val playlistId = current.playlistId ?: return
        picked.update { it + (playlistId to groupId) }
        val token = LastLiveCategory.token(groupId, current.categories) ?: return
        viewModelScope.launch { repository.setLastCategory(playlistId, token) }
    }

    /** Zaboravlja izbor iz ovog pokretanja, da Uživo sledeći put krene od nedavno gledanih. */
    fun startFresh() {
        picked.value = emptyMap()
    }

    /** Sakriva kategoriju; vraća se na ekranu „Uredi kategorije”. */
    fun hideCategory(categoryId: Long) {
        viewModelScope.launch { repository.setCategoryHidden(categoryId, true) }
    }

    fun moveCategoryToTop(categoryId: Long) {
        val playlistId = state.value.playlistId ?: return
        viewModelScope.launch { repository.moveCategoryToTop(playlistId, categoryId) }
    }
}

/**
 * Zapis poslednje otvorene grupe kanala. Kategorija se pamti po nazivu, jer se njen ID menja
 * kad se lista osveži.
 */
object LastLiveCategory {
    private const val FAVORITES = "fav"
    private const val RECENT = "recent"
    private const val ALL = "all"
    private const val CATEGORY = "c:"
    private const val LIST = "l:"

    fun token(groupId: Long, categories: List<Category>): String? = when (groupId) {
        ChannelGroups.FAVORITES -> FAVORITES
        ChannelGroups.RECENT -> RECENT
        ChannelGroups.ALL -> ALL
        else -> ChannelGroups.favoriteListId(groupId)?.let { LIST + it }
            ?: categories.firstOrNull { it.id == groupId }?.let { CATEGORY + plainName(it.name) }
    }

    fun resolve(
        token: String?,
        categories: List<Category>,
        hasRecent: Boolean,
        lists: List<FavoriteList> = emptyList(),
    ): Long? {
        if (token == null) return null
        return when {
            token == FAVORITES -> ChannelGroups.FAVORITES
            token == RECENT -> ChannelGroups.RECENT.takeIf { hasRecent }
            token == ALL -> ChannelGroups.ALL
            token.startsWith(LIST) -> {
                val id = token.removePrefix(LIST).toLongOrNull()
                lists.firstOrNull { it.id == id }?.groupId
            }
            token.startsWith(CATEGORY) -> {
                val name = token.removePrefix(CATEGORY)
                categories.firstOrNull { plainName(it.name) == name }?.id
            }
            else -> null
        }
    }

    /** Naziv bez katanca koji zaključana kategorija nosi ispred naziva. */
    private fun plainName(name: String): String = name.removePrefix(LockRepository.LOCK_MARK)
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

    private companion object {
        const val SEARCH_DELAY_MS = 300L
    }
}

/** Podešavanja aplikacije. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ChannelRepository,
    private val settings: SettingsStore,
    private val sleepTimer: com.bane23.iptvplayer.player.SleepTimer,
    private val vodDiskCache: com.bane23.iptvplayer.data.repo.VodDiskCache,
) : ViewModel() {

    /** Tajmer gašenja u minutima; 0 dok je isključen. */
    val sleepTimerMinutes: StateFlow<Int> = sleepTimer.minutes

    fun setSleepTimer(minutes: Int) = sleepTimer.set(minutes)

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

    fun setAudioLanguage(slot: Int, language: String) {
        viewModelScope.launch { settings.setAudioLanguage(slot, language) }
    }

    fun setSubtitleLanguage(slot: Int, language: String) {
        viewModelScope.launch { settings.setSubtitleLanguage(slot, language) }
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

    /** Tema aplikacije (boje pozadine, neon); null dok se ne pročita, da se u podešavanjima ne trepne pogrešna. */
    val appTheme: StateFlow<AppTheme?> = settings.appTheme
        .map<String?, AppTheme?> { AppTheme.fromName(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch { settings.setAppTheme(theme.name) }
    }

    /** Bočni meni na TV-u samo sa ikonicama; menja se strelicom na vrhu menija. */
    val tvRailIconsOnly: StateFlow<Boolean> = settings.tvRailIconsOnly
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setTvRailIconsOnly(enabled: Boolean) {
        viewModelScope.launch { settings.setTvRailIconsOnly(enabled) }
    }

    /** Sačuvano ime veličine prikaza (vidi `UiScale`); null dok korisnik ništa nije izabrao. */
    val uiScale: StateFlow<String?> = settings.uiScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setUiScale(scale: UiScale) {
        viewModelScope.launch { settings.setUiScale(scale.name) }
    }

    val tvChannelPreview: StateFlow<Boolean> = settings.tvChannelPreview
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val autostart: StateFlow<Boolean> = settings.autostart
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAutostart(enabled: Boolean) {
        viewModelScope.launch { settings.setAutostart(enabled) }
    }

    val reopenAfterUpdate: StateFlow<Boolean> = settings.reopenAfterUpdate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setReopenAfterUpdate(enabled: Boolean) {
        viewModelScope.launch { settings.setReopenAfterUpdate(enabled) }
    }

    val drawUnderCutout: StateFlow<Boolean> = settings.drawUnderCutout
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setDrawUnderCutout(enabled: Boolean) {
        viewModelScope.launch { settings.setDrawUnderCutout(enabled) }
    }

    val appFullScreen: StateFlow<Boolean> = settings.appFullScreen
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setAppFullScreen(enabled: Boolean) {
        viewModelScope.launch { settings.setAppFullScreen(enabled) }
    }

    val playerBrightnessSlider: StateFlow<Boolean> = settings.playerBrightnessSlider
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setPlayerBrightnessSlider(enabled: Boolean) {
        viewModelScope.launch { settings.setPlayerBrightnessSlider(enabled) }
    }

    val playerVolumeSlider: StateFlow<Boolean> = settings.playerVolumeSlider
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setPlayerVolumeSlider(enabled: Boolean) {
        viewModelScope.launch { settings.setPlayerVolumeSlider(enabled) }
    }

    val backgroundPlay: StateFlow<Boolean> = settings.backgroundPlay
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setBackgroundPlay(enabled: Boolean) {
        viewModelScope.launch { settings.setBackgroundPlay(enabled) }
    }

    val matchFrameRate: StateFlow<Boolean> = settings.matchFrameRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setMatchFrameRate(enabled: Boolean) {
        viewModelScope.launch { settings.setMatchFrameRate(enabled) }
    }

    val downloadOnMobile: StateFlow<Boolean> = settings.downloadOnMobile
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setDownloadOnMobile(enabled: Boolean) {
        viewModelScope.launch { settings.setDownloadOnMobile(enabled) }
    }

    val pauseInfo: StateFlow<Boolean> = settings.pauseInfo
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setPauseInfo(enabled: Boolean) {
        viewModelScope.launch { settings.setPauseInfo(enabled) }
    }

    val blackTheme: StateFlow<Boolean> = settings.blackTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setBlackTheme(enabled: Boolean) {
        viewModelScope.launch { settings.setBlackTheme(enabled) }
    }

    val watchNext: StateFlow<Boolean> = settings.watchNext
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setWatchNext(enabled: Boolean) {
        viewModelScope.launch { settings.setWatchNext(enabled) }
    }

    val autoRefreshPlaylists: StateFlow<Boolean> = settings.autoRefreshPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setAutoRefreshPlaylists(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoRefreshPlaylists(enabled) }
    }

    val vodLocalCache: StateFlow<Boolean> = settings.vodLocalCache
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Kad se čuvanje isključi, sačuvani spiskovi se brišu sa uređaja. */
    fun setVodLocalCache(enabled: Boolean) {
        viewModelScope.launch {
            settings.setVodLocalCache(enabled)
            if (!enabled) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vodDiskCache.clearAll() }
        }
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
