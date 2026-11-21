package com.bane23.iptvplayer.ui.common

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.crash.CrashLog
import com.bane23.iptvplayer.data.crash.CrashReport
import com.bane23.iptvplayer.data.storage.PublicFolder
import java.text.DateFormat
import java.util.Date

/** Vrednost reda „Izveštaj o grešci” u podešavanjima: vreme poslednjeg pada ili „Nema”. */
@Composable
fun crashReportValue(): String {
    val report by CrashLog.report.collectAsStateWithLifecycle()
    return report?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.timeMs)) }
        ?: stringResource(R.string.crash_report_none)
}

/**
 * Daje izveštaj korisniku: na telefonu kroz sistemsko „Podeli”, a na TV-u, gde aplikacija za
 * deljenje najčešće nema, kao tekstualni fajl u Downloads/Glowbox. Bez izveštaja samo javlja da ga nema.
 */
fun handOverCrashReport(context: Context) {
    val report = CrashLog.report.value
    if (report == null) {
        AppMessages.show(context, R.string.crash_report_empty)
        return
    }
    CrashLog.markSeen(context)
    if (DeviceType.isTv(context)) save(context, report) else share(context, report)
}

private fun share(context: Context, report: CrashReport) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_report_title))
        .putExtra(Intent.EXTRA_TEXT, report.text)
    val chooser = Intent.createChooser(send, context.getString(R.string.crash_report_title))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (_: Exception) {
        AppMessages.show(context, R.string.crash_report_failed, long = true)
    }
}

private fun save(context: Context, report: CrashReport) {
    val file = try {
        PublicFolder.directory(context)?.let { dir ->
            PublicFolder.freeFile(dir, FILE_NAME, "txt").also { it.writeText(report.text) }
        }
    } catch (_: Exception) {
        null
    }
    val message = if (file != null) {
        context.getString(R.string.crash_report_saved, "Downloads/${PublicFolder.NAME}/${file.name}")
    } else {
        context.getString(R.string.crash_report_failed)
    }
    AppMessages.show(message, long = true)
}

private const val FILE_NAME = "glowbox-izvestaj"

/**
 * Posle pada, pri sledećem pokretanju, jednom nudi izveštaj. Odbijanje ga ne briše: ostaje u
 * podešavanjima dok ga sledeći pad ne zameni.
 */
@Composable
fun CrashReportPrompt() {
    val report by CrashLog.report.collectAsStateWithLifecycle()
    if (report?.fresh != true) return
    val context = LocalContext.current
    val isTv = DeviceType.isTv(context)
    val later = { CrashLog.markSeen(context) }
    UpdateAlert(
        AlertContent(
            title = stringResource(R.string.crash_prompt_title),
            text = stringResource(if (isTv) R.string.crash_prompt_text_tv else R.string.crash_prompt_text),
            confirm = stringResource(if (isTv) R.string.crash_prompt_save else R.string.crash_prompt_share) to
                { handOverCrashReport(context) },
            onDismiss = later,
            dismiss = stringResource(R.string.crash_prompt_later) to later,
        ),
    )
}
