package com.bane23.iptvplayer.ui.common.recording

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.db.RecordingEntity
import com.bane23.iptvplayer.data.recording.RecordingStorage
import com.bane23.iptvplayer.ui.common.guide.formatTime
import com.bane23.iptvplayer.ui.common.player.formatDuration
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.Flow

/** Drugi red snimka: kanal, početak, trajanje i veličina (ili stanje, kad nije običan završen snimak). */
@Composable
fun RecordingEntity.details(): String {
    val context = LocalContext.current
    val parts = mutableListOf<String>()
    if (channelName != title) parts += channelName
    parts += DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(startedAt))
    when (status) {
        RecordingEntity.STATUS_RECORDING -> {
            parts += stringResource(R.string.recording_in_progress)
            parts += Formatter.formatShortFileSize(context, sizeBytes)
        }
        RecordingEntity.STATUS_FAILED -> parts += stringResource(errorText())
        RecordingEntity.STATUS_SCHEDULED -> {
            parts += stringResource(R.string.recording_scheduled_label)
            stopAtMs?.let { parts += stringResource(R.string.recording_until, formatTime(it)) }
        }
        else -> {
            endedAt?.let { parts += formatDuration(it - startedAt) }
            parts += Formatter.formatShortFileSize(context, sizeBytes)
            if (error != null) parts += stringResource(errorText())
        }
    }
    return parts.joinToString(" · ")
}

private fun RecordingEntity.errorText(): Int = when (error) {
    RecordingEntity.ERROR_SPACE -> R.string.recording_error_space
    RecordingEntity.ERROR_UNSUPPORTED -> R.string.recording_error_unsupported
    RecordingEntity.ERROR_MISSED -> R.string.recording_error_missed
    RecordingEntity.ERROR_NO_CHANNEL -> R.string.recording_error_channel
    else -> if (status == RecordingEntity.STATUS_FAILED) R.string.recording_error_failed else R.string.recording_error_interrupted
}

/** Može da se pusti: ima fajl sa sadržajem. */
val RecordingEntity.playable: Boolean get() = status != RecordingEntity.STATUS_FAILED && sizeBytes > 0

@Composable
fun RecordingStorage.label(): String {
    val context = LocalContext.current
    return stringResource(
        R.string.recording_storage,
        Formatter.formatShortFileSize(context, usedBytes),
        Formatter.formatShortFileSize(context, freeBytes),
    )
}

/** Prikazuje kratke poruke o snimanju iz plejera. */
@Composable
fun RecordingNotices(notices: Flow<Int>) {
    val context = LocalContext.current
    LaunchedEffect(notices) {
        notices.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
}

/**
 * Dugme za snimanje: na Androidu 13+ prvo traži dozvolu za obaveštenja, da se vidi da snimanje traje
 * i da može da se zaustavi iz obaveštenja. Snimanje kreće i kad korisnik dozvolu odbije.
 */
@Composable
fun rememberRecordingToggle(isRecording: Boolean, toggle: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { toggle() }
    return {
        val needsPermission = !isRecording && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            try {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (_: Exception) {
                // Uređaj nema ekran za dozvole (neki televizori): snima se bez obaveštenja.
                toggle()
            }
        } else {
            toggle()
        }
    }
}
