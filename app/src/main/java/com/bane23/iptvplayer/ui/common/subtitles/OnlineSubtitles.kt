package com.bane23.iptvplayer.ui.common.subtitles

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.subtitles.OpenSubtitlesRepository
import com.bane23.iptvplayer.data.subtitles.SubtitleException
import com.bane23.iptvplayer.data.subtitles.SubtitleFailure
import com.bane23.iptvplayer.data.subtitles.SubtitleResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@StringRes
fun SubtitleFailure.message(): Int = when (this) {
    SubtitleFailure.NETWORK -> R.string.subtitles_error_network
    SubtitleFailure.QUOTA -> R.string.subtitles_error_quota
    SubtitleFailure.BAD_LOGIN -> R.string.subtitles_error_login
    SubtitleFailure.FAILED -> R.string.subtitles_error_failed
}

/** Naziv jezika titla na jeziku interfejsa (npr. "sr" daje „Srpski”). */
@Composable
fun SubtitleResult.languageName(): String {
    if (language.isEmpty()) return "?"
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val name = Locale.forLanguageTag(language).getDisplayName(locale)
    return name.ifEmpty { language }.replaceFirstChar { it.titlecase(locale) }
}

/** Drugi red uz titl: jezik, broj preuzimanja i napomene. */
@Composable
fun SubtitleResult.details(): String = listOfNotNull(
    languageName(),
    "↓ $downloads",
    stringResource(R.string.subtitles_machine).takeIf { machineTranslated },
    stringResource(R.string.subtitles_hearing).takeIf { hearingImpaired },
).joinToString("  ·  ")

/** Kratka poruka da je titl uključen, sa brojem preostalih preuzimanja kad ga server javi. */
fun appliedSubtitleToast(context: Context, remaining: Int?) {
    val text = if (remaining != null) {
        context.getString(R.string.subtitles_applied_remaining, remaining)
    } else {
        context.getString(R.string.subtitles_applied)
    }
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

data class OpenSubtitlesUiState(
    /** Korisničko ime prijavljenog naloga, ili null kad prijave nema. */
    val username: String? = null,
    val signingIn: Boolean = false,
    @StringRes val message: Int? = null,
)

/** Ekran OpenSubtitles naloga: prijava korisničkim imenom i lozinkom, i odjava. */
@HiltViewModel
class OpenSubtitlesViewModel @Inject constructor(
    private val repository: OpenSubtitlesRepository,
) : ViewModel() {

    private val local = MutableStateFlow(OpenSubtitlesUiState())

    val state: StateFlow<OpenSubtitlesUiState> = combine(local, repository.username) { current, name ->
        current.copy(username = name)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpenSubtitlesUiState())

    fun signIn(username: String, password: String) {
        if (local.value.signingIn || username.isBlank() || password.isEmpty()) return
        local.update { it.copy(signingIn = true, message = null) }
        viewModelScope.launch {
            val message = try {
                repository.login(username, password)
                null
            } catch (e: SubtitleException) {
                e.failure.message()
            }
            local.update { it.copy(signingIn = false, message = message) }
        }
    }

    fun signOut() {
        local.update { it.copy(message = null) }
        viewModelScope.launch { repository.logout() }
    }
}
