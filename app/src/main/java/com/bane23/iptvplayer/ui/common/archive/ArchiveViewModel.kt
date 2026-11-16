package com.bane23.iptvplayer.ui.common.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.repo.ArchiveRepository
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.domain.ArchiveProgram
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Emisije jednog dana, najnovije prvo. [label] je datum ispisan na jeziku uređaja. */
data class ArchiveDay(val label: String, val programs: List<ArchiveProgram>)

data class ArchiveUiState(
    val channelName: String = "",
    val loading: Boolean = true,
    /** Server nije odgovorio ili je odgovor nečitljiv. */
    val failed: Boolean = false,
    val days: List<ArchiveDay> = emptyList(),
)

/** Arhiva jednog kanala: emisije koje su prošle, grupisane po danima. */
@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val archive: ArchiveRepository,
    private val channels: ChannelRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ArchiveUiState())
    val state: StateFlow<ArchiveUiState> = _state.asStateFlow()

    private var channelId: Long? = null

    fun open(id: Long) {
        if (channelId == id) return
        channelId = id
        load()
    }

    fun retry() = load()

    private fun load() {
        val id = channelId ?: return
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val name = channels.getChannel(id)?.name.orEmpty()
            val programs = try {
                archive.programs(id)
            } catch (_: IOException) {
                null
            }
            _state.update {
                it.copy(channelName = name, loading = false, failed = programs == null, days = group(programs.orEmpty()))
            }
        }
    }

    private fun group(programs: List<ArchiveProgram>): List<ArchiveDay> {
        val calendar = Calendar.getInstance()
        val format = DateFormat.getDateInstance(DateFormat.FULL)
        return programs
            .groupBy { program ->
                calendar.timeInMillis = program.startMs
                calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
            }
            .values
            .map { ArchiveDay(format.format(Date(it.first().startMs)), it) }
    }
}
