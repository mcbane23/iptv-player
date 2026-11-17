package com.bane23.iptvplayer.ui.common.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.lan.TransferAccounts
import com.bane23.iptvplayer.data.lan.TransferBundle
import com.bane23.iptvplayer.data.lan.TransferCodec
import com.bane23.iptvplayer.data.lan.TransferResult
import com.bane23.iptvplayer.data.lan.TransferTarget
import com.bane23.iptvplayer.data.lan.TransferTraktLogin
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
 * Slanje svih lista i podešavanja na TV. Otvara se linkom sa stranice TV-a; ništa se ne šalje dok
 * korisnik ne potvrdi, jer paket sadrži lozinke naloga.
 */
@HiltViewModel
class SendToTvViewModel @Inject constructor(
    private val transfers: TransferRepository,
    private val trakt: TraktRepository,
    httpClient: OkHttpClient,
) : ViewModel() {
    private val _state = MutableStateFlow(SendToTvUiState())
    val state: StateFlow<SendToTvUiState> = _state.asStateFlow()

    // TV učitava svaku listu pre nego što odgovori, pa odgovor može da kasni više minuta.
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
        viewModelScope.launch {
            val export = transfers.exportBundle()
            bundle = export.bundle
            _state.update {
                it.copy(
                    loading = false,
                    address = TransferTarget.displayAddress(valid),
                    playlistCount = export.bundle.playlists.size,
                    skipped = export.skipped,
                    openSubtitlesUser = export.bundle.accounts?.openSubtitles?.username,
                    traktAvailable = trakt.isAvailable,
                )
            }
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
        val url = target ?: return
        val token = traktToken
        val payload = (bundle ?: return).let { base ->
            if (token == null) {
                base
            } else {
                val login = TransferTraktLogin(token.accessToken, token.refreshToken, token.expiresIn, token.createdAt)
                base.copy(accounts = (base.accounts ?: TransferAccounts()).copy(trakt = login))
            }
        }
        if (_state.value.sending || !_state.value.canSend) return
        _state.update { it.copy(sending = true, error = null, errorDetail = null, result = null) }
        viewModelScope.launch {
            if (token != null) traktSent = true
            val outcome = withContext(Dispatchers.IO) { post(url + TransferTarget.BUNDLE_PATH, payload) }
            _state.update {
                when (outcome) {
                    is TransferResult -> it.copy(sending = false, result = outcome)
                    is Unreachable -> it.copy(sending = false, error = SendError.UNREACHABLE, errorDetail = outcome.detail)
                    else -> it.copy(sending = false, error = outcome as? SendError)
                }
            }
        }
    }

    /** Vraća [TransferResult], [SendError] ili [Unreachable]. */
    private fun post(url: String, payload: TransferBundle): Any {
        val request = Request.Builder()
            .url(url)
            .post(TransferCodec.encode(payload).toRequestBody(JSON))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val result = if (response.isSuccessful) TransferCodec.decodeResult(response.body.string()) else null
                result ?: SendError.REFUSED
            }
        } catch (e: IOException) {
            // Poruka sadrži samo adresu TV-a u kućnoj mreži, ne i sadržaj paketa.
            Unreachable(listOfNotNull(e.javaClass.simpleName, e.message).joinToString(": ").take(MAX_DETAIL_LENGTH))
        }
    }

    private class Unreachable(val detail: String)

    override fun onCleared() {
        // Prijava koja nije stigla do TV-a se opoziva, da ne ostane neiskorišćena na nalogu.
        val unused = traktToken?.takeIf { !traktSent } ?: return
        cleanupScope.launch { trakt.discard(unused) }
    }

    private companion object {
        const val READ_TIMEOUT_MINUTES = 15L
        const val MAX_DETAIL_LENGTH = 200

        /** Nadživljava ekran: opoziv neupotrebljene prijave ide posle njegovog zatvaranja. */
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
