package com.bane23.iptvplayer.ui.common.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.db.RecordingEntity
import com.bane23.iptvplayer.data.recording.RecordingRepository
import com.bane23.iptvplayer.data.recording.RecordingStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ekran „Snimci”: spisak snimaka, zaustavljanje, brisanje i zauzeće prostora. */
@HiltViewModel
class RecordingsViewModel @Inject constructor(private val repository: RecordingRepository) : ViewModel() {

    /** Null dok se spisak prvi put ne učita. */
    val recordings: StateFlow<List<RecordingEntity>?> = repository.recordings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _storage = MutableStateFlow<RecordingStorage?>(null)
    val storage: StateFlow<RecordingStorage?> = _storage.asStateFlow()

    init {
        // Zauzeće se menja dok se snima i posle brisanja.
        viewModelScope.launch { repository.recordings.collect { _storage.value = repository.storage() } }
    }

    fun stop(id: Long) = repository.stop(id)

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
