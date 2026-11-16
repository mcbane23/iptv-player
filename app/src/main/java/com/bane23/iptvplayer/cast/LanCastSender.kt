package com.bane23.iptvplayer.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.bane23.iptvplayer.data.cast.CastCodec
import com.bane23.iptvplayer.data.cast.CastCommand
import com.bane23.iptvplayer.data.cast.CastPairConfirm
import com.bane23.iptvplayer.data.cast.CastPairResult
import com.bane23.iptvplayer.data.cast.CastPlayRequest
import com.bane23.iptvplayer.data.cast.CastServer
import com.bane23.iptvplayer.data.cast.CastStatus
import com.bane23.iptvplayer.data.prefs.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** TV u kućnoj mreži na kom je otvorena ova aplikacija. */
data class LanDevice(val name: String, val host: String, val port: Int) {
    /** Osnovna adresa; IPv6 adresa ide u uglaste zagrade i bez oznake mrežnog interfejsa. */
    val baseUrl: String
        get() {
            val clean = host.substringBefore('%')
            return if (':' in clean) "http://[$clean]:$port" else "http://$clean:$port"
        }
}

data class LanPickerState(
    val devices: List<LanDevice> = emptyList(),
    /** TV koji čeka kod sa svog ekrana (prvo povezivanje). */
    val pairingWith: LanDevice? = null,
    val codeWrong: Boolean = false,
)

/**
 * Slanje na ovu aplikaciju na TV-u kroz kućnu mrežu. TV pušta strim svojim plejerom, pa radi sa
 * svim formatima koje aplikacija pušta. Stanje ima isti oblik kao kod Chromecast-a ([CastState]),
 * da daljinski bude jedan.
 */
@Singleton
class LanCastSender @Inject constructor(
    @ApplicationContext context: Context,
    httpClient: OkHttpClient,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = httpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .build()
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val _state = MutableStateFlow(CastState())
    val state: StateFlow<CastState> = _state.asStateFlow()

    private val _picker = MutableStateFlow(LanPickerState())
    val picker: StateFlow<LanPickerState> = _picker.asStateFlow()

    private var discovery: NsdManager.DiscoveryListener? = null
    private var device: LanDevice? = null
    private var key: String? = null
    private var pending: CastMedia? = null
    private var pollJob: Job? = null

    fun startDiscovery() {
        val manager = nsd ?: return
        if (discovery != null) return
        _picker.update { it.copy(devices = emptyList()) }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) = resolve(manager, serviceInfo, attempt = 0)

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName
                _picker.update { state -> state.copy(devices = state.devices.filterNot { it.name == name }) }
            }
        }
        discovery = listener
        try {
            manager.discoverServices(CastServer.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: RuntimeException) {
            discovery = null
        }
    }

    fun stopDiscovery() {
        val listener = discovery ?: return
        discovery = null
        try {
            nsd?.stopServiceDiscovery(listener)
        } catch (_: RuntimeException) {
            // pretraga nije ni krenula
        }
    }

    /** Adresa i port servisa. Stariji Android razrešava samo jedan servis odjednom, pa se ponavlja. */
    @Suppress("DEPRECATION")
    private fun resolve(manager: NsdManager, info: NsdServiceInfo, attempt: Int) {
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE && attempt < MAX_RESOLVE_ATTEMPTS) {
                    scope.launch {
                        delay(RESOLVE_RETRY_MS)
                        if (discovery != null) resolve(manager, info, attempt + 1)
                    }
                }
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                val found = LanDevice(serviceInfo.serviceName, host, serviceInfo.port)
                _picker.update { state ->
                    state.copy(devices = state.devices.filterNot { it.name == found.name } + found)
                }
            }
        }
        try {
            manager.resolveService(info, listener)
        } catch (_: RuntimeException) {
            // servis je u međuvremenu nestao
        }
    }

    /** Povezuje se sa TV-om i pušta [media]; pri prvom povezivanju TV prvo prikaže kod. */
    fun connect(target: LanDevice, media: CastMedia) {
        pending = media
        _state.update { it.copy(connecting = true, connectFailed = false, failed = false) }
        scope.launch {
            val known = settings.castPeers.first()[target.name]
            if (known == null) {
                beginPairing(target)
            } else {
                when (sendPlay(target, known, media)) {
                    HTTP_OK -> connected(target, known, media)
                    HTTP_FORBIDDEN -> beginPairing(target)
                    else -> connectFailed()
                }
            }
        }
    }

    /** Kod koji je korisnik prepisao sa ekrana TV-a. */
    fun confirmCode(code: String) {
        val target = _picker.value.pairingWith ?: return
        val media = pending ?: return
        scope.launch {
            val response = call(target, "/cast/confirm", CastCodec.encode(CastPairConfirm(code)))
            val newKey = response?.takeIf { it.first == HTTP_OK }
                ?.let { CastCodec.decode<CastPairResult>(it.second)?.key }
            when {
                response == null -> {
                    _picker.update { it.copy(pairingWith = null, codeWrong = false) }
                    connectFailed()
                }

                newKey.isNullOrEmpty() -> _picker.update { it.copy(codeWrong = true) }

                else -> {
                    settings.setCastPeer(target.name, newKey)
                    _picker.update { it.copy(pairingWith = null, codeWrong = false) }
                    _state.update { it.copy(connecting = true) }
                    if (sendPlay(target, newKey, media) == HTTP_OK) connected(target, newKey, media) else connectFailed()
                }
            }
        }
    }

    fun cancelPairing() {
        pending = null
        _picker.update { it.copy(pairingWith = null, codeWrong = false) }
        _state.update { it.copy(connecting = false) }
    }

    /** Pušta [media] na već povezanom TV-u (npr. sledeći kanal). */
    fun play(media: CastMedia) {
        val target = device ?: return
        val currentKey = key ?: return
        _state.update { it.copy(media = media, failed = false, buffering = true, playing = false, positionMs = media.positionMs) }
        scope.launch {
            if (sendPlay(target, currentKey, media) != HTTP_OK) _state.update { it.copy(failed = true, buffering = false) }
        }
    }

    fun togglePlayPause() = command(CastCommand.TOGGLE)

    fun seekTo(positionMs: Long) {
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0)) }
        command(CastCommand.SEEK_TO, positionMs.coerceAtLeast(0))
    }

    fun seekBy(deltaMs: Long) = seekTo(_state.value.positionMs + deltaMs)

    fun changeVolume(up: Boolean) = command(if (up) CastCommand.VOLUME_UP else CastCommand.VOLUME_DOWN)

    /** Zaustavlja puštanje na TV-u i prekida vezu. */
    fun stop() {
        val target = device
        val currentKey = key
        reset()
        if (target != null && currentKey != null) {
            scope.launch { call(target, "/cast/k/$currentKey/command", CastCodec.encode(CastCommand(CastCommand.STOP))) }
        }
    }

    private fun command(action: String, valueMs: Long = 0) {
        val target = device ?: return
        val currentKey = key ?: return
        scope.launch {
            val response = call(target, "/cast/k/$currentKey/command", CastCodec.encode(CastCommand(action, valueMs)))
            response?.takeIf { it.first == HTTP_OK }?.let { CastCodec.decode<CastStatus>(it.second) }?.let { applyStatus(it) }
        }
    }

    private suspend fun beginPairing(target: LanDevice) {
        val response = call(target, "/cast/pair", "{}")
        if (response?.first == HTTP_OK) {
            _picker.update { it.copy(pairingWith = target, codeWrong = false) }
            _state.update { it.copy(connecting = false) }
        } else {
            connectFailed()
        }
    }

    private suspend fun sendPlay(target: LanDevice, targetKey: String, media: CastMedia): Int? {
        val request = CastPlayRequest(
            title = media.title,
            urls = media.urls,
            userAgent = media.userAgent,
            referrer = media.referrer,
            live = media.live,
            positionMs = if (media.live) 0 else media.positionMs,
        )
        return call(target, "/cast/k/$targetKey/play", CastCodec.encode(request))?.first
    }

    private fun connected(target: LanDevice, targetKey: String, media: CastMedia) {
        device = target
        key = targetKey
        pending = null
        _state.value = CastState(
            deviceName = target.name,
            media = media,
            buffering = true,
            positionMs = media.positionMs,
        )
        pollJob?.cancel()
        pollJob = scope.launch {
            var misses = 0
            var seenActive = false
            while (isActive) {
                delay(POLL_MS)
                val response = call(target, "/cast/k/$targetKey/status", null)
                val status = response?.takeIf { it.first == HTTP_OK }?.let { CastCodec.decode<CastStatus>(it.second) }
                when {
                    status == null -> if (++misses >= MAX_MISSES) reset()

                    // Plejer na TV-u je zatvoren (Nazad na daljinskom TV-a).
                    !status.active -> if (seenActive || ++misses >= MAX_MISSES) reset()

                    else -> {
                        misses = 0
                        seenActive = true
                        applyStatus(status)
                    }
                }
            }
        }
    }

    private fun applyStatus(status: CastStatus) {
        if (!status.active || device == null) return
        _state.update {
            it.copy(
                playing = status.playing,
                buffering = status.buffering,
                failed = status.failed,
                positionMs = status.positionMs,
                durationMs = status.durationMs,
            )
        }
    }

    private fun connectFailed() {
        pending = null
        _state.update { CastState(connectFailed = true) }
    }

    private fun reset() {
        pollJob?.cancel()
        pollJob = null
        device = null
        key = null
        pending = null
        _state.value = CastState()
    }

    /** POST sa JSON telom, ili GET kad je [body] null. Vraća status i telo, ili null kad se TV ne javlja. */
    private suspend fun call(target: LanDevice, path: String, body: String?): Pair<Int, String>? =
        withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder().url(target.baseUrl + path)
                if (body != null) builder.post(body.toRequestBody(JSON))
                client.newCall(builder.build()).execute().use { response ->
                    response.code to response.body.string()
                }
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
        const val CONNECT_TIMEOUT_S = 4L
        const val READ_TIMEOUT_S = 6L
        const val POLL_MS = 1_000L
        const val MAX_MISSES = 5
        const val MAX_RESOLVE_ATTEMPTS = 10
        const val RESOLVE_RETRY_MS = 400L
    }
}
