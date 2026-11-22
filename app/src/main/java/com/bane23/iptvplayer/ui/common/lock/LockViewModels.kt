package com.bane23.iptvplayer.ui.common.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.data.repo.LockRepository
import com.bane23.iptvplayer.data.repo.VodKind
import com.bane23.iptvplayer.data.repo.VodRepository
import com.bane23.iptvplayer.domain.Pin
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sadržaj pred kojim stoji provera PIN-a. */
sealed interface LockTarget {
    data class Channel(val channelId: Long) : LockTarget

    /** Film ([series] = false) ili serija. */
    data class Vod(val series: Boolean, val itemId: Long) : LockTarget

    /** Snimak kanala: zaključan je kad je kanal sa kog je snimljen u zaključanoj kategoriji. */
    data class Recording(val recordingId: Long) : LockTarget

    /** Sve što je sada zaključano: unos PIN-a sa spiska kategorija, pre nego što se išta pusti. */
    data object Everything : LockTarget
}

enum class GateStatus { CHECKING, LOCKED, OPEN }

data class GateUiState(val status: GateStatus = GateStatus.CHECKING, val wrongPin: Boolean = false)

/** Odlučuje da li sadržaj sme da se otvori odmah ili tek posle tačnog PIN-a. */
@HiltViewModel
class PinGateViewModel @Inject constructor(
    private val locks: LockRepository,
    private val vod: VodRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GateUiState())
    val state: StateFlow<GateUiState> = _state.asStateFlow()

    private var checked = false

    fun check(target: LockTarget) {
        if (checked) return
        checked = true
        // Provera se ponavlja kad se stanje zaključavanja promeni: sadržaj otvoren posle PIN-a se ponovo
        // zatvara čim se zaključa (izlazak iz aplikacije, „Zaključaj ponovo”).
        viewModelScope.launch {
            locks.activeKeys.distinctUntilChanged().collectLatest { evaluate(target) }
        }
    }

    private suspend fun evaluate(target: LockTarget) {
        val locked = when (target) {
            is LockTarget.Channel -> locks.isChannelLocked(target.channelId)
            is LockTarget.Recording -> locks.isRecordingLocked(target.recordingId)
            LockTarget.Everything -> locks.anythingLocked()
            is LockTarget.Vod -> try {
                vod.isLocked(if (target.series) VodKind.SERIES else VodKind.MOVIES, target.itemId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Bez spiska sa servera ne zna se da li je zaključano; tada se traži PIN.
                locks.activeKeysNow().isNotEmpty()
            }
        }
        _state.update { if (locked) it.copy(status = GateStatus.LOCKED) else GateUiState(GateStatus.OPEN) }
    }

    fun submit(pin: String) {
        viewModelScope.launch {
            // Tačan PIN menja stanje zaključavanja, pa se sadržaj otvara kroz ponovljenu proveru.
            if (!locks.unlock(pin)) _state.update { it.copy(wrongPin = true) }
        }
    }
}

/** Kategorija na ekranu roditeljske zaštite. [key] je ključ pod kojim se pamti zaključavanje. */
data class LockItem(val key: String, val name: String, val locked: Boolean)

enum class ParentalStep {
    /** PIN još nije postavljen: unos novog. */
    NEW_PIN,

    /** Ponovni unos novog PIN-a, radi provere. */
    REPEAT_PIN,

    /** PIN postoji, ali nije unet u ovom pokretanju aplikacije. */
    ENTER_PIN,

    /** Otključano: spisak kategorija i radnje sa PIN-om. */
    MANAGE,
}

enum class ParentalError { WRONG_PIN, MISMATCH }

data class ParentalUiState(
    val loading: Boolean = true,
    val step: ParentalStep = ParentalStep.NEW_PIN,
    val error: ParentalError? = null,
    val channels: List<LockItem> = emptyList(),
    val movies: List<LockItem> = emptyList(),
    val series: List<LockItem> = emptyList(),
)

/** Ekran roditeljske zaštite: postavljanje PIN-a i izbor kategorija koje se zaključavaju. */
@HiltViewModel
class ParentalViewModel @Inject constructor(
    private val locks: LockRepository,
    private val channels: ChannelRepository,
    private val vod: VodRepository,
) : ViewModel() {
    private data class Names(val playlistId: Long?, val live: List<String>, val movies: List<Pair<String, String>>, val series: List<Pair<String, String>>)

    /** Unos novog PIN-a u toku: prvi unos čeka potvrdu, a `changing` znači da se menja postojeći. */
    private data class Entry(val first: String? = null, val changing: Boolean = false, val error: ParentalError? = null)

    private val entry = MutableStateFlow(Entry())
    private val vodNames = MutableStateFlow<Pair<List<Pair<String, String>>, List<Pair<String, String>>>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val names = channels.activePlaylistId.flatMapLatest { playlistId ->
        if (playlistId == null) {
            flowOf(Names(null, emptyList(), emptyList(), emptyList()))
        } else {
            combine(channels.observeAllCategories(playlistId), vodNames) { live, vodLists ->
                // Zaključavanje važi po listi, pa se nude samo kategorije aktivne liste (i kad su liste spojene).
                Names(playlistId, live.filter { it.playlistId == playlistId }.map { it.name }, vodLists?.first.orEmpty(), vodLists?.second.orEmpty())
            }
        }
    }

    val state: StateFlow<ParentalUiState> =
        combine(locks.hasPin, locks.unlocked, locks.lockedKeys, names, entry) { hasPin, unlocked, keys, names, entry ->
            val step = when {
                entry.first != null -> ParentalStep.REPEAT_PIN
                !hasPin || entry.changing -> ParentalStep.NEW_PIN
                !unlocked -> ParentalStep.ENTER_PIN
                else -> ParentalStep.MANAGE
            }
            val playlistId = names.playlistId
            fun vodItems(list: List<Pair<String, String>>, series: Boolean) = list.map { (id, name) ->
                val key = LockRepository.vodKey(playlistId ?: 0, series, id)
                LockItem(key, name, key in keys)
            }
            ParentalUiState(
                loading = false,
                step = step,
                error = entry.error,
                channels = names.live.map { name ->
                    val key = LockRepository.liveKey(playlistId ?: 0, name)
                    LockItem(key, name, key in keys)
                },
                movies = vodItems(names.movies, series = false),
                series = vodItems(names.series, series = true),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ParentalUiState())

    init {
        // Kategorije filmova i serija postoje samo za Xtream liste; za ostale spisak ostaje prazan.
        viewModelScope.launch {
            suspend fun load(kind: VodKind) = try {
                vod.allCategories(kind).map { it.id to it.name }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            vodNames.value = load(VodKind.MOVIES) to load(VodKind.SERIES)
        }
    }

    /** Unet je PIN od četiri cifre; šta znači zavisi od koraka. */
    fun submit(pin: String) {
        if (!Pin.isValid(pin)) return
        viewModelScope.launch {
            when (state.value.step) {
                ParentalStep.NEW_PIN -> entry.update { it.copy(first = pin, error = null) }

                ParentalStep.REPEAT_PIN -> if (entry.value.first == pin) {
                    locks.setPin(pin)
                    entry.value = Entry()
                } else {
                    entry.update { it.copy(first = null, error = ParentalError.MISMATCH) }
                }

                ParentalStep.ENTER_PIN -> {
                    val ok = locks.unlock(pin)
                    entry.update { it.copy(error = if (ok) null else ParentalError.WRONG_PIN) }
                }

                ParentalStep.MANAGE -> Unit
            }
        }
    }

    fun changePin() {
        entry.value = Entry(changing = true)
    }

    fun removePin() {
        viewModelScope.launch {
            locks.removePin()
            entry.value = Entry()
        }
    }

    fun lockAgain() = locks.lockAgain()

    fun toggle(item: LockItem) {
        viewModelScope.launch { locks.setLocked(item.key, !item.locked) }
    }
}
