package com.bane23.iptvplayer.ui.mobile

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.backup.BackupImportState
import com.bane23.iptvplayer.ui.common.AppMessages
import com.bane23.iptvplayer.ui.common.backup.BackupEffects
import com.bane23.iptvplayer.ui.common.backup.BackupViewModel
import com.bane23.iptvplayer.ui.common.backup.summary
import com.bane23.iptvplayer.ui.common.backup.text

/**
 * Izvoz svih lista i podešavanja u fajl i uvoz iz njega, na telefonu. Mesto i fajl se biraju
 * sistemskim biračem; pre izvoza se nudi lozinka, a pre uvoza izbor između dopune i zamene.
 */
@Composable
fun MobileBackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackupEffects(viewModel, state, importState)

    // Lozinka se ne čuva u sačuvanom stanju ekrana.
    var exportPassword by remember { mutableStateOf("") }
    var askExportPassword by remember { mutableStateOf(false) }
    var importPassword by remember { mutableStateOf("") }
    var confirmReplace by remember { mutableStateOf(false) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        viewModel.exportTo(uri)
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(uri)
    }
    val busy = state.working || importState is BackupImportState.Running

    AppScaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTopBar(title = stringResource(R.string.backup_title), onBack = onBack)
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.backup_intro), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { askExportPassword = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backup_export))
                }
                Text(
                    text = stringResource(R.string.backup_export_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        viewModel.clearImportResult()
                        try {
                            // Fajl upravnici ne prijavljuju svi istu vrstu za .json, pa se nude svi fajlovi.
                            openFile.launch(arrayOf("*/*"))
                        } catch (_: ActivityNotFoundException) {
                            AppMessages.show(context, R.string.backup_file_unreadable)
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.backup_import))
                }
                Text(
                    text = stringResource(R.string.backup_import_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val status = importState.text() ?: stringResource(R.string.backup_working).takeIf { state.working }
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (importState is BackupImportState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    if (askExportPassword) {
        AlertDialog(
            onDismissRequest = { askExportPassword = false },
            title = { Text(stringResource(R.string.backup_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.backup_password_export_text))
                    PasswordField(exportPassword, { exportPassword = it }, stringResource(R.string.backup_password_field))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askExportPassword = false
                    viewModel.armExport(exportPassword)
                    exportPassword = ""
                    try {
                        createFile.launch(viewModel.suggestedFileName)
                    } catch (_: ActivityNotFoundException) {
                        AppMessages.show(context, R.string.backup_export_failed)
                    }
                }) {
                    Text(
                        stringResource(if (exportPassword.isEmpty()) R.string.backup_export_plain else R.string.backup_export_protected),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    askExportPassword = false
                    exportPassword = ""
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (state.needsPassword) {
        val cancel = {
            importPassword = ""
            viewModel.cancelPending()
        }
        AlertDialog(
            onDismissRequest = cancel,
            title = { Text(stringResource(R.string.backup_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.backup_password_import_text))
                    PasswordField(importPassword, { importPassword = it }, stringResource(R.string.backup_password_field_required))
                    if (state.wrongPassword) {
                        Text(stringResource(R.string.backup_password_wrong), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.submitPassword(importPassword) }, enabled = importPassword.isNotEmpty() && !state.working) {
                    Text(stringResource(if (state.working) R.string.backup_working else R.string.backup_continue))
                }
            },
            dismissButton = { TextButton(onClick = cancel) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    state.ready?.let { info ->
        val cancel = {
            importPassword = ""
            confirmReplace = false
            viewModel.cancelPending()
        }
        if (confirmReplace) {
            AlertDialog(
                onDismissRequest = cancel,
                title = { Text(stringResource(R.string.backup_replace_confirm_title)) },
                text = { Text(stringResource(R.string.backup_replace_confirm_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmReplace = false
                        importPassword = ""
                        viewModel.startImport(replace = true)
                    }) { Text(stringResource(R.string.backup_replace_confirm)) }
                },
                dismissButton = { TextButton(onClick = cancel) { Text(stringResource(R.string.action_cancel)) } },
            )
        } else {
            AlertDialog(
                onDismissRequest = cancel,
                title = { Text(stringResource(R.string.backup_mode_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(info.summary())
                        if (info.fromTv) {
                            Text(stringResource(R.string.backup_mode_other_device_tv), style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = {
                                importPassword = ""
                                viewModel.startImport(replace = false)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.backup_mode_merge)) }
                        Text(stringResource(R.string.backup_mode_merge_summary), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { confirmReplace = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.backup_mode_replace))
                        }
                        Text(stringResource(R.string.backup_mode_replace_summary), style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = cancel) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

/** Lozinka se vidi dok se kuca: greška u kucanju bi fajl učinila nečitljivim zauvek. */
@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(MAX_PASSWORD)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Text,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val MAX_PASSWORD = 100
