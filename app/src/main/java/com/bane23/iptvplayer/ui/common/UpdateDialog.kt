package com.bane23.iptvplayer.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.update.AppUpdater
import com.bane23.iptvplayer.data.update.UpdateChannel
import com.bane23.iptvplayer.data.update.UpdateError
import com.bane23.iptvplayer.data.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class UpdateViewModel @Inject constructor(private val updater: AppUpdater) : ViewModel() {
    val state: StateFlow<UpdateState> = updater.state
    val installedVersion: String = updater.installedVersionName
    val channel: StateFlow<UpdateChannel> =
        updater.channel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateChannel.DEFAULT)

    fun setChannel(channel: UpdateChannel) = updater.setChannel(channel)

    fun checkOnStart() = updater.checkOnStart()
    fun check() = updater.check(userInitiated = true)
    fun update() = updater.downloadAndInstall()
    fun dismiss() = updater.dismiss()
    fun canInstall() = updater.canInstall()
}

/**
 * Poruke o ažuriranju aplikacije, iznad svih ekrana. Pri startu tiho proverava novu verziju
 * i javlja se samo ako je ima. Radi na dodir i daljinskim (fokus odmah na glavnom dugmetu).
 */
@Composable
fun UpdateDialog(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.checkOnStart() }

    // Povratak iz sistemskih podešavanja: ako je dozvola data, instalacija kreće sama.
    LifecycleResumeEffect(state) {
        if (state is UpdateState.NeedsPermission && viewModel.canInstall()) viewModel.update()
        onPauseOrDispose { }
    }

    // Jedan dijalog za sva stanja: menja se samo sadržaj, pa prozor ne nestaje i ne treperi
    // (npr. „Proveravam…” odmah prelazi u „Imate najnoviju verziju”).
    val content: AlertContent? = when (val s = state) {
        UpdateState.Idle -> null
        UpdateState.Checking -> AlertContent(
            title = stringResource(R.string.update_title),
            text = stringResource(R.string.update_checking),
            progress = -1f,
            confirm = stringResource(R.string.update_close) to viewModel::dismiss,
            onDismiss = viewModel::dismiss,
        )
        UpdateState.UpToDate -> AlertContent(
            title = stringResource(R.string.update_title),
            text = stringResource(R.string.update_up_to_date, viewModel.installedVersion),
            confirm = stringResource(R.string.update_ok) to viewModel::dismiss,
            onDismiss = viewModel::dismiss,
        )
        is UpdateState.Available -> AlertContent(
            title = stringResource(R.string.update_available_title),
            text = stringResource(
                R.string.update_available_text,
                s.manifest.versionName,
                viewModel.installedVersion,
            ),
            confirm = stringResource(R.string.update_install) to viewModel::update,
            dismiss = stringResource(R.string.update_later) to viewModel::dismiss,
            onDismiss = viewModel::dismiss,
        )
        is UpdateState.Downloading -> AlertContent(
            title = stringResource(R.string.update_available_title),
            text = s.progress?.let { stringResource(R.string.update_downloading_percent, (it * 100).toInt()) }
                ?: stringResource(R.string.update_downloading),
            progress = s.progress ?: -1f,
            confirm = stringResource(R.string.update_cancel) to viewModel::dismiss,
            // Slučajan dodir pored dijaloga ne prekida preuzimanje.
            onDismiss = {},
        )
        is UpdateState.NeedsPermission -> AlertContent(
            title = stringResource(R.string.update_permission_title),
            text = stringResource(R.string.update_permission_text),
            confirm = stringResource(R.string.update_open_settings) to { openInstallPermission(context) },
            dismiss = stringResource(R.string.update_cancel) to viewModel::dismiss,
            onDismiss = viewModel::dismiss,
        )
        is UpdateState.Installing -> AlertContent(
            title = stringResource(R.string.update_available_title),
            text = stringResource(R.string.update_installing),
            confirm = stringResource(R.string.update_close) to viewModel::dismiss,
            onDismiss = viewModel::dismiss,
        )
        is UpdateState.Failed -> AlertContent(
            title = stringResource(R.string.update_title),
            text = stringResource(
                when (s.reason) {
                    UpdateError.NETWORK -> R.string.update_error_network
                    UpdateError.BAD_FILE -> R.string.update_error_file
                    UpdateError.INSTALL -> R.string.update_error_install
                },
            ),
            confirm = stringResource(R.string.update_ok) to viewModel::dismiss,
            onDismiss = viewModel::dismiss,
        )
    }
    if (content != null) UpdateAlert(content)
}

private data class AlertContent(
    val title: String,
    val text: String,
    val confirm: Pair<String, () -> Unit>,
    val onDismiss: () -> Unit,
    val dismiss: Pair<String, () -> Unit>? = null,
    /** Napredak od 0 do 1, negativno za neodređen, null bez trake. */
    val progress: Float? = null,
)

@Composable
private fun UpdateAlert(content: AlertContent) {
    val (title, text, confirm, onDismiss, dismiss, progress) = content
    val confirmFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text)
                if (progress != null) {
                    val modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                    if (progress < 0f) {
                        LinearProgressIndicator(modifier = modifier)
                    } else {
                        LinearProgressIndicator(progress = { progress }, modifier = modifier)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = confirm.second, modifier = Modifier.focusRequester(confirmFocus)) {
                Text(confirm.first)
            }
        },
        dismissButton = dismiss?.let { (label, action) ->
            { TextButton(onClick = action) { Text(label) } }
        },
    )
    // Na TV-u fokus mora odmah biti na dugmetu, inače daljinski nema šta da pritisne.
    LaunchedEffect(title, confirm.first) { runCatching { confirmFocus.requestFocus() } }
}

/** Otvara sistemsko podešavanje „Instaliranje nepoznatih aplikacija” za ovu aplikaciju. */
private fun openInstallPermission(context: Context) {
    val intents = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
        add(Intent(Settings.ACTION_SECURITY_SETTINGS))
        add(Intent(Settings.ACTION_SETTINGS))
    }
    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

/** Naziv kanala ažuriranja za podešavanja. */
@Composable
fun UpdateChannel.label(): String = stringResource(
    when (this) {
        UpdateChannel.RELEASE -> R.string.update_channel_release
        UpdateChannel.DEV -> R.string.update_channel_dev
    },
)
