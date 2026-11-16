package com.bane23.iptvplayer.ui.common.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.db.RecordingEntity
import com.bane23.iptvplayer.data.recording.RecordingLocation
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

    private val _locations = MutableStateFlow<List<RecordingLocation>>(emptyList())

    /** Mesta za čuvanje; izbor se nudi samo kad ih ima više od jednog (priključena SD kartica ili USB). */
    val locations: StateFlow<List<RecordingLocation>> = _locations.asStateFlow()

    init {
        // Zauzeće se menja dok se snima i posle brisanja.
        viewModelScope.launch { repository.recordings.collect { _storage.value = repository.storage() } }
        refreshLocations()
    }

    /** Ponovo čita priključene memorije (npr. posle povratka na ekran). */
    fun refreshLocations() {
        viewModelScope.launch { _locations.value = repository.locations() }
    }

    fun setLocation(path: String) {
        viewModelScope.launch {
            repository.setLocation(path)
            _locations.value = repository.locations()
            _storage.value = repository.storage()
        }
    }

    /** Prelazi na sledeće mesto u spisku (za TV, gde OK menja vrednost). */
    fun nextLocation() {
        val all = _locations.value
        if (all.size < 2) return
        setLocation(all[(all.indexOfFirst { it.selected } + 1).mod(all.size)].path)
    }

    fun stop(id: Long) = repository.stop(id)

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
