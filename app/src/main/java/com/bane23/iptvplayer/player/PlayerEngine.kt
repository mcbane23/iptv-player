package com.bane23.iptvplayer.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
) {

    private val appContext = context.applicationContext
    private val httpFactory = DefaultHttpDataSource.Factory()
        // IPTV serveri često preusmeravaju sa http na https i obrnuto.
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)

    private val renderersFactory = DefaultRenderersFactory(appContext)
        // Ako izabrani dekoder ne uspe da krene, plejer proba sledeći umesto da prijavi grešku.
        .setEnableDecoderFallback(true)
        .apply {
            if (settings.decoder == DecoderMode.SOFTWARE) {
                setMediaCodecSelector(
                    MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                        MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                            .sortedByDescending { it.softwareOnly }
                    },
                )
            }
        }

    val player: ExoPlayer = ExoPlayer.Builder(appContext, renderersFactory)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(appContext, httpFactory)))
        .apply { loadControl(settings.buffer)?.let { setLoadControl(it) } }
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

    /** Dodat titl koji treba uključiti čim se pojavi među zapisima. */
    private var pendingSubtitleId: String? = null

    val positionMs: Long get() = player.currentPosition.coerceAtLeast(0)

    /** Trajanje sadržaja, ili 0 dok nije poznato (i za kanale uživo). */
    val durationMs: Long get() = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = publishStatus()

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = publishStatus()

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                attempts = 0
                playedCurrentUrl = true
            }
            publishStatus()
        }

        override fun onTracksChanged(tracks: Tracks) = publishTracks(tracks)

        override fun onPlayerError(error: PlaybackException) = handleError(error)
    }

    init {
        player.addListener(listener)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(settings.audioLanguage.ifEmpty { null })
            .setPreferredTextLanguage(settings.subtitleLanguage.ifEmpty { null })
            .build()
    }

    /**
     * Pušta strim. Kad je zadato [startPositionMs], sadržaj je film ili epizoda: kreće od te
     * pozicije, a posle prekida se nastavlja tamo gde je stao.
     */
    fun play(source: StreamSource, startPositionMs: Long? = null, subtitles: List<ExternalSubtitle>? = null) {
        retryJob?.cancel()
        seekable = startPositionMs != null
        resumePositionMs = startPositionMs ?: 0
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
        prepareCurrent()
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
            prepareCurrent()
        } else {
            if (player.isCurrentMediaItemLive) player.seekToDefaultPosition()
            player.play()
        }
    }

    fun pause() {
        player.pause()
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

    fun release() {
        retryJob?.cancel()
        player.removeListener(listener)
        player.release()
    }

    private fun selectTrack(type: Int, option: TrackOption) {
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
    }

    private fun prepareCurrent() {
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
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.uri))
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
        player.playWhenReady = true
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

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1_500L
    }
}
