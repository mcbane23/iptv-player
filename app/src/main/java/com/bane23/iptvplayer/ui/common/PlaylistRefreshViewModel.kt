package com.bane23.iptvplayer.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.repo.PlaylistRepository
import com.bane23.iptvplayer.domain.PlaylistError
import com.bane23.iptvplayer.domain.PlaylistException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Osvežavanje aktivne liste povlačenjem nadole na telefonu (Uživo, Filmovi, Serije). Radi isto što
 * i „Osveži” na ekranu Liste: ponovo preuzima celu listu, a stari sadržaj ostaje ako ne uspe.
 */
@HiltViewModel
class PlaylistRefreshViewModel @Inject constructor(
    private val repository: PlaylistRepository,
) : ViewModel() {

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Javlja da je lista uspešno osvežena, da ekrani koji drže svoj spisak mogu da ga učitaju iznova. */
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    private val _errors = MutableSharedFlow<PlaylistError>(extraBufferCapacity = 1)

    /** Osvežavanje nije uspelo; na ekranu ostaje dosadašnji sadržaj. */
    val errors: SharedFlow<PlaylistError> = _errors.asSharedFlow()

    fun refresh(playlistId: Long) {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                repository.refreshPlaylist(playlistId)
                _finished.tryEmit(Unit)
            } catch (e: PlaylistException) {
                _errors.tryEmit(e.error)
            } finally {
                _refreshing.value = false
            }
        }
    }
}
