package com.bane23.iptvplayer.data.saver

import android.content.Context
import com.bane23.iptvplayer.data.download.NetworkKind
import com.bane23.iptvplayer.data.download.NetworkWatcher
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.data.repo.LockRepository
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.player.PlaybackActivity
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.player.PlayerEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Kako se završilo poslednje merenje kvaliteta omiljenih kanala. */
enum class QualityScanResult { DONE, NEEDS_WIFI, NO_FAVORITES, ALL_KNOWN }

data class QualityScanState(
    val running: Boolean = false,
    /** Koliko je verzija kanala do sada obrađeno i koliko ih ukupno ima. */
    val done: Int = 0,
    val total: Int = 0,
    /** Koliko je verzija uspešno izmereno. */
    val measured: Int = 0,
    /** Ishod poslednjeg merenja; null dok traje i dok nijedno nije završeno. Svako završeno merenje dobija nov [finishedSeq]. */
    val result: QualityScanResult? = null,
    val finishedSeq: Int = 0,
)

/**
 * Merenje kvaliteta omiljenih kanala, za štednju mobilnih podataka: nakratko otvori svaku verziju
 * omiljenih kanala (isti kanal u drugom kvalitetu) kojoj se rezolucija još ne zna i zapamti je. Radi
 * samo na Wi-Fi mreži, jedan po jedan kanal (mnogi nalozi dozvoljavaju samo jednu vezu), bez slike i
 * zvuka, i prekida se čim korisnik pusti nešto u plejeru. Živi koliko i aplikacija, pa izlazak iz
 * Podešavanja ne prekida merenje.
 */
@Singleton
class QualityScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ChannelRepository,
    private val locks: LockRepository,
    private val settings: SettingsStore,
    private val network: NetworkWatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _state = MutableStateFlow(QualityScanState())
    val state: StateFlow<QualityScanState> = _state.asStateFlow()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.update { QualityScanState(running = true, finishedSeq = it.finishedSeq) }
            val result = try {
                scan()
            } finally {
                _state.update { it.copy(running = false) }
            }
            _state.update { it.copy(result = result, finishedSeq = it.finishedSeq + 1) }
        }
    }

    /** Prekida merenje; ono što je do tada izmereno ostaje zapamćeno. */
    fun cancel() {
        job?.cancel()
    }

    private suspend fun scan(): QualityScanResult {
        if (network.kind.first() != NetworkKind.WIFI) return QualityScanResult.NEEDS_WIFI
        val playlistId = repository.activePlaylistIdNow() ?: return QualityScanResult.NO_FAVORITES
        val favorites = LinkedHashSet(repository.groupChannelIds(playlistId, ChannelGroups.FAVORITES))
        for (list in repository.observeFavoriteLists(playlistId).first()) {
            favorites += repository.groupChannelIds(playlistId, list.groupId)
        }
        if (favorites.isEmpty()) return QualityScanResult.NO_FAVORITES
        // Verzije omiljenih kanala kojima rezolucija još nije izmerena.
        val targets = LinkedHashSet<Long>()
        for (id in favorites) {
            val channel = repository.getChannel(id) ?: continue
            for (source in repository.sources(channel)) {
                if (source.height <= 0 && !locks.isChannelLocked(source.channelId)) targets += source.channelId
            }
        }
        if (targets.isEmpty()) return QualityScanResult.ALL_KNOWN
        _state.update { it.copy(total = targets.size) }
        // Plejer dobija svoj opseg, da se njegovi poslovi ugase zajedno sa njim.
        val engineScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        val engine = PlayerEngine(context, engineScope, settings.playerSettings.first())
        engine.player.volume = 0f
        // Korisnik je pustio nešto u plejeru: merenje mu ne sme zauzimati vezu.
        val watcher = scope.launch {
            PlaybackActivity.others(engine).first { it }
            cancel()
        }
        try {
            for (id in targets) {
                if (network.kind.first() != NetworkKind.WIFI) break
                val source = repository.streamSource(id)
                if (source != null) {
                    engine.play(source)
                    val seen = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                        engine.state.first { it.video.height > 0 || it.status == PlaybackStatus.FAILED }
                    }
                    engine.stop()
                    val video = seen?.video
                    if (video != null && video.height > 0) {
                        repository.saveQuality(id, video.width, video.height)
                        _state.update { it.copy(measured = it.measured + 1) }
                    }
                }
                _state.update { it.copy(done = it.done + 1) }
                // Kratka pauza, da server ne dobija zahteve jedan za drugim bez predaha.
                delay(PAUSE_BETWEEN_MS)
            }
        } finally {
            watcher.cancel()
            engine.release()
            engineScope.cancel()
        }
        return QualityScanResult.DONE
    }

    private companion object {
        const val PROBE_TIMEOUT_MS = 12_000L
        const val PAUSE_BETWEEN_MS = 800L
    }
}
