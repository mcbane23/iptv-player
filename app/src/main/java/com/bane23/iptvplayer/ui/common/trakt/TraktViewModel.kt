package com.bane23.iptvplayer.ui.common.trakt

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.trakt.DeviceCode
import com.bane23.iptvplayer.data.trakt.TraktLoginResult
import com.bane23.iptvplayer.data.trakt.TraktRepository
import com.bane23.iptvplayer.data.trakt.TraktSyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Korak prijave na Trakt. */
sealed interface TraktLogin {
    data object Idle : TraktLogin

    data object Starting : TraktLogin

    /** Korisnik treba da otvori [DeviceCode.verificationUrl] i unese [DeviceCode.userCode]. */
    data class Waiting(val code: DeviceCode) : TraktLogin
}

data class TraktUiState(
    /** Korisničko ime ("" ako nije poznato), ili null kad nalog nije povezan. */
    val username: String? = null,
    val lastSyncMs: Long? = null,
    val login: TraktLogin = TraktLogin.Idle,
    val syncing: Boolean = false,
    /** Automatska sinhronizacija je zaustavljena dok ručna ne uspe. */
    val syncPaused: Boolean = false,
    @StringRes val message: Int? = null,
) {
    val connected: Boolean get() = username != null
}

/** Ekran Trakt naloga: prijava kodom, ručna sinhronizacija i odjava. */
@HiltViewModel
class TraktViewModel @Inject constructor(
    private val trakt: TraktRepository,
) : ViewModel() {

    private val local = MutableStateFlow(TraktUiState())
    private var loginJob: Job? = null

    val state: StateFlow<TraktUiState> = combine(local, trakt.username, trakt.lastSyncMs, trakt.syncPaused) { current, name, lastSync, paused ->
        current.copy(username = name, lastSyncMs = lastSync, syncPaused = paused)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TraktUiState())

    /** Privremeni dnevnik razmene pozicija (dok se ne nađe zašto se pozicija gubi između uređaja). */
    val playbackLog = trakt.playbackLogEntries

    fun connect() {
        loginJob?.cancel()
        local.update { it.copy(login = TraktLogin.Starting, message = null) }
        loginJob = viewModelScope.launch {
            val pending = try {
                trakt.startLogin()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                local.update { it.copy(login = TraktLogin.Idle, message = R.string.trakt_error_network) }
                return@launch
            }
            local.update { it.copy(login = TraktLogin.Waiting(pending.code)) }
            val message = when (trakt.awaitLogin(pending)) {
                TraktLoginResult.SUCCESS -> null
                TraktLoginResult.EXPIRED -> R.string.trakt_error_expired
                TraktLoginResult.DENIED -> R.string.trakt_error_denied
                TraktLoginResult.FAILED -> R.string.trakt_error_network
            }
            local.update { it.copy(login = TraktLogin.Idle, message = message) }
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        loginJob = null
        local.update { it.copy(login = TraktLogin.Idle) }
    }

    fun syncNow() {
        if (local.value.syncing) return
        local.update { it.copy(syncing = true, message = null) }
        viewModelScope.launch {
            val result = trakt.sync()
            local.update {
                it.copy(syncing = false, message = if (result == TraktSyncResult.FAILED) R.string.trakt_sync_failed else null)
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            trakt.logout()
            local.update { it.copy(message = null) }
        }
    }
}
