package com.bane23.iptvplayer.ui.common.cast

import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.cast.CastReceiver
import com.bane23.iptvplayer.cast.CastSession
import com.bane23.iptvplayer.data.cast.CastCommand
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.domain.StreamSource
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.player.PlaybackStatus
import com.bane23.iptvplayer.player.PlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Prijem na TV-u: stanje koje treba celoj aplikaciji (kod za povezivanje, novi zahtev za puštanje). */
@HiltViewModel
class CastHostViewModel @Inject constructor(private val receiver: CastReceiver) : ViewModel() {
    val session: StateFlow<CastSession?> = receiver.session
    val pairingCode: StateFlow<String?> = receiver.pairingCode

    fun start() = receiver.start()

    fun stop() = receiver.stop()

    fun dismissPairing() = receiver.dismissPairing()
}

/** Plejer na TV-u za sadržaj poslat sa telefona. Komande stižu sa telefona, a stanje se vraća njemu. */
@HiltViewModel
class CastPlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val receiver: CastReceiver,
    settings: SettingsStore,
) : ViewModel() {
    private val engine = PlayerEngine(context, viewModelScope, runBlocking { settings.playerSettings.first() })
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val player get() = engine.player
    val playback: StateFlow<PlaybackState> = engine.state
    val session: StateFlow<CastSession?> = receiver.session

    init {
        viewModelScope.launch {
            receiver.session.collect { current ->
                if (current == null) {
                    engine.stop()
                } else {
                    val request = current.request
                    engine.play(
                        StreamSource(request.urls, request.userAgent, request.referrer),
                        startPositionMs = request.positionMs.takeIf { !request.live && it > 0 },
                    )
                }
            }
        }
        viewModelScope.launch {
            receiver.commands.collect { command ->
                when (command.action) {
                    CastCommand.TOGGLE -> togglePlayPause()
                    CastCommand.SEEK_BY -> engine.seekBy(command.valueMs)
                    CastCommand.SEEK_TO -> engine.seekTo(command.valueMs)
                    CastCommand.VOLUME_UP -> changeVolume(AudioManager.ADJUST_RAISE)
                    CastCommand.VOLUME_DOWN -> changeVolume(AudioManager.ADJUST_LOWER)
                }
                publish()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(STATUS_TICK_MS)
                publish()
            }
        }
    }

    fun togglePlayPause() {
        if (engine.state.value.status == PlaybackStatus.FAILED) engine.retry() else engine.togglePlayPause()
    }

    fun onForeground() = engine.restore()

    fun onBackground() = engine.stop()

    private fun changeVolume(direction: Int) {
        try {
            audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } catch (_: RuntimeException) {
            // uređaj sa fiksnom jačinom zvuka
        }
    }

    private fun publish() {
        val status = engine.state.value.status
        receiver.status.update {
            if (!it.active) {
                it
            } else {
                it.copy(
                    playing = status == PlaybackStatus.PLAYING,
                    buffering = status == PlaybackStatus.LOADING || status == PlaybackStatus.RETRYING,
                    failed = status == PlaybackStatus.FAILED,
                    positionMs = engine.positionMs,
                    durationMs = engine.durationMs,
                )
            }
        }
    }

    override fun onCleared() {
        engine.release()
        receiver.closeSession()
    }

    private companion object {
        const val STATUS_TICK_MS = 500L
    }
}
