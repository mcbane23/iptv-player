package com.bane23.iptvplayer.ui.common.vod

import com.bane23.iptvplayer.ui.common.player.MAX_SUBTITLE_SHIFT_MS
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.AppMessages
import com.bane23.iptvplayer.cast.CastMedia
import com.bane23.iptvplayer.cast.CastVodItem
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.db.DownloadEntity
import com.bane23.iptvplayer.data.download.DownloadRepository
import com.bane23.iptvplayer.data.recording.RecordingRepository
import com.bane23.iptvplayer.data.repo.ArchiveRepository
import com.bane23.iptvplayer.data.repo.VodRepository
import com.bane23.iptvplayer.data.subtitles.OpenSubtitlesRepository
import com.bane23.iptvplayer.data.subtitles.SubtitleException
import com.bane23.iptvplayer.data.subtitles.SubtitleFailure
import com.bane23.iptvplayer.data.subtitles.SubtitleQuery
import com.bane23.iptvplayer.data.subtitles.SubtitleResult
import com.bane23.iptvplayer.data.trakt.TraktRepository
import com.bane23.iptvplayer.data.xtream.Episode
import com.bane23.iptvplayer.data.xtream.SeriesDetails
import com.bane23.iptvplayer.domain.StreamSource
import com.bane23.iptvplayer.player.DecoderMode
import com.bane23.iptvplayer.player.ExternalSubtitle
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.player.NextEpisode
import com.bane23.iptvplayer.player.PlayerEngine
import com.bane23.iptvplayer.player.TrackOption
import com.bane23.iptvplayer.ui.common.player.AspectMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import com.bane23.iptvplayer.ui.common.player.formatDuration

/** Oznake za ekran pauze: ocena (osim prazne i nule), godina i žanr, samo ono što postoji. */
internal fun pauseMeta(rating: String?, releaseDate: String?, genre: String?): List<String> = listOfNotNull(
    rating?.trim()?.takeIf { it.toFloatOrNull()?.let { value -> value > 0f } == true }?.let { "★ $it" },
    releaseDate?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) },
    genre?.trim()?.takeIf { it.isNotEmpty() },
)

data class VodPlayerUiState(
    val title: String = "",
    /** Sezona i epizoda (za serije). */
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** Opis filma ili epizode za ekran pauze; null dok nije učitan ili ga nema. */
    val description: String? = null,
    /** Kratke oznake za ekran pauze (ocena, godina, žanr; za epizodu sezona i broj). */
    val meta: List<String> = emptyList(),
    /** Mesto na koje će se premotati kad se strelica pusti; null kad se ne premotava. */
    val scrubMs: Long? = null,
    /** Pozicija sa koje je premotavanje krenulo, za prikaz pomaka. */
    val scrubFromMs: Long = 0,
    val aspectMode: AspectMode = AspectMode.FIT,
    /** Dekoder koji plejer trenutno bira prvi. */
    val decoder: DecoderMode = DecoderMode.AUTO,
    /** Brzina puštanja; 1 = obična. Važi dok je plejer otvoren, i za sledeću epizodu. */
    val speed: Float = 1f,
    /**
     * Pomeraj titla u milisekundama (pozitivno = titl izlazi kasnije); null kad nema titla iz fajla,
     * jer se titl ugrađen u snimak ne može pomerati.
     */
    val subtitleOffsetMs: Long? = null,
    val hasNext: Boolean = false,
    /** Kartica „Sledeća epizoda” pred kraj epizode; null dok je nema. */
    val nextCard: NextCard? = null,
    /** Strim nije mogao ni da se sastavi (nema veze, lista više nije Xtream i sl.). */
    val sourceMissing: Boolean = false,
    /** Pre puštanja se čeka pozicija sa drugog uređaja (Trakt). */
    val fetchingPosition: Boolean = false,
    /** Može li se titl potražiti na internetu (film ili epizoda, a APK ima ključ za OpenSubtitles). */
    val onlineSubtitles: Boolean = false,
)

/**
 * Kartica „Sledeća epizoda”: koja epizoda sledi i za koliko sekundi kreće sama. Sa [stillWatching]
 * epizoda ne kreće sama, nego kartica pita „Još gledate?” (nekoliko epizoda je prošlo bez dodira).
 */
data class NextCard(
    val season: Int,
    val episode: Int,
    val title: String,
    val secondsLeft: Int,
    val stillWatching: Boolean,
    /** Sledeća kreće sama kad odbrojavanje istekne; false kad je to isključeno u Podešavanjima. */
    val auto: Boolean = true,
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
    @ApplicationContext private val context: Context,
    private val repository: VodRepository,
    private val settings: SettingsStore,
    private val archive: ArchiveRepository,
    private val recordings: RecordingRepository,
    private val downloads: DownloadRepository,
    private val subtitles: OpenSubtitlesRepository,
    private val sleepTimer: com.bane23.iptvplayer.player.SleepTimer,
    private val trakt: TraktRepository,
) : ViewModel() {

    // Podešavanja su potrebna pre nego što se plejer napravi; čitanje iz DataStore-a je kratko.
    private val playerSettings = runBlocking { settings.playerSettings.first() }
    private val engine = PlayerEngine(context, viewModelScope, playerSettings)

    /** Korak dugmadi napred i nazad, iz podešavanja. */
    val seekStepMs: Long = playerSettings.seekStepSeconds * 1_000L
    val player get() = engine.player

    /** Tekst titla sa interneta za trenutak koji se gleda; prikazuje ga [com.bane23.iptvplayer.ui.common.player.VideoSurface]. */
    val subtitleText get() = engine.subtitleText
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
    private var lastSavedPositionMs = -1L
    private var scrubJob: Job? = null

    /** Čeka poziciju sa drugog uređaja koja je zakasnila (vidi [followLateRemote]). */
    private var lateRemoteJob: Job? = null

    /** Gledalac je sam premotao od početka puštanja: zakasnela pozicija se tad ne primenjuje. */
    private var userMoved = false

    init {
        // Tajmer gašenja je istekao: gledanje se pauzira, pa uređaj posle sam ugasi ekran.
        viewModelScope.launch { sleepTimer.expired.collect { engine.pause() } }
        // Pozicija se osvežava dok se gleda i povremeno pamti.
        viewModelScope.launch {
            while (isActive) {
                delay(POSITION_TICK_MS)
                val duration = engine.durationMs
                if (duration > 0) {
                    val position = engine.positionMs
                    _ui.update { it.copy(positionMs = position, durationMs = duration, nextCard = nextCard(position, duration)) }
                    val now = System.currentTimeMillis()
                    // Dok je pauzirano pozicija stoji, pa nema šta novo da se upiše u bazu.
                    if (now - lastSavedAt >= SAVE_EVERY_MS && engine.positionMs != lastSavedPositionMs) saveProgress()
                }
            }
        }
        viewModelScope.launch { settings.autoNextEpisode.collect { autoNext = it } }
        // Pauza duža od nekoliko sekundi šalje poziciju, da drugi uređaj nastavi odatle i bez izlaska iz plejera.
        viewModelScope.launch {
            playback.map { it.status == PlaybackStatus.PAUSED }.distinctUntilChanged().collectLatest { paused ->
                if (paused) {
                    delay(PAUSE_SHARE_MS)
                    saveProgress(stopped = true)
                }
            }
        }
        viewModelScope.launch { engine.seekLost.collect { AppMessages.show(context, R.string.vod_seek_unavailable) } }
        // Kad se epizoda završi, kreće sledeća.
        viewModelScope.launch {
            playback.map { it.ended }.distinctUntilChanged().collect { ended ->
                if (ended) {
                    saveProgress(finished = true)
                    // Sledeća kreće sama, osim kad je gledalac odustao na kartici ili treba da potvrdi da još gleda.
                    if (autoNext && _ui.value.hasNext && !nextCancelled && !NextEpisode.needsConfirmation(autoAdvances)) {
                        autoAdvances++
                        advance()
                    }
                }
            }
        }
    }

    fun startMovie(movieId: Long, name: String, extension: String, startOver: Boolean, poster: String? = null) {
        if (started) return
        started = true
        item = Item(movieId, seriesId = null, title = name, poster = poster, extension = extension)
        _ui.update { it.copy(title = name, onlineSubtitles = subtitles.isAvailable) }
        // Opis za ekran pauze; puštanje ga ne čeka i radi i kad se ne učita.
        viewModelScope.launch {
            runCatching { repository.movie(movieId) }.onSuccess { details ->
                _ui.update {
                    it.copy(
                        description = details.plot?.takeIf(String::isNotBlank),
                        meta = pauseMeta(details.rating, details.releaseDate, details.genre),
                    )
                }
            }
        }
        viewModelScope.launch {
            play(
                startOver,
                savedPosition = { repository.movieProgress(movieId)?.takeIf { it.resumable }?.positionMs },
            ) {
                // Film preuzet na telefon se pušta sa uređaja, bez interneta.
                downloads.localSource(episode = false, movieId) ?: repository.movieSource(movieId, extension)
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
                archive.source(channelId, startMs, stopMs, startToken) ?: throw java.io.IOException("No archive")
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
                StreamSource(listOf(android.net.Uri.fromFile(file).toString()), null, null)
            }
        }
    }

    /**
     * Film ili epizoda preuzeti na telefon, sa ekrana „Preuzeto”; radi i bez interneta. Pozicija se
     * pamti kao i inače kad preuzimanje pripada aktivnoj listi. Za epizodu se spisak ostalih epizoda
     * traži sa interneta, pa sledeća epizoda postoji samo kad se on učita.
     */
    fun startDownload(downloadId: Long) {
        if (started) return
        started = true
        viewModelScope.launch {
            val row = downloads.get(downloadId)
            val file = row?.let(downloads::fileSource)
            if (row == null || file == null) {
                _ui.update { it.copy(sourceMissing = true) }
                return@launch
            }
            val ownList = repository.activePlaylistId.first() == row.playlistId
            val episode = row.kind == DownloadEntity.KIND_EPISODE
            if (ownList) {
                item = Item(row.itemId, row.seriesId.takeIf { episode }, row.title, row.poster, row.extension.takeUnless { episode })
            }
            _ui.update {
                it.copy(
                    title = listOfNotNull(row.title, row.subtitle).joinToString(" · "),
                    onlineSubtitles = ownList && subtitles.isAvailable,
                )
            }
            val seriesId = row.seriesId
            if (ownList && episode && seriesId != null) {
                // Ne čeka se: bez interneta ovo ne uspe, a epizoda sa uređaja svejedno kreće.
                launch {
                    // Bez interneta se spisak epizoda sastavlja od preuzetih, da „Sledeća” i kartica na kraju
                    // epizode rade i tada. Ništa se ne preuzima samo od sebe.
                    val details = runCatching { repository.series(seriesId) }.getOrNull()
                        ?: downloads.offlineSeries(row.playlistId, seriesId)
                        ?: return@launch
                    val current = details.episodes.firstOrNull { it.id == row.itemId } ?: return@launch
                    series = details
                    _ui.update {
                        it.copy(
                            season = current.season,
                            episode = current.number,
                            description = (current.plot ?: details.plot)?.takeIf(String::isNotBlank),
                            meta = listOf("S${current.season} E${current.number}") + pauseMeta(null, null, details.genre),
                            hasNext = details.episodeAfter(current.id) != null,
                        )
                    }
                }
            }
            play(
                startOver = false,
                savedPosition = {
                    val saved = when {
                        !ownList -> null
                        episode -> repository.episodeProgress(row.itemId)
                        else -> repository.movieProgress(row.itemId)
                    }
                    saved?.takeIf { it.resumable }?.positionMs
                },
            ) { file }
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

    /** Gledalac je na kartici „Sledeća epizoda” odustao: ova epizoda se završava bez prelaska na sledeću. */
    private var nextCancelled = false

    /** Koliko je epizoda zaredom krenulo samo, bez ijednog dodira daljinskog ili ekrana. */
    private var autoAdvances = 0

    /** Podešavanje „Automatski pusti sledeću epizodu”. */
    private var autoNext = true

    private fun nextCard(positionMs: Long, durationMs: Long): NextCard? {
        if (nextCancelled) return null
        val current = item ?: return null
        val following = series?.episodeAfter(current.id) ?: return null
        val seconds = NextEpisode.secondsLeft(positionMs, durationMs) ?: return null
        return NextCard(
            season = following.season,
            episode = following.number,
            title = following.title,
            secondsLeft = seconds,
            stillWatching = autoNext && NextEpisode.needsConfirmation(autoAdvances),
            auto = autoNext,
        )
    }

    /** Svaki dodir daljinskog ili ekrana znači da neko gleda, pa se brojanje epizoda vraća na nulu. */
    fun onUserActivity() {
        autoAdvances = 0
    }

    /** „Pusti odmah”, odnosno „Da, gledam”: sledeća epizoda kreće odmah. */
    fun acceptNext() {
        autoAdvances = 0
        advance()
    }

    /** „Otkaži” na kartici: epizoda se gleda do kraja, a sledeća ne kreće sama. */
    fun cancelNext() {
        nextCancelled = true
        _ui.update { it.copy(nextCard = null) }
    }

    /** Prelazi na sledeću epizodu, od početka (dugme i taster „Sledeća”). */
    fun next() {
        autoAdvances = 0
        advance()
    }

    private fun advance() {
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

    /** Sadržaj koji ne može da se premotava: umesto povratka na početak ostaje gde je, uz kratku poruku. */
    private fun seekBlocked(): Boolean {
        if (!engine.seekBlocked) return false
        scrubJob?.cancel()
        _ui.update { it.copy(scrubMs = null) }
        AppMessages.show(context, R.string.vod_seek_unavailable)
        return true
    }

    fun seekBy(deltaMs: Long) {
        if (seekBlocked()) return
        userMoved = true
        engine.seekBy(deltaMs)
        _ui.update { it.copy(positionMs = (it.positionMs + deltaMs).coerceIn(0, it.durationMs.coerceAtLeast(0))) }
    }

    fun seekTo(positionMs: Long) {
        if (seekBlocked()) return
        userMoved = true
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
        if (state.durationMs <= 0 || seekBlocked()) return
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

    fun cycleAspectMode() =
        setAspectMode(AspectMode.entries[(_ui.value.aspectMode.ordinal + 1) % AspectMode.entries.size])

    fun setAspectMode(mode: AspectMode) = _ui.update { it.copy(aspectMode = mode) }

    /** Prelazi na drugi dekoder odmah; gledanje se nastavlja sa istog mesta. Izbor se pamti kao u podešavanjima. */
    fun setDecoder(mode: DecoderMode) {
        if (_ui.value.decoder == mode) return
        _ui.update { it.copy(decoder = mode) }
        engine.setDecoder(mode)
        viewModelScope.launch { settings.setPlayerDecoder(mode) }
    }

    /** Brzina puštanja filma ili epizode. Ne pamti se u podešavanjima. */
    fun setSpeed(speed: Float) {
        engine.setSpeed(speed)
        _ui.update { it.copy(speed = speed) }
    }

    /** Pomera titl za [deltaMs] (kad kasni ili žuri). Važi odmah, dok se ovaj film ili epizoda gleda. */
    fun shiftSubtitle(deltaMs: Long) {
        val current = _ui.value.subtitleOffsetMs ?: return
        val offset = (current + deltaMs).coerceIn(-MAX_SUBTITLE_SHIFT_MS, MAX_SUBTITLE_SHIFT_MS)
        if (offset == current) return
        _ui.update { it.copy(subtitleOffsetMs = offset) }
        engine.setSubtitleOffset(offset)
    }

    /** Izbor u plejeru važi samo za ono što se gleda; podrazumevani jezici iz podešavanja se ne menjaju. */
    fun selectAudio(option: TrackOption) = engine.selectAudio(option)

    fun selectText(option: TrackOption?) {
        engine.selectText(option)
        // Isključeni titlovi ili drugi zapis: zapamćeni titl sa interneta se više ne uključuje sam.
        val savedName = savedSubtitleName
        val current = item
        if (savedName != null && current != null && (option == null || !option.label.startsWith(savedName))) {
            savedSubtitleName = null
            viewModelScope.launch { subtitles.forget(current.id, episode = current.seriesId != null) }
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
                _ui.update { it.copy(subtitleOffsetMs = 0) }
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

    fun onForeground() = engine.restore()

    /** Sa [keepPlaying] telefon nastavlja da pušta u pozadini; pozicija se pamti u oba slučaja. */
    fun onBackground(keepPlaying: Boolean = false) {
        saveProgress(stopped = !keepPlaying)
        if (!keepPlaying) engine.stop()
    }

    private suspend fun playEpisode(seriesId: Long, details: SeriesDetails, episode: Episode, startOver: Boolean) {
        nextCancelled = false
        item = Item(episode.id, seriesId, title = details.name, poster = details.poster, extension = null)
        _ui.update {
            it.copy(
                title = listOfNotNull(details.name, episode.title).joinToString(" · "),
                season = episode.season,
                episode = episode.number,
                description = (episode.plot ?: details.plot)?.takeIf(String::isNotBlank),
                meta = listOf("S${episode.season} E${episode.number}") + pauseMeta(null, null, details.genre),
                positionMs = 0,
                durationMs = 0,
                scrubMs = null,
                nextCard = null,
                hasNext = details.episodeAfter(episode.id) != null,
                onlineSubtitles = subtitles.isAvailable,
            )
        }
        play(
            startOver,
            savedPosition = { repository.episodeProgress(episode.id)?.takeIf { it.resumable }?.positionMs },
        ) {
            downloads.localSource(episode = true, episode.id) ?: repository.episodeSource(episode.id, episode.extension)
        }
    }

    /** [savedPosition] čita zapamćenu poziciju (ako je ima), a [load] vraća izvor strima. */
    private suspend fun play(
        startOver: Boolean,
        savedPosition: suspend () -> Long? = { null },
        load: suspend () -> StreamSource,
    ) {
        lateRemoteJob?.cancel()
        userMoved = false
        try {
            // Pozicija sa drugog uređaja stiže pre čitanja zapamćene, da gledanje krene odatle.
            val exchange = if (startOver) null else trakt.startPlaybackExchange()
            if (exchange != null) {
                _ui.update { it.copy(fetchingPosition = true) }
                try {
                    withTimeoutOrNull(PLAYBACK_SYNC_WAIT_MS) { exchange.await() }
                } finally {
                    _ui.update { it.copy(fetchingPosition = false) }
                }
            }
            val source = load()
            val start = if (startOver) null else savedPosition()
            this.source = source
            _ui.update { it.copy(sourceMissing = false) }
            // Titl sa interneta izabran ranije za ovaj film ili epizodu uključuje se sam, iz fajla na uređaju.
            val saved = item?.let { subtitles.saved(it.id, episode = it.seriesId != null) }
            savedSubtitleName = saved?.first?.name
            _ui.update { it.copy(subtitleOffsetMs = if (saved != null) 0L else null) }
            engine.play(
                source,
                startPositionMs = start ?: 0,
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
            if (!startOver) trakt.logPlayerStart(_ui.value.title, start ?: 0, exchange?.isCompleted)
            if (exchange != null && !exchange.isCompleted) followLateRemote(exchange, start, savedPosition)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            engine.stop()
            _ui.update { it.copy(sourceMissing = true) }
        }
    }

    /**
     * Pozicija sa drugog uređaja nije stigla pre početka: kad stigne (najkasnije za [LATE_REMOTE_MS]),
     * gledanje skače na nju, osim ako je gledalac u međuvremenu sam premotao. Dok se čeka, ovaj uređaj
     * ne pamti svoju poziciju: novija bi prekrila onu sa drugog uređaja.
     */
    private fun followLateRemote(exchange: Deferred<*>, startedAt: Long?, savedPosition: suspend () -> Long?) {
        val current = item
        lateRemoteJob = viewModelScope.launch {
            withTimeoutOrNull(LATE_REMOTE_MS) { exchange.await() } ?: return@launch
            if (item != current || userMoved) return@launch
            val remote = savedPosition() ?: return@launch
            if (remote == startedAt || abs(remote - engine.positionMs) < LATE_JUMP_MIN_MS) return@launch
            engine.seekTo(remote)
            _ui.update { it.copy(positionMs = remote) }
            AppMessages.show(context.getString(R.string.vod_resumed_from_other_device, formatDuration(remote)))
        }
    }

    /** [stopped]: gledanje je prekinuto (izlaz iz plejera ili aplikacije), pa pozicija ide i na Trakt. */
    private fun saveProgress(finished: Boolean = false, stopped: Boolean = false) {
        val current = item ?: return
        if (!finished && lateRemoteJob?.isActive == true) return
        val duration = engine.durationMs
        if (duration <= 0) return
        lastSavedAt = System.currentTimeMillis()
        val position = if (finished) duration else engine.positionMs
        lastSavedPositionMs = position
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
        const val PAUSE_SHARE_MS = 10_000L
        const val PLAYBACK_SYNC_WAIT_MS = 8_000L
        const val LATE_REMOTE_MS = 30_000L
        const val LATE_JUMP_MIN_MS = 10_000L
        const val SCRUB_COMMIT_MS = 1_200L
    }
}
