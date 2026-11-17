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
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.bane23.iptvplayer.domain.StreamSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlaybackStatus { IDLE, LOADING, PLAYING, PAUSED, RETRYING, FAILED }

/** Zašto strim ne može da se pusti, u obliku koji UI prevodi u poruku. */
enum class PlaybackFailure { NETWORK, NOT_FOUND, FORBIDDEN, UNSUPPORTED, UNKNOWN }

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
    private val httpFactory = DefaultHttpDataSource.Factory()
        // IPTV serveri često preusmeravaju sa http na https i obrnuto.
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)

    /** Koji dekoder se bira pri sledećem pokretanju dekodera; menja se i u toku gledanja, vidi [setDecoder]. */
    @Volatile
    private var decoderMode = settings.decoder

    /** Da li je poslednji izabrani dekoder slike tražen za tunel režim; čita se kad dekoder krene. */
    @Volatile
    private var videoTunneled = false

    private val renderersFactory = DefaultRenderersFactory(appContext)
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
    private val extractorsFactory = DefaultExtractorsFactory()
        .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)

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

    /** Titlovi dodati uz trenutni sadržaj; ostaju i posle ponovnog pokušaja. */
    private var externalSubtitles: List<ExternalSubtitle> = emptyList()

    /** Pomerene kopije titlova (ID titla i adresa kopije), kad je korisnik ispravio vreme titla. */
    private var shiftedSubtitleUris: Map<String, String> = emptyMap()

    /** Dodat titl koji treba uključiti čim se pojavi među zapisima. */
    private var pendingSubtitleId: String? = null

    val positionMs: Long get() = player.currentPosition.coerceAtLeast(0)

    /** Trajanje sadržaja, ili 0 dok nije poznato (i za kanale uživo). */
    val durationMs: Long get() = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                wasReady = true
            } else if (playbackState == Player.STATE_BUFFERING && wasReady) {
                _state.update { it.copy(video = it.video.copy(rebuffers = it.video.rebuffers + 1)) }
            }
            if (playbackState == Player.STATE_ENDED && !seekable && source != null) {
                reconnectLive()
            } else {
                publishStatus()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = publishStatus()

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
            val container = format.containerMimeType?.substringAfter('/')?.removePrefix("x-") ?: return
            _state.update { it.copy(video = it.video.copy(container = container)) }
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
        player.addListener(listener)
        player.addAnalyticsListener(analyticsListener)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(settings.audioLanguage.ifEmpty { null })
            .setPreferredTextLanguage(settings.subtitleLanguage.ifEmpty { null })
            .build()
        applyTunneling()
    }

    /**
     * Pušta strim. Kad je zadato [startPositionMs], sadržaj je film ili epizoda: kreće od te
     * pozicije, a posle prekida se nastavlja tamo gde je stao.
     */
    fun play(source: StreamSource, startPositionMs: Long? = null, subtitles: List<ExternalSubtitle>? = null) {
        retryJob?.cancel()
        seekable = startPositionMs != null
        resumePositionMs = startPositionMs ?: 0
        if (subtitles != null || source != this.source) shiftedSubtitleUris = emptyMap()
        if (subtitles != null) {
            // Titlovi zapamćeni za ovaj sadržaj: poslednji se uključuje čim ga plejer prijavi.
            externalSubtitles = subtitles
            pendingSubtitleId = subtitles.lastOrNull()?.id
        } else if (source != this.source) {
            externalSubtitles = emptyList()
            pendingSubtitleId = null
        }
        this.source = source
        urlIndex = 0
        attempts = 0
        playedCurrentUrl = false
        _state.value = PlaybackState(status = PlaybackStatus.LOADING)
        prepareCurrent(autoPlay = true)
    }

    /** Ručni ponovni pokušaj posle greške. */
    fun retry() {
        val current = source ?: return
        play(current, if (seekable) resumePositionMs else null)
    }

    fun seekTo(positionMs: Long) {
        if (!seekable) return
        val duration = durationMs
        val target = if (duration > 0) positionMs.coerceIn(0, duration) else positionMs.coerceAtLeast(0)
        resumePositionMs = target
        player.seekTo(target)
    }

    fun seekBy(deltaMs: Long) = seekTo(positionMs + deltaMs)

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

    /** Brzina puštanja (1 = obična). Ostaje i kad se sadržaj pripremi iznova ili pređe na sledeću epizodu. */
    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
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
        rememberPosition()
        player.stop()
    }

    fun selectAudio(option: TrackOption) = selectTrack(C.TRACK_TYPE_AUDIO, option)

    fun selectText(option: TrackOption?) {
        if (option == null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            selectTrack(C.TRACK_TYPE_TEXT, option)
        }
    }

    /**
     * Dodaje titl iz fajla i uključuje ga. Sadržaj se priprema iznova (plejer ne ume da doda titl u
     * hodu), pa se gledanje nastavlja sa istog mesta posle kratkog učitavanja.
     */
    fun addSubtitle(subtitle: ExternalSubtitle) {
        if (source == null) return
        retryJob?.cancel()
        externalSubtitles = externalSubtitles.filterNot { it.id == subtitle.id } + subtitle
        pendingSubtitleId = subtitle.id
        rememberPosition()
        if (_state.value.status == PlaybackStatus.FAILED) {
            attempts = 0
            _state.update { it.copy(status = PlaybackStatus.LOADING, failure = null, failureDetail = null) }
        }
        prepareCurrent()
    }

    /** Titlovi iz fajla koji su sada dodati uz sadržaj. */
    fun externalSubtitles(): List<ExternalSubtitle> = externalSubtitles

    /**
     * Zamenjuje fajlove titlova pomerenim kopijama ([uris]: ID titla i adresa kopije; prazno vraća
     * originale). Sadržaj se priprema iznova, pa se gledanje nastavlja sa istog mesta.
     */
    fun setShiftedSubtitles(uris: Map<String, String>) {
        if (source == null || externalSubtitles.isEmpty() || uris == shiftedSubtitleUris) return
        retryJob?.cancel()
        shiftedSubtitleUris = uris
        pendingSubtitleId = externalSubtitles.last().id
        rememberPosition()
        prepareCurrent()
    }

    /**
     * Menja dekoder u toku gledanja. Sadržaj se priprema iznova, da se dekoder ponovo izabere; film
     * ili epizoda nastavljaju sa istog mesta.
     */
    fun setDecoder(mode: DecoderMode) {
        if (decoderMode == mode) return
        decoderMode = mode
        applyTunneling()
        if (source == null) return
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
        selector.setParameters(selector.buildUponParameters().setTunnelingEnabled(!shared && decoderMode == DecoderMode.TUNNEL))
    }

    fun release() {
        retryJob?.cancel()
        statsJob?.cancel()
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
     * ostaje pauziran i posle ponovnog pokušaja, promene dekodera ili dodavanja titla.
     */
    private fun prepareCurrent(autoPlay: Boolean = false) {
        wasReady = false
        val current = source ?: return
        val url = current.urls.getOrNull(urlIndex) ?: return
        val headers = buildMap {
            current.referrer?.let { put("Referer", it) }
        }
        httpFactory.setUserAgent(current.userAgent)
        httpFactory.setDefaultRequestProperties(headers)
        val item = MediaItem.Builder()
            .setUri(url)
            .setSubtitleConfigurations(
                externalSubtitles.map { subtitle ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(shiftedSubtitleUris[subtitle.id] ?: subtitle.uri))
                        .setId(subtitle.id)
                        .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                        .setLanguage(subtitle.language)
                        .setLabel(subtitle.label)
                        .build()
                },
            )
            .build()
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

    /** Pamti dokle se stiglo, da se posle prekida ili povratka u aplikaciju nastavi odatle. */
    private fun rememberPosition() {
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
        // Tek dodat titl se uključuje čim ga plejer prijavi; izbor pokreće novi poziv sa uključenim titlom.
        val pending = pendingSubtitleId
        if (pending != null) {
            tracks.groups.forEach { group ->
                if (group.type != C.TRACK_TYPE_TEXT) return@forEach
                for (trackIndex in 0 until group.length) {
                    // Plejer ispred oznake spoljnog titla dodaje redni broj izvora ("1:oznaka").
                    val id = group.getTrackFormat(trackIndex).id ?: continue
                    if (group.isTrackSupported(trackIndex) && (id == pending || id.endsWith(":$pending"))) {
                        pendingSubtitleId = null
                        if (!group.isTrackSelected(trackIndex)) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                                .build()
                        }
                    }
                }
            }
        }
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
        _state.update {
            it.copy(audioTracks = audio, textTracks = text, textEnabled = text.any { t -> t.selected })
        }
    }

    private fun classify(error: PlaybackException): PlaybackFailure = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> PlaybackFailure.NETWORK

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            val code = (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
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
    }
}
