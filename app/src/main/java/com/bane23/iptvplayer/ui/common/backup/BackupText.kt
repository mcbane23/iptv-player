package com.bane23.iptvplayer.ui.common.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.backup.BackupImportState
import com.bane23.iptvplayer.ui.common.AppLocale
import com.bane23.iptvplayer.ui.common.AppMessages
import java.text.DateFormat
import java.util.Date

/** Tekst o toku ili ishodu uvoza, ili null dok uvoza nema. */
@Composable
fun BackupImportState.text(): String? = when (this) {
    BackupImportState.Idle -> null
    is BackupImportState.Running ->
        if (total > 0) stringResource(R.string.backup_importing_list, current, total) else stringResource(R.string.backup_importing)
    is BackupImportState.Done -> stringResource(R.string.backup_import_done, result.added, result.existing, result.failed)
    BackupImportState.Failed -> stringResource(R.string.backup_import_failed)
}

/** Red sa datumom izvoza i brojem lista u fajlu. */
@Composable
fun BackupInfo.summary(): String {
    val date = if (createdAt > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(createdAt)) else "?"
    return stringResource(R.string.backup_mode_info, date, playlists)
}

/**
 * Zajedničko za telefon i TV: kratke poruke iz [viewModel] idu u poruke aplikacije, a kad uvoz
 * promeni jezik, ekran se pravi iznova da se tekstovi odmah vide na njemu.
 */
@Composable
fun BackupEffects(viewModel: BackupViewModel, state: BackupUiState, importState: BackupImportState) {
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        val text = if (notice.arg != null) resources.getString(notice.text, notice.arg) else resources.getString(notice.text)
        AppMessages.show(text, long = true)
        viewModel.noticeShown()
    }
    LaunchedEffect(importState) {
        if (importState is BackupImportState.Done && importState.result.languageChanged) {
            viewModel.languageApplied()
            AppLocale.recreate(context)
        }
    }
}
