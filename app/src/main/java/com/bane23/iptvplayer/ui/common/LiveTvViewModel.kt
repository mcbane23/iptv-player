package com.bane23.iptvplayer.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.domain.Category
import com.bane23.iptvplayer.domain.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class LiveTvUiState(
    val loading: Boolean = true,
    /** Aktivna lista, ili null ako nijedna nije izabrana. */
    val playlistId: Long? = null,
    val categories: List<Category> = emptyList(),
    val totalChannels: Int = 0,
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
                ) { categories, total ->
                    LiveTvUiState(
                        loading = false,
                        playlistId = playlistId,
                        categories = categories,
                        totalChannels = total,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveTvUiState())
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
