package com.bane23.iptvplayer.ui.common.vod

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.cast.CastMedia
import com.bane23.iptvplayer.cast.CastVodItem
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.recording.RecordingRepository
import com.bane23.iptvplayer.data.repo.ArchiveRepository
import com.bane23.iptvplayer.data.repo.VodRepository
import com.bane23.iptvplayer.data.subtitles.OpenSubtitlesRepository
import com.bane23.iptvplayer.data.subtitles.SubtitleException
import com.bane23.iptvplayer.data.subtitles.SubtitleFailure
import com.bane23.iptvplayer.data.subtitles.SubtitleQuery
import com.bane23.iptvplayer.data.subtitles.SubtitleResult
import com.bane23.iptvplayer.data.xtream.Episode
import com.bane23.iptvplayer.data.xtream.SeriesDetails
import com.bane23.iptvplayer.domain.StreamSource
import com.bane23.iptvplayer.player.DecoderMode
import com.bane23.iptvplayer.player.ExternalSubtitle
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.player.PlayerEngine
import com.bane23.iptvplayer.player.TrackOption
import com.bane23.iptvplayer.ui.common.player.AspectMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
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
    /** Mesto na koje će se premotati kad se strelica pusti; null kad se ne premotava. */
    val scrubMs: Long? = null,
    /** Pozicija sa koje je premotavanje krenulo, za prikaz pomaka. */
    val scrubFromMs: Long = 0,
    val aspectMode: AspectMode = AspectMode.FIT,
    /** Dekoder koji plejer trenutno bira prvi. */
    val decoder: DecoderMode = DecoderMode.AUTO,
    val hasNext: Boolean = false,
    /** Strim nije mogao ni da se sastavi (nema veze, lista više nije Xtream i sl.). */
    val sourceMissing: Boolean = false,
    /** Može li se titl potražiti na internetu (film ili epizoda, a APK ima ključ za OpenSubtitles). */
    val onlineSubtitles: Boolean = false,
)

/** Pretraga titlova na internetu za ono što se gleda. */
data class OnlineSubtitlesState(
    val searching: Boolean = false,
    /** Null dok pretraga nije završena; prazan spisak znači da titlova nema. */
    val results: List<SubtitleResult>? = null,
    /** Titl koji se upravo preuzima. */
    val downloadingId: Long? = null,
    val failure: SubtitleFailure? = null,
    /** Titl koji je preuzet i uključen; ekran po tome zatvara spisak. */
    val appliedId: Long? = null,
    /** Koliko preuzimanja je ostalo za danas, ako je server javio. */
    val remaining: Int? = null,
)

/** Plejer za filmove i epizode: premotavanje, pamćenje pozicije i prelazak na sledeću epizodu. */
@HiltViewModel
class VodPlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: VodRepository,
    private val settings: SettingsStore,
    private val archive: ArchiveRepository,
    private val recordings: RecordingRepository,
    private val subtitles: OpenSubtitlesRepository,
) : ViewModel() {

    // Podešavanja su potrebna pre nego što se plejer napravi; čitanje iz DataStore-a je kratko.
    private val playerSettings = runBlocking { settings.playerSettings.first() }
    private val engine = PlayerEngine(context, viewModelScope, playerSettings)

    /** Korak dugmadi napred i nazad, iz podešavanja. */
    val seekStepMs: Long = playerSettings.seekStepSeconds * 1_000L
    val player get() = engine.player
    val playback: StateFlow<PlaybackState> = engine.state

    private val _ui = MutableStateFlow(VodPlayerUiState(decoder = playerSettings.decoder))
    val ui: StateFlow<VodPlayerUiState> = _ui.asStateFlow()

    private val _online = MutableStateFlow(OnlineSubtitlesState())
    val online: StateFlow<OnlineSubtitlesState> = _online.asStateFlow()
    private var onlineJob: Job? = null

    /** Naziv titla sa interneta koji je uključen za ono što se gleda (zapamćen ili upravo preuzet). */
    private var savedSubtitleName: String? = null

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
    private var source: StreamSource? = null
    private var started = false
    private var lastSavedAt = 0L
    private var scrubJob: Job? = null

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
        _ui.update { it.copy(title = name, onlineSubtitles = subtitles.isAvailable) }
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

    /** Snimak kanala sa uređaja. Pozicija se ne pamti, pa uvek kreće od početka. */
    fun startRecording(recordingId: Long) {
        if (started) return
        started = true
        viewModelScope.launch {
            play(startOver = true) {
                val recording = recordings.get(recordingId) ?: throw java.io.IOException("No recording")
                _ui.update { it.copy(title = recording.title) }
                val file = java.io.File(recording.path)
                if (!file.exists()) throw java.io.IOException("No file")
                StreamSource(listOf(android.net.Uri.fromFile(file).toString()), null, null) to null
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

    /** Ono što se gleda, u obliku za slanje na TV (cast), sa mestom do kog se stiglo. */
    fun castMedia(): CastMedia? {
        val current = source ?: return null
        // Snimak sa uređaja nema adresu koju bi TV mogao da otvori.
        if (current.urls.all { it.startsWith("file:") }) return null
        return CastMedia(
            title = _ui.value.title,
            urls = current.urls,
            live = false,
            positionMs = engine.positionMs,
            vod = item?.let { CastVodItem(it.id, it.seriesId, it.title, it.poster, it.extension) },
            userAgent = current.userAgent,
            referrer = current.referrer,
        )
    }

    fun retry() = engine.retry()

    fun seekBy(deltaMs: Long) {
        engine.seekBy(deltaMs)
        _ui.update { it.copy(positionMs = (it.positionMs + deltaMs).coerceIn(0, it.durationMs.coerceAtLeast(0))) }
    }

    fun seekTo(positionMs: Long) {
        scrubJob?.cancel()
        engine.seekTo(positionMs)
        _ui.update { it.copy(positionMs = positionMs, scrubMs = null) }
    }

    /**
     * Premotavanje strelicama na daljinskom: dok se strelica drži pomera se samo oznaka na traci,
     * a plejer premota tek kad se strelica pusti ([commitScrub]) ili malo posle poslednjeg pritiska.
     * Tako se ne učitava iznova na svaki korak.
     */
    fun scrubBy(deltaMs: Long) {
        val state = _ui.value
        if (state.durationMs <= 0) return
        val from = state.scrubMs ?: state.positionMs
        val target = (from + deltaMs).coerceIn(0, state.durationMs)
        _ui.update {
            it.copy(scrubMs = target, scrubFromMs = if (it.scrubMs == null) it.positionMs else it.scrubFromMs)
        }
        scrubJob?.cancel()
        scrubJob = viewModelScope.launch {
            delay(SCRUB_COMMIT_MS)
            commitScrub()
        }
    }

    fun commitScrub() {
        val target = _ui.value.scrubMs ?: return
        seekTo(target)
    }

    fun cycleAspectMode() = _ui.update {
        it.copy(aspectMode = AspectMode.entries[(it.aspectMode.ordinal + 1) % AspectMode.entries.size])
    }

    /** Prelazi na drugi dekoder odmah; gledanje se nastavlja sa istog mesta. Izbor se pamti kao u podešavanjima. */
    fun cycleDecoder() {
        val next = DecoderMode.entries[(_ui.value.decoder.ordinal + 1) % DecoderMode.entries.size]
        _ui.update { it.copy(decoder = next) }
        engine.setDecoder(next)
        viewModelScope.launch { settings.setPlayerDecoder(next) }
    }

    /** Izbor u plejeru se pamti kao podrazumevani jezik, da sledeći film ili epizoda krene isto. */
    fun selectAudio(option: TrackOption) {
        engine.selectAudio(option)
        option.language?.let { language -> viewModelScope.launch { settings.setAudioLanguage(language) } }
    }

    fun selectText(option: TrackOption?) {
        engine.selectText(option)
        // Isključeni titlovi ili drugi zapis: zapamćeni titl sa interneta se više ne uključuje sam.
        val savedName = savedSubtitleName
        val current = item
        if (savedName != null && current != null && (option == null || !option.label.startsWith(savedName))) {
            savedSubtitleName = null
            viewModelScope.launch { subtitles.forget(current.id, episode = current.seriesId != null) }
        }
        if (option == null) {
            viewModelScope.launch { settings.setSubtitleLanguage("") }
        } else {
            option.language?.let { language -> viewModelScope.launch { settings.setSubtitleLanguage(language) } }
        }
    }

    /** Traži titlove za film ili epizodu koja se gleda. Svako otvaranje spiska traži iznova. */
    fun searchOnlineSubtitles() {
        val current = item ?: return
        val state = _ui.value
        onlineJob?.cancel()
        _online.value = OnlineSubtitlesState(searching = true)
        onlineJob = viewModelScope.launch {
            try {
                val seriesId = current.seriesId
                val query = if (seriesId != null) {
                    SubtitleQuery(
                        title = current.title.orEmpty(),
                        tmdbId = series?.tmdbId ?: lookup { repository.seriesListTmdbId(seriesId) },
                        season = state.season,
                        episode = state.episode,
                    )
                } else {
                    SubtitleQuery(title = current.title.orEmpty(), tmdbId = lookup { repository.movieTmdbId(current.id) })
                }
                _online.value = OnlineSubtitlesState(results = subtitles.search(query))
            } catch (e: SubtitleException) {
                _online.value = OnlineSubtitlesState(failure = e.failure)
            }
        }
    }

    /** Preuzima izabrani titl i uključuje ga; gledanje se nastavlja sa istog mesta. */
    fun downloadSubtitle(result: SubtitleResult) {
        if (_online.value.downloadingId != null) return
        _online.update { it.copy(downloadingId = result.fileId, failure = null) }
        onlineJob = viewModelScope.launch {
            try {
                val downloaded = subtitles.download(result)
                engine.addSubtitle(
                    ExternalSubtitle(
                        id = "os-${result.fileId}",
                        uri = android.net.Uri.fromFile(downloaded.file).toString(),
                        language = result.language.ifEmpty { null },
                        label = result.name,
                    ),
                )
                // Izbor se pamti: sledeći put kad se ovo pusti, titl je već uključen.
                savedSubtitleName = result.name
                item?.let { subtitles.remember(it.id, episode = it.seriesId != null, result = result) }
                _online.update { it.copy(downloadingId = null, appliedId = result.fileId, remaining = downloaded.remaining) }
            } catch (e: SubtitleException) {
                _online.update { it.copy(downloadingId = null, failure = e.failure) }
            }
        }
    }

    /** Spisak je zatvoren: pretraga koja traje se prekida, a preuzimanje koje je krenulo se završava. */
    fun closeOnlineSubtitles() {
        if (_online.value.downloadingId == null) onlineJob?.cancel()
        _online.update { OnlineSubtitlesState(downloadingId = it.downloadingId) }
    }

    /** TMDB ID se traži kod provajdera; ako to ne uspe, pretraga ide po nazivu. */
    private suspend fun lookup(load: suspend () -> Long?): Long? = try {
        load()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    fun onForeground() = engine.resume()

    fun onBackground() {
        saveProgress(stopped = true)
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
                scrubMs = null,
                hasNext = details.episodeAfter(episode.id) != null,
                onlineSubtitles = subtitles.isAvailable,
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
        load: suspend () -> Pair<StreamSource, Long?>,
    ) {
        try {
            val (source, savedPosition) = load()
            this.source = source
            _ui.update { it.copy(sourceMissing = false) }
            // Titl sa interneta izabran ranije za ovaj film ili epizodu uključuje se sam, iz fajla na uređaju.
            val saved = item?.let { subtitles.saved(it.id, episode = it.seriesId != null) }
            savedSubtitleName = saved?.first?.name
            engine.play(
                source,
                startPositionMs = if (startOver) 0 else savedPosition ?: 0,
                subtitles = saved?.let { (subtitle, file) ->
                    listOf(
                        ExternalSubtitle(
                            id = "os-${subtitle.fileId}",
                            uri = android.net.Uri.fromFile(file).toString(),
                            language = subtitle.language.ifEmpty { null },
                            label = subtitle.name,
                        ),
                    )
                } ?: emptyList(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            engine.stop()
            _ui.update { it.copy(sourceMissing = true) }
        }
    }

    /** [stopped]: gledanje je prekinuto (izlaz iz plejera ili aplikacije), pa pozicija ide i na Trakt. */
    private fun saveProgress(finished: Boolean = false, stopped: Boolean = false) {
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
            stopped = stopped,
        )
    }

    override fun onCleared() {
        saveProgress(stopped = true)
        engine.release()
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
        const val SAVE_EVERY_MS = 10_000L
        const val SCRUB_COMMIT_MS = 1_200L
    }
}
