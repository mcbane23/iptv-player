package com.bane23.iptvplayer.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.reminder.Reminder
import com.bane23.iptvplayer.data.reminder.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Podsetnik koji treba prikazati preko bilo kog ekrana aplikacije. */
@HiltViewModel
class ReminderViewModel @Inject constructor(private val repository: ReminderRepository) : ViewModel() {

    val due: StateFlow<Reminder?> = repository.due
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismiss(reminder: Reminder) {
        viewModelScope.launch { repository.remove(reminder) }
    }

    /** Sklanja podsetnik i javlja ID kanala koji treba pustiti (ako kanal još postoji). */
    fun watch(reminder: Reminder, onChannel: (Long) -> Unit) {
        viewModelScope.launch {
            val channelId = repository.channelId(reminder)
            repository.remove(reminder)
            if (channelId != null) onChannel(channelId)
        }
    }
}
