package com.bane23.iptvplayer.ui.common.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.player.PlaybackFailure
import com.bane23.iptvplayer.player.PlaybackState
import com.bane23.iptvplayer.player.PlaybackStatus

@Composable
fun AspectMode.label(): String = when (this) {
    AspectMode.FIT -> stringResource(R.string.aspect_fit)
    AspectMode.FILL -> stringResource(R.string.aspect_fill)
    AspectMode.ZOOM -> stringResource(R.string.aspect_zoom)
}

@Composable
fun PlaybackFailure.message(): String = when (this) {
    PlaybackFailure.NETWORK -> stringResource(R.string.playback_error_network)
    PlaybackFailure.NOT_FOUND -> stringResource(R.string.playback_error_not_found)
    PlaybackFailure.FORBIDDEN -> stringResource(R.string.playback_error_forbidden)
    PlaybackFailure.UNSUPPORTED -> stringResource(R.string.playback_error_unsupported)
    PlaybackFailure.UNKNOWN -> stringResource(R.string.playback_error_unknown)
}

/**
 * Tekst koji se prikazuje preko slike kad se ne gleda normalno (učitavanje, ponovni pokušaj, greška),
 * ili null kad nema šta da se prikaže.
 */
@Composable
fun statusMessage(ui: PlayerUiState, playback: PlaybackState): String? = when {
    ui.sourceMissing -> stringResource(R.string.playback_error_source)
    playback.status == PlaybackStatus.FAILED -> {
        val text = (playback.failure ?: PlaybackFailure.UNKNOWN).message()
        if (playback.failureDetail.isNullOrBlank()) text else "$text\n\n${playback.failureDetail}"
    }
    playback.status == PlaybackStatus.RETRYING -> stringResource(R.string.playback_retrying)
    playback.status == PlaybackStatus.LOADING -> stringResource(R.string.playback_loading)
    else -> null
}
