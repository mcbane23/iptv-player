package com.bane23.iptvplayer.ui.common.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.domain.RemoteAction
import com.bane23.iptvplayer.domain.RemoteButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Raspored dugmadi daljinskog: čita ga plejer kanala na TV-u, a menja ekran „Dugmad daljinskog”. */
@HiltViewModel
class RemoteButtonsViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {

    val mapping: StateFlow<Map<RemoteButton, RemoteAction>> = settings.remoteButtons
        .stateIn(viewModelScope, SharingStarted.Eagerly, RemoteButton.entries.associateWith { it.defaultAction })

    fun set(button: RemoteButton, action: RemoteAction) {
        viewModelScope.launch { settings.setRemoteButton(button, action) }
    }
}
