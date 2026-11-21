package com.bane23.iptvplayer.ui.common.download

import android.text.format.Formatter
import com.bane23.iptvplayer.ui.common.AppMessages
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.db.DownloadEntity
import com.bane23.iptvplayer.data.download.DownloadState
import com.bane23.iptvplayer.data.download.DownloadStorage
import com.bane23.iptvplayer.ui.common.recording.rememberRecordingToggle
import kotlinx.coroutines.flow.Flow

/** Drugi red preuzimanja u spisku: stanje, procenat i veličina. */
@Composable
fun DownloadItem.details(): String {
    val context = LocalContext.current
    val size = (if (totalBytes > 0) totalBytes else bytes).takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
    val parts = when (state) {
        DownloadState.DOWNLOADING -> listOfNotNull(stringResource(R.string.download_state_downloading), percent?.let { "$it%" }, size)
        DownloadState.DONE -> listOfNotNull(stringResource(R.string.download_state_done), size)
        DownloadState.FAILED -> listOf(
            stringResource(
                if (entity.error == DownloadEntity.ERROR_SPACE) R.string.download_error_space else R.string.download_state_failed,
            ),
        )
        else -> listOfNotNull(stringResource(state.waitingText()), percent?.takeIf { it > 0 }?.let { "$it%" })
    }
    return parts.joinToString(" · ")
}

/** Kratko stanje za dugme u detaljima filma, npr. „Preuzimanje · 62%”. */
@Composable
fun DownloadItem.shortLabel(): String = when (state) {
    DownloadState.DOWNLOADING ->
        listOfNotNull(stringResource(R.string.download_state_downloading), percent?.let { "$it%" }).joinToString(" · ")
    DownloadState.DONE -> stringResource(R.string.download_state_done)
    DownloadState.FAILED -> stringResource(R.string.download_state_failed)
    else -> stringResource(state.waitingText())
}

private fun DownloadState.waitingText(): Int = when (this) {
    DownloadState.HELD_PLAYING -> R.string.download_state_held_playing
    DownloadState.HELD_WIFI -> R.string.download_state_held_wifi
    DownloadState.HELD_OFFLINE -> R.string.download_state_held_offline
    DownloadState.PAUSED -> R.string.download_state_paused
    else -> R.string.download_state_waiting
}

@Composable
fun DownloadStorage.label(): String {
    val context = LocalContext.current
    return stringResource(
        R.string.download_storage,
        Formatter.formatShortFileSize(context, usedBytes),
        Formatter.formatShortFileSize(context, freeBytes),
    )
}

/** Prikazuje kratke poruke o preuzimanju. */
@Composable
fun DownloadNotices(notices: Flow<Int>) {
    val context = LocalContext.current
    LaunchedEffect(notices) {
        notices.collect { AppMessages.show(context, it) }
    }
}

/**
 * Dugme „Preuzmi”: na Androidu 13+ prvo traži dozvolu za obaveštenja, da se napredak vidi i da
 * preuzimanje može da se pauzira iz obaveštenja. Preuzimanje kreće i kad korisnik dozvolu odbije.
 */
@Composable
fun rememberDownloadStart(start: () -> Unit): () -> Unit = rememberRecordingToggle(isRecording = false, toggle = start)
