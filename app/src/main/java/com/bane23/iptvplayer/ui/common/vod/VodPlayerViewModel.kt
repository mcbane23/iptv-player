package com.bane23.iptvplayer.ui.common.vod

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.ArchiveRepository
import com.bane23.iptvplayer.data.repo.VodRepository
import com.bane23.iptvplayer.data.xtream.Episode
import com.bane23.iptvplayer.data.xtream.SeriesDetails
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.player.PlayerEngine
import com.bane23.iptvplayer.player.TrackOption
import com.bane23.iptvplayer.ui.common.player.AspectMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class VodPlayerUiState(
    val title: String = "",
    /** Sezona i epizoda (za serije). */
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val aspectMode: AspectMode = AspectMode.FIT,
    val hasNext: Boolean = false,
    /** Strim nije mogao ni da se sastavi (nema veze, lista više nije Xtream i sl.). */
    val sourceMissing: Boolean = false,
)

/** Plejer za filmove i epizode: premotavanje, pamćenje pozicije i prelazak na sledeću epizodu. */
@HiltViewModel
class VodPlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: VodRepository,
    private val settings: SettingsStore,
    private val archive: ArchiveRepository,
) : ViewModel() {

    // Podešavanja su potrebna pre nego što se plejer napravi; čitanje iz DataStore-a je kratko.
    private val playerSettings = runBlocking { settings.playerSettings.first() }
    private val engine = PlayerEngine(context, viewModelScope, playerSettings)

    /** Korak dugmadi napred i nazad, iz podešavanja. */
    val seekStepMs: Long = playerSettings.seekStepSeconds * 1_000L
    val player get() = engine.player
    val playback: StateFlow<PlaybackState> = engine.state

    private val _ui = MutableStateFlow(VodPlayerUiState())
    val ui: StateFlow<VodPlayerUiState> = _ui.asStateFlow()

    /**
     * Ono što se trenutno gleda; [seriesId] je null za film. [title], [poster] i [extension] se pamte
     * uz poziciju, za red „Nastavi gledanje” (za epizodu su to naziv i poster serije).
     */
    private data class Item(
        val id: Long,
        val seriesId: Long?,
        val title: String?,
        val poster: String?,
        val extension: String?,
    )

    private var item: Item? = null
    private var series: SeriesDetails? = null
    private var started = false
    private var lastSavedAt = 0L

    init {
        // Pozicija se osvežava dok se gleda i povremeno pamti.
        viewModelScope.launch {
            while (isActive) {
                delay(POSITION_TICK_MS)
                val duration = engine.durationMs
                if (duration > 0) {
                    _ui.update { it.copy(positionMs = engine.positionMs, durationMs = duration) }
                    val now = System.currentTimeMillis()
                    if (now - lastSavedAt >= SAVE_EVERY_MS) saveProgress()
                }
            }
        }
        // Kad se epizoda završi, kreće sledeća.
        viewModelScope.launch {
            playback.map { it.ended }.distinctUntilChanged().collect { ended ->
                if (ended) {
                    saveProgress(finished = true)
                    if (_ui.value.hasNext) next()
                }
            }
        }
    }

    fun startMovie(movieId: Long, name: String, extension: String, startOver: Boolean, poster: String? = null) {
        if (started) return
        started = true
        item = Item(movieId, seriesId = null, title = name, poster = poster, extension = extension)
        _ui.update { it.copy(title = name) }
        viewModelScope.launch {
            play(startOver) {
                val start = repository.movieProgress(movieId)?.takeIf { it.resumable }?.positionMs
                repository.movieSource(movieId, extension) to start
            }
        }
    }

    /** Emisija iz arhive kanala. Pozicija se ne pamti, pa uvek kreće od početka. */
    fun startArchive(channelId: Long, startMs: Long, stopMs: Long, title: String, startToken: String?) {
        if (started) return
        started = true
        _ui.update { it.copy(title = title) }
        viewModelScope.launch {
            play(startOver = true) {
                val source = archive.source(channelId, startMs, stopMs, startToken) ?: throw java.io.IOException("No archive")
                source to null
            }
        }
    }

    fun startEpisode(seriesId: Long, episodeId: Long, startOver: Boolean) {
        if (started) return
        started = true
        viewModelScope.launch {
            val details = try {
                repository.series(seriesId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            val episode = details?.episodes?.firstOrNull { it.id == episodeId }
            if (details == null || episode == null) {
                _ui.update { it.copy(sourceMissing = true) }
                return@launch
            }
            series = details
            playEpisode(seriesId, details, episode, startOver)
        }
    }

    /** Prelazi na sledeću epizodu, od početka. */
    fun next() {
        val current = item ?: return
        val seriesId = current.seriesId ?: return
        val details = series ?: return
        val following = details.episodeAfter(current.id) ?: return
        saveProgress()
        viewModelScope.launch { playEpisode(seriesId, details, following, startOver = true) }
    }

    fun togglePlayPause() = engine.togglePlayPause()

    fun retry() = engine.retry()

    fun seekBy(deltaMs: Long) {
        engine.seekBy(deltaMs)
        _ui.update { it.copy(positionMs = (it.positionMs + deltaMs).coerceIn(0, it.durationMs.coerceAtLeast(0))) }
    }

    fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
        _ui.update { it.copy(positionMs = positionMs) }
    }

    fun cycleAspectMode() = _ui.update {
        it.copy(aspectMode = AspectMode.entries[(it.aspectMode.ordinal + 1) % AspectMode.entries.size])
    }

    /** Izbor u plejeru se pamti kao podrazumevani jezik, da sledeći film ili epizoda krene isto. */
    fun selectAudio(option: TrackOption) {
        engine.selectAudio(option)
        option.language?.let { language -> viewModelScope.launch { settings.setAudioLanguage(language) } }
    }

    fun selectText(option: TrackOption?) {
        engine.selectText(option)
        if (option == null) {
            viewModelScope.launch { settings.setSubtitleLanguage("") }
        } else {
            option.language?.let { language -> viewModelScope.launch { settings.setSubtitleLanguage(language) } }
        }
    }

    fun onForeground() = engine.resume()

    fun onBackground() {
        saveProgress()
        engine.stop()
    }

    private suspend fun playEpisode(seriesId: Long, details: SeriesDetails, episode: Episode, startOver: Boolean) {
        item = Item(episode.id, seriesId, title = details.name, poster = details.poster, extension = null)
        _ui.update {
            it.copy(
                title = listOfNotNull(details.name, episode.title).joinToString(" · "),
                season = episode.season,
                episode = episode.number,
                positionMs = 0,
                durationMs = 0,
                hasNext = details.episodeAfter(episode.id) != null,
            )
        }
        play(startOver) {
            val start = repository.episodeProgress(episode.id)?.takeIf { it.resumable }?.positionMs
            repository.episodeSource(episode.id, episode.extension) to start
        }
    }

    /** [load] vraća izvor strima i zapamćenu poziciju (ako je ima). */
    private suspend fun play(
        startOver: Boolean,
        load: suspend () -> Pair<com.bane23.iptvplayer.domain.StreamSource, Long?>,
    ) {
        try {
            val (source, savedPosition) = load()
            _ui.update { it.copy(sourceMissing = false) }
            engine.play(source, startPositionMs = if (startOver) 0 else savedPosition ?: 0)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            engine.stop()
            _ui.update { it.copy(sourceMissing = true) }
        }
    }

    private fun saveProgress(finished: Boolean = false) {
        val current = item ?: return
        val duration = engine.durationMs
        if (duration <= 0) return
        lastSavedAt = System.currentTimeMillis()
        val position = if (finished) duration else engine.positionMs
        repository.saveProgress(
            itemId = current.id,
            seriesId = current.seriesId,
            positionMs = position,
            durationMs = duration,
            title = current.title,
            poster = current.poster,
            extension = current.extension,
        )
    }

    override fun onCleared() {
        saveProgress()
        engine.release()
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
        const val SAVE_EVERY_MS = 10_000L
    }
}
