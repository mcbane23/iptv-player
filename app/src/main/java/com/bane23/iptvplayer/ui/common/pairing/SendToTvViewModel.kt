package com.bane23.iptvplayer.ui.common.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.data.lan.TransferBundle
import com.bane23.iptvplayer.data.lan.TransferCodec
import com.bane23.iptvplayer.data.lan.TransferResult
import com.bane23.iptvplayer.data.lan.TransferTarget
import com.bane23.iptvplayer.data.repo.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
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
)

/**
 * Slanje svih lista i podešavanja na TV. Otvara se linkom sa stranice TV-a; ništa se ne šalje dok
 * korisnik ne potvrdi, jer paket sadrži lozinke naloga.
 */
@HiltViewModel
class SendToTvViewModel @Inject constructor(
    private val transfers: TransferRepository,
    httpClient: OkHttpClient,
) : ViewModel() {
    private val _state = MutableStateFlow(SendToTvUiState())
    val state: StateFlow<SendToTvUiState> = _state.asStateFlow()

    // TV učitava svaku listu pre nego što odgovori, pa odgovor može da kasni više minuta.
    private val client = httpClient.newBuilder().readTimeout(READ_TIMEOUT_MINUTES, TimeUnit.MINUTES).build()
    private var target: String? = null
    private var bundle: TransferBundle? = null

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
                )
            }
        }
    }

    fun send() {
        val url = target ?: return
        val payload = bundle ?: return
        if (_state.value.sending || payload.playlists.isEmpty()) return
        _state.update { it.copy(sending = true, error = null, result = null) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { post(url + TransferTarget.BUNDLE_PATH, payload) }
            _state.update {
                it.copy(sending = false, result = outcome as? TransferResult, error = outcome as? SendError)
            }
        }
    }

    /** Vraća [TransferResult] ili [SendError]. */
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
        } catch (_: IOException) {
            SendError.UNREACHABLE
        }
    }

    private companion object {
        const val READ_TIMEOUT_MINUTES = 15L
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
