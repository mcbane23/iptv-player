package com.bane23.iptvplayer.cast

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Način slanja na TV. */
enum class CastKind { CHROMECAST, TV_APP }

/**
 * Jedno mesto za oba načina slanja na TV: Chromecast i ova aplikacija na TV-u. Daljinski radi sa
 * onim načinom kojim je veza uspostavljena.
 */
@Singleton
class CastHub @Inject constructor(
    val chromecast: CastSender,
    val tvApp: LanCastSender,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _kind = MutableStateFlow(CastKind.CHROMECAST)
    val kind: StateFlow<CastKind> = _kind.asStateFlow()

    /** Stanje načina koji se trenutno koristi. */
    val state: StateFlow<CastState> = combine(_kind, chromecast.state, tvApp.state) { kind, cast, lan ->
        if (kind == CastKind.TV_APP) lan else cast
    }.stateIn(scope, SharingStarted.Eagerly, CastState())

    private val onTvApp get() = _kind.value == CastKind.TV_APP

    fun connectChromecast(routeId: String, media: CastMedia) {
        tvApp.cancelPairing()
        _kind.value = CastKind.CHROMECAST
        chromecast.connect(routeId, media)
    }

    fun connectTvApp(device: LanDevice, media: CastMedia) {
        _kind.value = CastKind.TV_APP
        tvApp.connect(device, media)
    }

    fun play(media: CastMedia) = if (onTvApp) tvApp.play(media) else chromecast.play(media)

    fun togglePlayPause() = if (onTvApp) tvApp.togglePlayPause() else chromecast.togglePlayPause()

    fun seekTo(positionMs: Long) = if (onTvApp) tvApp.seekTo(positionMs) else chromecast.seekTo(positionMs)

    fun seekBy(deltaMs: Long) = if (onTvApp) tvApp.seekBy(deltaMs) else chromecast.seekBy(deltaMs)

    fun changeVolume(up: Boolean) =
        if (onTvApp) tvApp.changeVolume(up) else chromecast.changeVolume(if (up) VOLUME_STEP else -VOLUME_STEP)

    fun stop() = if (onTvApp) tvApp.stop() else chromecast.stop()

    private companion object {
        const val VOLUME_STEP = 0.05
    }
}
