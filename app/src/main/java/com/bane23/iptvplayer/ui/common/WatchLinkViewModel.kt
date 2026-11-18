package com.bane23.iptvplayer.ui.common

import androidx.lifecycle.ViewModel
import com.bane23.iptvplayer.data.repo.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Provera linka iz reda „Nastavite sa gledanjem” na početnom ekranu TV-a. */
@HiltViewModel
class WatchLinkViewModel @Inject constructor(private val channels: ChannelRepository) : ViewModel() {
    /** Link važi samo za aktivnu listu: brojevi filmova i serija su kod svake liste drugi. */
    suspend fun isActive(playlistId: Long): Boolean = channels.activePlaylistIdNow() == playlistId
}
