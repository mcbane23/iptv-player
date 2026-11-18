package com.bane23.iptvplayer.ui.common.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bane23.iptvplayer.cast.CastMedia
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.recording.RecordingRepository
import com.bane23.iptvplayer.data.recording.RecordingStartError
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.data.repo.ChannelSource
import com.bane23.iptvplayer.data.repo.EpgRepository
import com.bane23.iptvplayer.data.repo.LockRepository
import com.bane23.iptvplayer.data.repo.PlaylistRepository
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.domain.ChannelGroups
import com.bane23.iptvplayer.domain.NowNext
import com.bane23.iptvplayer.player.DecoderMode
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.player.PlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Kako se slika uklapa u ekran. */
enum class AspectMode { FIT, FILL, ZOOM }

data class PlayerUiState(
    val channel: Channel? = null,
    /** Kategorija kroz koju se listaju kanali; null = svi kanali liste. */
    val categoryId: Long? = null,
    /** Redni broj trenutnog kanala u toj kategoriji (od nule). */
    val position: Int = 0,
    val aspectMode: AspectMode = AspectMode.FIT,
    /** Dekoder koji plejer trenutno bira prvi. */
    val decoder: DecoderMode = DecoderMode.AUTO,
    /** Broj kanala koji korisnik upravo kuca na daljinskom. */
    val typedNumber: String = "",
    /** Strim ne može ni da se sastavi (npr. lozinka više ne može da se pročita). */
    val sourceMissing: Boolean = false,
    /** Kanal koji se gledao pre ovog, za brz povratak; null dok se kanal nijednom nije promenio. */
    val lastChannel: Channel? = null,
    /** Izvori kanala: on sam i isti kanal u spojenim listama. Više od jednog samo kad su liste spojene. */
    val sources: List<ChannelSource> = emptyList(),
    /** Izvor (ID kanala) čija se slika trenutno pušta; null dok kanal nije izabran. */
    val sourceId: Long? = null,
    /** Izvori koji se od izbora kanala nisu javili. */
    val failedSources: Set<Long> = emptySet(),
)

private data class ListScope(val playlistId: Long, val categoryId: Long?, val startIndex: Int)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: ChannelRepository,
    private val epg: EpgRepository,
    private val settings: SettingsStore,
    private val locks: LockRepository,
    private val recordings: RecordingRepository,
    private val playlists: PlaylistRepository,
    private val sleepTimer: com.bane23.iptvplayer.player.SleepTimer,
) : ViewModel() {

    // Podešavanja su potrebna pre nego što se plejer napravi; čitanje iz DataStore-a je kratko.
    private val initialSettings = runBlocking { settings.playerSettings.first() }
    private val engine = PlayerEngine(context, viewModelScope, initialSettings)
    val player get() = engine.player
    val playback: StateFlow<PlaybackState> = engine.state

    private val _ui = MutableStateFlow(PlayerUiState(decoder = initialSettings.decoder))
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    private val listScope = MutableStateFlow<ListScope?>(null)

    /** Kanali trenutne kategorije, za listu preko slike. Kreće od kanala koji se gleda. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val channels: Flow<PagingData<Channel>> = listScope.filterNotNull()
        .flatMapLatest { repository.channels(it.playlistId, it.categoryId, it.startIndex) }
        .cachedIn(viewModelScope)

    /** Da li je kanal koji se gleda među favoritima. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isFavorite: StateFlow<Boolean> = _ui.map { it.channel }
        .distinctUntilChangedBy { it?.id }
        .flatMapLatest { channel -> if (channel == null) flowOf(false) else repository.observeFavorite(channel) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Da li se kanal koji se gleda upravo snima. */
    val isRecording: StateFlow<Boolean> = combine(_ui.map { it.channel }, recordings.active) { channel, active ->
        channel != null && active.any { it.isOf(channel) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _recordingNotice = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /** Kratka poruka o snimanju (tekst iz `strings.xml`): počelo, zaustavljeno ili zašto ne može. */
    val recordingNotice: SharedFlow<Int> = _recordingNotice.asSharedFlow()

    /** Šta je na kanalu sada i šta sledi; osvežava se dok se kanal gleda. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val nowNext: StateFlow<NowNext> = _ui.map { it.channel?.id }
        .distinctUntilChanged()
        .flatMapLatest { channelId ->
            if (channelId == null) {
                flowOf(NowNext())
            } else {
                flow {
                    while (true) {
                        emit(epg.nowNext(channelId))
                        delay(NOW_NEXT_REFRESH_MS)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowNext())

    /**
     * Za posebne grupe (favoriti, nedavno gledano): redosled kanala zapamćen pri otvaranju plejera.
     * Tako se redosled ne menja pod prstima dok se kanali gledaju.
     */
    private var groupIds: List<Long>? = null

    private var started = false
    private var numberJob: Job? = null

    /** Koliko puta zaredom je plejer morao da pobegne sa izvora iz neke liste, po listi. */
    private val sourceFailures = HashMap<Long, Int>()

    private val _sourceNotice = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Naziv liste na čiji je izvor plejer sam prešao jer se prethodni nije javio. */
    val sourceNotice: SharedFlow<String> = _sourceNotice.asSharedFlow()

    init {
        // Tajmer gašenja je istekao: kanal se pauzira, pa uređaj posle sam ugasi ekran.
        viewModelScope.launch { sleepTimer.expired.collect { engine.pause() } }
        watchPlaylistRefresh()
        watchSourceFailure()
    }

    /**
     * Kad se izvor kanala ne javi ni posle ponovnih pokušaja, a isti kanal postoji u spojenoj listi,
     * plejer sam prelazi na sledeći izvor. Kad izvor proradi, njegova lista više nije sumnjiva.
     */
    private fun watchSourceFailure() {
        viewModelScope.launch {
            engine.state.map { it.status }.distinctUntilChanged().collect { status ->
                when (status) {
                    PlaybackStatus.FAILED -> failover()
                    PlaybackStatus.PLAYING -> currentSource()?.let { sourceFailures.remove(it.playlistId) }
                    else -> Unit
                }
            }
        }
    }

    private fun currentSource(): ChannelSource? = _ui.value.let { state -> state.sources.firstOrNull { it.channelId == state.sourceId } }

    private suspend fun failover() {
        val state = _ui.value
        val current = state.sourceId ?: return
        var failed = state.failedSources + current
        if (state.sources.size > 1) {
            currentSource()?.let { sourceFailures[it.playlistId] = (sourceFailures[it.playlistId] ?: 0) + 1 }
        }
        for (candidate in state.sources) {
            if (candidate.channelId in failed) continue
            val source = repository.streamSource(candidate.channelId)
            if (source == null) {
                failed = failed + candidate.channelId
                continue
            }
            // Kanal je u međuvremenu promenjen: novi kanal ima svoje izvore.
            if (_ui.value.channel?.id != state.channel?.id) return
            _ui.update { it.copy(sourceId = candidate.channelId, failedSources = failed) }
            engine.play(source)
            _sourceNotice.tryEmit(candidate.playlistName)
            return
        }
        _ui.update { if (it.channel?.id == state.channel?.id) it.copy(failedSources = failed) else it }
    }

    /** Izvor koji je korisnik sam izabrao u panelu „Izvori”. */
    fun selectSource(channelId: Long) {
        val picked = _ui.value.sources.firstOrNull { it.channelId == channelId } ?: return
        viewModelScope.launch {
            val source = repository.streamSource(channelId) ?: return@launch
            sourceFailures.remove(picked.playlistId)
            _ui.update { it.copy(sourceId = channelId, failedSources = it.failedSources - channelId) }
            engine.play(source)
        }
    }

    /** Lista je osvežena dok se kanal gleda: kanal je dobio nov ID, pa se traži ponovo. Slika ne staje. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun watchPlaylistRefresh() {
        viewModelScope.launch {
            _ui.map { it.channel }
                .distinctUntilChangedBy { it?.id }
                .flatMapLatest { channel ->
                    if (channel == null) emptyFlow<Channel>() else repository.observeGone(channel.id).map { channel }
                }
                .collect { stale -> relink(stale) }
        }
    }

    /**
     * Zamenjuje kanal koji se gleda istim kanalom iz osvežene liste. Bez toga omiljeni, vodič, snimanje,
     * spisak kanala i prelazak na sledeći kanal ne bi radili do izlaska iz plejera.
     */
    private suspend fun relink(stale: Channel) {
        val fresh = repository.refreshed(stale) ?: return
        if (fresh.id == stale.id) return
        val oldCategory = _ui.value.categoryId
        // I kategorije su dobile nov ID; posebne grupe (omiljeni, nedavno) i „svi kanali” ostaju iste.
        val categoryId = if (oldCategory == null || oldCategory == ChannelGroups.ALL || ChannelGroups.isVirtual(oldCategory)) {
            oldCategory
        } else {
            fresh.categoryId
        }
        if (groupIds != null && categoryId != null) groupIds = repository.groupChannelIds(fresh.playlistId, categoryId)
        if (_ui.value.channel?.id != stale.id) return
        _ui.update { it.copy(categoryId = categoryId) }
        val position = positionOf(fresh)
        _ui.update { if (it.channel?.id == stale.id) it.copy(channel = fresh, position = position) else it }
        // I izvori kanala su dobili nove ID-jeve; ostaje izvor iz iste liste koji se i do sada gledao.
        val sources = repository.sources(fresh).filter { it.channelId == fresh.id || !locks.isChannelLocked(it.channelId) }
        _ui.update { state ->
            if (state.channel?.id != fresh.id) return@update state
            val playing = state.sources.firstOrNull { it.channelId == state.sourceId }?.playlistId
            state.copy(
                sources = sources,
                sourceId = sources.firstOrNull { it.playlistId == playing }?.channelId ?: fresh.id,
                failedSources = emptySet(),
            )
        }
        listScope.value = ListScope(fresh.playlistId, categoryId, position)
    }

    /** Poziva se jednom, pri otvaranju plejera. */
    fun start(channelId: Long, categoryId: Long?) {
        if (started) return
        started = true
        _ui.update { it.copy(categoryId = categoryId) }
        viewModelScope.launch {
            val channel = repository.getChannel(channelId) ?: return@launch
            if (ChannelGroups.isVirtual(categoryId) && categoryId != null) {
                groupIds = repository.groupChannelIds(channel.playlistId, categoryId)
            }
            val position = positionOf(channel)
            listScope.value = ListScope(channel.playlistId, categoryId, position)
            show(channel)
        }
    }

    /**
     * Kanal izabran u spisku preko slike. U spiskovima kao što su „Svi kanali” i omiljeni ima i kanala
     * iz zaključanih kategorija; oni se odavde ne puštaju (roditeljska zaštita), samo se javi poruka.
     */
    fun playChannel(channel: Channel) {
        viewModelScope.launch {
            if (channel.id != _ui.value.channel?.id && locks.isChannelLocked(channel.id)) {
                _recordingNotice.tryEmit(R.string.pin_locked_title)
            } else {
                show(channel)
            }
        }
    }

    fun next() = step { neighbor(it, 1) }

    fun previous() = step { neighbor(it, -1) }

    /** Vraća na kanal koji se gledao pre ovog; dva kanala se tako smenjuju jednim dugmetom. */
    fun playLastChannel() {
        val remembered = _ui.value.lastChannel ?: return
        viewModelScope.launch {
            // Ako je lista u međuvremenu osvežena, kanal ima nov ID.
            val last = repository.refreshed(remembered) ?: return@launch
            if (!locks.isChannelLocked(last.id)) show(last)
        }
    }

    /**
     * Počinje snimanje kanala koji se gleda, ili ga zaustavlja ako se već snima. [stopAtMs] je vreme
     * kad snimanje samo staje; null = dok ga korisnik ne zaustavi.
     */
    fun toggleRecording(stopAtMs: Long? = null) {
        val channel = _ui.value.channel ?: return
        if (isRecording.value) {
            recordings.stopChannel(channel)
            _recordingNotice.tryEmit(R.string.recording_stopped)
            return
        }
        viewModelScope.launch {
            val error = recordings.start(channel.id, nowNext.value.now?.title, stopAtMs)
            _recordingNotice.tryEmit(
                when (error) {
                    // Snimanje ide preko posebne veze: sa nalogom od jednog strima smeta gledanju.
                    null -> if (singleStreamAccount()) R.string.recording_started_single_stream else R.string.recording_started
                    RecordingStartError.NO_SPACE -> R.string.recording_no_space
                    RecordingStartError.NO_STORAGE -> R.string.recording_no_storage
                    RecordingStartError.NO_SOURCE -> R.string.recording_failed
                },
            )
        }
    }

    /** Da li nalog aktivne liste dozvoljava samo jedan strim istovremeno. Kad se ne zna, vraća false. */
    private suspend fun singleStreamAccount(): Boolean = withTimeoutOrNull(SINGLE_STREAM_CHECK_MS) {
        repository.activePlaylistId.first()?.let { playlists.maxConnections(it) } == 1
    } ?: false

    fun toggleFavorite() {
        val channel = _ui.value.channel ?: return
        viewModelScope.launch { repository.toggleFavorite(channel.id) }
    }

    fun togglePlayPause() = engine.togglePlayPause()

    /** Kanal koji se gleda, u obliku za slanje na TV (cast). Null ako strim ne može da se sastavi. */
    suspend fun castMedia(): CastMedia? {
        val channel = _ui.value.channel ?: return null
        val source = repository.streamSource(_ui.value.sourceId ?: channel.id) ?: return null
        return CastMedia(
            title = channel.name,
            urls = source.urls,
            live = true,
            channelId = channel.id,
            categoryId = _ui.value.categoryId,
            userAgent = source.userAgent,
            referrer = source.referrer,
        )
    }

    fun retry() {
        val channel = _ui.value.channel ?: return
        playChannel(channel)
    }

    fun cycleAspectMode() =
        setAspectMode(AspectMode.entries[(_ui.value.aspectMode.ordinal + 1) % AspectMode.entries.size])

    fun setAspectMode(mode: AspectMode) = _ui.update { it.copy(aspectMode = mode) }

    /** Prelazi na drugi dekoder odmah, bez izlaska iz kanala. Izbor se pamti kao u podešavanjima. */
    fun setDecoder(mode: DecoderMode) {
        if (_ui.value.decoder == mode) return
        _ui.update { it.copy(decoder = mode) }
        engine.setDecoder(mode)
        viewModelScope.launch { settings.setPlayerDecoder(mode) }
    }

    /** Izbor u plejeru se pamti kao podrazumevani jezik, da sledeći kanal ili film krene isto. */
    fun selectAudio(option: com.bane23.iptvplayer.player.TrackOption) {
        engine.selectAudio(option)
        option.language?.let { language -> viewModelScope.launch { settings.setAudioLanguage(language) } }
    }

    fun selectText(option: com.bane23.iptvplayer.player.TrackOption?) {
        engine.selectText(option)
        if (option == null) {
            viewModelScope.launch { settings.setSubtitleLanguage("") }
        } else {
            option.language?.let { language -> viewModelScope.launch { settings.setSubtitleLanguage(language) } }
        }
    }

    /** Cifra sa daljinskog: brojevi se skupljaju, pa se posle kratke pauze prelazi na taj kanal. */
    fun typeDigit(digit: Int) {
        val typed = (_ui.value.typedNumber + digit).takeLast(MAX_NUMBER_DIGITS)
        _ui.update { it.copy(typedNumber = typed) }
        numberJob?.cancel()
        numberJob = viewModelScope.launch {
            delay(NUMBER_ENTRY_DELAY_MS)
            val number = typed.toIntOrNull()
            val current = _ui.value.channel
            _ui.update { it.copy(typedNumber = "") }
            if (number != null && current != null) {
                // Brojem se ne može ući u kanal iz zaključane kategorije.
                repository.byNumber(current.playlistId, number)?.takeUnless { locks.isChannelLocked(it.id) }?.let { show(it) }
            }
        }
    }

    /** Aplikacija je ponovo vidljiva. */
    fun onForeground() = engine.restore()

    /**
     * Aplikacija više nije vidljiva: strim se zaustavlja da ne troši mrežu u pozadini, osim kad
     * telefon nastavlja da pušta u pozadini ([keepPlaying]).
     */
    fun onBackground(keepPlaying: Boolean = false) {
        if (!keepPlaying) engine.stop()
    }

    private fun step(pick: suspend (Channel) -> Channel?) {
        val current = _ui.value.channel ?: return
        viewModelScope.launch { pick(current)?.let { show(it) } }
    }

    private suspend fun positionOf(channel: Channel): Int {
        val ids = groupIds ?: return repository.positionOf(channel, _ui.value.categoryId)
        return ids.indexOf(channel.id).coerceAtLeast(0)
    }

    /** Kao [rawNeighbor], ali preskače kanale iz zaključanih kategorija (roditeljska zaštita). */
    private suspend fun neighbor(channel: Channel, delta: Int): Channel? {
        var candidate = rawNeighbor(channel, delta)
        var skipped = 0
        while (candidate != null && candidate.id != channel.id && locks.isChannelLocked(candidate.id)) {
            if (++skipped > MAX_LOCKED_SKIP) return null
            candidate = rawNeighbor(candidate, delta)
        }
        return candidate
    }

    /** Sledeći ([delta] = 1) ili prethodni (-1) kanal u trenutnoj kategoriji ili grupi, ukrug. */
    private suspend fun rawNeighbor(channel: Channel, delta: Int): Channel? {
        val ids = groupIds
        if (ids == null) {
            val categoryId = _ui.value.categoryId
            return if (delta > 0) repository.next(channel, categoryId) else repository.previous(channel, categoryId)
        }
        if (ids.isEmpty()) return null
        val index = ids.indexOf(channel.id)
        return repository.getChannel(ids[(index + delta).mod(ids.size)])
    }

    private suspend fun show(channel: Channel) {
        val position = positionOf(channel)
        // Izvori iz zaključanih kategorija se ne nude (roditeljska zaštita).
        val sources = repository.sources(channel).filter { it.channelId == channel.id || !locks.isChannelLocked(it.channelId) }
        // Lista sa koje je plejer već više puta morao da pobegne preskače se, da se ne čeka na nju kod svakog kanala.
        val start = sources.firstOrNull { (sourceFailures[it.playlistId] ?: 0) < SKIP_SOURCE_AFTER } ?: sources.firstOrNull()
        val sourceId = start?.channelId ?: channel.id
        _ui.update {
            val before = it.channel
            it.copy(
                channel = channel,
                position = position,
                sourceMissing = false,
                lastChannel = if (before != null && before.id != channel.id) before else it.lastChannel,
                sources = sources,
                sourceId = sourceId,
                failedSources = emptySet(),
            )
        }
        val source = repository.streamSource(sourceId)
        if (source == null) {
            engine.stop()
            _ui.update { it.copy(sourceMissing = true) }
        } else {
            engine.play(source)
            repository.markWatched(channel.id)
        }
    }

    override fun onCleared() {
        engine.release()
    }

    private companion object {
        const val MAX_LOCKED_SKIP = 300

        /** Posle ovoliko bežanja zaredom sa izvora iste liste, kanali kreću odmah sa sledećeg izvora. */
        const val SKIP_SOURCE_AFTER = 2
        const val MAX_NUMBER_DIGITS = 5
        const val NUMBER_ENTRY_DELAY_MS = 1_500L
        const val NOW_NEXT_REFRESH_MS = 30_000L
        const val SINGLE_STREAM_CHECK_MS = 4_000L
    }
}
