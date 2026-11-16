package com.bane23.iptvplayer.ui.common.subtitles

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.lan.LoginServer
import com.bane23.iptvplayer.data.lan.LoginSubmission
import com.bane23.iptvplayer.data.lan.LoginTexts
import com.bane23.iptvplayer.data.lan.PairingServer
import com.bane23.iptvplayer.data.subtitles.OpenSubtitlesRepository
import com.bane23.iptvplayer.data.subtitles.SubtitleException
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

data class OpenSubtitlesPairingUiState(
    /** Adresa koju telefon treba da otvori; null dok server ne krene ili ako nema lokalne mreže. */
    val url: String? = null,
    /** Telefon je poslao podatke i prijava je u toku. */
    val working: Boolean = false,
)

/**
 * Prijava na OpenSubtitles preko telefona: dok je ekran naloga na TV-u otvoren radi mali server u
 * lokalnoj mreži, a telefon preko QR koda otvara njegovu stranicu i šalje korisničko ime i lozinku.
 * Server se gasi čim se ekran zatvori.
 */
@HiltViewModel
class OpenSubtitlesPairingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: OpenSubtitlesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OpenSubtitlesPairingUiState())
    val state: StateFlow<OpenSubtitlesPairingUiState> = _state.asStateFlow()

    private val server = LoginServer(texts(), ::submit)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val address = PairingServer.localAddress() ?: return@launch
            val port = try {
                server.start()
            } catch (_: IOException) {
                return@launch
            }
            _state.update { it.copy(url = "http://$address:$port/${server.token}") }
        }
    }

    /** Poziva ga server na svojoj niti; čeka ishod prijave, da ga telefon odmah vidi. */
    private fun submit(submission: LoginSubmission): String? {
        if (submission.username.isEmpty() || submission.password.isEmpty()) {
            return context.getString(R.string.error_missing_fields)
        }
        _state.update { it.copy(working = true) }
        return try {
            runBlocking { repository.login(submission.username, submission.password) }
            null
        } catch (e: SubtitleException) {
            context.getString(e.failure.message())
        } finally {
            _state.update { it.copy(working = false) }
        }
    }

    override fun onCleared() {
        server.stop()
    }

    private fun texts() = LoginTexts(
        title = context.getString(R.string.opensubtitles_page_title),
        intro = context.getString(R.string.opensubtitles_page_intro),
        username = context.getString(R.string.field_username),
        password = context.getString(R.string.field_password),
        submit = context.getString(R.string.opensubtitles_page_submit),
        doneTitle = context.getString(R.string.opensubtitles_page_done_title),
        doneText = context.getString(R.string.pairing_page_done_text),
        notFound = context.getString(R.string.pairing_page_not_found),
    )
}
