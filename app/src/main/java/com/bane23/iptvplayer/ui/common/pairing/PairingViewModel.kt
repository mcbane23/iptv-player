package com.bane23.iptvplayer.ui.common.pairing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.lan.PairingResult
import com.bane23.iptvplayer.data.lan.PairingServer
import com.bane23.iptvplayer.data.lan.PairingSubmission
import com.bane23.iptvplayer.data.lan.PairingTexts
import com.bane23.iptvplayer.data.lan.TransferBundle
import com.bane23.iptvplayer.data.lan.TransferResult
import com.bane23.iptvplayer.data.repo.PlaylistRepository
import com.bane23.iptvplayer.cast.CastReceiver
import com.bane23.iptvplayer.domain.PlaylistDraft
import com.bane23.iptvplayer.domain.PlaylistError
import com.bane23.iptvplayer.domain.PlaylistError.Kind
import com.bane23.iptvplayer.domain.PlaylistException
import com.bane23.iptvplayer.domain.PlaylistType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class PairingUiState(
    val starting: Boolean = true,
    /** Adresa koju telefon treba da otvori; null dok server ne krene ili ako nema mreže. */
    val url: String? = null,
    /** Uređaj nije povezan na lokalnu mrežu ili server nije mogao da se pokrene. */
    val unavailable: Boolean = false,
    /** Lista se upravo dodaje (telefon je poslao podatke). */
    val working: Boolean = false,
    /** Naziv liste koja je upravo dodata. */
    val addedName: String? = null,
    /** Prenos svih lista iz aplikacije na telefonu: koja se lista učitava (od 1) i koliko ih je. */
    val transferStep: Int = 0,
    val transferTotal: Int = 0,
    /** Ishod poslednjeg prenosa svih lista. */
    val transferResult: TransferResult? = null,
)

/**
 * Dodavanje liste sa telefona: dok je ekran otvoren radi mali server u lokalnoj mreži, a telefon
 * preko QR koda otvara njegovu stranicu i šalje podatke. Server se gasi čim se ekran zatvori.
 */
@HiltViewModel
class PairingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlaylistRepository,
    private val castReceiver: CastReceiver,
) : ViewModel() {
    private val _state = MutableStateFlow(PairingUiState())
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    private val server = PairingServer(texts(), ::submit, onBundle = ::receive)

    init {
        start()
    }

    /** Pokreće server (i ponovo, ako prvi put nije bilo mreže). */
    fun start() {
        _state.update { it.copy(starting = true, unavailable = false) }
        viewModelScope.launch(Dispatchers.IO) {
            val address = PairingServer.localAddress()
            val port = if (address == null) {
                null
            } else {
                try {
                    server.start()
                } catch (_: IOException) {
                    null
                }
            }
            _state.update {
                if (address == null || port == null) {
                    it.copy(starting = false, unavailable = true, url = null)
                } else {
                    it.copy(starting = false, url = "http://$address:$port/${server.token}")
                }
            }
        }
    }

    /** Poziva ga server na svojoj niti; čeka da se lista učita, da telefon odmah dobije ishod. */
    private fun submit(submission: PairingSubmission): PairingResult {
        val draft = if (submission.xtream) {
            PlaylistDraft(
                type = PlaylistType.XTREAM,
                name = submission.name.ifBlank { context.getString(R.string.pairing_default_name) },
                source = submission.server,
                username = submission.username,
                password = submission.password,
                epgUrl = submission.epgUrl,
            )
        } else {
            PlaylistDraft(
                type = PlaylistType.M3U_URL,
                name = submission.name.ifBlank { context.getString(R.string.pairing_default_name) },
                source = submission.url,
                epgUrl = submission.epgUrl,
            )
        }
        _state.update { it.copy(working = true, transferResult = null) }
        return try {
            runBlocking { repository.addPlaylist(draft) }
            _state.update { it.copy(working = false, addedName = draft.name) }
            PairingResult.Added(draft.name)
        } catch (e: PlaylistException) {
            _state.update { it.copy(working = false) }
            PairingResult.Failed(message(e.error))
        }
    }

    /**
     * Paket sa svim listama iz aplikacije na telefonu. Poziva ga server na svojoj niti. Ide istim
     * putem kao prenos iz podešavanja telefona: telefonu se odgovara odmah, a nove liste TV učitava
     * u pozadini i napredak pokazuje preko cele aplikacije, i kad se ovaj ekran zatvori.
     */
    private fun receive(bundle: TransferBundle): TransferResult {
        _state.update { it.copy(addedName = null, transferResult = null) }
        return castReceiver.transfer(bundle)
    }

    override fun onCleared() {
        server.stop()
    }

    /** Poruka o grešci za stranicu na telefonu. Bez tehničkih detalja: u njima može da bude adresa sa lozinkom. */
    private fun message(error: PlaylistError): String = when (error.kind) {
        Kind.MISSING_FIELDS -> context.getString(R.string.error_missing_fields)
        Kind.INVALID_URL -> context.getString(R.string.error_invalid_url)
        Kind.NO_NETWORK -> context.getString(R.string.error_no_network)
        Kind.TIMEOUT -> context.getString(R.string.error_timeout)
        Kind.HOST_NOT_FOUND -> context.getString(R.string.error_host_not_found)
        Kind.SSL_ERROR -> context.getString(R.string.error_ssl)
        Kind.HTTP_ERROR -> context.getString(R.string.error_http, error.httpCode ?: 0)
        Kind.EMPTY_PLAYLIST -> context.getString(R.string.error_empty_playlist)
        Kind.FILE_UNREADABLE -> context.getString(R.string.error_file_unreadable)
        Kind.AUTH_FAILED -> context.getString(R.string.error_auth_failed)
        Kind.ACCOUNT_EXPIRED -> context.getString(R.string.error_account_expired)
        Kind.ACCOUNT_DISABLED -> context.getString(R.string.error_account_disabled)
        Kind.BAD_RESPONSE -> context.getString(R.string.error_bad_response)
        Kind.CREDENTIALS_UNREADABLE -> context.getString(R.string.error_credentials_unreadable)
        Kind.UNKNOWN -> context.getString(R.string.error_unknown)
    }

    private fun texts() = PairingTexts(
        title = context.getString(R.string.pairing_page_title),
        intro = context.getString(R.string.pairing_page_intro),
        xtreamHeading = context.getString(R.string.playlist_type_xtream),
        m3uHeading = context.getString(R.string.playlist_type_m3u_url),
        name = context.getString(R.string.pairing_field_name),
        server = context.getString(R.string.field_server),
        username = context.getString(R.string.field_username),
        password = context.getString(R.string.field_password),
        m3uUrl = context.getString(R.string.field_m3u_url),
        epgUrl = context.getString(R.string.field_epg_url),
        submit = context.getString(R.string.pairing_page_submit),
        wait = context.getString(R.string.pairing_page_wait),
        doneTitle = context.getString(R.string.pairing_page_done_title),
        doneText = context.getString(R.string.pairing_page_done_text),
        notFound = context.getString(R.string.pairing_page_not_found),
        appHeading = context.getString(R.string.pairing_page_app_heading),
        appText = context.getString(R.string.pairing_page_app_text),
        appButton = context.getString(R.string.pairing_page_app_button),
    )
}
