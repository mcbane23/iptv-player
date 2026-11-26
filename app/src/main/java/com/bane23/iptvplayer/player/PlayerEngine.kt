package com.bane23.iptvplayer.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.bane23.iptvplayer.domain.StreamSource
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlaybackStatus { IDLE, LOADING, PLAYING, PAUSED, RETRYING, FAILED }

/** Zašto strim ne može da se pusti, u obliku koji UI prevodi u poruku. */
enum class PlaybackFailure { NETWORK, NOT_FOUND, FORBIDDEN, UNSUPPORTED, UNKNOWN }

/** Greške dekodera posle kojih se proverava da li uređaj uopšte ume da pusti taj snimak. */
private val DECODER_ERRORS = setOf(
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
)

/** Jedan audio zapis ili titl koji korisnik može da izabere. */
data class TrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean,
    /** Jezik zapisa (npr. "sr"), ako ga strim navodi. */
    val language: String? = null,
)

/** Titl iz fajla na uređaju (preuzet sa interneta), koji se dodaje uz strim. Format je SubRip (.srt). */
data class ExternalSubtitle(
    /** Jedinstvena oznaka, po kojoj se titl prepoznaje među zapisima. */
    val id: String,
    val uri: String,
    val language: String?,
    val label: String,
)

/** Tehnički podaci o slici koja se trenutno pušta. */
data class VideoInfo(
    /** Naziv dekodera slike, ili null dok nije pokrenut. */
    val decoder: String? = null,
    /** Dekoder radi softverski (na procesoru), a ne preko hardvera uređaja. */
    val software: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    /** Koliko je slika ispušteno od puštanja, jer uređaj nije stigao da ih prikaže. */
    val droppedFrames: Int = 0,
    /** Koliko puta je slika toliko zaostala za zvukom da je plejer preskočio do sledeće ključne slike. */
    val keyframeSkips: Int = 0,
    /** Koliko puta je zvuk ostao bez podataka. */
    val audioUnderruns: Int = 0,
    /** Koliko puta je gledanje stalo da se sadržaj učita (posle prvog pokretanja). */
    val rebuffers: Int = 0,
    /** Vrsta fajla ili strima (npr. "matroska", "mp4", "mp2t"), ako je poznata. */
    val container: String? = null,
    /** Dekoder radi u tunel režimu (uređaj sam usklađuje sliku sa zvukom). */
    val tunneled: Boolean = false,
    /** Broj sličica u sekundi koji strim javlja; 0 kad se ne zna. */
    val frameRate: Float = 0f,
    /** Broj sličica u sekundi izmeren iz prikazanih slika, za strim koji ga ne javlja; 0 dok se ne izmeri. */
    val measuredFrameRate: Float = 0f,
    /** Kako se sadržaj premotava; vidi [SeekKind]. */
    val seek: SeekKind = SeekKind.NORMAL,
    /** Koliko puta je plejer sam pokrenuo film ili epizodu koja je predugo stajala na učitavanju. */
    val stallRecoveries: Int = 0,
)

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val failure: PlaybackFailure? = null,
    /** Tehnički opis poslednje greške, za prikaz ispod poruke. */
    val failureDetail: String? = null,
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    val textEnabled: Boolean = false,
    /** Sadržaj je odgledan do kraja (samo za filmove i epizode). */
    val ended: Boolean = false,
    val video: VideoInfo = VideoInfo(),
)

/** Najveća brzina puštanja sa zvukom; iznad nje snimak ide bez zvuka. */
const val MAX_SPEED_WITH_SOUND = 2f

/**
 * Omotač oko ExoPlayer-a: puštanje strima, automatski ponovni pokušaj kad strim pukne,
 * izbor zvuka i titlova. Ne zna ništa o kanalima ni o UI-ju.
 */
@OptIn(UnstableApi::class)
class PlayerEngine(
    context: Context,
    private val scope: CoroutineScope,
    settings: PlayerSettings = PlayerSettings(),
    /**
     * Plejer je jedan od više njih na ekranu (prikaz više kanala): drži mali bafer, da nekoliko
     * plejera zajedno ne napuni memoriju uređaja.
     */
    private val shared: Boolean = false,
) {

    private val appContext = context.applicationContext

    /** Približno premotavanje fajlova bez indeksa; vidi [ApproxSeekExtractor]. */
    private val approxSeek = ApproxSeekState()
    private val loadedBytes = java.util.concurrent.atomic.AtomicLong()

    /** Koliko je bajtova ovaj plejer do sada preuzeo sa mreže; iz razlike u vremenu se meri protok strima. */
    val bytesLoaded: Long get() = loadedBytes.get()

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setTransferListener(RangeGuard(approxSeek) { loadedBytes.addAndGet(it.toLong()) })
        // IPTV serveri često preusmeravaju sa http na https i obrnuto.
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)

    /** Koji dekoder se bira pri sledećem pokretanju dekodera; menja se i u toku gledanja, vidi [setDecoder]. */
    @Volatile
    private var decoderMode = settings.decoder

    /** Izabrano izjednačavanje jačine zvuka; vidi [setLoudness]. */
    @Volatile
    private var loudness = settings.loudness.also { LoudnessNormalization.level = it }

    /** Da li je poslednji izabrani dekoder slike tražen za tunel režim; čita se kad dekoder krene. */
    @Volatile
    private var videoTunneled = false

    /** Brzina puštanja je drugačija od obične; tada zvuk ne sme da ide neraspakovan ni kroz tunel. */
    private var speedChanged = false

    /**
     * Sadržaj je pripremljen pauziran (povratak u aplikaciju, ponovni pokušaj tokom pauze), pa ide bez
     * tunela do sledeće pripreme. Neki dekoderi u tunelu ne umeju da zadrže sliku dok zvuk stoji, pa bi
     * slika krenula sama, bez zvuka, a plejer bi i dalje bio pauziran (XGIMI, Bane).
     */
    private var tunnelHeldForPause = false

    /** Zvuk koji se sada pušta ide neraspakovan ka TV-u ili pojačalu. */
    private var audioPassthrough = false

    /** Izlaz zvuka ovog plejera; postoji od trenutka kad se plejer napravi. */
    @Volatile
    private var audioGate: PassthroughGate? = null

    private val renderersFactory = object : DefaultRenderersFactory(appContext) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            // Višekanalni zvuk se spaja u stereo kad uređaj ne može da ga pusti (vidi [StereoDownmix]).
            // Posle spajanja u stereo se izjednačava jačina (vidi [LoudnessNormalizerProcessor]).
            .setAudioProcessors(arrayOf(StereoDownmixProcessor(), LoudnessNormalizerProcessor()))
            .build()
            .let { sink ->
                PassthroughGate(sink).also {
                    it.preferPcm = loudness != LoudnessLevel.OFF
                    audioGate = it
                }
            }
    }
        // Ako izabrani dekoder ne uspe da krene, plejer proba sledeći umesto da prijavi grešku.
        .setEnableDecoderFallback(true)
        .setMediaCodecSelector(
            MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                val decoders = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                if (MimeTypes.isVideo(mimeType)) videoTunneled = requiresTunnelingDecoder
                if (decoderMode == DecoderMode.SOFTWARE) decoders.sortedByDescending { it.softwareOnly } else decoders
            },
        )

    /**
     * Mnogi TV strimovi (MPEG-TS) retko šalju pune ključne slike (IDR), a redovno obične I slike.
     * Kad slika zaostane za zvukom, plejer preskače do sledeće ključne slike; sa ovim se i I slika
     * računa kao ključna, pa se slika vraća brže umesto da stoji dok zvuk ide.
     */
    private val extractorsFactory = ApproxSeekExtractorsFactory(
        DefaultExtractorsFactory().setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES),
        approxSeek,
    )

    val player: ExoPlayer = ExoPlayer.Builder(appContext, renderersFactory)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(appContext, httpFactory), extractorsFactory))
        .apply { (if (shared) sharedLoadControl() else loadControl(settings.buffer))?.let { setLoadControl(it) } }
        // Kad se slušalice odvoje (Bluetooth veza pukne ili se kabl izvuče), zvuk bi prešao na
        // zvučnik uređaja; plejer se tada sam pauzira.
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var source: StreamSource? = null
    private var urlIndex = 0
    private var attempts = 0

    /** Da li je trenutna adresa bar jednom uspešno krenula; ako nije, vredi probati sledeću. */
    private var playedCurrentUrl = false
    private var retryJob: Job? = null

    /** Film ili epizoda (može da se premotava), za razliku od kanala uživo. */
    private var seekable = false

    /** Pozicija od koje sledeća priprema plejera treba da krene (samo kad je [seekable]). */
    private var resumePositionMs = 0L

    /**
     * Pozicija od koje je sadržaj trebalo da krene, dok plejer ne potvrdi da može da se premotava.
     * Fajl bez indeksa i bez trajanja postaje premotljiv tek pošto se malo učita; tada se skače ovde.
     */
    private var pendingResumeMs = 0L

    private val _seekLost = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Server nije dozvolio skok, pa je sadržaj krenuo od početka i više ne može da se premotava. */
    val seekLost: SharedFlow<Unit> = _seekLost.asSharedFlow()

    private val _stallSkipped = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    /** Film ili epizoda su predugo stajali na učitavanju, pa je plejer preskočio ovoliko milisekundi. */
    val stallSkipped: SharedFlow<Long> = _stallSkipped.asSharedFlow()

    private var stallJob: Job? = null

    /** Koji je po redu pokušaj oporavka od zastoja u toku; 0 dok sadržaj teče. */
    private var stallStep = 0

    /**
     * Titlovi iz fajla dodati uz trenutni sadržaj; ostaju i posle ponovnog pokušaja. Plejer za njih
     * ne zna: aplikacija ih sama čita i prikazuje preko slike ([subtitleText]), pa se sadržaj ne
     * priprema iznova kad se titl doda, zameni ili pomeri.
     */
    private var externalSubtitles: List<ExternalSubtitle> = emptyList()

    /** Pročitani titlovi iz fajla, po ID-ju titla. */
    private var externalCues: Map<String, SrtCues> = emptyMap()

    /** Titl iz fajla koji je sada uključen, ili null kad nije nijedan. */
    private var activeExternalId: String? = null

    /** Za koliko je korisnik pomerio titl iz fajla (pozitivno = titl izlazi kasnije). */
    private var subtitleOffsetMs = 0L

    private var subtitleLoadJob: Job? = null
    private var subtitleTextJob: Job? = null

    private val _subtitleText = MutableStateFlow<String?>(null)

    /** Tekst uključenog titla iz fajla za trenutak koji se gleda, ili null kad nema šta da se prikaže. */
    val subtitleText: StateFlow<String?> = _subtitleText.asStateFlow()

    val positionMs: Long get() = player.currentPosition.coerceAtLeast(0)

    /** Trajanje sadržaja, ili 0 dok nije poznato (i za kanale uživo). */
    val durationMs: Long get() = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                wasReady = true
                stallStep = 0
            } else if (playbackState == Player.STATE_BUFFERING && wasReady) {
                _state.update { it.copy(video = it.video.copy(rebuffers = it.video.rebuffers + 1)) }
            }
            if (playbackState == Player.STATE_ENDED && !seekable && source != null) {
                reconnectLive()
            } else {
                publishStatus()
            }
            watchStall()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            publishStatus()
            watchStall()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) = applyPendingResume()

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                attempts = 0
                playedCurrentUrl = true
            }
            watchVideoCounters(isPlaying)
            publishStatus()
        }

        override fun onTracksChanged(tracks: Tracks) = publishTracks(tracks)

        override fun onPlayerError(error: PlaybackException) = handleError(error)

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width == 0 || videoSize.height == 0) return
            _state.update { it.copy(video = it.video.copy(width = videoSize.width, height = videoSize.height)) }
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            _state.update {
                it.copy(
                    video = it.video.copy(
                        decoder = decoderName,
                        software = isSoftwareDecoder(decoderName),
                        tunneled = videoTunneled,
                    ),
                )
            }
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            if (format.frameRate > 0f) _state.update { it.copy(video = it.video.copy(frameRate = format.frameRate)) }
            // Veličina slike iz samog strima, pre nego što dekoder prikaže prvu sliku: poznata je i kad
            // plejer nema površinu za sliku (merenje kvaliteta kanala).
            if (format.width > 0 && format.height > 0) {
                _state.update { if (it.video.height > 0) it else it.copy(video = it.video.copy(width = format.width, height = format.height)) }
            }
            val container = format.containerMimeType?.substringAfter('/')?.removePrefix("x-") ?: return
            _state.update { it.copy(video = it.video.copy(container = container)) }
        }

        override fun onAudioTrackInitialized(
            eventTime: AnalyticsListener.EventTime,
            audioTrackConfig: AudioSink.AudioTrackConfig,
        ) {
            audioPassthrough = !Util.isEncodingLinearPcm(audioTrackConfig.encoding)
        }

        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long,
        ) {
            _state.update { it.copy(video = it.video.copy(audioUnderruns = it.video.audioUnderruns + 1)) }
        }
    }

    /** Da li je sadržaj već jednom krenuo; posle toga se svako novo učitavanje broji kao zastoj. */
    private var wasReady = false
    private var statsJob: Job? = null
    private val frameRateMeter = FrameRateMeter()

    /**
     * Brojači dekodera slike se čitaju jednom u sekundi; plejer ih sam javlja tek posle 50 ispuštenih slika.
     * Čitaju se samo dok slika ide: pauziran ili zaustavljen plejer ne budi procesor.
     */
    private fun watchVideoCounters(playing: Boolean) {
        statsJob?.cancel()
        statsJob = if (playing) {
            scope.launch {
                while (true) {
                    delay(STATS_INTERVAL_MS)
                    readVideoCounters()
                }
            }
        } else {
            // Poslednje stanje, da prikaz ne ostane sekundu iza.
            readVideoCounters()
            null
        }
    }

    private fun readVideoCounters() {
        val measured = frameRateMeter.frameRate()
        if (_state.value.video.measuredFrameRate != measured) {
            _state.update { it.copy(video = it.video.copy(measuredFrameRate = measured)) }
        }
        val seek = approxSeek.kind
        if (_state.value.video.seek != seek) _state.update { it.copy(video = it.video.copy(seek = seek)) }
        val counters = player.videoDecoderCounters ?: return
        counters.ensureUpdated()
        val dropped = counters.droppedBufferCount
        val skips = counters.droppedToKeyframeCount
        val video = _state.value.video
        if (video.droppedFrames != dropped || video.keyframeSkips != skips) {
            _state.update { it.copy(video = it.video.copy(droppedFrames = dropped, keyframeSkips = skips)) }
        }
    }

    init {
        // Preuzimanje na telefon staje dok plejer drži vezu sa provajderom; fajl sa uređaja je ne drži.
        scope.launch {
            state.map { it.status }.distinctUntilChanged().collect { status ->
                val busy = status != PlaybackStatus.IDLE && status != PlaybackStatus.FAILED
                val local = source?.urls?.firstOrNull()?.startsWith("file:") == true
                PlaybackActivity.set(this@PlayerEngine, busy && !local)
            }
        }
        // Izjednačavanje zvuka može da se promeni i u toku gledanja (brzi meni plejera).
        scope.launch { LoudnessNormalization.selected.collect(::setLoudness) }
        player.addListener(listener)
        player.addAnalyticsListener(analyticsListener)
        // U prikazu više kanala se broj sličica ne ispisuje, pa se tamo ni ne meri.
        if (!shared) {
            player.setVideoFrameMetadataListener { presentationTimeUs, _, _, _ -> frameRateMeter.onFrame(presentationTimeUs) }
        }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguages(*settings.audioLanguages.toTypedArray())
            .setPreferredTextLanguages(*settings.subtitleLanguages.toTypedArray())
            // Titl koji je u fajlu označen kao podrazumevan ne uključuje se sam: važe samo jezici iz podešavanja.
            .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        applyTunneling()
    }

    /**
     * Pušta strim. Kad je zadato [startPositionMs], sadržaj je film ili epizoda: kreće od te
     * pozicije, a posle prekida se nastavlja tamo gde je stao.
     */
    fun play(source: StreamSource, startPositionMs: Long? = null, subtitles: List<ExternalSubtitle>? = null) {
        retryJob?.cancel()
        stallJob?.cancel()
        stallStep = 0
        seekable = startPositionMs != null
        resumePositionMs = startPositionMs ?: 0
        val newSource = source != this.source
        if (newSource) {
            // Izbor zvuka i titla u plejeru važi samo za sadržaj koji se gledao; novi kreće po podešavanjima.
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }
        if (source != this.source) frameRateMeter.reset()
        this.source = source
        urlIndex = 0
        attempts = 0
        playedCurrentUrl = false
        _state.value = PlaybackState(status = PlaybackStatus.LOADING)
        if (subtitles != null || newSource) {
            // Titlovi zapamćeni za ovaj sadržaj: poslednji se odmah uključuje.
            val hadExternal = activeExternalId != null
            externalSubtitles = subtitles.orEmpty()
            externalCues = emptyMap()
            subtitleOffsetMs = 0
            val active = externalSubtitles.lastOrNull()
            if (active == null && hadExternal && !newSource) {
                // Titl iz fajla je isključio titlove iz samog sadržaja; bez njega opet važe.
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
            }
            showExternal(active)
        }
        prepareCurrent(autoPlay = true)
    }

    /** Ručni ponovni pokušaj posle greške. */
    fun retry() {
        val current = source ?: return
        play(current, if (seekable) resumePositionMs else null)
    }

    /**
     * Sadržaj je učitan, a ne može da se premotava (npr. fajl bez indeksa ili server koji ga šalje
     * samo od početka). Premotavanje bi takav sadržaj vratilo na početak, pa se i ne pokušava.
     */
    val seekBlocked: Boolean get() = seekable && wasReady && !player.isCurrentMediaItemSeekable

    fun seekTo(positionMs: Long) {
        if (!seekable || seekBlocked) return
        pendingResumeMs = 0
        val duration = durationMs
        val target = if (duration > 0) positionMs.coerceIn(0, duration) else positionMs.coerceAtLeast(0)
        resumePositionMs = target
        player.seekTo(target)
    }

    fun seekBy(deltaMs: Long) = seekTo(positionMs + deltaMs)

    /** Sadržaj je postao premotljiv: ako je trebalo da krene sa zapamćenog mesta, sada skače tamo. */
    private fun applyPendingResume() {
        val target = pendingResumeMs
        if (target <= 0 || !player.isCurrentMediaItemSeekable) return
        pendingResumeMs = 0
        if (player.currentPosition < target - RESUME_SLACK_MS) player.seekTo(target)
    }

    fun togglePlayPause() {
        if (_state.value.status == PlaybackStatus.FAILED) {
            retry()
        } else if (player.playWhenReady) {
            player.pause()
        } else {
            resume()
        }
    }

    /** Nastavlja gledanje. Strim uživo se ponovo priprema, da bi krenuo od trenutka emitovanja. */
    fun resume() {
        if (source == null || _state.value.status == PlaybackStatus.FAILED) return
        if (player.playbackState == Player.STATE_IDLE) {
            retryJob?.cancel()
            prepareCurrent(autoPlay = true)
        } else {
            if (player.isCurrentMediaItemLive) player.seekToDefaultPosition()
            player.play()
        }
    }

    fun pause() {
        player.pause()
    }

    /**
     * Brzina puštanja (1 = obična). Ostaje i kad se sadržaj pripremi iznova ili pređe na sledeću epizodu.
     * Iznad [MAX_SPEED_WITH_SOUND] snimak ide bez zvuka, jer se tada više ništa ne razume.
     *
     * Zvuk koji ide neraspakovan ka TV-u ili pojačalu, kao i tunel režim, uvek idu običnom brzinom.
     * Zato se oba isključuju dok je brzina drugačija od obične; ako je takav zvuk baš sada u toku,
     * sadržaj se priprema iznova i nastavlja sa istog mesta.
     */
    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        player.volume = if (speed > MAX_SPEED_WITH_SOUND) 0f else 1f
        val changed = speed != 1f
        if (changed == speedChanged) return
        speedChanged = changed
        applyTunneling()
        val gate = audioGate ?: return
        gate.pcmOnly = changed
        val reload = if (changed) audioPassthrough else gate.refused
        // Sadržaj koji ne može da se premotava bi krenuo od početka; bolje da nastavi kako ide.
        if (reload && seekable && source != null && !seekBlocked) prepareAgain()
    }

    /**
     * Aplikacija je ponovo vidljiva posle [stop]. Gledanje se nastavlja samo ako je išlo kad je
     * aplikacija otišla u pozadinu; sadržaj koji je korisnik pauzirao se priprema, ali ostaje pauziran.
     */
    fun restore() {
        // Plejer koji je nastavio da pušta u pozadini nije ni stajao.
        if (player.playbackState != Player.STATE_IDLE) return
        if (player.playWhenReady) {
            resume()
        } else if (source != null && _state.value.status != PlaybackStatus.FAILED && player.playbackState == Player.STATE_IDLE) {
            _state.update { it.copy(status = PlaybackStatus.LOADING) }
            prepareCurrent()
        }
    }

    /** Zaustavlja puštanje i oslobađa vezu sa serverom (npr. kad aplikacija ode u pozadinu). */
    fun stop() {
        retryJob?.cancel()
        stallJob?.cancel()
        stallStep = 0
        rememberPosition()
        player.stop()
    }

    fun selectAudio(option: TrackOption) = selectTrack(C.TRACK_TYPE_AUDIO, option)

    fun selectText(option: TrackOption?) {
        when {
            option == null -> {
                showExternal(null)
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }

            option.groupIndex == EXTERNAL_GROUP -> showExternal(externalSubtitles.getOrNull(option.trackIndex))

            else -> {
                showExternal(null)
                selectTrack(C.TRACK_TYPE_TEXT, option)
            }
        }
    }

    /** Dodaje titl iz fajla i uključuje ga. Slika i zvuk idu dalje bez prekida. */
    fun addSubtitle(subtitle: ExternalSubtitle) {
        if (source == null) return
        externalSubtitles = externalSubtitles.filterNot { it.id == subtitle.id } + subtitle
        externalCues = externalCues - subtitle.id
        subtitleOffsetMs = 0
        showExternal(subtitle)
    }

    /** Pomera titl iz fajla za [offsetMs] (pozitivno = titl izlazi kasnije). Važi odmah. */
    fun setSubtitleOffset(offsetMs: Long) {
        subtitleOffsetMs = offsetMs
    }

    /**
     * Uključuje titl iz fajla ([subtitle]) ili ga isključuje (null). Dok je uključen, titlovi iz
     * samog sadržaja su isključeni, da se ne prikazuju dva titla odjednom.
     */
    private fun showExternal(subtitle: ExternalSubtitle?) {
        subtitleLoadJob?.cancel()
        activeExternalId = subtitle?.id
        if (subtitle == null) {
            subtitleTextJob?.cancel()
            subtitleTextJob = null
            _subtitleText.value = null
        } else {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            if (subtitle.id !in externalCues) {
                subtitleLoadJob = scope.launch {
                    val cues = withContext(Dispatchers.IO) {
                        try {
                            val path = Uri.parse(subtitle.uri).path ?: throw IOException("No path")
                            SrtCues.parse(SrtCues.decode(File(path).readBytes()))
                        } catch (_: IOException) {
                            // Fajl ne može da se pročita: titl ostaje u spisku, ali nema šta da prikaže.
                            SrtCues(emptyList())
                        }
                    }
                    externalCues = externalCues + (subtitle.id to cues)
                }
            }
            if (subtitleTextJob?.isActive != true) {
                subtitleTextJob = scope.launch {
                    while (true) {
                        _subtitleText.value = activeExternalId
                            ?.let { externalCues[it] }
                            ?.textAt(player.currentPosition - subtitleOffsetMs)
                        delay(SUBTITLE_TICK_MS)
                    }
                }
            }
        }
        publishTracks(player.currentTracks)
    }

    /**
     * Menja dekoder u toku gledanja. Sadržaj se priprema iznova, da se dekoder ponovo izabere; film
     * ili epizoda nastavljaju sa istog mesta.
     */
    fun setDecoder(mode: DecoderMode) {
        if (decoderMode == mode) return
        decoderMode = mode
        applyTunneling()
        // Sadržaj koji ne može da se premotava bi krenuo od početka; novi dekoder važi od sledećeg puštanja.
        if (source == null || seekBlocked) return
        prepareAgain()
    }

    /**
     * Prati izabrano izjednačavanje jačine zvuka ([LoudnessNormalization]). Raspakovan zvuk se menja
     * odmah; ako zvuk baš sada ide neraspakovan ka TV-u ili pojačalu, a izjednačavanje se uključi,
     * sadržaj se priprema iznova da bi se zvuk raspakovao. Pri isključivanju takav zvuk ostaje raspakovan do sledećeg puštanja.
     */
    private fun setLoudness(level: LoudnessLevel) {
        if (loudness == level) return
        val wasOff = loudness == LoudnessLevel.OFF
        loudness = level
        val gate = audioGate ?: return
        gate.preferPcm = level != LoudnessLevel.OFF
        if (wasOff && audioPassthrough && source != null && !seekBlocked) prepareAgain()
    }

    /** Priprema trenutni sadržaj iznova, da se dekoderi ponovo izaberu; film ili epizoda nastavljaju sa istog mesta. */
    private fun prepareAgain() {
        retryJob?.cancel()
        rememberPosition()
        attempts = 0
        _state.update { it.copy(status = PlaybackStatus.LOADING, failure = null, failureDetail = null, video = VideoInfo()) }
        // Zaustavljanje oslobađa trenutni dekoder; bez toga bi plejer nastavio sa istim.
        player.stop()
        prepareCurrent()
    }

    /**
     * Uključuje tunel režim kad je izabran. U prikazu više kanala ostaje isključen, jer uređaji
     * podržavaju samo jedan takav dekoder.
     */
    private fun applyTunneling() {
        val selector = player.trackSelector as? DefaultTrackSelector ?: return
        selector.setParameters(
            selector.buildUponParameters().setTunnelingEnabled(
                !shared && !speedChanged && !tunnelHeldForPause && decoderMode == DecoderMode.TUNNEL,
            ),
        )
    }

    fun release() {
        PlaybackActivity.set(this, false)
        retryJob?.cancel()
        statsJob?.cancel()
        stallJob?.cancel()
        subtitleLoadJob?.cancel()
        subtitleTextJob?.cancel()
        player.removeListener(listener)
        player.removeAnalyticsListener(analyticsListener)
        player.release()
    }

    private fun selectTrack(type: Int, option: TrackOption) {
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
    }

    /**
     * Priprema trenutnu adresu. Bez [autoPlay] se čuva ono što je korisnik izabrao: pauziran sadržaj
     * ostaje pauziran i posle ponovnog pokušaja ili promene dekodera.
     */
    private fun prepareCurrent(autoPlay: Boolean = false) {
        wasReady = false
        val current = source ?: return
        val pausedPrepare = !autoPlay && !player.playWhenReady
        if (pausedPrepare != tunnelHeldForPause) {
            tunnelHeldForPause = pausedPrepare
            applyTunneling()
        }
        val url = current.urls.getOrNull(urlIndex) ?: return
        val headers = buildMap {
            current.referrer?.let { put("Referer", it) }
        }
        httpFactory.setUserAgent(current.userAgent)
        httpFactory.setDefaultRequestProperties(headers)
        val item = MediaItem.fromUri(url)
        audioGate?.refused = false
        pendingResumeMs = if (seekable) resumePositionMs else 0
        if (seekable) {
            player.setMediaItem(item, resumePositionMs)
        } else {
            player.setMediaItem(item)
        }
        player.prepare()
        if (autoPlay) player.playWhenReady = true
    }

    private fun handleError(error: PlaybackException) {
        val current = source ?: return
        // Zaostajanje za strimom uživo: samo skoči na trenutak emitovanja.
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        if (approxSeek.kind == SeekKind.APPROXIMATE && rangeRefused(error)) {
            // Server ne da fajl od sredine: sadržaj kreće od početka i dalje se vodi kao nepremotljiv.
            current.urls.getOrNull(urlIndex)?.let { approxSeek.refused.add(Uri.parse(it).toString()) }
            resumePositionMs = 0
            _seekLost.tryEmit(Unit)
            scheduleRetry(delayMs = 0)
            return
        }
        cannotDecode(error)?.let { format ->
            // Uređaj nema dekoder za ovaj snimak (npr. 4K HEVC na slabijem TV-u): ponovni pokušaji ne pomažu,
            // pa se odmah javlja da format nije podržan, umesto „Veza prekinuta” (Bane).
            _state.update {
                it.copy(
                    status = PlaybackStatus.FAILED,
                    failure = PlaybackFailure.UNSUPPORTED,
                    failureDetail = listOf(format, describe(error)).joinToString(" · "),
                )
            }
            return
        }
        if (needsStereo(error)) {
            // Uređaj ne može da otvori izlaz za 5.1 (ili više kanala): isti sadržaj odmah iznova, spojen u stereo.
            StereoDownmix.enabled = true
            rememberPosition()
            scheduleRetry(delayMs = 0)
            return
        }
        rememberPosition()
        attempts++
        val hasAnotherUrl = urlIndex + 1 < current.urls.size
        when {
            // Format ili adresa ne valja: odmah probaj sledeću adresu (npr. .m3u8 posle .ts).
            hasAnotherUrl && !playedCurrentUrl -> {
                urlIndex++
                attempts = 0
                scheduleRetry(delayMs = 0)
            }

            attempts <= MAX_ATTEMPTS -> scheduleRetry(delayMs = RETRY_DELAY_MS * attempts)

            else -> _state.update {
                it.copy(
                    status = PlaybackStatus.FAILED,
                    failure = classify(error),
                    failureDetail = describe(error),
                )
            }
        }
    }

    /**
     * Server je uredno zatvorio vezu kanala uživo (npr. zbog ograničenja broja veza na nalogu).
     * Plejer to vidi kao kraj sadržaja i stao bi na poslednjoj slici, pa se kanal pušta iznova.
     */
    private fun reconnectLive() {
        attempts++
        if (attempts <= MAX_ATTEMPTS) {
            scheduleRetry(delayMs = RETRY_DELAY_MS * attempts)
        } else {
            _state.update {
                it.copy(
                    status = PlaybackStatus.FAILED,
                    failure = PlaybackFailure.NETWORK,
                    failureDetail = "Stream ended",
                )
            }
        }
    }

    /**
     * Film ili epizoda stoje na učitavanju iako je korisnik pustio sadržaj. Neki serveri u toku
     * gledanja prestanu da šalju podatke, a veza ostane otvorena, pa plejer čeka bez kraja (Bane:
     * pomagao je samo skok udesno). Posle [STALL_TIMEOUT_MS] se zato sadržaj sam priprema iznova.
     */
    private fun watchStall() {
        val stalled = seekable && player.playWhenReady && player.playbackState == Player.STATE_BUFFERING &&
            (wasReady || stallStep > 0)
        if (!stalled) {
            stallJob?.cancel()
            stallJob = null
            return
        }
        if (stallJob?.isActive == true) return
        stallJob = scope.launch {
            delay(STALL_TIMEOUT_MS)
            recoverFromStall()
        }
    }

    /**
     * Prvi pokušaj priprema sadržaj iznova sa istog mesta: nova veza sa serverom, bez propuštenog kadra.
     * Ako ni to ne pomogne, svaki sledeći pokušaj preskoči [STALL_SKIP_MS] napred, kao ručni skok udesno.
     * Posle [MAX_STALL_STEPS] pokušaja plejer odustaje i ostaje na učitavanju.
     */
    private fun recoverFromStall() {
        if (source == null || stallStep >= MAX_STALL_STEPS) return
        // Sadržaj koji ne može da se premotava bi krenuo od početka.
        if (stallStep == 0 && !player.isCurrentMediaItemSeekable) return
        if (stallStep == 0) rememberPosition()
        stallStep++
        if (stallStep > 1) {
            val duration = durationMs
            val target = resumePositionMs + STALL_SKIP_MS
            resumePositionMs = if (duration > 0) target.coerceAtMost(duration - STALL_SKIP_MS) else target
            _stallSkipped.tryEmit(STALL_SKIP_MS)
        }
        _state.update { it.copy(video = it.video.copy(stallRecoveries = it.video.stallRecoveries + 1)) }
        retryJob?.cancel()
        // Zaustavljanje zatvara zaglavljenu vezu (server dozvoljava samo jednu), pa tek onda ide nova.
        player.stop()
        prepareCurrent()
    }

    /** Pamti dokle se stiglo, da se posle prekida ili povratka u aplikaciju nastavi odatle. */
    private fun rememberPosition() {
        // Dok skok na zapamćeno mesto još čeka, trenutna pozicija nije ono odakle treba nastaviti.
        if (pendingResumeMs > 0) return
        if (seekable && player.currentPosition > 0) {
            resumePositionMs = player.currentPosition
        }
    }

    private fun scheduleRetry(delayMs: Long) {
        _state.update { it.copy(status = PlaybackStatus.RETRYING) }
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delayMs)
            prepareCurrent()
        }
    }

    private fun publishStatus() {
        val current = _state.value.status
        if (current == PlaybackStatus.FAILED) return
        val status = when {
            player.playbackState == Player.STATE_READY && player.playWhenReady -> PlaybackStatus.PLAYING
            player.playbackState == Player.STATE_READY -> PlaybackStatus.PAUSED
            player.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.LOADING
            // Između pokušaja plejer je u stanju IDLE; to nije kraj, nego čekanje.
            current == PlaybackStatus.RETRYING || current == PlaybackStatus.LOADING -> current
            player.playbackState == Player.STATE_ENDED -> PlaybackStatus.PAUSED
            else -> PlaybackStatus.IDLE
        }
        val ended = seekable && player.playbackState == Player.STATE_ENDED
        _state.update { it.copy(status = status, failure = null, failureDetail = null, ended = ended) }
    }

    private fun publishTracks(tracks: Tracks) {
        val audio = ArrayList<TrackOption>()
        val text = ArrayList<TrackOption>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            val target = when (group.type) {
                C.TRACK_TYPE_AUDIO -> audio
                C.TRACK_TYPE_TEXT -> text
                else -> return@forEachIndexed
            }
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                val label = listOfNotNull(
                    format.label?.takeIf { it.isNotBlank() },
                    format.language?.takeIf { it.isNotBlank() && it != "und" },
                ).distinct().joinToString(" · ").ifEmpty { "${target.size + 1}" }
                val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
                target.add(TrackOption(groupIndex, trackIndex, label, group.isTrackSelected(trackIndex), language))
            }
        }
        // Titlovi iz fajla idu na kraj spiska, kao i dok ih je prijavljivao sam plejer.
        externalSubtitles.forEachIndexed { index, subtitle ->
            val language = subtitle.language?.takeIf { it.isNotBlank() && it != "und" }
            val label = listOfNotNull(subtitle.label.takeIf { it.isNotBlank() }, language)
                .distinct().joinToString(" · ").ifEmpty { "${text.size + 1}" }
            text.add(TrackOption(EXTERNAL_GROUP, index, label, subtitle.id == activeExternalId, language))
        }
        _state.update {
            it.copy(audioTracks = audio, textTracks = text, textEnabled = text.any { t -> t.selected })
        }
    }

    /** Server je na zahtev za deo fajla poslao ceo fajl ili ga odbio. */
    private fun rangeRefused(error: PlaybackException): Boolean {
        var cause: Throwable? = error
        repeat(MAX_CAUSE_DEPTH) {
            val current = cause ?: return false
            if (current is RangeIgnoredException) return true
            if (current is HttpDataSource.InvalidResponseCodeException && current.responseCode == 416) return true
            cause = current.cause
        }
        return false
    }

    /**
     * Opis snimka (npr. „video/hevc 3840×2160”) kad greška dekodera slike potiče od toga što nijedan dekoder
     * na uređaju ne prima taj format; inače null, pa se greška obrađuje kao i ostale (ponovni pokušaj).
     */
    private fun cannotDecode(error: PlaybackException): String? {
        if (error.errorCode !in DECODER_ERRORS) return null
        val format = (error as? ExoPlaybackException)?.rendererFormat ?: player.videoFormat ?: return null
        val mime = format.sampleMimeType ?: return null
        if (!MimeTypes.isVideo(mime)) return null
        val supported = try {
            MediaCodecUtil.getDecoderInfos(mime, false, false).any { it.isFormatSupported(appContext, format) }
        } catch (e: MediaCodecUtil.DecoderQueryException) {
            return null
        }
        if (supported) return null
        return if (format.width > 0 && format.height > 0) "$mime ${format.width}×${format.height}" else mime
    }

    private fun needsStereo(error: PlaybackException): Boolean {
        if (StereoDownmix.enabled || error.errorCode != PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED) return false
        val format = (error.cause as? AudioSink.InitializationException)?.format ?: player.audioFormat
        return (format?.channelCount ?: 0) > 2
    }

    private fun classify(error: PlaybackException): PlaybackFailure = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> PlaybackFailure.NETWORK

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            val code = (error.cause as? HttpDataSource.InvalidResponseCodeException)
                ?.responseCode
            when (code) {
                401, 403 -> PlaybackFailure.FORBIDDEN
                404 -> PlaybackFailure.NOT_FOUND
                else -> PlaybackFailure.NETWORK
            }
        }

        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackFailure.NOT_FOUND

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        -> PlaybackFailure.UNSUPPORTED

        else -> PlaybackFailure.UNKNOWN
    }

    /** Softverski dekoderi samog Androida; sve ostalo su dekoderi proizvođača uređaja. */
    private fun isSoftwareDecoder(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith("omx.google.") || lower.startsWith("c2.android.") || lower.contains(".sw.")
    }

    private fun describe(error: PlaybackException): String {
        val cause = error.cause
        val causeText = cause?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
        return listOfNotNull(error.errorCodeName, causeText).joinToString(" · ")
    }

    /** Bafer prema podešavanju; null znači podrazumevane vrednosti plejera. */
    private fun loadControl(buffer: BufferSize): DefaultLoadControl? = when (buffer) {
        BufferSize.NORMAL -> null
        BufferSize.SMALL -> DefaultLoadControl.Builder()
            .setBufferDurationsMs(8_000, 20_000, 1_000, 2_000)
            .build()

        BufferSize.LARGE -> DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 90_000, 4_000, 8_000)
            .build()
    }

    /** Mali bafer za prikaz više kanala: najviše oko 20 sekundi ili [SHARED_BUFFER_BYTES] po plejeru. */
    private fun sharedLoadControl(): DefaultLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(10_000, 20_000, 1_500, 3_000)
        .setTargetBufferBytes(SHARED_BUFFER_BYTES)
        .build()

    private companion object {
        const val SHARED_BUFFER_BYTES = 24 * 1024 * 1024
        const val STATS_INTERVAL_MS = 1_000L
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1_500L

        /** Koliko film ili epizoda smeju da stoje na učitavanju pre nego što ih plejer sam pokrene. */
        const val STALL_TIMEOUT_MS = 5_000L
        const val STALL_SKIP_MS = 2_000L
        const val MAX_STALL_STEPS = 4

        /** Koliko pozicija sme da bude ispred zapamćenog mesta, a da se na njega ipak ne skače. */
        const val RESUME_SLACK_MS = 5_000L
        const val MAX_CAUSE_DEPTH = 8

        /** Oznaka grupe za titlove iz fajla u [TrackOption]; redni broj zapisa je mesto u spisku titlova. */
        const val EXTERNAL_GROUP = -1

        /** Koliko često se proverava koji red titla iz fajla treba prikazati. */
        const val SUBTITLE_TICK_MS = 100L
    }
}
