package com.bane23.iptvplayer.ui.common.recording

import androidx.lifecycle.ViewModel
import com.bane23.iptvplayer.data.recording.ActiveRecording
import com.bane23.iptvplayer.data.recording.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Oznaka na početnom ekranu: šta se upravo snima i zaustavljanje. */
@HiltViewModel
class ActiveRecordingsViewModel @Inject constructor(private val repository: RecordingRepository) : ViewModel() {

    val active: StateFlow<List<ActiveRecording>> = repository.active

    fun stop(id: Long) = repository.stop(id)
}
