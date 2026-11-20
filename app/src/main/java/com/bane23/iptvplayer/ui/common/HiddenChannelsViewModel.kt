package com.bane23.iptvplayer.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.db.HiddenChannelEntity
import com.bane23.iptvplayer.data.repo.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Sakrivanje pojedinačnih kanala i spisak sakrivenih (aktivna lista i liste spojene sa njom). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HiddenChannelsViewModel @Inject constructor(private val repository: ChannelRepository) : ViewModel() {

    val hidden: StateFlow<List<HiddenChannelEntity>> = repository.activePlaylistId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeHiddenChannels(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun hide(channelId: Long) {
        viewModelScope.launch { repository.hideChannel(channelId) }
    }

    fun show(channel: HiddenChannelEntity) {
        viewModelScope.launch { repository.showChannel(channel.playlistId, channel.channelKey) }
    }
}
