package com.bane23.iptvplayer.ui.common.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.player.PlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Kako se slika uklapa u ekran. */
enum class AspectMode { FIT, FILL, ZOOM }

data class PlayerUiState(
    val channel: Channel? = null,
    /** Kategorija kroz koju se listaju kanali; null = svi kanali liste. */
    val categoryId: Long? = null,
    /** Redni broj trenutnog kanala u toj kategoriji (od nule). */
    val position: Int = 0,
    val aspectMode: AspectMode = AspectMode.FIT,
    /** Broj kanala koji korisnik upravo kuca na daljinskom. */
    val typedNumber: String = "",
    /** Strim ne može ni da se sastavi (npr. lozinka više ne može da se pročita). */
    val sourceMissing: Boolean = false,
)

private data class ListScope(val playlistId: Long, val categoryId: Long?, val startIndex: Int)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: ChannelRepository,
) : ViewModel() {

    private val engine = PlayerEngine(context, viewModelScope)
    val player get() = engine.player
    val playback: StateFlow<PlaybackState> = engine.state

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    private val listScope = MutableStateFlow<ListScope?>(null)

    /** Kanali trenutne kategorije, za listu preko slike. Kreće od kanala koji se gleda. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val channels: Flow<PagingData<Channel>> = listScope.filterNotNull()
        .flatMapLatest { repository.channels(it.playlistId, it.categoryId, it.startIndex) }
        .cachedIn(viewModelScope)

    /** Da li je kanal koji se gleda među favoritima. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isFavorite: StateFlow<Boolean> = _ui.map { it.channel }
        .distinctUntilChangedBy { it?.id }
        .flatMapLatest { channel -> if (channel == null) flowOf(false) else repository.observeFavorite(channel) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Za posebne grupe (favoriti, nedavno gledano): redosled kanala zapamćen pri otvaranju plejera.
     * Tako se redosled ne menja pod prstima dok se kanali gledaju.
     */
    private var groupIds: List<Long>? = null

    private var started = false
    private var numberJob: Job? = null

    /** Poziva se jednom, pri otvaranju plejera. */
    fun start(channelId: Long, categoryId: Long?) {
        if (started) return
        started = true
        _ui.update { it.copy(categoryId = categoryId) }
        viewModelScope.launch {
            val channel = repository.getChannel(channelId) ?: return@launch
            if (ChannelGroups.isVirtual(categoryId) && categoryId != null) {
                groupIds = repository.groupChannelIds(channel.playlistId, categoryId)
            }
            val position = positionOf(channel)
            listScope.value = ListScope(channel.playlistId, categoryId, position)
            show(channel)
        }
    }

    fun playChannel(channel: Channel) {
        viewModelScope.launch { show(channel) }
    }

    fun next() = step { neighbor(it, 1) }

    fun previous() = step { neighbor(it, -1) }

    fun toggleFavorite() {
        val channel = _ui.value.channel ?: return
        viewModelScope.launch { repository.toggleFavorite(channel.id) }
    }

    fun togglePlayPause() = engine.togglePlayPause()

    fun retry() {
        val channel = _ui.value.channel ?: return
        playChannel(channel)
    }

    fun cycleAspectMode() = _ui.update {
        it.copy(aspectMode = AspectMode.entries[(it.aspectMode.ordinal + 1) % AspectMode.entries.size])
    }

    fun selectAudio(option: com.bane23.iptvplayer.player.TrackOption) = engine.selectAudio(option)

    fun selectText(option: com.bane23.iptvplayer.player.TrackOption?) = engine.selectText(option)

    /** Cifra sa daljinskog: brojevi se skupljaju, pa se posle kratke pauze prelazi na taj kanal. */
    fun typeDigit(digit: Int) {
        val typed = (_ui.value.typedNumber + digit).takeLast(MAX_NUMBER_DIGITS)
        _ui.update { it.copy(typedNumber = typed) }
        numberJob?.cancel()
        numberJob = viewModelScope.launch {
            delay(NUMBER_ENTRY_DELAY_MS)
            val number = typed.toIntOrNull()
            val current = _ui.value.channel
            _ui.update { it.copy(typedNumber = "") }
            if (number != null && current != null) {
                repository.byNumber(current.playlistId, number)?.let { show(it) }
            }
        }
    }

    /** Aplikacija je ponovo vidljiva. */
    fun onForeground() = engine.resume()

    /** Aplikacija više nije vidljiva: strim se zaustavlja da ne troši mrežu u pozadini. */
    fun onBackground() = engine.stop()

    private fun step(pick: suspend (Channel) -> Channel?) {
        val current = _ui.value.channel ?: return
        viewModelScope.launch { pick(current)?.let { show(it) } }
    }

    private suspend fun positionOf(channel: Channel): Int {
        val ids = groupIds ?: return repository.positionOf(channel, _ui.value.categoryId)
        return ids.indexOf(channel.id).coerceAtLeast(0)
    }

    /** Sledeći ([delta] = 1) ili prethodni (-1) kanal u trenutnoj kategoriji ili grupi, ukrug. */
    private suspend fun neighbor(channel: Channel, delta: Int): Channel? {
        val ids = groupIds
        if (ids == null) {
            val categoryId = _ui.value.categoryId
            return if (delta > 0) repository.next(channel, categoryId) else repository.previous(channel, categoryId)
        }
        if (ids.isEmpty()) return null
        val index = ids.indexOf(channel.id)
        return repository.getChannel(ids[(index + delta).mod(ids.size)])
    }

    private suspend fun show(channel: Channel) {
        val position = positionOf(channel)
        _ui.update { it.copy(channel = channel, position = position, sourceMissing = false) }
        val source = repository.streamSource(channel.id)
        if (source == null) {
            engine.stop()
            _ui.update { it.copy(sourceMissing = true) }
        } else {
            engine.play(source)
            repository.markWatched(channel.id)
        }
    }

    override fun onCleared() {
        engine.release()
    }

    private companion object {
        const val MAX_NUMBER_DIGITS = 5
        const val NUMBER_ENTRY_DELAY_MS = 1_500L
    }
}
