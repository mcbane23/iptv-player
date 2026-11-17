package com.bane23.iptvplayer.ui.common

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.data.repo.EpgRepository
import com.bane23.iptvplayer.data.repo.LockRepository
import com.bane23.iptvplayer.domain.NowNext
import com.bane23.iptvplayer.domain.Program
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.player.PlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Mali pregled kanala na TV-u (Uživo, desna kolona): pušta kanal na kome je fokus, bez zvuka,
 * i daje šta je na njemu sada i sledeće. Plejer se pravi tek kad prvi put zatreba i zaustavlja
 * pre otvaranja pravog plejera, jer mnogi provajderi dozvoljavaju samo jednu vezu.
 */
@HiltViewModel
class ChannelPreviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ChannelRepository,
    private val epg: EpgRepository,
    private val settings: SettingsStore,
    private val locks: LockRepository,
) : ViewModel() {

    val enabled: StateFlow<Boolean> = settings.tvChannelPreview
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private var engine: PlayerEngine? = null

    private val _player = MutableStateFlow<Player?>(null)
    /** Plejer za površinu pregleda; null dok se ništa ne pušta. */
    val player: StateFlow<Player?> = _player.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackState())
    val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    private val _nowNext = MutableStateFlow(NowNext())
    val nowNext: StateFlow<NowNext> = _nowNext.asStateFlow()

    private val _later = MutableStateFlow<List<Program>>(emptyList())
    /** Emisije koje slede na kanalu u fokusu, za kratak program ispod pregleda. */
    val later: StateFlow<List<Program>> = _later.asStateFlow()

    private var focusJob: Job? = null
    private var stateJob: Job? = null
    private var shownId: Long? = null

    /**
     * Fokus je prešao na kanal [channelId]. Vodič se učitava posle vrlo kratke pauze, a slika tek posle kratke pauze,
     * da brzo listanje kroz kanale ne otvara strim za svaki.
     */
    fun focus(channelId: Long) {
        if (channelId == shownId) return
        shownId = channelId
        focusJob?.cancel()
        focusJob = viewModelScope.launch {
            _nowNext.value = NowNext()
            _later.value = emptyList()
            // Dok se taster drži i fokus brzo prelazi preko kanala, vodič se ne čita za svaki.
            delay(NOW_NEXT_DELAY_MS)
            _nowNext.value = epg.nowNext(channelId)
            _later.value = epg.later(channelId, LATER_COUNT)
            if (!enabled.first()) {
                stop()
                return@launch
            }
            // Kanal iz zaključane kategorije se ne prikazuje ni u malom pregledu.
            if (locks.isChannelLocked(channelId)) {
                stop()
                return@launch
            }
            delay(PREVIEW_DELAY_MS)
            val source = repository.streamSource(channelId) ?: return@launch
            val current = engine ?: createEngine()
            current.play(source)
        }
    }

    /** Zaustavlja pregled i oslobađa vezu (pre pravog plejera i kad aplikacija ode u pozadinu). */
    fun stop() {
        focusJob?.cancel()
        shownId = null
        stateJob?.cancel()
        engine?.release()
        engine = null
        _player.value = null
        _playback.value = PlaybackState()
    }

    private suspend fun createEngine(): PlayerEngine {
        val created = PlayerEngine(context, viewModelScope, settings.playerSettings.first())
        // Pregled je uvek bez zvuka.
        created.player.volume = 0f
        engine = created
        _player.value = created.player
        stateJob = viewModelScope.launch { created.state.collect { _playback.value = it } }
        return created
    }

    override fun onCleared() {
        stop()
    }

    private companion object {
        const val NOW_NEXT_DELAY_MS = 150L
        const val PREVIEW_DELAY_MS = 650L
        const val LATER_COUNT = 3
    }
}
