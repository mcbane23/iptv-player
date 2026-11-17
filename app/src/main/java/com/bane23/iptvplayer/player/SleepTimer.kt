package com.bane23.iptvplayer.player

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tajmer gašenja: posle izabranog broja minuta javlja plejeru koji je otvoren da pauzira. Jedan je
 * za celu aplikaciju, pa nastavlja da odbrojava i kad se pređe na drugi kanal ili film. Ne pamti se:
 * gašenjem aplikacije nestaje.
 */
@Singleton
class SleepTimer(private val scope: CoroutineScope) {

    @Inject
    constructor() : this(CoroutineScope(SupervisorJob() + Dispatchers.Main))

    private val _minutes = MutableStateFlow(0)

    /** Izabrano trajanje u minutima; 0 dok je tajmer isključen. */
    val minutes: StateFlow<Int> = _minutes.asStateFlow()

    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Javlja se kad vreme istekne. Ako tada nijedan plejer nije otvoren, ne dešava se ništa. */
    val expired: SharedFlow<Unit> = _expired.asSharedFlow()

    private var job: Job? = null

    /** Pokreće odbrojavanje od početka; 0 gasi tajmer. */
    fun set(minutes: Int) {
        job?.cancel()
        _minutes.value = minutes.coerceAtLeast(0)
        if (minutes <= 0) return
        job = scope.launch {
            delay(minutes * 60_000L)
            _minutes.value = 0
            _expired.tryEmit(Unit)
        }
    }

    companion object {
        /** Vrednosti koje se nude u plejeru, u minutima. */
        val OPTIONS = listOf(0, 15, 30, 45, 60, 90)
    }
}
