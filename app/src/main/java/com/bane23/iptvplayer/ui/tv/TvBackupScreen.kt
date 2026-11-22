package com.bane23.iptvplayer.ui.tv

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.backup.BackupImportState
import com.bane23.iptvplayer.data.storage.PublicFolder
import com.bane23.iptvplayer.ui.common.AppMessages
import com.bane23.iptvplayer.ui.common.backup.BackupEffects
import com.bane23.iptvplayer.ui.common.backup.BackupViewModel
import com.bane23.iptvplayer.ui.common.backup.summary
import com.bane23.iptvplayer.ui.common.backup.text
import com.bane23.iptvplayer.ui.common.icons.AppIcons
import com.bane23.iptvplayer.ui.common.theme.glowBackground
import java.text.DateFormat
import java.util.Date

/**
 * Izvoz svih lista i podešavanja u fajl i uvoz iz njega, na TV-u. Fajl ide u Downloads/Glowbox, a
 * uvoz nudi fajlove nađene u tom folderu. Koraci (lozinka, izbor između dopune i zamene, potvrda
 * zamene) se smenjuju na istom ekranu; Nazad vraća korak unazad.
 */
@Composable
fun TvBackupScreen(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackupEffects(viewModel, state, importState)

    var askExportPassword by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }
    var confirmReplace by remember { mutableStateOf(false) }
    // Šta se radi kad korisnik odgovori na pitanje o dozvoli za memoriju (Android 10 i stariji).
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshFiles()
        afterPermission?.invoke()
        afterPermission = null
    }
    // Posle povratka sa sistemskog ekrana za dozvolu spisak fajlova se čita iznova.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshFiles()
        onPauseOrDispose { }
    }

    val busy = state.working || importState is BackupImportState.Running
    val step = when {
        state.ready != null && confirmReplace -> Step.CONFIRM_REPLACE
        state.ready != null -> Step.MODE
        state.needsPassword -> Step.IMPORT_PASSWORD
        askExportPassword -> Step.EXPORT_PASSWORD
        else -> Step.MAIN
    }
    val cancel = {
        askExportPassword = false
        exportPassword = ""
        importPassword = ""
        confirmReplace = false
        viewModel.cancelPending()
    }
    BackHandler(enabled = step != Step.MAIN) {
        if (step == Step.CONFIRM_REPLACE) {
            confirmReplace = false
        } else {
            cancel()
        }
    }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(step) {
        // Redovi novog koraka postoje tek od sledećeg crtanja.
        withFrameNanos { }
        firstFocus.tryRequestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .glowBackground(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.background)
            // Margine zbog overscan-a na televizorima.
            .padding(horizontal = 48.dp, vertical = 27.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val rowWidth = Modifier.fillMaxWidth(0.6f)
        when (step) {
            Step.MAIN -> {
                Title(stringResource(R.string.backup_title))
                Note(stringResource(R.string.backup_intro))
                Note(stringResource(R.string.backup_tv_folder))
                TvTextRow(
                    text = stringResource(R.string.backup_export),
                    onClick = {
                        if (busy) return@TvTextRow
                        val start = { askExportPassword = true }
                        if (PublicFolder.hasPermission(context)) {
                            start()
                        } else {
                            afterPermission = start
                            try {
                                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } catch (_: Exception) {
                                afterPermission = null
                                AppMessages.show(context, R.string.backup_tv_allow_files_missing, long = true)
                            }
                        }
                    },
                    icon = AppIcons.Download,
                    modifier = rowWidth.focusRequester(firstFocus),
                )
                Note(stringResource(R.string.backup_export_summary))
                state.exportedTo?.let { Note(stringResource(R.string.backup_exported_to, it)) }
                val status = importState.text() ?: stringResource(R.string.backup_working).takeIf { state.working }
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (importState is BackupImportState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                Heading(stringResource(R.string.backup_import))
                Note(stringResource(R.string.backup_import_summary))
                if (!state.canReadForeign) {
                    TvTextRow(
                        text = stringResource(R.string.backup_tv_allow_files),
                        onClick = {
                            if (PublicFolder.needsPermission) {
                                try {
                                    storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                } catch (_: Exception) {
                                    AppMessages.show(context, R.string.backup_tv_allow_files_missing, long = true)
                                }
                            } else if (!openAllFilesAccess(context)) {
                                AppMessages.show(context, R.string.backup_tv_allow_files_missing, long = true)
                            }
                        },
                        icon = AppIcons.Lock,
                        modifier = rowWidth,
                    )
                    Note(stringResource(R.string.backup_tv_allow_files_summary))
                }
                if (state.files.isEmpty()) Note(stringResource(R.string.backup_tv_no_files))
                state.files.forEach { file ->
                    TvTextRow(
                        text = file.name,
                        trailing = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(file.modifiedAt)),
                        onClick = {
                            if (!busy) {
                                viewModel.clearImportResult()
                                viewModel.open(file.path)
                            }
                        },
                        modifier = rowWidth,
                    )
                }
                TvTextRow(
                    text = stringResource(R.string.backup_tv_refresh),
                    onClick = viewModel::refreshFiles,
                    icon = AppIcons.Refresh,
                    modifier = rowWidth,
                )
            }

            Step.EXPORT_PASSWORD -> {
                Title(stringResource(R.string.backup_password_title))
                Note(stringResource(R.string.backup_password_export_text))
                TvFilterField(
                    value = exportPassword,
                    onChange = { exportPassword = it.take(MAX_PASSWORD) },
                    placeholder = stringResource(R.string.backup_password_field),
                    icon = AppIcons.Lock,
                    modifier = rowWidth,
                )
                TvTextRow(
                    text = stringResource(if (exportPassword.isEmpty()) R.string.backup_export_plain else R.string.backup_export_protected),
                    onClick = {
                        val password = exportPassword
                        cancel()
                        viewModel.exportToFolder(password)
                    },
                    icon = AppIcons.Check,
                    modifier = rowWidth.focusRequester(firstFocus),
                )
                TvTextRow(text = stringResource(R.string.action_cancel), onClick = cancel, modifier = rowWidth)
            }

            Step.IMPORT_PASSWORD -> {
                Title(stringResource(R.string.backup_password_title))
                Note(stringResource(R.string.backup_password_import_text))
                TvFilterField(
                    value = importPassword,
                    onChange = { importPassword = it.take(MAX_PASSWORD) },
                    placeholder = stringResource(R.string.backup_password_field_required),
                    icon = AppIcons.Lock,
                    modifier = rowWidth.focusRequester(firstFocus),
                )
                if (state.wrongPassword) {
                    Text(
                        text = stringResource(R.string.backup_password_wrong),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TvTextRow(
                    text = stringResource(if (state.working) R.string.backup_working else R.string.backup_continue),
                    onClick = { if (importPassword.isNotEmpty()) viewModel.submitPassword(importPassword) },
                    icon = AppIcons.Check,
                    modifier = rowWidth,
                )
                TvTextRow(text = stringResource(R.string.action_cancel), onClick = cancel, modifier = rowWidth)
            }

            Step.MODE -> {
                Title(stringResource(R.string.backup_mode_title))
                state.ready?.let { info ->
                    Note(info.summary())
                    if (!info.fromTv) Note(stringResource(R.string.backup_mode_other_device_phone))
                }
                TvTextRow(
                    text = stringResource(R.string.backup_mode_merge),
                    onClick = {
                        importPassword = ""
                        viewModel.startImport(replace = false)
                    },
                    modifier = rowWidth.focusRequester(firstFocus),
                )
                Note(stringResource(R.string.backup_mode_merge_summary))
                TvTextRow(text = stringResource(R.string.backup_mode_replace), onClick = { confirmReplace = true }, modifier = rowWidth)
                Note(stringResource(R.string.backup_mode_replace_summary))
                TvTextRow(text = stringResource(R.string.action_cancel), onClick = cancel, modifier = rowWidth)
            }

            Step.CONFIRM_REPLACE -> {
                Title(stringResource(R.string.backup_replace_confirm_title))
                Note(stringResource(R.string.backup_replace_confirm_text))
                // Fokus je na „Otkaži”, da slučajan OK ne obriše ništa.
                TvTextRow(text = stringResource(R.string.action_cancel), onClick = cancel, modifier = rowWidth.focusRequester(firstFocus))
                TvTextRow(
                    text = stringResource(R.string.backup_replace_confirm),
                    onClick = {
                        confirmReplace = false
                        importPassword = ""
                        viewModel.startImport(replace = true)
                    },
                    icon = AppIcons.Warning,
                    modifier = rowWidth,
                )
            }
        }
    }
}

private enum class Step { MAIN, EXPORT_PASSWORD, IMPORT_PASSWORD, MODE, CONFIRM_REPLACE }

@Composable
private fun Title(text: String) {
    Text(text = text, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(0.7f),
    )
}

/**
 * Otvara sistemski ekran na kome se aplikaciji uključuje pristup svim fajlovima (Android 11+).
 * Vraća false kad uređaj takav ekran nema.
 */
private fun openAllFilesAccess(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    val intents = listOf(
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: ActivityNotFoundException) {
            // probati sledeći oblik
        } catch (_: SecurityException) {
            // probati sledeći oblik
        }
    }
    return false
}

private const val MAX_PASSWORD = 100
