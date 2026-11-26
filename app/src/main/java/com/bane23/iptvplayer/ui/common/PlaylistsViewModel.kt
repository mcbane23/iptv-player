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
    /** Ime liste na koju se greška odnosi. */
    val errorPlaylistName: String? = null,
) {
    /** Kanali svih lista se prikazuju zajedno. */
    val merged: Boolean get() = playlists.any { it.merged }
}

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: PlaylistRepository,
) : ViewModel() {

    private val refreshing = MutableStateFlow<Set<Long>>(emptySet())
    /** Greška poslednjeg osvežavanja i lista na koju se odnosi. */
    private val error = MutableStateFlow<Pair<Long, PlaylistError>?>(null)

    val state: StateFlow<PlaylistsUiState> =
        combine(repository.observePlaylists(), refreshing, error) { playlists, busy, err ->
            PlaylistsUiState(
                loading = false,
                playlists = playlists,
                refreshing = busy,
                error = err?.second,
                errorPlaylistName = err?.let { (id, _) -> playlists.firstOrNull { it.id == id }?.name },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistsUiState())

    fun refresh(id: Long) {
        if (id in refreshing.value) return
        refreshing.update { it + id }
        // Stara poruka (možda za drugu listu) više ne važi.
        error.value = null
        viewModelScope.launch {
            try {
                repository.refreshPlaylist(id)
            } catch (e: PlaylistException) {
                error.value = id to e.error
            } finally {
                refreshing.update { it - id }
            }
        }
    }

    fun delete(id: Long) {
        if (error.value?.first == id) error.value = null
        viewModelScope.launch { repository.deletePlaylist(id) }
    }

    fun setActive(id: Long) {
        // Izbor druge liste sklanja poruku o grešci prethodne.
        if (error.value?.first != id) error.value = null
        viewModelScope.launch { repository.setActivePlaylist(id) }
    }

    /** Spaja kanale svih lista u jedan prikaz, odnosno vraća prikaz samo aktivne liste. */
    fun setAllMerged(merged: Boolean) {
        viewModelScope.launch { repository.setAllMerged(merged) }
    }

    fun dismissError() {
        error.value = null
    }
}
