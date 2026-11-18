package com.bane23.iptvplayer.ui.common.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.db.RecordingEntity
import com.bane23.iptvplayer.data.epg.GuideLayout
import com.bane23.iptvplayer.data.recording.ActiveRecording
import com.bane23.iptvplayer.data.recording.RecordingRepository
import com.bane23.iptvplayer.data.recording.RecordingStartError
import com.bane23.iptvplayer.data.reminder.Reminder
import com.bane23.iptvplayer.data.reminder.ReminderRepository
import kotlinx.coroutines.flow.map
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.data.repo.EpgRepository
import com.bane23.iptvplayer.data.repo.EpgStatus
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.domain.Program
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Jedna ćelija u redu vodiča: emisija, ili praznina kad je [program] null. */
data class GuideCell(val program: Program?, val weight: Float)

/** Emisija koju je korisnik izabrao (dodirom ili fokusom), sa kanalom na kom je. */
data class GuideSelection(val channel: Channel, val program: Program?) {
    /** Kanal ima arhivu, pa prošle emisije mogu da se pogledaju. */
    val hasArchive: Boolean get() = channel.archiveDays != null
}

/** Šta je zakazano za snimanje i šta se upravo snima, za oznake u vodiču. */
data class GuideRecordings(
    /** Zakazana snimanja: ključ emisije ([key]) i ID snimka. */
    val scheduled: Map<String, Long> = emptyMap(),
    /** Kanali koji se upravo snimaju, po ključu [ActiveRecording.channelKeyOf]. */
    val activeChannels: Set<String> = emptySet(),
) {
    /** Emisija je zakazana za snimanje, ili je u toku na kanalu koji se snima. */
    fun marks(channel: Channel, program: Program, now: Long): Boolean =
        key(channel, program.startMs) in scheduled ||
            (isActive(channel) && program.startMs <= now && program.stopMs > now)

    /** Da li se kanal upravo snima. */
    fun isActive(channel: Channel): Boolean = ActiveRecording.channelKeyOf(channel) in activeChannels

    companion object {
        fun key(channel: Channel, startMs: Long): String = key(channel.playlistId, channel.stableKey, startMs)

        fun key(playlistId: Long, channelKey: String, startMs: Long): String = "$playlistId|$channelKey|$startMs"
    }
}

data class GuideUiState(
    /** Početak prikazanog vremenskog prozora. */
    val windowStart: Long,
    val selection: GuideSelection? = null,
)

/** Vodič u obliku mreže: kanali jedne kategorije i njihove emisije u vremenskom prozoru. */
@HiltViewModel
class GuideViewModel @Inject constructor(
    private val channels: ChannelRepository,
    private val epg: EpgRepository,
    private val recorder: RecordingRepository,
    private val reminderRepository: ReminderRepository,
) : ViewModel() {

    private data class Scope(val playlistId: Long, val categoryId: Long?)

    private val scope = MutableStateFlow<Scope?>(null)

    private val _ui = MutableStateFlow(GuideUiState(windowStart = currentSlot()))
    val ui: StateFlow<GuideUiState> = _ui.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val channelList: Flow<PagingData<Channel>> = scope.filterNotNull()
        .flatMapLatest { channels.channels(it.playlistId, it.categoryId) }
        .cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val status: StateFlow<EpgStatus> = scope.filterNotNull()
        .flatMapLatest { epg.observeStatus(it.playlistId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EpgStatus())

    val recordings: StateFlow<GuideRecordings> = combine(recorder.recordings, recorder.active) { rows, active ->
        GuideRecordings(
            scheduled = rows.filter { it.status == RecordingEntity.STATUS_SCHEDULED }
                .associate { GuideRecordings.key(it.playlistId, it.channelKey, it.startedAt) to it.id },
            activeChannels = active.mapTo(HashSet()) { it.key },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuideRecordings())

    /** Ključevi emisija ([GuideRecordings.key]) koje imaju podsetnik, za zvonce u vodiču. */
    val reminders: StateFlow<Set<String>> = reminderRepository.reminders
        .map { list -> list.mapTo(HashSet<String>()) { it.key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Uključuje podsetnik za emisiju koja još nije počela, odnosno uklanja ga. */
    fun toggleReminder(channel: Channel, program: Program) {
        val reminder = Reminder(channel.playlistId, channel.stableKey, program.startMs, channel.name, program.title)
        val exists = reminder.key in reminders.value
        if (!exists && program.startMs <= System.currentTimeMillis()) return
        viewModelScope.launch {
            if (exists) reminderRepository.remove(reminder) else reminderRepository.add(reminder)
            _recordingNotice.tryEmit(if (exists) R.string.reminder_removed else R.string.reminder_set)
        }
    }

    /**
     * Raspored jednog kanala za panel „Program” u plejeru: emisija koja je u toku i one koje slede
     * u narednih [SCHEDULE_AHEAD_MS].
     */
    suspend fun schedule(channelId: Long): List<Program> {
        val now = System.currentTimeMillis()
        return epg.programs(channelId, now, now + SCHEDULE_AHEAD_MS).filter { it.stopMs > now }
    }

    private val _recordingNotice = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /** Kratka poruka o snimanju (tekst iz `strings.xml`). */
    val recordingNotice: SharedFlow<Int> = _recordingNotice.asSharedFlow()

    /** Emisija može da se snimi ili zakaže: još nije završena. */
    fun canRecord(program: Program?): Boolean = program != null && program.stopMs > System.currentTimeMillis()

    /**
     * Emisija koja je u toku snima se odmah, do svog kraja; buduća se zakazuje. Ako se već snima
     * ili je zakazana, snimanje se zaustavlja, odnosno otkazuje.
     */
    fun toggleRecording(channel: Channel, program: Program) {
        val now = System.currentTimeMillis()
        if (program.stopMs <= now) return
        val state = recordings.value
        val scheduledId = state.scheduled[GuideRecordings.key(channel, program.startMs)]
        viewModelScope.launch {
            when {
                scheduledId != null -> {
                    recorder.delete(scheduledId)
                    _recordingNotice.tryEmit(R.string.recording_schedule_cancelled)
                }
                program.startMs <= now && state.isActive(channel) -> {
                    recorder.stopChannel(channel)
                    _recordingNotice.tryEmit(R.string.recording_stopped)
                }
                else -> {
                    val error = recorder.schedule(channel.id, program.title, program.startMs, program.stopMs)
                    _recordingNotice.tryEmit(
                        when (error) {
                            null -> if (program.startMs <= now) R.string.recording_started else R.string.recording_scheduled
                            RecordingStartError.NO_SPACE -> R.string.recording_no_space
                            RecordingStartError.NO_STORAGE -> R.string.recording_no_storage
                            RecordingStartError.NO_SOURCE -> R.string.recording_failed
                        },
                    )
                }
            }
        }
    }

    fun open(playlistId: Long, categoryId: Long?) {
        scope.value = Scope(playlistId, categoryId)
    }

    /** Pomera prozor za [steps] koraka od pola sata; ne ide dalje u prošlost nego što se vodič čuva. */
    fun shift(steps: Int) = _ui.update {
        val earliest = currentSlot() - MAX_PAST_MS
        val latest = currentSlot() + MAX_FUTURE_MS
        it.copy(windowStart = (it.windowStart + steps * SLOT_MS).coerceIn(earliest, latest))
    }

    fun jumpToNow() = _ui.update { it.copy(windowStart = currentSlot()) }

    fun select(channel: Channel, program: Program?) = _ui.update { it.copy(selection = GuideSelection(channel, program)) }

    fun clearSelection() = _ui.update { it.copy(selection = null) }

    fun refresh() {
        scope.value?.let { epg.requestRefresh(it.playlistId) }
    }

    /** Ćelije reda jednog kanala za prozor koji počinje u [windowStart] i traje [windowMs]. */
    suspend fun row(channelId: Long, windowStart: Long, windowMs: Long): List<GuideCell> {
        val end = windowStart + windowMs
        val programs = epg.programs(channelId, windowStart, end)
        return GuideLayout.cells(programs.map { it.startMs to it.stopMs }, windowStart, end).map { cell ->
            GuideCell(cell.index?.let(programs::get), cell.weight)
        }
    }

    private fun currentSlot(): Long = System.currentTimeMillis() / SLOT_MS * SLOT_MS

    private companion object {
        const val SCHEDULE_AHEAD_MS = 24 * 60 * 60_000L
        const val SLOT_MS = 30 * 60_000L
        const val MAX_PAST_MS = 6 * 3_600_000L
        const val MAX_FUTURE_MS = 46 * 3_600_000L
    }
}
