package com.bane23.iptvplayer.ui.common.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bane23.iptvplayer.data.epg.GuideLayout
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.data.repo.EpgRepository
import com.bane23.iptvplayer.data.repo.EpgStatus
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.domain.Program
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Jedna ćelija u redu vodiča: emisija, ili praznina kad je [program] null. */
data class GuideCell(val program: Program?, val weight: Float)

/** Emisija koju je korisnik izabrao (dodirom ili fokusom), sa kanalom na kom je. */
data class GuideSelection(val channel: Channel, val program: Program?) {
    /** Kanal ima arhivu, pa prošle emisije mogu da se pogledaju. */
    val hasArchive: Boolean get() = channel.archiveDays != null
}

data class GuideUiState(
    /** Početak prikazanog vremenskog prozora. */
    val windowStart: Long,
    val selection: GuideSelection? = null,
)

/** Vodič u obliku mreže: kanali jedne kategorije i njihove emisije u vremenskom prozoru. */
@HiltViewModel
class GuideViewModel @Inject constructor(
    private val channels: ChannelRepository,
    private val epg: EpgRepository,
) : ViewModel() {

    private data class Scope(val playlistId: Long, val categoryId: Long?)

    private val scope = MutableStateFlow<Scope?>(null)

    private val _ui = MutableStateFlow(GuideUiState(windowStart = currentSlot()))
    val ui: StateFlow<GuideUiState> = _ui.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val channelList: Flow<PagingData<Channel>> = scope.filterNotNull()
        .flatMapLatest { channels.channels(it.playlistId, it.categoryId) }
        .cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val status: StateFlow<EpgStatus> = scope.filterNotNull()
        .flatMapLatest { epg.observeStatus(it.playlistId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EpgStatus())

    fun open(playlistId: Long, categoryId: Long?) {
        scope.value = Scope(playlistId, categoryId)
    }

    /** Pomera prozor za [steps] koraka od pola sata; ne ide dalje u prošlost nego što se vodič čuva. */
    fun shift(steps: Int) = _ui.update {
        val earliest = currentSlot() - MAX_PAST_MS
        val latest = currentSlot() + MAX_FUTURE_MS
        it.copy(windowStart = (it.windowStart + steps * SLOT_MS).coerceIn(earliest, latest))
    }

    fun jumpToNow() = _ui.update { it.copy(windowStart = currentSlot()) }

    fun select(channel: Channel, program: Program?) = _ui.update { it.copy(selection = GuideSelection(channel, program)) }

    fun clearSelection() = _ui.update { it.copy(selection = null) }

    fun refresh() {
        scope.value?.let { epg.requestRefresh(it.playlistId) }
    }

    /** Ćelije reda jednog kanala za prozor koji počinje u [windowStart] i traje [windowMs]. */
    suspend fun row(channelId: Long, windowStart: Long, windowMs: Long): List<GuideCell> {
        val end = windowStart + windowMs
        val programs = epg.programs(channelId, windowStart, end)
        return GuideLayout.cells(programs.map { it.startMs to it.stopMs }, windowStart, end).map { cell ->
            GuideCell(cell.index?.let(programs::get), cell.weight)
        }
    }

    private fun currentSlot(): Long = System.currentTimeMillis() / SLOT_MS * SLOT_MS

    private companion object {
        const val SLOT_MS = 30 * 60_000L
        const val MAX_PAST_MS = 6 * 3_600_000L
        const val MAX_FUTURE_MS = 46 * 3_600_000L
    }
}
