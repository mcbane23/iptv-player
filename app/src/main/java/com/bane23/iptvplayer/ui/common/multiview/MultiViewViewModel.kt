package com.bane23.iptvplayer.ui.common.multiview

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.data.repo.LockRepository
import com.bane23.iptvplayer.domain.Category
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.player.PlayerEngine
import com.bane23.iptvplayer.player.PlayerSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Jedno polje u prikazu više kanala. [player] postoji tek kad je u polje stavljen kanal. */
data class MultiTile(
    val channel: Channel? = null,
    val player: Player? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
)

/** Izbor kanala za polje [slot]: prvo kategorija, pa kanal. */
data class MultiPicker(val slot: Int, val categoryId: Long? = null, val categoryName: String? = null)

data class MultiViewUiState(
    /** Broj polja na ekranu: 2 ili 4. */
    val count: Int = 2,
    val tiles: List<MultiTile> = List(MultiViewViewModel.MAX_TILES) { MultiTile() },
    /** Polje čiji se zvuk čuje. */
    val active: Int = 0,
    val picker: MultiPicker? = null,
)

/**
 * Prikaz više kanala odjednom. Svako polje ima svoj plejer; zvuk ide samo sa aktivnog polja.
 */
@HiltViewModel
class MultiViewViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ChannelRepository,
    private val settings: SettingsStore,
    private val locks: LockRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MultiViewUiState())
    val state: StateFlow<MultiViewUiState> = _state.asStateFlow()

    private val engines = arrayOfNulls<PlayerEngine>(MAX_TILES)
    private var playerSettings: PlayerSettings? = null
    private var foreground = false

    /** Kategorije aktivne liste za izbor kanala (zaključane nose katanac u nazivu). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<Category>> = repository.activePlaylistId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeCategories(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val pickerCategory = MutableStateFlow<Long?>(null)

    /** Kanali kategorije izabrane u izboru kanala. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pickerChannels: Flow<PagingData<Channel>> =
        combine(repository.activePlaylistId, pickerCategory) { playlistId, categoryId -> playlistId to categoryId }
            .flatMapLatest { (playlistId, categoryId) ->
                if (playlistId == null || categoryId == null) {
                    flowOf(PagingData.empty())
                } else {
                    repository.channels(playlistId, categoryId)
                }
            }
            .cachedIn(viewModelScope)

    fun setCount(count: Int) {
        if (count != 2 && count != MAX_TILES) return
        for (index in count until MAX_TILES) clear(index)
        _state.update { it.copy(count = count, active = it.active.coerceAtMost(count - 1)) }
        applyVolume()
    }

    fun setActive(index: Int) {
        if (index !in 0 until _state.value.count || _state.value.active == index) return
        _state.update { it.copy(active = index) }
        applyVolume()
    }

    fun openPicker(slot: Int) {
        pickerCategory.value = null
        _state.update { it.copy(picker = MultiPicker(slot)) }
    }

    fun pickCategory(category: Category) {
        pickerCategory.value = category.id
        _state.update { state -> state.copy(picker = state.picker?.copy(categoryId = category.id, categoryName = category.name)) }
    }

    /** Korak nazad u izboru: sa kanala na kategorije, sa kategorija na zatvaranje. Vraća false kad nema izbora. */
    fun pickerBack(): Boolean {
        val picker = _state.value.picker ?: return false
        if (picker.categoryId != null) {
            pickerCategory.value = null
            _state.update { it.copy(picker = MultiPicker(picker.slot)) }
        } else {
            _state.update { it.copy(picker = null) }
        }
        return true
    }

    /** Stavlja kanal u polje za koje je izbor otvoren. Zaključan kanal se ne može izabrati. */
    fun pickChannel(channel: Channel) {
        val slot = _state.value.picker?.slot ?: return
        viewModelScope.launch {
            if (locks.isChannelLocked(channel.id)) return@launch
            val source = repository.streamSource(channel.id) ?: return@launch
            val engine = engine(slot)
            _state.update { state ->
                state.copy(
                    picker = null,
                    active = slot,
                    tiles = state.tiles.replace(slot) { MultiTile(channel, engine.player, PlaybackStatus.LOADING) },
                )
            }
            applyVolume()
            engine.play(source)
            if (!foreground) engine.stop()
        }
    }

    fun retry(index: Int) {
        engines[index]?.retry()
    }

    fun onForeground() {
        foreground = true
        engines.forEach { it?.resume() }
    }

    fun onBackground() {
        foreground = false
        engines.forEach { it?.stop() }
    }

    private fun clear(index: Int) {
        engines[index]?.release()
        engines[index] = null
        _state.update { state -> state.copy(tiles = state.tiles.replace(index) { MultiTile() }) }
    }

    private suspend fun engine(index: Int): PlayerEngine {
        engines[index]?.let { return it }
        val playerSettings = playerSettings ?: settings.playerSettings.first().also { playerSettings = it }
        val engine = PlayerEngine(context, viewModelScope, playerSettings)
        engines[index] = engine
        viewModelScope.launch {
            engine.state.collect { playback ->
                // Polje je u međuvremenu ispražnjeno: stanje starog plejera se više ne prikazuje.
                if (engines[index] !== engine) return@collect
                _state.update { state -> state.copy(tiles = state.tiles.replace(index) { it.copy(status = playback.status) }) }
            }
        }
        return engine
    }

    private fun applyVolume() {
        val active = _state.value.active
        engines.forEachIndexed { index, engine -> engine?.player?.volume = if (index == active) 1f else 0f }
    }

    private fun <T> List<T>.replace(index: Int, change: (T) -> T): List<T> =
        mapIndexed { i, item -> if (i == index) change(item) else item }

    override fun onCleared() {
        engines.forEach { it?.release() }
    }

    companion object {
        const val MAX_TILES = 4
    }
}
