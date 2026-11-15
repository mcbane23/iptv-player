package com.bane23.iptvplayer.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.repo.PlaylistRepository
import com.bane23.iptvplayer.domain.PlaylistDraft
import com.bane23.iptvplayer.domain.PlaylistError
import com.bane23.iptvplayer.domain.PlaylistException
import com.bane23.iptvplayer.domain.PlaylistType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditPlaylistUiState(
    /** null = nova lista. */
    val playlistId: Long? = null,
    val type: PlaylistType = PlaylistType.M3U_URL,
    val name: String = "",
    val url: String = "",
    val fileUri: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val saving: Boolean = false,
    val error: PlaylistError? = null,
    /** Lista je sačuvana; ekran treba da se zatvori. */
    val saved: Boolean = false,
) {
    val isEditing: Boolean get() = playlistId != null
}

@HiltViewModel
class EditPlaylistViewModel @Inject constructor(
    private val repository: PlaylistRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditPlaylistUiState())
    val state: StateFlow<EditPlaylistUiState> = _state.asStateFlow()

    private var loaded = false

    /** Učitava postojeću listu za izmenu. Poziva se jednom, pri otvaranju ekrana. */
    fun load(playlistId: Long?) {
        if (loaded) return
        loaded = true
        if (playlistId == null) return
        viewModelScope.launch {
            val playlist = repository.getPlaylist(playlistId) ?: return@launch
            _state.update {
                it.copy(
                    playlistId = playlist.id,
                    type = playlist.type,
                    name = playlist.name,
                    url = if (playlist.type == PlaylistType.M3U_URL) playlist.source else "",
                    fileUri = if (playlist.type == PlaylistType.M3U_FILE) playlist.source else "",
                    server = if (playlist.type == PlaylistType.XTREAM) playlist.source else "",
                    username = playlist.username.orEmpty(),
                )
            }
        }
    }

    fun setType(type: PlaylistType) = _state.update { it.copy(type = type, error = null) }
    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setUrl(value: String) = _state.update { it.copy(url = value) }
    fun setServer(value: String) = _state.update { it.copy(server = value) }
    fun setUsername(value: String) = _state.update { it.copy(username = value) }
    fun setPassword(value: String) = _state.update { it.copy(password = value) }

    fun setFile(uri: String, suggestedName: String?) = _state.update {
        it.copy(
            fileUri = uri,
            name = it.name.ifBlank { suggestedName.orEmpty() },
            error = null,
        )
    }

    fun showError(kind: PlaylistError.Kind) = _state.update { it.copy(error = PlaylistError(kind)) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun save() {
        val current = _state.value
        if (current.saving) return
        val draft = PlaylistDraft(
            type = current.type,
            name = current.name,
            source = when (current.type) {
                PlaylistType.M3U_URL -> current.url
                PlaylistType.M3U_FILE -> current.fileUri
                PlaylistType.XTREAM -> current.server
            },
            username = current.username,
            password = current.password,
        )
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val id = current.playlistId
                if (id == null) repository.addPlaylist(draft) else repository.updatePlaylist(id, draft)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: PlaylistException) {
                _state.update { it.copy(saving = false, error = e.error) }
            }
        }
    }
}
