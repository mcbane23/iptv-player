package com.bane23.iptvplayer.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.repo.PlaylistRepository
import com.bane23.iptvplayer.domain.Playlist
import com.bane23.iptvplayer.domain.PlaylistError
import com.bane23.iptvplayer.domain.PlaylistException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaylistsUiState(
    val loading: Boolean = true,
    val playlists: List<Playlist> = emptyList(),
    /** Liste koje se trenutno osvežavaju. */
    val refreshing: Set<Long> = emptySet(),
    val error: PlaylistError? = null,
)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: PlaylistRepository,
) : ViewModel() {

    private val refreshing = MutableStateFlow<Set<Long>>(emptySet())
    private val error = MutableStateFlow<PlaylistError?>(null)

    val state: StateFlow<PlaylistsUiState> =
        combine(repository.observePlaylists(), refreshing, error) { playlists, busy, err ->
            PlaylistsUiState(loading = false, playlists = playlists, refreshing = busy, error = err)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistsUiState())

    fun refresh(id: Long) {
        if (id in refreshing.value) return
        refreshing.update { it + id }
        viewModelScope.launch {
            try {
                repository.refreshPlaylist(id)
            } catch (e: PlaylistException) {
                error.value = e.error
            } finally {
                refreshing.update { it - id }
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deletePlaylist(id) }
    }

    fun setActive(id: Long) {
        viewModelScope.launch { repository.setActivePlaylist(id) }
    }

    /** Uključuje listu u spojeni prikaz kanala, odnosno isključuje je. */
    fun setMerged(id: Long, merged: Boolean) {
        viewModelScope.launch { repository.setMerged(id, merged) }
    }

    fun dismissError() {
        error.value = null
    }
}
