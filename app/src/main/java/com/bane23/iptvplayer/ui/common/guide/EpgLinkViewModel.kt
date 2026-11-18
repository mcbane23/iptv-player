package com.bane23.iptvplayer.ui.common.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.epg.GuideChannel
import com.bane23.iptvplayer.data.epg.GuideMatch
import com.bane23.iptvplayer.data.repo.EpgRepository
import com.bane23.iptvplayer.domain.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Kanal iz vodiča ponuđen za povezivanje, sa emisijom koja je sada na njemu (ako je vodič ima). */
data class EpgLinkCandidate(val key: String, val name: String, val nowTitle: String? = null, val nowStopMs: Long? = null)

data class EpgLinkUiState(
    val query: String = "",
    val loading: Boolean = true,
    /** Vodič liste još nije učitan ili je prazan, pa nema šta da se ponudi. */
    val guideEmpty: Boolean = false,
    val candidates: List<EpgLinkCandidate> = emptyList(),
    /** Kanal vodiča koji je već ručno izabran za ovaj kanal, ili null. */
    val linkedKey: String? = null,
)

/**
 * Ručno povezivanje kanala liste sa kanalom iz vodiča: za kanale kojima se oznaka u listi i u vodiču
 * ne poklapaju, pa u vodiču nemaju program. Nudi kanale vodiča sa najsličnijim nazivom.
 */
@HiltViewModel
class EpgLinkViewModel @Inject constructor(private val epg: EpgRepository) : ViewModel() {

    private val _ui = MutableStateFlow(EpgLinkUiState())
    val ui: StateFlow<EpgLinkUiState> = _ui.asStateFlow()

    private var channel: Channel? = null
    private var guide: List<GuideChannel> = emptyList()
    private var search: Job? = null

    /** Otvara izbor za [target]; pretraga počinje od njegovog naziva. */
    fun open(target: Channel) {
        channel = target
        search?.cancel()
        _ui.value = EpgLinkUiState(query = GuideMatch.normalize(target.name))
        search = viewModelScope.launch {
            guide = epg.guideChannels(target.playlistId)
            val linked = epg.linkedKey(target.id)
            _ui.update { it.copy(guideEmpty = guide.isEmpty(), linkedKey = linked) }
            load(target, _ui.value.query)
        }
    }

    fun setQuery(query: String) {
        val target = channel ?: return
        _ui.update { it.copy(query = query) }
        search?.cancel()
        search = viewModelScope.launch { load(target, query) }
    }

    /** Vezuje kanal za kanal vodiča [key]; null vraća kanal na oznaku iz liste. */
    fun pick(key: String?) {
        val target = channel ?: return
        viewModelScope.launch { epg.linkGuide(target.id, key) }
    }

    private suspend fun load(target: Channel, query: String) {
        val ranked = withContext(Dispatchers.Default) { GuideMatch.rank(query, guide, LIMIT) }
        val candidates = ranked.map { found ->
            val now = epg.nowOn(target.playlistId, found.key)
            EpgLinkCandidate(found.key, found.name, now?.title, now?.stopMs)
        }
        _ui.update { it.copy(loading = false, candidates = candidates) }
    }

    private companion object {
        const val LIMIT = 20
    }
}
