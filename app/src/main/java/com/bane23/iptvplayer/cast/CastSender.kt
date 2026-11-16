package com.bane23.iptvplayer.cast

import android.content.Context
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.bane23.iptvplayer.ui.common.DeviceType
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Uređaj na koji može da se šalje (Chromecast ili TV sa ugrađenim Chromecast-om). */
data class CastRoute(val id: String, val name: String)

data class CastState(
    /** Google Cast radi na ovom telefonu (postoje Google Play servisi). */
    val available: Boolean = false,
    val routes: List<CastRoute> = emptyList(),
    val connecting: Boolean = false,
    /** Ime uređaja sa kojim je veza uspostavljena, ili null. */
    val deviceName: String? = null,
    /** Povezivanje nije uspelo. */
    val connectFailed: Boolean = false,
    val media: CastMedia? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    /** Uređaj nije mogao da pusti strim. */
    val failed: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

/**
 * Slanje sadržaja na Chromecast i upravljanje puštanjem. Sve funkcije se zovu sa glavne niti.
 * Na TV-u i na telefonima bez Google Play servisa ne radi ništa ([CastState.available] ostaje false).
 */
@Singleton
class CastSender @Inject constructor(@ApplicationContext private val context: Context) {
    private val _state = MutableStateFlow(CastState())
    val state: StateFlow<CastState> = _state.asStateFlow()

    private var castContext: CastContext? = null
    private var router: MediaRouter? = null
    private var selector: MediaRouteSelector? = null
    private var session: CastSession? = null
    private var client: RemoteMediaClient? = null
    private var pending: CastMedia? = null
    private var initStarted = false
    private var discovering = false

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = publishRoutes()

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = publishRoutes()

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = publishRoutes()
    }

    private val clientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = publishPlayback()
    }

    private val progressListener = RemoteMediaClient.ProgressListener { progressMs, durationMs ->
        _state.update { it.copy(positionMs = progressMs.coerceAtLeast(0), durationMs = durationMs.coerceAtLeast(0)) }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = Unit

        override fun onSessionStarted(session: CastSession, sessionId: String) = attach(session)

        override fun onSessionStartFailed(session: CastSession, error: Int) = detach(connectFailed = true)

        override fun onSessionEnding(session: CastSession) = Unit

        override fun onSessionEnded(session: CastSession, error: Int) = detach(connectFailed = false)

        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = attach(session)

        override fun onSessionResumeFailed(session: CastSession, error: Int) = detach(connectFailed = false)

        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    /** Priprema Google Cast. Sme da se pozove više puta. */
    fun init() {
        if (initStarted) return
        initStarted = true
        if (DeviceType.isTv(context)) return
        try {
            CastContext.getSharedInstance(context, Executors.newSingleThreadExecutor())
                .addOnSuccessListener { cast ->
                    castContext = cast
                    router = MediaRouter.getInstance(context)
                    selector = cast.mergedSelector
                    cast.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
                    _state.update { it.copy(available = selector != null) }
                    cast.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let { attach(it) }
                    if (discovering) startDiscovery()
                }
        } catch (_: RuntimeException) {
            // Nema Google Play servisa: dugme za cast se ne prikazuje.
        }
    }

    /** Traži uređaje dok je otvoren izbor uređaja. */
    fun startDiscovery() {
        discovering = true
        val mediaRouter = router ?: return
        val routeSelector = selector ?: return
        mediaRouter.addCallback(routeSelector, routerCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        publishRoutes()
    }

    fun stopDiscovery() {
        discovering = false
        router?.removeCallback(routerCallback)
    }

    /** Povezuje se sa uređajem i, kad veza uspe, pušta [media]. */
    fun connect(routeId: String, media: CastMedia) {
        val mediaRouter = router ?: return
        val route = mediaRouter.routes.firstOrNull { it.id == routeId } ?: return
        pending = media
        _state.update { it.copy(connecting = true, connectFailed = false, failed = false) }
        mediaRouter.selectRoute(route)
    }

    /** Pušta [media] na već povezanom uređaju (npr. sledeći kanal). */
    fun play(media: CastMedia) {
        val remote = client
        val url = CastUrls.pick(media.urls)
        if (remote == null || url == null) {
            _state.update { it.copy(media = media, failed = true) }
            return
        }
        val metadata = MediaMetadata(if (media.live) MediaMetadata.MEDIA_TYPE_GENERIC else MediaMetadata.MEDIA_TYPE_MOVIE)
        metadata.putString(MediaMetadata.KEY_TITLE, media.title)
        val info = MediaInfo.Builder(url)
            .setStreamType(if (media.live) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(CastUrls.contentType(url, media.live))
            .setMetadata(metadata)
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setAutoplay(true)
            .setCurrentTime(if (media.live) 0 else media.positionMs.coerceAtLeast(0))
            .build()
        _state.update {
            it.copy(media = media, failed = false, buffering = true, playing = false, positionMs = media.positionMs, durationMs = 0)
        }
        remote.load(request).setResultCallback { result ->
            if (!result.status.isSuccess) _state.update { it.copy(failed = true, buffering = false) }
        }
    }

    fun togglePlayPause() {
        client?.togglePlayback()
    }

    fun seekTo(positionMs: Long) {
        val remote = client ?: return
        val target = positionMs.coerceAtLeast(0)
        remote.seek(MediaSeekOptions.Builder().setPosition(target).build())
        _state.update { it.copy(positionMs = target) }
    }

    fun seekBy(deltaMs: Long) = seekTo(_state.value.positionMs + deltaMs)

    /** Menja jačinu zvuka na TV-u za [delta] (od -1 do 1). */
    fun changeVolume(delta: Double) {
        val current = session ?: return
        try {
            current.volume = (current.volume + delta).coerceIn(0.0, 1.0)
        } catch (_: IOException) {
            // veza je upravo prekinuta
        } catch (_: IllegalStateException) {
            // sesija više nije povezana
        }
    }

    /** Zaustavlja puštanje na TV-u i prekida vezu. */
    fun stop() {
        pending = null
        castContext?.sessionManager?.endCurrentSession(true)
    }

    private fun attach(castSession: CastSession) {
        if (session === castSession && client != null) return
        release()
        session = castSession
        val remote = castSession.remoteMediaClient
        client = remote
        remote?.registerCallback(clientCallback)
        remote?.addProgressListener(progressListener, PROGRESS_PERIOD_MS)
        _state.update {
            it.copy(
                connecting = false,
                connectFailed = false,
                deviceName = castSession.castDevice?.friendlyName ?: "",
            )
        }
        pending?.let { media ->
            pending = null
            play(media)
        }
    }

    private fun detach(connectFailed: Boolean) {
        release()
        pending = null
        _state.update {
            CastState(available = it.available, routes = it.routes, connectFailed = connectFailed)
        }
    }

    private fun release() {
        client?.unregisterCallback(clientCallback)
        client?.removeProgressListener(progressListener)
        client = null
        session = null
    }

    private fun publishRoutes() {
        val mediaRouter = router ?: return
        val routeSelector = selector ?: return
        val routes = mediaRouter.routes
            .filter { it.isEnabled && !it.isDefault && it.matchesSelector(routeSelector) }
            .map { CastRoute(it.id, it.name) }
        _state.update { it.copy(routes = routes) }
    }

    private fun publishPlayback() {
        val remote = client ?: return
        val playerState = remote.playerState
        val idleError = playerState == MediaStatus.PLAYER_STATE_IDLE && remote.idleReason == MediaStatus.IDLE_REASON_ERROR
        _state.update {
            it.copy(
                playing = playerState == MediaStatus.PLAYER_STATE_PLAYING,
                buffering = playerState == MediaStatus.PLAYER_STATE_BUFFERING ||
                    playerState == MediaStatus.PLAYER_STATE_LOADING,
                failed = it.failed || idleError,
            )
        }
    }

    private companion object {
        const val PROGRESS_PERIOD_MS = 1_000L
    }
}
