package com.bane23.iptvplayer.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
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
)

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val failure: PlaybackFailure? = null,
    /** Tehnički opis poslednje greške, za prikaz ispod poruke. */
    val failureDetail: String? = null,
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    val textEnabled: Boolean = false,
)

/**
 * Omotač oko ExoPlayer-a: puštanje strima, automatski ponovni pokušaj kad strim pukne,
 * izbor zvuka i titlova. Ne zna ništa o kanalima ni o UI-ju.
 */
@OptIn(UnstableApi::class)
class PlayerEngine(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val httpFactory = DefaultHttpDataSource.Factory()
        // IPTV serveri često preusmeravaju sa http na https i obrnuto.
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(appContext, httpFactory)))
        .build()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var source: StreamSource? = null
    private var urlIndex = 0
    private var attempts = 0

    /** Da li je trenutna adresa bar jednom uspešno krenula; ako nije, vredi probati sledeću. */
    private var playedCurrentUrl = false
    private var retryJob: Job? = null

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
    }

    fun play(source: StreamSource) {
        retryJob?.cancel()
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
        play(current)
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
        player.setMediaItem(MediaItem.fromUri(url))
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
        _state.update { it.copy(status = status, failure = null, failureDetail = null) }
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
                target.add(TrackOption(groupIndex, trackIndex, label, group.isTrackSelected(trackIndex)))
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

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1_500L
    }
}
