package com.bane23.iptvplayer.ui.common.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.cast.LanCastSender
import com.bane23.iptvplayer.cast.LanDevice
import com.bane23.iptvplayer.data.cast.CastCodec
import com.bane23.iptvplayer.data.cast.CastPairConfirm
import com.bane23.iptvplayer.data.cast.CastPairResult
import com.bane23.iptvplayer.data.cast.CastServer
import com.bane23.iptvplayer.data.lan.TransferAccounts
import com.bane23.iptvplayer.data.lan.TransferBundle
import com.bane23.iptvplayer.data.lan.TransferCodec
import com.bane23.iptvplayer.data.lan.TransferResult
import com.bane23.iptvplayer.data.lan.TransferTarget
import com.bane23.iptvplayer.data.lan.TransferTraktLogin
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.TransferRepository
import com.bane23.iptvplayer.data.trakt.TokenResponse
import com.bane23.iptvplayer.data.trakt.TraktLoginResult
import com.bane23.iptvplayer.data.trakt.TraktRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

enum class SendError {
    /** Link nije adresa TV-a u lokalnoj mreži. */
    BAD_TARGET,

    /** TV se ne javlja: ekran sa kodom je zatvoren ili telefon nije na istoj mreži. */
    UNREACHABLE,

    /** TV je odbio paket (kod je istekao ili je na TV-u starija verzija aplikacije). */
    REFUSED,
}

data class SendToTvUiState(
    val loading: Boolean = true,
    /** Adresa TV-a za prikaz (`adresa:port`). */
    val address: String = "",
    /** Broj lista koje će biti poslate. */
    val playlistCount: Int = 0,
    /** Liste koje ne mogu da se pošalju (učitane iz fajla na telefonu). */
    val skipped: Int = 0,
    val sending: Boolean = false,
    val result: TransferResult? = null,
    val error: SendError? = null,
    /** Tehnički razlog kada se TV ne javlja (vrsta greške veze), da kvar može da se prijavi. */
    val errorDetail: String? = null,
    /** Korisničko ime OpenSubtitles naloga koji se šalje uz liste, ili null. */
    val openSubtitlesUser: String? = null,
    /** Da li aplikacija ima Trakt (tada može da se zatraži posebna prijava za TV). */
    val traktAvailable: Boolean = false,
    val trakt: TvTraktLogin = TvTraktLogin.None,
    /** Broj omiljenih kanala, odnosno filmova i serija, koji idu uz liste. */
    val favoriteChannels: Int = 0,
    val favoriteVod: Int = 0,
    /** Otvoreno iz podešavanja: TV se bira sa spiska uređaja u mreži, a ne adresom iz QR koda. */
    val discovery: Boolean = false,
    /** Televizori u kućnoj mreži na kojima je aplikacija otvorena. */
    val devices: List<LanDevice> = emptyList(),
    val selected: LanDevice? = null,
    /** TV je prikazao kod za prvo povezivanje i čeka da se prepiše. */
    val pairing: Boolean = false,
    val codeWrong: Boolean = false,
) {
    /** Ima li šta da se pošalje. */
    val canSend: Boolean get() = playlistCount > 0 || openSubtitlesUser != null || trakt == TvTraktLogin.Ready
}

/** Posebna Trakt prijava za TV: telefon je traži, korisnik je odobrava na Trakt stranici. */
sealed interface TvTraktLogin {
    data object None : TvTraktLogin

    data object Starting : TvTraktLogin

    /** Čeka se da korisnik odobri [code] na stranici [url]. */
    data class Waiting(val code: String, val url: String) : TvTraktLogin

    /** Prijava je dobijena i ide uz sledeće slanje. */
    data object Ready : TvTraktLogin

    data class Failed(val reason: TraktLoginResult) : TvTraktLogin
}

/**
 * Slanje svih lista, omiljenih, podešavanja i naloga na TV. Otvara se linkom sa stranice TV-a (QR kod)
 * ili iz podešavanja, kada se TV bira sa spiska uređaja u mreži. Ništa se ne šalje dok korisnik ne
 * potvrdi, jer paket sadrži lozinke naloga.
 */
@HiltViewModel
class SendToTvViewModel @Inject constructor(
    private val transfers: TransferRepository,
    private val trakt: TraktRepository,
    private val lan: LanCastSender,
    private val settings: SettingsStore,
    httpClient: OkHttpClient,
) : ViewModel() {
    private val _state = MutableStateFlow(SendToTvUiState())
    val state: StateFlow<SendToTvUiState> = _state.asStateFlow()

    // Novija verzija na TV-u odgovara odmah; starija tek kad učita svaku listu, pa odgovor može da kasni.
    private val client = httpClient.newBuilder().readTimeout(READ_TIMEOUT_MINUTES, TimeUnit.MINUTES).build()
    private var target: String? = null
    private var bundle: TransferBundle? = null
    private var traktToken: TokenResponse? = null
    private var traktJob: Job? = null
    private var traktSent = false

    fun prepare(rawTarget: String) {
        if (target != null || _state.value.error != null) return
        val valid = TransferTarget.validate(rawTarget)
        if (valid == null) {
            _state.update { it.copy(loading = false, error = SendError.BAD_TARGET) }
            return
        }
        target = valid
        viewModelScope.launch { export(address = TransferTarget.displayAddress(valid)) }
    }

    /** Priprema za slanje na TV koji korisnik izabere sa spiska (stavka u podešavanjima). */
    fun prepareDiscovery() {
        if (_state.value.discovery) return
        _state.update { it.copy(discovery = true) }
        viewModelScope.launch { export(address = "") }
        viewModelScope.launch {
            lan.picker.collect { picker ->
                _state.update { state ->
                    val devices = picker.devices.sortedBy { it.name }
                    // Izabrani TV se prati po imenu, jer mu se adresa menja kad se aplikacija ponovo otvori.
                    val selected = devices.firstOrNull { it.name == state.selected?.name }
                        ?: state.selected.takeIf { state.sending || state.pairing }
                        ?: devices.singleOrNull()
                    state.copy(devices = devices, selected = selected)
                }
            }
        }
    }

    fun startDiscovery() = lan.startDiscovery()

    fun stopDiscovery() = lan.stopDiscovery()

    fun select(device: LanDevice) {
        if (_state.value.sending) return
        _state.update { it.copy(selected = device, pairing = false, codeWrong = false, error = null, errorDetail = null) }
    }

    private suspend fun export(address: String) {
        val export = transfers.exportBundle()
        bundle = export.bundle
        _state.update { state ->
            state.copy(
                loading = false,
                address = address,
                playlistCount = export.bundle.playlists.size,
                skipped = export.skipped,
                openSubtitlesUser = export.bundle.accounts?.openSubtitles?.username,
                traktAvailable = trakt.isAvailable,
                favoriteChannels = export.bundle.playlists.sumOf { it.favorites.size },
                favoriteVod = export.bundle.playlists.sumOf { it.vodFavorites.size },
            )
        }
    }

    /** Traži od Trakt-a novu prijavu namenjenu TV-u; korisnik je odobrava na stranici koju otvara ekran. */
    fun requestTrakt() {
        if (traktJob?.isActive == true || traktToken != null) return
        _state.update { it.copy(trakt = TvTraktLogin.Starting) }
        traktJob = viewModelScope.launch {
            val pending = try {
                trakt.startLogin()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(trakt = TvTraktLogin.Failed(TraktLoginResult.FAILED)) }
                return@launch
            }
            val code = pending.code
            _state.update {
                it.copy(trakt = TvTraktLogin.Waiting(code.userCode, code.verificationUrl.trimEnd('/') + "/" + code.userCode))
            }
            val (result, token) = trakt.awaitToken(pending)
            traktToken = token
            _state.update { it.copy(trakt = if (token != null) TvTraktLogin.Ready else TvTraktLogin.Failed(result)) }
        }
    }

    fun cancelTrakt() {
        traktJob?.cancel()
        traktJob = null
        if (traktToken == null) _state.update { it.copy(trakt = TvTraktLogin.None) }
    }

    fun send() {
        val state = _state.value
        if (state.sending || !state.canSend) return
        val payload = payload() ?: return
        if (state.discovery) {
            val device = state.selected ?: return
            _state.update { it.copy(sending = true, pairing = false, codeWrong = false, error = null, errorDetail = null, result = null) }
            viewModelScope.launch {
                val key = settings.castPeers.first()[device.name]
                if (key == null) beginPairing(device) else sendToDevice(device, key, payload)
            }
        } else {
            val url = target ?: return
            _state.update { it.copy(sending = true, error = null, errorDetail = null, result = null) }
            viewModelScope.launch { finish(postBundle(url + TransferTarget.BUNDLE_PATH, payload)) }
        }
    }

    /** Kod koji je korisnik prepisao sa ekrana TV-a; tačan kod odmah nastavlja slanje. */
    fun confirmCode(code: String) {
        val state = _state.value
        val device = state.selected ?: return
        if (!state.pairing || state.sending) return
        val payload = payload() ?: return
        _state.update { it.copy(sending = true, codeWrong = false) }
        viewModelScope.launch {
            val response = call(device.baseUrl + CastServer.CONFIRM_PATH, CastCodec.encode(CastPairConfirm(code)))
            val key = (response as? Response)?.takeIf { it.code == HTTP_OK }
                ?.let { CastCodec.decode<CastPairResult>(it.body)?.key }
            when {
                response is Unreachable -> finish(response)
                key.isNullOrEmpty() -> _state.update { it.copy(sending = false, codeWrong = true) }
                else -> {
                    settings.setCastPeer(device.name, key)
                    _state.update { it.copy(pairing = false) }
                    sendToDevice(device, key, payload)
                }
            }
        }
    }

    /** Paket sa prijavom za Trakt, ako je dobijena. */
    private fun payload(): TransferBundle? {
        val base = bundle ?: return null
        val token = traktToken ?: return base
        val login = TransferTraktLogin(token.accessToken, token.refreshToken, token.expiresIn, token.createdAt)
        return base.copy(accounts = (base.accounts ?: TransferAccounts()).copy(trakt = login))
    }

    private suspend fun sendToDevice(device: LanDevice, key: String, payload: TransferBundle) {
        val outcome = postBundle("${device.baseUrl}${CastServer.KEY_PREFIX}$key/${CastServer.BUNDLE_ACTION}", payload)
        // TV više ne priznaje ključ (npr. aplikacija je ponovo instalirana): povezuje se iznova.
        if (outcome is Response && outcome.code == HTTP_FORBIDDEN) beginPairing(device) else finish(outcome)
    }

    /** Traži od TV-a da prikaže kod za prvo povezivanje. */
    private suspend fun beginPairing(device: LanDevice) {
        when (val response = call(device.baseUrl + CastServer.PAIR_PATH, "{}")) {
            is Response ->
                if (response.code == HTTP_OK) {
                    _state.update { it.copy(sending = false, pairing = true, codeWrong = false) }
                } else {
                    finish(response)
                }

            is Unreachable -> finish(response)
        }
    }

    private suspend fun postBundle(url: String, payload: TransferBundle): Outcome {
        if (payload.accounts?.trakt != null) traktSent = true
        return call(url, TransferCodec.encode(payload))
    }

    private fun finish(outcome: Outcome) {
        _state.update {
            when (outcome) {
                is Unreachable -> it.copy(sending = false, error = SendError.UNREACHABLE, errorDetail = outcome.detail)

                is Response -> {
                    val result = if (outcome.code == HTTP_OK) TransferCodec.decodeResult(outcome.body) else null
                    if (result != null) it.copy(sending = false, result = result) else it.copy(sending = false, error = SendError.REFUSED)
                }
            }
        }
    }

    private suspend fun call(url: String, json: String): Outcome = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).post(json.toRequestBody(JSON)).build()
            client.newCall(request).execute().use { response -> Response(response.code, response.body.string()) }
        } catch (e: IOException) {
            // Poruka sadrži samo adresu TV-a u kućnoj mreži, ne i sadržaj paketa.
            Unreachable(listOfNotNull(e.javaClass.simpleName, e.message).joinToString(": ").take(MAX_DETAIL_LENGTH))
        } catch (e: IllegalArgumentException) {
            Unreachable(e.javaClass.simpleName)
        }
    }

    private sealed interface Outcome

    private class Response(val code: Int, val body: String) : Outcome

    private class Unreachable(val detail: String) : Outcome

    override fun onCleared() {
        if (_state.value.discovery) lan.stopDiscovery()
        // Prijava koja nije stigla do TV-a se opoziva, da ne ostane neiskorišćena na nalogu.
        val unused = traktToken?.takeIf { !traktSent } ?: return
        cleanupScope.launch { trakt.discard(unused) }
    }

    private companion object {
        const val READ_TIMEOUT_MINUTES = 15L
        const val MAX_DETAIL_LENGTH = 200
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403

        /** Nadživljava ekran: opoziv neupotrebljene prijave ide posle njegovog zatvaranja. */
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
